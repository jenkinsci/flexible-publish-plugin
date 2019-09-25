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

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.AxisList;
import hudson.matrix.Combination;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.ArtifactArchiver;

import org.jenkins_ci.plugins.flexible_publish.strategy.FailAtEndExecutionStrategy;
import org.jenkins_ci.plugins.flexible_publish.strategy.FailFastExecutionStrategy;
import org.jenkins_ci.plugins.flexible_publish.testutils.AggregationRecorder;
import org.jenkins_ci.plugins.flexible_publish.testutils.FileWriteBuilder;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkins_ci.plugins.run_condition.core.StringsMatchCondition;
import org.jvnet.hudson.test.Bug;
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
    
    public void testMiltipleActions() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new AggregationRecorder(),
                                new AggregationRecorder()
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));
        p.save();
        
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertBuildStatusSuccess(build);
        assertEquals(2, build.getActions(AggregationRecorder.AggregatorAction.class).size());
    }
    
    
    public void testMixedWithNonaggregateble() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "value2")));
        p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new ArtifactArchiver("**/*", "", false),
                                new AggregationRecorder()
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));
        p.save();
        
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertBuildStatusSuccess(build);
        assertNotNull(build.getAction(AggregationRecorder.AggregatorAction.class));
    }
    
    public static class FailureAggregationRecorder extends Recorder implements MatrixAggregatable {
        public FailureAggregationRecorder() {
        }
        
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            return true;
        }
        
        @Override
        public MatrixAggregator createAggregator(MatrixBuild build,
                Launcher launcher, BuildListener listener) {
            return new MatrixAggregator(build, launcher, listener) {
                @Override
                public boolean endBuild() throws InterruptedException, IOException {
                    // aggregation fails
                    return false;
                }
            };
        }
        
        @Override
        public DescriptorImpl getDescriptor() {
            return (DescriptorImpl)super.getDescriptor();
        }
            
        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @SuppressWarnings("rawtypes")
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "FailureAggregationRecorder";
            }
        }
    }
    
    public static class ThrowGeneralExceptionRecorder extends Recorder implements MatrixAggregatable {
        public ThrowGeneralExceptionRecorder() {
        }
        
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            return true;
        }
        
        @Override
        public MatrixAggregator createAggregator(MatrixBuild build,
                Launcher launcher, BuildListener listener) {
            return new MatrixAggregator(build, launcher, listener) {
                @Override
                public boolean endBuild() throws InterruptedException, IOException {
                    throw new IOException("Intended failure");
                }
            };
        }
        
        @Override
        public DescriptorImpl getDescriptor() {
            return (DescriptorImpl)super.getDescriptor();
        }
            
        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @SuppressWarnings("rawtypes")
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "FailureAggregationRecorder";
            }
        }
    }
    
    public void testRunAggregatorsWithFailAtEnd() throws Exception {
        // matrix-project executes all aggregators (endBuild) even one of them failed.
        // This demonstrates an aggregation of a matrix project works as "Fail at end".
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FailureAggregationRecorder());
            p.getPublishersList().add(new AggregationRecorder());
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatusSuccess(b);
                // results of aggregation deosn't affect build result.
                // usually aggregators update results by themselves.
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        // matrix-project changes the build result to failure when an aggregator throws Exception.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new ThrowGeneralExceptionRecorder());
            p.getPublishersList().add(new AggregationRecorder());
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // matrix-project 1.4 doesn't run following aggregations.
            // assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        //// Flexible Publish runs as matrix-projects do.
        
        // flexible-publish executes all aggregators even one of them failed.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailureAggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatusSuccess(b);
                // results of aggregation deosn't affect build result.
                // usually aggregators update results by themselves.
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        // flexible-publish executes all aggregators even one of them throws Exception.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThrowGeneralExceptionRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        
        //// FailAtEndExecutionStrategy works as so also for publishers in a condition.
        
        // FailAtEndExecutionStrategy executes all aggregators even one of them failed.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailureAggregationRecorder(),
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatusSuccess(b);
                // results of aggregation deosn't affect build result.
                // usually aggregators update results by themselves.
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        // FailAtEndExecutionStrategy executes all aggregators even one of them throws Exception.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThrowGeneralExceptionRecorder(),
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailAtEndExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
    }
    
    public void testRunAggregatorsWithFailFast() throws Exception {
        // flexible-publish executes all aggregators even one of them failed.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailureAggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatusSuccess(b);
                // results of aggregation deosn't affect build result.
                // usually aggregators update results by themselves.
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        // flexible-publish executes all aggregators even one of them throws Exception.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThrowGeneralExceptionRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // AggregationRecorder is executed even a prior aggregator fails.
            assertNotNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        
        //// ConditionalPublisher doesn't work so with FailFastExecutionStrategy.
        
        // FailFastExecutionStrategy stops executing aggregators when one of them failed.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailureAggregationRecorder(),
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatusSuccess(b);
                // results of aggregation deosn't affect build result.
                // usually aggregators update results by themselves.
            
            // AggregationRecorder isn't executed as a prior aggregator fails.
            assertNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
        
        // FailFastExecutionStrategy stops executing aggregators when one of them throws Exception.
        {
            MatrixProject p = createMatrixProject();
            p.setAxes(new AxisList(new TextAxis("axis1", "value1")));
            
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThrowGeneralExceptionRecorder(),
                                    new AggregationRecorder()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null,
                            new FailFastExecutionStrategy()
                    )
            )));
            
            MatrixBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // AggregationRecorder isn't executed as a prior aggregator fails.
            assertNull(b.getAction(AggregationRecorder.AggregatorAction.class));
        }
    }
    
    @Bug(28585)
    public void testOnlyNonaggregateble() throws Exception {
        MatrixProject p = createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new StringsMatchCondition("${axis1}", "value1", false),
                        Arrays.<BuildStep>asList(
                                new ArtifactArchiver("**/*", "", false)
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null,
                        new FailFastExecutionStrategy()
                )
        )));
        
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertBuildStatusSuccess(build);
        
        {
            MatrixRun r = build.getRun(new Combination(axisList, "value1"));
            assertBuildStatusSuccess(r);
            assertEquals(Arrays.asList("artifact.txt"), Arrays.asList(r.getArtifactsDir().list()));
        }
        {
            MatrixRun r = build.getRun(new Combination(axisList, "value2"));
            assertBuildStatusSuccess(r);
            assertNull(r.getArtifactsDir().list());
        }
    }

    private MatrixProject createMatrixProject() throws IOException {
        return jenkins.createProject(MatrixProject.class, createUniqueProjectName());
    }
}
