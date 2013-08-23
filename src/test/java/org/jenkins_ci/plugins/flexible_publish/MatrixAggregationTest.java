/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkins_ci.plugins.flexible_publish;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import hudson.matrix.Axis;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.FreeStyleBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;

import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkins_ci.plugins.run_condition.core.StringsMatchCondition;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 *
 */
public class MatrixAggregationTest extends HudsonTestCase {
    public void testIgnoredForFreeStyle() throws Exception{
        FreeStyleProject p = createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("param1", "")
        ));
        FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new StringsMatchCondition(
                                "${param1}",
                                "value1",
                                false
                        ),
                        new AggregationRecorder(),
                        new BuildStepRunner.Fail(),
                        true,
                        new AlwaysRun(),
                        new BuildStepRunner.Fail()
                )
        ));
        p.getPublishersList().add(publisher);
        p.save();
        
        // if param1 == value1, recorder runs.
        {
            FreeStyleBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value1"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNotNull(action);
            assertEquals(p.getFullName(), action.getProjectName());
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNull(aggregator);
        }
        
        // if param1 != value1, recorder does not run.
        {
            FreeStyleBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value2"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNull(aggregator);
        }
    }
    
    public void testNotConfiguredForParent() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("param1", "")
        ));
        FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new StringsMatchCondition(
                                "${param1}",
                                "value1",
                                false
                        ),
                        new AggregationRecorder(),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        ));
        p.getPublishersList().add(publisher);
        p.save();
        
        // if param1 == value1, recorder runs.
        {
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value1"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                action = run.getAction(AggregationRecorder.RecorderAction.class);
                assertNotNull(action);
                assertEquals(run.getParent().getFullName(), action.getProjectName());
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNotNull(aggregator);
            assertEquals(2, aggregator.size());
            for (MatrixConfiguration conf: p.getActiveConfigurations()) {
                assertTrue(aggregator.contains(conf.getFullName()));
            }
        }
        
        // if param1 != value1, recorder does not runs.
        {
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value2"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                action = run.getAction(AggregationRecorder.RecorderAction.class);
                assertNull(action);
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNull(aggregator);
        }
    }
    
    public void testConfiguredForParent() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("param1", "")
        ));
        FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new StringsMatchCondition(
                                "${axis1}",
                                "value1",
                                false
                        ),
                        new AggregationRecorder(),
                        new BuildStepRunner.Fail(),
                        true,
                        new StringsMatchCondition(
                                "${param1}",
                                "value1",
                                false
                        ),
                        new BuildStepRunner.Fail()
                )
        ));
        p.getPublishersList().add(publisher);
        p.save();
        
        // if param1 == value1, aggregation runs.
        {
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value1"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                if ("value1".equals(run.getParent().getCombination().get("axis1"))) {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNotNull(action);
                    assertEquals(run.getParent().getFullName(), action.getProjectName());
                } else {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNull(action);
                }
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNotNull(aggregator);
            assertEquals(1, aggregator.size());
            for(MatrixConfiguration conf: p.getActiveConfigurations()) {
                if ("value1".equals(conf.getCombination().get("axis1"))) {
                    assertTrue(aggregator.contains(conf.getFullName()));
                } else {
                    assertFalse(aggregator.contains(conf.getFullName()));
                }
            }
        }
        
        // if param1 != value1, aggregation runs.
        {
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value2"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                if ("value1".equals(run.getParent().getCombination().get("axis1"))) {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNotNull(action);
                    assertEquals(run.getParent().getFullName(), action.getProjectName());
                } else {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNull(action);
                }
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNull(aggregator);
        }
    }
    
    public void testRunner() throws Exception {
        // Run.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
            p.addProperty(new ParametersDefinitionProperty(
                    new StringParameterDefinition("param1", "")
            ));
            FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new StringsMatchCondition(
                                    "${nosuchparam}",
                                    "value1",
                                    false
                            ),
                            new AggregationRecorder(),
                            new BuildStepRunner.Run(),
                            true,
                            new StringsMatchCondition(
                                    "${param1}",
                                    "value1",
                                    false
                            ),
                            new BuildStepRunner.Fail()
                    )
            ));
            p.getPublishersList().add(publisher);
            p.save();
            
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value1"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                action = run.getAction(AggregationRecorder.RecorderAction.class);
                assertNotNull(action);
                assertEquals(run.getParent().getFullName(), action.getProjectName());
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNotNull(aggregator);
            assertEquals(2, aggregator.size());
            for(MatrixConfiguration conf: p.getActiveConfigurations()) {
                assertTrue(aggregator.contains(conf.getFullName()));
            }
        }
        
        // DontRun.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
            p.addProperty(new ParametersDefinitionProperty(
                    new StringParameterDefinition("param1", "")
            ));
            FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new StringsMatchCondition(
                                    "${nosuchparam}",
                                    "value1",
                                    false
                            ),
                            new AggregationRecorder(),
                            new BuildStepRunner.DontRun(),
                            true,
                            new StringsMatchCondition(
                                    "${param1}",
                                    "value1",
                                    false
                            ),
                            new BuildStepRunner.Fail()
                    )
            ));
            p.getPublishersList().add(publisher);
            p.save();
            
            MatrixBuild build = p.scheduleBuild2(
                    0,
                    new Cause.UserCause(),
                    new ParametersAction(new StringParameterValue("param1", "value1"))
            ).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                action = run.getAction(AggregationRecorder.RecorderAction.class);
                assertNull(action);
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNotNull(aggregator);
            assertEquals(0, aggregator.size());
        }
    }
    
    public void testRunnerForParent() throws Exception {
        // Run.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
            FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new StringsMatchCondition(
                                    "${axis1}",
                                    "value1",
                                    false
                            ),
                            new AggregationRecorder(),
                            new BuildStepRunner.Fail(),
                            true,
                            new StringsMatchCondition(
                                    "${nosuchparam}",
                                    "value1",
                                    false
                            ),
                            new BuildStepRunner.Run()
                    )
            ));
            p.getPublishersList().add(publisher);
            p.save();
            
            MatrixBuild build = p.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                if ("value1".equals(run.getParent().getCombination().get("axis1"))) {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNotNull(action);
                    assertEquals(run.getParent().getFullName(), action.getProjectName());
                } else {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNull(action);
                }
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNotNull(aggregator);
            assertEquals(1, aggregator.size());
            for(MatrixConfiguration conf: p.getActiveConfigurations()) {
                if ("value1".equals(conf.getCombination().get("axis1"))) {
                    assertTrue(aggregator.contains(conf.getFullName()));
                } else {
                    assertFalse(aggregator.contains(conf.getFullName()));
                }
            }
        }
        
        // DontRun.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
            FlexiblePublisher publisher = new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new StringsMatchCondition(
                                    "${axis1}",
                                    "value1",
                                    false
                            ),
                            new AggregationRecorder(),
                            new BuildStepRunner.Fail(),
                            true,
                            new StringsMatchCondition(
                                    "${nosuchparam}",
                                    "value1",
                                    false
                            ),
                            new BuildStepRunner.DontRun()
                    )
            ));
            p.getPublishersList().add(publisher);
            p.save();
            
            MatrixBuild build = p.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(build);
            AggregationRecorder.RecorderAction action
                = build.getAction(AggregationRecorder.RecorderAction.class);
            assertNull(action);
            for (MatrixRun run: build.getRuns()) {
                if ("value1".equals(run.getParent().getCombination().get("axis1"))) {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNotNull(action);
                    assertEquals(run.getParent().getFullName(), action.getProjectName());
                } else {
                    action = run.getAction(AggregationRecorder.RecorderAction.class);
                    assertNull(action);
                }
            }
            AggregationRecorder.AggregatorAction aggregator
                = build.getAction(AggregationRecorder.AggregatorAction.class);
            assertNull(aggregator);
        }
    }
}
