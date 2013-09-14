/*
 * The MIT License
 *
 * Copyright (C) 2011 by Anthony Robinson
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

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStep;
import net.sf.json.JSONObject;

import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;

public class ConditionalPublisher implements Describable<ConditionalPublisher>, DependecyDeclarer {

    private final RunCondition condition;
    private final BuildStep publisher;
    private BuildStepRunner runner;
    
    // used for multiconfiguration projects with MatrixAggregatable.
    // if null, used condition and publlisher.
    private final RunCondition aggregationCondition;
    private final BuildStepRunner aggregationRunner;

    public ConditionalPublisher(final RunCondition condition, final BuildStep publisher, final BuildStepRunner runner) {
        this(condition, publisher, runner, false, null, null);
    }
    
    /**
     * @param condition
     * @param publisher
     * @param runner
     * @param configuredAggregation
     * @param aggregationCondition
     * @param aggregationRunner
     * @see ConditionalPublisherDescriptor#newInstance(StaplerRequest, JSONObject)
     */
    @DataBoundConstructor
    public ConditionalPublisher(final RunCondition condition, final BuildStep publisher, final BuildStepRunner runner,
            boolean configuredAggregation, final RunCondition aggregationCondition, final BuildStepRunner aggregationRunner) {
        this.condition = condition;
        this.publisher = publisher;
        this.runner = runner;
        if (configuredAggregation) {
            this.aggregationCondition = aggregationCondition;
            this.aggregationRunner = aggregationRunner;
        } else {
            this.aggregationCondition = null;
            this.aggregationRunner = null;
        }
    }

    public RunCondition getCondition() {
        return condition;
    }

    public BuildStep getPublisher() {
        return publisher;
    }

    public BuildStepRunner getRunner() {
        return runner;
    }

    public RunCondition getAggregationCondition() {
        return aggregationCondition;
    }

    public BuildStepRunner getAggregationRunner() {
        return aggregationRunner;
    }

    public boolean isConfiguredAggregation() {
        return getAggregationCondition() != null;
    }

    public ConditionalPublisherDescriptor getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(ConditionalPublisherDescriptor.class);
    }

    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
        return publisher.getProjectActions(project);
    }

    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {
        return runner.prebuild(condition, publisher, build, listener);
    }

    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                                                                                throws InterruptedException, IOException {
        return runner.perform(condition, publisher, build, launcher, listener);
    }

    public Object readResolve() {
        if (runner == null)
            runner = new BuildStepRunner.Fail();
        return this;
    }

    @Extension
    public static class ConditionalPublisherDescriptor extends Descriptor<ConditionalPublisher> {

        @Override
        public String getDisplayName() {
            return "Never seen - one hopes :-)";
        }

        public DescriptorExtensionList<BuildStepRunner, BuildStepRunner.BuildStepRunnerDescriptor> getBuildStepRunners() {
            return BuildStepRunner.all();
        }

        public BuildStepRunner.BuildStepRunnerDescriptor getDefaultBuildStepRunner() {
            return Hudson.getInstance().getDescriptorByType(BuildStepRunner.Fail.FailDescriptor.class);
        }

        public List<? extends Descriptor<? extends RunCondition>> getRunConditions() {
            return RunCondition.all();
        }

        public RunCondition.RunConditionDescriptor getDefaultRunCondition() {
            return Hudson.getInstance().getDescriptorByType(AlwaysRun.AlwaysRunDescriptor.class);
        }

        public List<? extends Descriptor<? extends BuildStep>> getAllowedPublishers(Object project) {
            if (!(project instanceof AbstractProject))
                return Collections.singletonList(getDefaultPublisher());
            return Hudson.getInstance().getDescriptorByType(FlexiblePublisher.FlexiblePublisherDescriptor.class).getPublisherLister()
                                                                                                            .getAllowedPublishers((AbstractProject<?,?>) project);
        }

        public Descriptor<? extends BuildStep> getDefaultPublisher() {
            return Hudson.getInstance().getDescriptorByType(ArtifactArchiver.DescriptorImpl.class);
        }

        public boolean isMatrixProject(Object it) {
            return (it instanceof MatrixProject);
        }

        /**
         * Build a new instance from parameters a user input in a configuration page.
         * 
         * Usually, it is done by {@link StaplerRequest#bindJSON(Class, JSONObject)}, 
         * and {@link DataBoundConstructor} of classes of posted objects.
         * 
         * But we have to use {@link Descriptor#newInstance(StaplerRequest, JSONObject)}
         * for classes without {@link DataBoundConstructor} (such as {@link hudson.tasks.Mailer})
         * and classes with {@link Descriptor#newInstance(StaplerRequest, JSONObject)}
         * doing different from their constructors with {@link DataBoundConstructor}
         * (such as {@link hudson.tasks.junit.JUnitResultArchiver}).
         * 
         * @param req
         * @param formData
         * @return
         * @throws hudson.model.Descriptor.FormException
         * @see hudson.model.Descriptor#newInstance(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
         * @see ConditionalPublisher#ConditionalPublisher(RunCondition, BuildStep, BuildStepRunner, boolean, RunCondition, BuildStepRunner)
         */
        @Override
        public ConditionalPublisher newInstance(StaplerRequest req, JSONObject formData)
                throws hudson.model.Descriptor.FormException {
            RunCondition condition = null;
            BuildStepRunner runner = null;
            BuildStep publisher = null;
            boolean configuredAggregation = false;
            RunCondition aggregationCondition = null;
            BuildStepRunner aggregationRunner = null;
            
            if (formData != null) {
                condition = req.bindJSON(RunCondition.class, formData.getJSONObject("condition"));
                runner = req.bindJSON(BuildStepRunner.class, formData.getJSONObject("runner"));
                if (formData.has("configuredAggregation")) {
                    configuredAggregation = formData.getBoolean("configuredAggregation");
                    aggregationCondition = req.bindJSON(RunCondition.class, formData.getJSONObject("aggregationCondition"));
                    aggregationRunner = req.bindJSON(BuildStepRunner.class, formData.getJSONObject("aggregationRunner"));
                }
                
                publisher = bindJSONWithDescriptor(req, formData, "publisher");
            }
            return new ConditionalPublisher(
                    condition,
                    publisher,
                    runner,
                    configuredAggregation,
                    aggregationCondition,
                    aggregationRunner
            );
        }

        /**
         * Construct an object from parameters input by a user.
         * 
         * Not using {@link DataBoundConstructor},
         * but using {@link Descriptor#newInstance(StaplerRequest, JSONObject)}.
         * 
         * @param req
         * @param formData
         * @param fieldName
         * @return
         * @throws hudson.model.Descriptor.FormException
         */
        private BuildStep bindJSONWithDescriptor(StaplerRequest req, JSONObject formData, String fieldName)
                throws hudson.model.Descriptor.FormException {
            formData = formData.getJSONObject(fieldName);
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            if (!formData.has("stapler-class")) {
                throw new FormException("No stapler-class is specified", fieldName);
            }
            String clazzName = formData.getString("stapler-class");
            if (clazzName == null) {
                throw new FormException("No stapler-class is specified", fieldName);
            }
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Describable<?>> clazz = (Class<? extends Describable<?>>)Jenkins.getInstance().getPluginManager().uberClassLoader.loadClass(clazzName);
                Descriptor<?> d = Jenkins.getInstance().getDescriptorOrDie(clazz);
                return (BuildStep)d.newInstance(req, formData);
            } catch(ClassNotFoundException e) {
                throw new FormException(
                        String.format("Failed to instantiate %s", clazz),
                        e,
                        fieldName
                );
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        if (publisher instanceof DependecyDeclarer) {
            ((DependecyDeclarer)publisher).buildDependencyGraph(owner,
                    new ConditionalDependencyGraphWrapper(graph, condition, runner));
        }
    }

}
