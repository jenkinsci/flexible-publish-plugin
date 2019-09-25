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
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
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
import hudson.model.Saveable;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BuildStep;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkins_ci.plugins.flexible_publish.builder.MarkPerformedBuilder;
import org.jenkins_ci.plugins.flexible_publish.strategy.ConditionalExecutionStrategy;
import org.jenkins_ci.plugins.flexible_publish.strategy.FailAtEndExecutionStrategy;
import org.jenkins_ci.plugins.flexible_publish.strategy.FailFastExecutionStrategy;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkins_ci.plugins.run_condition.core.AlwaysRun;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

public class ConditionalPublisher implements Describable<ConditionalPublisher>, DependecyDeclarer {

    private static final Logger LOGGER = Logger.getLogger(ConditionalPublisher.class.getName());
    
    private final RunCondition condition;
    @Deprecated
    private transient BuildStep publisher;
    private List<BuildStep> publisherList;
    private BuildStepRunner runner;
    
    // used for multiconfiguration projects with MatrixAggregatable.
    // if null, used condition and publlisher.
    private final RunCondition aggregationCondition;
    private final BuildStepRunner aggregationRunner;
    
    private /*final*/ ConditionalExecutionStrategy executionStrategy;

    @Deprecated
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
     * @deprecated use {@link #ConditionalPublisher(RunCondition, List, BuildStepRunner, boolean, RunCondition, BuildStepRunner)} instead
     */
    @Deprecated
    public ConditionalPublisher(final RunCondition condition, final BuildStep publisher, final BuildStepRunner runner,
            boolean configuredAggregation, final RunCondition aggregationCondition, final BuildStepRunner aggregationRunner) {
        this(condition, Arrays.asList(publisher), runner, configuredAggregation, aggregationCondition, aggregationRunner);
    }
    /**
     * @param condition
     * @param publisherList
     * @param runner
     * @param configuredAggregation
     * @param aggregationCondition
     * @param aggregationRunner
     * @see ConditionalPublisherDescriptor#newInstance(StaplerRequest, JSONObject)
     */
    @Deprecated
    public ConditionalPublisher(final RunCondition condition, final List<BuildStep> publisherList, final BuildStepRunner runner,
            boolean configuredAggregation, final RunCondition aggregationCondition, final BuildStepRunner aggregationRunner) {
        this(condition, publisherList, runner, configuredAggregation, aggregationCondition, aggregationRunner, null);
    }
    
    @DataBoundConstructor
    public ConditionalPublisher(final RunCondition condition, final List<BuildStep> publisherList, final BuildStepRunner runner,
            boolean configuredAggregation, final RunCondition aggregationCondition, final BuildStepRunner aggregationRunner,
            ConditionalExecutionStrategy executionStrategy) {
        this.condition = condition;
        this.publisherList = publisherList;
        this.runner = runner;
        if (configuredAggregation) {
            this.aggregationCondition = aggregationCondition;
            this.aggregationRunner = aggregationRunner;
        } else {
            this.aggregationCondition = null;
            this.aggregationRunner = null;
        }
        this.executionStrategy = (executionStrategy != null)?executionStrategy:new FailAtEndExecutionStrategy();
    }

    public RunCondition getCondition() {
        return condition;
    }

    /**
     * @return
     * @deprecated use {@link #getPublisherList()} instead
     */
    @Deprecated
    public BuildStep getPublisher() {
        List<BuildStep> publisherList = getPublisherList();
        return !publisherList.isEmpty()?publisherList.get(0):null;
    }

    public List<BuildStep> getPublisherList() {
        return publisherList != null?publisherList:Collections.<BuildStep>emptyList();
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

    public ConditionalExecutionStrategy getExecutionStrategy() {
        return executionStrategy;
    }

    public ConditionalPublisherDescriptor getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(ConditionalPublisherDescriptor.class);
    }

    public Collection<? extends Action> getProjectActions(final AbstractProject<?, ?> project) {
        Collection<Action> actions = new ArrayList<Action>();
        for (BuildStep publisher: getPublisherList()) {
            actions.addAll(publisher.getProjectActions(project));
        }
        return actions;
    }

    private ConditionalExecutionStrategy.PublisherContext createExecutionStrategyContext() {
        return new ConditionalExecutionStrategy.PublisherContext(
                runner,
                condition,
                getPublisherList()
        );
    }

    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {
        return getExecutionStrategy().prebuild(createExecutionStrategyContext(), build, listener);
    }

    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                                                                                throws InterruptedException, IOException {
        return getExecutionStrategy().perform(createExecutionStrategyContext(), build, launcher, listener);
    }

    public ConditionalMatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        boolean supportAggregation = false;
        
        for (BuildStep publisher: getPublisherList()) {
            if (publisher instanceof MatrixAggregatable) {
                supportAggregation = true;
                break;
            }
        }
        
        if (!supportAggregation) {
            if (isConfiguredAggregation()) {
                // alerts if all publishers doesn't support aggregation
                // even if configured for aggregation
                listener.getLogger().println(String.format(
                        "[%s] WARNING: Condition for Matrix Aggregation is configured for %s which does not support aggregation",
                        getDescriptor().getDisplayName(),
                        FlexiblePublisher.getBuildStepShortName(getPublisherList())
                ));
            }
            return null;
        }
        // First, decide whether the condition is satisfied
        // in the parent scope.
        RunCondition cond;
        BuildStepRunner runner;
        if (isConfiguredAggregation()) {
            cond = getAggregationCondition();
            runner = getAggregationRunner();
        } else {
            cond = getCondition();
            runner = getRunner();
        }
        
        MarkPerformedBuilder mpb = new MarkPerformedBuilder();
        boolean isSuccess = false;
        try {
            isSuccess = runner.perform(cond, mpb, build, launcher, listener);
        } catch(Exception e) {
            e.printStackTrace(listener.getLogger());
        }
        
        if (!isSuccess || !mpb.isPerformed()) {
            // condition is not satisfied.
            // no need to run aggregation.
            return null;
        }
        
        List<MatrixAggregator> baseAggregatorList = new ArrayList<MatrixAggregator>();
        for (BuildStep publisher: getPublisherList()) {
            if(!(publisher instanceof MatrixAggregatable))
            {
                continue;
            }
            MatrixAggregator baseAggregator
                = ((MatrixAggregatable)publisher).createAggregator(build, launcher, listener);
            if (baseAggregator == null) {
                continue;
            }
            baseAggregatorList.add(baseAggregator);
        }
        
        return new ConditionalMatrixAggregator(
            build, launcher, listener, this, baseAggregatorList
        );
    }

    public Object readResolve() {
        if (publisher != null) {
            publisherList = new ArrayList<BuildStep>();
            publisherList.add(publisher);
            publisher = null;
        }
        
        if (publisherList != null) {
            // We also need to guard against any null values in the publisherList
            // This can happen if the projects publisher list was removed, returning
            // a list with one null element.
            if (publisherList.contains(null)) {
                publisherList = new ArrayList<BuildStep>(publisherList);
                publisherList.remove(null);
            }
        }
        
        if (runner == null)
            runner = new BuildStepRunner.Fail();
        
        if (executionStrategy == null)
        {
            // for backward compatibility with <= 0.14.1.
            // this is no longer the default behavior.
            executionStrategy = new FailFastExecutionStrategy();
        }
        
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

        public List<? extends Descriptor<? extends ConditionalExecutionStrategy>> getExecutionStrategies() {
            return ConditionalExecutionStrategy.all();
        }

        /**
         * Build a new instance from parameters a user input in a configuration page.
         * 
         * Usually, it is done by {@link StaplerRequest#bindJSON(Class, JSONObject)}, 
         * and {@link DataBoundConstructor} of classes of posted objects.
         * 
         * But we have to use {@link Descriptor#newInstance(StaplerRequest, JSONObject)}
         * for classes without {@link DataBoundConstructor} (such as {@code hudson.tasks.Mailer})
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
            List<BuildStep> publisherList = null;
            boolean configuredAggregation = false;
            RunCondition aggregationCondition = null;
            BuildStepRunner aggregationRunner = null;
            ConditionalExecutionStrategy executionStrategy = null;
            
            if (formData != null) {
                condition = req.bindJSON(RunCondition.class, formData.getJSONObject("condition"));
                runner = req.bindJSON(BuildStepRunner.class, formData.getJSONObject("runner"));
                if (formData.has("configuredAggregation")) {
                    configuredAggregation = formData.getBoolean("configuredAggregation");
                    aggregationCondition = req.bindJSON(RunCondition.class, formData.getJSONObject("aggregationCondition"));
                    aggregationRunner = req.bindJSON(BuildStepRunner.class, formData.getJSONObject("aggregationRunner"));
                }
                
                JSONArray a = JSONArray.fromObject(formData.get("publisherList"));
                if (a != null && !a.isEmpty()) {
                    publisherList = new ArrayList<BuildStep>(a.size());
                    for(int idx = 0; idx < a.size(); ++idx) {
                        BuildStep buildstep = bindJSONWithDescriptor(req, a.getJSONObject(idx), "publisherList");
                        if (buildstep != null)
                            publisherList.add(buildstep);
                    }
                }
                executionStrategy = req.bindJSON(ConditionalExecutionStrategy.class, formData.getJSONObject("executionStrategy"));
            }
            return new ConditionalPublisher(
                    condition,
                    publisherList,
                    runner,
                    configuredAggregation,
                    aggregationCondition,
                    aggregationRunner,
                    executionStrategy
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
         * @param fieldName used for exception information.
         * @return
         * @throws hudson.model.Descriptor.FormException
         */
        private BuildStep bindJSONWithDescriptor(StaplerRequest req, JSONObject formData, String fieldName)
                throws hudson.model.Descriptor.FormException {
            if (formData == null || formData.isNullObject()) {
                return null;
            }
            String clazzName = formData.optString("$class", null);
            if (clazzName == null) {
              // Fall back on the legacy stapler-class attribute.
              clazzName = formData.optString("stapler-class", null);
            }
            if (clazzName == null) {
                throw new FormException("No $class or stapler-class is specified", fieldName);
            }
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Describable<?>> clazz = (Class<? extends Describable<?>>)Jenkins.getInstance().getPluginManager().uberClassLoader.loadClass(clazzName);
                Descriptor<?> d = Jenkins.getInstance().getDescriptorOrDie(clazz);
                return (BuildStep)d.newInstance(req, formData);
            } catch(ClassNotFoundException e) {
                throw new FormException(
                        String.format("Failed to instantiate: class not found %s", clazzName),
                        e,
                        fieldName
                );
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void buildDependencyGraph(AbstractProject owner, DependencyGraph graph) {
        // As DependecyDeclarer was renamed to DependencyDeclarer in Jenkins 1.501,
        // codes referring DependecyDeclarer may not work depending on which version of
        // Jenkins core used.
        // Instead, I use DescribableList#buildDependencyGraph, which is a part of
        // Jenkins core and always work.
        // See JENKINS-25017 for details.
      for (BuildStep publisher: getPublisherList()) {
        if (publisher instanceof Publisher) {
            DescribableList<Publisher, Descriptor<Publisher>> lst = new DescribableList<Publisher, Descriptor<Publisher>>(
                    new Saveable() {
                        @Override
                        public void save() throws IOException {}
                    },
                    Arrays.asList((Publisher)publisher)
            );
            lst.buildDependencyGraph(owner, new ConditionalDependencyGraphWrapper(graph, condition, runner));
        } else if (publisher instanceof Builder) {
            // Case used with Any Build Step plugin (https://wiki.jenkins-ci.org/display/JENKINS/Any+Build+Step+Plugin).
            DescribableList<Builder, Descriptor<Builder>> lst = new DescribableList<Builder, Descriptor<Builder>>(
                    new Saveable() {
                        @Override
                        public void save() throws IOException {}
                    },
                    Arrays.asList((Builder)publisher)
            );
            lst.buildDependencyGraph(owner, new ConditionalDependencyGraphWrapper(graph, condition, runner));
        } else if (publisher instanceof DependecyDeclarer) {
            ((DependecyDeclarer)publisher).buildDependencyGraph(owner,
                    new ConditionalDependencyGraphWrapper(graph, condition, runner));
        } else {
            LOGGER.log(Level.WARNING, "May failed to build dependency for {0} in {1}", new Object[]{
                    publisher.getClass(),
                    owner.getFullName()
            });
        }
      }
    }

}
