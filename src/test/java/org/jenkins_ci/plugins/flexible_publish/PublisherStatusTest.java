package org.jenkins_ci.plugins.flexible_publish;

import hudson.model.*;
import hudson.tasks.*;
import org.apache.commons.io.FileUtils;
import org.jenkins_ci.plugins.flexible_publish.testutils.*;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

/**
* Test that checks returned publisher statuses in different order and returned statuses
* @author Kanstantsin Shautsou
*/
public class PublisherStatusTest {
    private final String testFileName = "result.txt";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Execute all ConditionalPublishers even one of their Actions fail
     */
    @Test
    public void testFalseStatus() throws IOException, ExecutionException, InterruptedException {
            FreeStyleProject p = j.createFreeStyleProject();

            p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new TrueFalsePublisher(false) // fail ConditionalPublisher
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    ),
                    new ConditionalPublisher(
                            new AlwaysRun(),
                            Arrays.<BuildStep>asList(
                                    new ResultWriterPublisher(testFileName),
                                    new ArtifactArchiver(testFileName, "", false)
                            ),
                            new BuildStepRunner.Fail(),
                            false,
                            null,
                            null
                    )
            )));

            FreeStyleBuild b = p.scheduleBuild2(0).get();
            assertEquals("Build must fail, because we used false Publisher", b.getResult(), Result.FAILURE);
            File file = new File(b.getArtifactsDir(), testFileName);
            assertTrue("Second ConditionalPublisher must run", file.exists());
            assertTrue("Second ConditionalPublisher must see FAILURE status",
                    FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }


    @Test
    public void testAbortExceptionStatus() throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new AbortExceptionPublisher() // fail ConditionalPublisher
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                ),
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new ResultWriterPublisher(testFileName),
                                new ArtifactArchiver(testFileName, "", false)
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertEquals("Build must fail, because we used false Publisher", b.getResult(), Result.FAILURE);
        File file = new File(b.getArtifactsDir(), testFileName);
        assertTrue("Second ConditionalPublisher must run", file.exists());
        assertTrue("Second ConditionalPublisher must see FAILURE status",
                FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }


    /**
     * According to current design any non AbortException fails the whole FlexiblePublisher
     */
    @Test
    public void testIOExceptionStatus() throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject p = j.createFreeStyleProject();

        p.getPublishersList().add(new FlexiblePublisher(Arrays.asList(
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new IOExceptionPublisher() // fail ConditionalPublisher
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                ),
                new ConditionalPublisher(
                        new AlwaysRun(),
                        Arrays.<BuildStep>asList(
                                new ResultWriterPublisher(testFileName),
                                new ArtifactArchiver(testFileName, "", false)
                        ),
                        new BuildStepRunner.Fail(),
                        false,
                        null,
                        null
                )
        )));

        FreeStyleBuild b = p.scheduleBuild2(0).get();
        assertEquals("Build must fail, because we used false Publisher", b.getResult(), Result.FAILURE);
        File file = new File(b.getArtifactsDir(), testFileName);
        assertFalse("Second ConditionalPublisher mustn't run", file.exists());

        // uncomment if behavior will be changed
//        assertTrue("Second ConditionalPublisher must run", file.exists());
//        assertTrue("Second ConditionalPublisher must see FAILURE status",
//                FileUtils.readFileToString(file).equals(Result.FAILURE.toString()));
    }


}
