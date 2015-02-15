/*
 * The MIT License
 * 
 * Copyright (c) 2014 IKEDA Yasuyuki
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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.ArtifactArchiver;

import org.jenkins_ci.plugins.flexible_publish.testutils.FileWriteBuilder;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkins_ci.plugins.run_condition.core.StringsMatchCondition;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 *
 */
public class FlexiblePublisherTest extends HudsonTestCase {
    @LocalData
    public void testMigrationFrom0_12() throws Exception {
        FreeStyleProject p = jenkins.getItemByFullName("migration_from_0.12", FreeStyleProject.class);
        FlexiblePublisher fp = p.getPublishersList().get(FlexiblePublisher.class);
        ConditionalPublisher cp = fp.getPublishers().get(0);
        assertEquals(
                Arrays.<Class<?>>asList(ArtifactArchiver.class),
                Lists.transform(cp.getPublisherList(), new Function<BuildStep, Class<?>>() {
                    public Class<?> apply(BuildStep input) {
                        return input.getClass();
                    }
                })
        );
        ArtifactArchiver aa = (ArtifactArchiver)cp.getPublisherList().get(0);
        assertEquals("artifact.txt", aa.getArtifacts());
        
        {
            @SuppressWarnings("deprecation")
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(
                    new StringParameterValue("SWITCH", "off")
            )).get();
            assertBuildStatusSuccess(b);
            assertFalse(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        {
            @SuppressWarnings("deprecation")
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(
                    new StringParameterValue("SWITCH", "on")
            )).get();
            assertBuildStatusSuccess(b);
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
    }
    
    public void testMultipleConditionsMultipleActions() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("SWITCH", "off")
        ));
        
        p.getBuildersList().add(new FileWriteBuilder("artifact1.txt", "blahblahblah"));
        p.getBuildersList().add(new FileWriteBuilder("artifact2.txt", "blahblahblah"));
        p.getBuildersList().add(new FileWriteBuilder("artifact3.txt", "blahblahblah"));
        p.getBuildersList().add(new FileWriteBuilder("artifact4.txt", "blahblahblah"));
        
        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new StringsMatchCondition("${SWITCH}", "off", false),
                        Arrays.<BuildStep>asList(
                                new ArtifactArchiver("artifact1.txt", "", false),
                                new ArtifactArchiver("artifact2.txt", "", false)
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                ),
                new ConditionalPublisher(
                        new StringsMatchCondition("${SWITCH}", "on", false),
                        Arrays.<BuildStep>asList(
                                new ArtifactArchiver("artifact3.txt", "", false),
                                new ArtifactArchiver("artifact4.txt", "", false)
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));
        
        {
            @SuppressWarnings("deprecation")
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(
                    new StringParameterValue("SWITCH", "off")
            )).get();
            assertBuildStatusSuccess(b);
            assertTrue(new File(b.getArtifactsDir(), "artifact1.txt").exists());
            assertTrue(new File(b.getArtifactsDir(), "artifact2.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact3.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact4.txt").exists());
        }
        
        {
            @SuppressWarnings("deprecation")
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(
                    new StringParameterValue("SWITCH", "on")
            )).get();
            assertBuildStatusSuccess(b);
            assertFalse(new File(b.getArtifactsDir(), "artifact1.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact2.txt").exists());
            assertTrue(new File(b.getArtifactsDir(), "artifact3.txt").exists());
            assertTrue(new File(b.getArtifactsDir(), "artifact4.txt").exists());
        }
        
        {
            @SuppressWarnings("deprecation")
            FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserCause(), new ParametersAction(
                    new StringParameterValue("SWITCH", "badValue")
            )).get();
            assertBuildStatusSuccess(b);
            assertFalse(new File(b.getArtifactsDir(), "artifact1.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact2.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact3.txt").exists());
            assertFalse(new File(b.getArtifactsDir(), "artifact4.txt").exists());
        }
    }
    
    public static class FailurePublisher extends Recorder {
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            return false;
        }
        
        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "FailurePublisher";
            }
        }
    }
    
    public static class ThorwAbortExceptionPublisher extends Recorder {
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            throw new AbortException("Intended abort");
            //return true;
        }
        
        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "ThorwAbortExceptionPublisher";
            }
        }
    }
    
    public static class ThorwGeneralExceptionPublisher extends Recorder {
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            throw new IOException("Intended abort");
            //return true;
        }
        
        @Extension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "ThorwGeneralExceptionPublisher";
            }
        }
    }
    
    public void testRunAllPublishers() throws Exception {
        // Jenkins executes all publishers even one of them failed.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FailurePublisher());
            p.getPublishersList().add(new ArtifactArchiver("**/*", "", false));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        // Jenkins executes all publishers even one of them throws AbortException.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new ThorwAbortExceptionPublisher());
            p.getPublishersList().add(new ArtifactArchiver("**/*", "", false));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.SUCCESS, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
            
            // Somehow Jenkins prints stacktrace for AbortException. You can see that here.
            // System.out.println(b.getLog());
        }
        
        // Jenkins executes all publishers even one of them throws any Exceptions.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new ThorwGeneralExceptionPublisher());
            p.getPublishersList().add(new ArtifactArchiver("**/*", "", false));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        //// Flexible Publish should run as Jenkins core do.
        
        // Flexible Publish executes all publishers even one of them failed.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailurePublisher()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        // Flexible Publish executes all publishers even one of them throws AbortException.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThorwAbortExceptionPublisher()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        // Flexible Publish executes all publishers even one of them throws any Exceptions.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThorwGeneralExceptionPublisher()
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        
        //// Of course, ConditionalPublisher should do so.
        
        // Flexible Publish executes all publishers in a condition even one of them failed.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new FailurePublisher(),
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        // Flexible Publish executes all publishers even one of them throws AbortException.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThorwAbortExceptionPublisher(),
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
        
        // Flexible Publish executes all publishers even one of them throws any Exceptions.
        {
            FreeStyleProject p = createFreeStyleProject();
            
            p.getBuildersList().add(new FileWriteBuilder("artifact.txt", "blahblahblah"));
            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ThorwGeneralExceptionPublisher(),
                                    new ArtifactArchiver("**/*", "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));
            
            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertBuildStatus(Result.FAILURE, b);
            
            // ArtifactArchiver is executed even prior publisher fails.
            assertTrue(new File(b.getArtifactsDir(), "artifact.txt").exists());
        }
    }
}
