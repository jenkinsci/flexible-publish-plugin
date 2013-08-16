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

import groovy.mock.interceptor.MockProxyMetaClass;
import hudson.model.DependencyGraph;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.tasks.BuildTrigger;

import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.NumericalComparisonCondition;
import org.jenkins_ci.plugins.run_condition.core.StringsMatchCondition;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;

/**
 * Tests for used with Triggers.
 */
public class TriggerTest extends HudsonTestCase {
    public void testRunCondition() throws Exception{
        // p1 triggers p2 if p1 is triggered with parameter "trigger_p2" is true.
        FreeStyleProject p1 = createFreeStyleProject();
        FreeStyleProject p2 = createFreeStyleProject();
        
        BooleanParameterDefinition triggerP2Def = new BooleanParameterDefinition(
                "trigger_p2",
                false,
                "Whether trigger p2");
        p1.addProperty(new ParametersDefinitionProperty(triggerP2Def));
        
        RunCondition cond = new StringsMatchCondition("${trigger_p2}", "true", false);
        BuildTrigger trigger = new BuildTrigger(p2.getName(), Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(cond, trigger, new BuildStepRunner.Run());
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p1.getPublishersList().add(flexiblePublisher);
        
        p1.save();
        
        jenkins.rebuildDependencyGraph();
        
        // Trigger p1 with trigger_p2 = true.
        {
            BooleanParameterValue triggerP2 = new BooleanParameterValue("trigger_p2", true);
            ParametersAction action = new ParametersAction(triggerP2);
            FreeStyleBuild p1Build = p1.scheduleBuild2(0, new Cause.UserCause(), action).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(p1Build);
            
            waitUntilNoActivityUpTo(60 * 1000);
            FreeStyleBuild p2Build = p2.getLastBuild();
            assertNotNull(p2Build);
            Cause.UpstreamCause cause = p2Build.getCause(Cause.UpstreamCause.class);
            assertEquals(p1.getFullName(), cause.getUpstreamProject());
            assertEquals(p1Build.getNumber(), cause.getUpstreamBuild());
            
            p2Build.delete();
        }
        
        // Trigger p1 with trigger_p2 = false
        {
            assertNull(p2.getLastBuild());
            
            BooleanParameterValue triggerP2 = new BooleanParameterValue("trigger_p2", false);
            ParametersAction action = new ParametersAction(triggerP2);
            FreeStyleBuild p1Build = p1.scheduleBuild2(0, new Cause.UserCause(), action).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(p1Build);
            
            waitUntilNoActivityUpTo(60 * 1000);
            FreeStyleBuild p2Build = p2.getLastBuild();
            assertNull(p2Build);
        }
        
        // Trigger p1 with trigger_p2 = true.
        // But p2 does not triggered when p1 is unstable
        // (Specification in BuildTrigger)
        p1.getBuildersList().add(new MockBuilder(Result.UNSTABLE));
        {
            assertNull(p2.getLastBuild());
            
            BooleanParameterValue triggerP2 = new BooleanParameterValue("trigger_p2", true);
            ParametersAction action = new ParametersAction(triggerP2);
            FreeStyleBuild p1Build = p1.scheduleBuild2(0, new Cause.UserCause(), action).get(60, TimeUnit.SECONDS);
            assertBuildStatus(Result.UNSTABLE, p1Build);
            
            waitUntilNoActivityUpTo(60 * 1000);
            FreeStyleBuild p2Build = p2.getLastBuild();
            assertNull(p2Build);
        }
    }
    
    public void testBuildStepRunner() throws Exception{
        // p1 triggers p2 if p1 is triggered with parameter "trigger_p2" is true.
        FreeStyleProject p1 = createFreeStyleProject();
        FreeStyleProject p2 = createFreeStyleProject();
        
        BuildTrigger trigger = new BuildTrigger(p2.getName(), Result.SUCCESS);
        RunCondition failureCondition = new NumericalComparisonCondition(
                "X.XXX", "1.23", new NumericalComparisonCondition.EqualTo()
        );
        
        // Run on exception
        {
            ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                    failureCondition,
                    trigger,
                    new BuildStepRunner.Run()
            );
            FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
            p1.getPublishersList().clear();
            p1.getPublishersList().add(flexiblePublisher);
            p1.save();
            jenkins.rebuildDependencyGraph();
            
            FreeStyleBuild p1Build = p1.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(p1Build);
            
            waitUntilNoActivityUpTo(60 * 1000);
            FreeStyleBuild p2Build = p2.getLastBuild();
            assertNotNull(p2Build);
            Cause.UpstreamCause cause = p2Build.getCause(Cause.UpstreamCause.class);
            assertEquals(p1.getFullName(), cause.getUpstreamProject());
            assertEquals(p1Build.getNumber(), cause.getUpstreamBuild());
            
            p2Build.delete();
        }
        
        // Don't run on exception
        {
            ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                    failureCondition,
                    trigger,
                    new BuildStepRunner.DontRun()
            );
            FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
            p1.getPublishersList().clear();
            p1.getPublishersList().add(flexiblePublisher);
            p1.save();
            jenkins.rebuildDependencyGraph();
            
            assertNull(p2.getLastBuild());
            
            FreeStyleBuild p1Build = p1.scheduleBuild2(0).get(60, TimeUnit.SECONDS);
            assertBuildStatusSuccess(p1Build);
            
            waitUntilNoActivityUpTo(60 * 1000);
            assertNull(p2.getLastBuild());
        }
    }
}
