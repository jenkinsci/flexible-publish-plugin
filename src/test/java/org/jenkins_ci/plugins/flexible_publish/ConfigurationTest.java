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
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import hudson.Launcher;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import hudson.model.Saveable;
import hudson.tasks.BuildStep;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Mailer;
import hudson.tasks.Publisher;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.util.DescribableList;

import org.jenkins_ci.plugins.flexible_publish.strategy.FailAtEndExecutionStrategy;
import org.jenkins_ci.plugins.flexible_publish.strategy.FailFastExecutionStrategy;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkins_ci.plugins.run_condition.core.NeverRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import org.kohsuke.stapler.DataBoundConstructor;

import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import static org.htmlunit.html.HtmlFormUtil.submit;
import static org.junit.Assert.*;

/**
 *
 */
public class ConfigurationTest {
    private WebClient wc;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        wc = j.createWebClient();
    }

    protected MatrixProject createMatrixProject() throws IOException {
        MatrixProject p = j.createProject(MatrixProject.class);
        return p;
    }

    protected FreeStyleProject createFreeStyleProject() throws IOException {
        return createFreeStyleProject(createUniqueProjectName());
    }

    protected String createUniqueProjectName() {
        return "test"+ j.jenkins.getItems().size();
    }

    protected FreeStyleProject createFreeStyleProject(String name) throws IOException {
        return j.createProject(FreeStyleProject.class, name);
    }
    
    private void reconfigure(AbstractProject<?,?> p) throws Exception {
        HtmlPage page = wc.getPage(p, "configure");
        submit(page.getFormByName("config"));
    }

    @Test
    public void testWithoutPublishers() throws Exception {
        // There is a case that just doing this fails with some versions of Jenkins...
        FreeStyleProject p = createFreeStyleProject();
        reconfigure(p);
    }
    
    @Test
    @Issue("JENKINS-19985")
    public void testNullCondition() throws  Exception {
      FreeStyleProject p = createFreeStyleProject();
      ConditionalPublisher conditionalPublisher = new ConditionalPublisher(new AlwaysRun(),
              Collections.<BuildStep>singletonList(null),
              null,
              false,
              null,
              null);
      FlexiblePublisher fp = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
      p.getPublishersList().add(fp);
      p.save();
      j.jenkins.reload();

      DescribableList<Publisher, Descriptor<Publisher>> publishersList = ((FreeStyleProject) j.jenkins.getItemByFullName(p.getFullName()))
              .getPublishersList();
      FlexiblePublisher publisher = publishersList.get(FlexiblePublisher.class);
      List<ConditionalPublisher> publishers = publisher.getPublishers();
      conditionalPublisher = publishers.get(0);
      assertNotNull(conditionalPublisher.getPublisherList());
      assertTrue(conditionalPublisher.getPublisherList().isEmpty());
    }

    @Test
    public void testSingleCondition() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher.getRunner().getClass());
        assertFalse(conditionalPublisher.isConfiguredAggregation());
        assertNull(conditionalPublisher.getAggregationCondition());
        assertNull(conditionalPublisher.getAggregationRunner());
        
        trigger = (BuildTrigger)conditionalPublisher.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
    }

    @Test
    public void testMultipleConditions() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ArtifactArchiver archiver = new ArtifactArchiver("**/*.jar", "some/bad.jar", true);
        ConditionalPublisher conditionalPublisher1 = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        ConditionalPublisher conditionalPublisher2 = new ConditionalPublisher(
                new NeverRun(),
                archiver,
                new BuildStepRunner.DontRun(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(
                conditionalPublisher1,
                conditionalPublisher2
        ));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher1 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher1.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher1.getRunner().getClass());
        assertFalse(conditionalPublisher1.isConfiguredAggregation());
        assertNull(conditionalPublisher1.getAggregationCondition());
        assertNull(conditionalPublisher1.getAggregationRunner());
        
        trigger = (BuildTrigger)conditionalPublisher1.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
        
        conditionalPublisher2 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(1);
        assertEquals(NeverRun.class, conditionalPublisher2.getCondition().getClass());
        assertEquals(BuildStepRunner.DontRun.class, conditionalPublisher2.getRunner().getClass());
        assertFalse(conditionalPublisher2.isConfiguredAggregation());
        assertNull(conditionalPublisher2.getAggregationCondition());
        assertNull(conditionalPublisher2.getAggregationRunner());
        
        archiver = (ArtifactArchiver)conditionalPublisher2.getPublisher();
        assertEquals("**/*.jar", archiver.getArtifacts());
        assertEquals("some/bad.jar", archiver.getExcludes());
    }

    @Test
    public void testMultipleConditionsMultipleActions() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        ConditionalPublisher conditionalPublisher1 = new ConditionalPublisher(
                new AlwaysRun(),
                Arrays.<BuildStep>asList(
                        new BuildTrigger("anotherProject1", Result.SUCCESS),
                        new BuildTrigger("anotherProject2", Result.UNSTABLE)
                ),
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        ConditionalPublisher conditionalPublisher2 = new ConditionalPublisher(
                new NeverRun(),
                Arrays.<BuildStep>asList(
                        new ArtifactArchiver("**/*.jar", "some/bad.jar", true),
                        new ArtifactArchiver("**/*.class", "some/bad.class", false)
                ),
                new BuildStepRunner.DontRun(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(
                conditionalPublisher1,
                conditionalPublisher2
        ));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher1 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher1.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher1.getRunner().getClass());
        assertFalse(conditionalPublisher1.isConfiguredAggregation());
        assertNull(conditionalPublisher1.getAggregationCondition());
        assertNull(conditionalPublisher1.getAggregationRunner());
        assertEquals(Arrays.<Class<?>>asList(
                        BuildTrigger.class,
                        BuildTrigger.class
                ), 
                Lists.transform(conditionalPublisher1.getPublisherList(), new Function<BuildStep, Class<?>>() {
                    @Override
                    public Class<?> apply(BuildStep input) {
                        return input.getClass();
                    }
                })
        );
        {
            BuildTrigger trigger = (BuildTrigger)conditionalPublisher1.getPublisherList().get(0);
            assertEquals("anotherProject1", trigger.getChildProjectsValue());
            assertEquals(Result.SUCCESS, trigger.getThreshold());
        }
        {
            BuildTrigger trigger = (BuildTrigger)conditionalPublisher1.getPublisherList().get(1);
            assertEquals("anotherProject2", trigger.getChildProjectsValue());
            assertEquals(Result.UNSTABLE, trigger.getThreshold());
        }
        
        conditionalPublisher2 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(1);
        assertEquals(NeverRun.class, conditionalPublisher2.getCondition().getClass());
        assertEquals(BuildStepRunner.DontRun.class, conditionalPublisher2.getRunner().getClass());
        assertFalse(conditionalPublisher2.isConfiguredAggregation());
        assertNull(conditionalPublisher2.getAggregationCondition());
        assertNull(conditionalPublisher2.getAggregationRunner());
        assertEquals(Arrays.<Class<?>>asList(
                    ArtifactArchiver.class,
                    ArtifactArchiver.class
            ), 
            Lists.transform(conditionalPublisher2.getPublisherList(), new Function<BuildStep, Class<?>>() {
                @Override
                public Class<?> apply(BuildStep input) {
                    return input.getClass();
                }
            })
        );
        {
            ArtifactArchiver archiver = (ArtifactArchiver)conditionalPublisher2.getPublisherList().get(0);
            assertEquals("**/*.jar", archiver.getArtifacts());
            assertEquals("some/bad.jar", archiver.getExcludes());
        }
        {
            ArtifactArchiver archiver = (ArtifactArchiver)conditionalPublisher2.getPublisherList().get(1);
            assertEquals("**/*.class", archiver.getArtifacts());
            assertEquals("some/bad.class", archiver.getExcludes());
        }
    }

    @Test
    public void testMatrixWithAggregationCondition() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "values2")));
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                true,
                new NeverRun(),
                new BuildStepRunner.DontRun()
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher.getRunner().getClass());
        assertTrue(conditionalPublisher.isConfiguredAggregation());
        assertEquals(NeverRun.class, conditionalPublisher.getAggregationCondition().getClass());
        assertEquals(BuildStepRunner.DontRun.class, conditionalPublisher.getAggregationRunner().getClass());
        
        trigger = (BuildTrigger)conditionalPublisher.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
    }

    @Test
    public void testMatrixWithoutAggregationCondition() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "values2")));
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher.getRunner().getClass());
        assertFalse(conditionalPublisher.isConfiguredAggregation());
        assertNull(conditionalPublisher.getAggregationCondition());
        assertNull(conditionalPublisher.getAggregationRunner());
        
        trigger = (BuildTrigger)conditionalPublisher.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
    }

    @Test
    public void testMatrixEnableAggregationCondition() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "values2")));
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm configForm = page.getFormByName("config");
        List<HtmlInput> inputList = configForm.getInputsByName("_.configuredAggregation");
        assertNotNull(inputList);
        assertEquals(1, inputList.size());
        
        // Enable it!
        assertFalse(((HtmlCheckBoxInput)inputList.get(0)).isChecked());
        ((HtmlCheckBoxInput)inputList.get(0)).click();
        assertTrue(((HtmlCheckBoxInput)inputList.get(0)).isChecked());
        submit(configForm);
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher.getRunner().getClass());
        assertTrue(conditionalPublisher.isConfiguredAggregation());
        assertNotNull(conditionalPublisher.getAggregationCondition());
        assertNotNull(conditionalPublisher.getAggregationRunner());
        
        trigger = (BuildTrigger)conditionalPublisher.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
    }

    @Test
    public void testMatrixDisableAggregationCondition() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("axis1", "value1", "values2")));
        BuildTrigger trigger = new BuildTrigger("anotherProject", Result.SUCCESS);
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                trigger,
                new BuildStepRunner.Run(), 
                true,
                new NeverRun(),
                new BuildStepRunner.DontRun()
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm configForm = page.getFormByName("config");
        List<HtmlInput> inputList = configForm.getInputsByName("_.configuredAggregation");
        assertNotNull(inputList);
        assertEquals(1, inputList.size());
        
        // Enable it!
        assertTrue(((HtmlCheckBoxInput)inputList.get(0)).isChecked());
        ((HtmlCheckBoxInput)inputList.get(0)).click();
        assertFalse(((HtmlCheckBoxInput)inputList.get(0)).isChecked());
        submit(configForm);
        
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(AlwaysRun.class, conditionalPublisher.getCondition().getClass());
        assertEquals(BuildStepRunner.Run.class, conditionalPublisher.getRunner().getClass());
        assertFalse(conditionalPublisher.isConfiguredAggregation());
        assertNull(conditionalPublisher.getAggregationCondition());
        assertNull(conditionalPublisher.getAggregationRunner());
        
        trigger = (BuildTrigger)conditionalPublisher.getPublisher();
        assertEquals("anotherProject", trigger.getChildProjectsValue());
        assertEquals(Result.SUCCESS, trigger.getThreshold());
    }

    @Test
    public void testNewInstanceDifferFromDataBoundConstructor() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> testDataPublishers
            = new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(Saveable.NOOP);
        testDataPublishers.add(new DummyTestDataPublisher());
        JUnitResultArchiver archiver = new JUnitResultArchiver("**/*.xml", true, testDataPublishers);
        
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                archiver,
                new BuildStepRunner.Run(), 
                false,
                null,
                null
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(conditionalPublisher));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        archiver = (JUnitResultArchiver)conditionalPublisher.getPublisher();
        assertEquals("**/*.xml", archiver.getTestResults());
        assertTrue(archiver.isKeepLongStdio());
        assertNotNull(archiver.getTestDataPublishers());
        assertEquals(1, archiver.getTestDataPublishers().size());
        assertEquals(DummyTestDataPublisher.class, archiver.getTestDataPublishers().get(0).getClass());
    }
    
    @Issue("JENKINS-26452")
    @Test
    public void testNoPublisher() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        
        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Collections.<BuildStep>emptyList(),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));
        
        p.save();
        
        // This doesn't fail till Jenkins 1.500.
        reconfigure(p);
        
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(0, p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0).getPublisherList().size());
    }
    
    @Issue("JENKINS-26452")
    @LocalData
    @Test
    public void testRecoverFrom26452() throws Exception {
        FreeStyleProject p = j.jenkins.getItemByFullName("affectedBy26452", FreeStyleProject.class);
        assertNotNull(p);
        
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(0, p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0).getPublisherList().size());
    }
    
    public static class DummyTestDataPublisher extends TestDataPublisher {
        @DataBoundConstructor
        public DummyTestDataPublisher() {
        }
        
        @Override
        public Data getTestData(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener, TestResult testResult)
                throws IOException, InterruptedException {
            return null;
        }
        
        @TestExtension
        public static class DescriptorImpl extends Descriptor<TestDataPublisher> {
            @Override
            public String getDisplayName() {
                return "DummyTestDataPublisher";
            }
            
        }
    }

    @Test
    public void testExecutionStrategy() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        ConditionalPublisher conditionalPublisher1 = new ConditionalPublisher(
                new AlwaysRun(),
                Arrays.<BuildStep>asList(
                        new BuildTrigger("anotherProject1", Result.SUCCESS)
                ),
                new BuildStepRunner.Run(), 
                false,
                null,
                null,
                new FailFastExecutionStrategy()
        );
        ConditionalPublisher conditionalPublisher2 = new ConditionalPublisher(
                new NeverRun(),
                Arrays.<BuildStep>asList(
                        new ArtifactArchiver("**/*.jar", "some/bad.jar", true)
                ),
                new BuildStepRunner.DontRun(), 
                false,
                null,
                null,
                new FailAtEndExecutionStrategy()
        );
        FlexiblePublisher flexiblePublisher = new FlexiblePublisher(Arrays.asList(
                conditionalPublisher1,
                conditionalPublisher2
        ));
        p.getPublishersList().add(flexiblePublisher);
        p.save();
        
        reconfigure(p);
        
        conditionalPublisher1 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(0);
        assertEquals(FailFastExecutionStrategy.class, conditionalPublisher1.getExecutionStrategy().getClass());
        conditionalPublisher2 = p.getPublishersList().get(FlexiblePublisher.class).getPublishers().get(1);
        assertEquals(FailAtEndExecutionStrategy.class, conditionalPublisher2.getExecutionStrategy().getClass());
    }
}
