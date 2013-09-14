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
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildTrigger;
import hudson.tasks.Mailer;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction.Data;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.util.DescribableList;

import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.jenkins_ci.plugins.run_condition.core.NeverRun;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 *
 */
public class ConfigurationTest extends HudsonTestCase {
    private WebClient wc;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        wc = new WebClient();
    }
    
    private void reconfigure(AbstractProject<?,?> p) throws Exception {
        HtmlPage page = wc.getPage(p, "configure");
        submit(page.getFormByName("config"));
    }
    
    public void testWithoutPublishers() throws Exception {
        // There is a case that just doing this fails with some versions of Jenkins...
        FreeStyleProject p = createFreeStyleProject();
        reconfigure(p);
    }
    
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
    
    public void testMiltipleConditions() throws Exception {
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
        assertTrue(archiver.isLatestOnly());
    }
    
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
    
    public void testNoDataBoundConstructor() throws Exception {
        // assert that Mailer does not have a constructor with DataBoundConstructor.
        {
            for(Constructor<?> c: Mailer.class.getConstructors()) {
                assertFalse(c.isAnnotationPresent(DataBoundConstructor.class));
            }
        }
        
        FreeStyleProject p = createFreeStyleProject();
        Mailer mailer = new Mailer();
        mailer.recipients = "test@example.com";
        mailer.dontNotifyEveryUnstableBuild = true;
        mailer.sendToIndividuals = true;
        
        ConditionalPublisher conditionalPublisher = new ConditionalPublisher(
                new AlwaysRun(),
                mailer,
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
        mailer = (Mailer)conditionalPublisher.getPublisher();
        assertEquals("test@example.com", mailer.recipients);
        // mailer.dontNotifyEveryUnstableBuild does not restored, for it is treated in a special way.
        // Detail: there can be multiple "mailer_notifyEveryUnstableBuild"s in a form.
        //assertTrue(mailer.dontNotifyEveryUnstableBuild);
        assertTrue(mailer.sendToIndividuals);
    }
    
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
}
