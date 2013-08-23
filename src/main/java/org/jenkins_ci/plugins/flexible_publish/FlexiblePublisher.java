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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;

import org.jenkins_ci.plugins.run_condition.RunCondition;
import org.jenkins_ci.plugins.run_condition.BuildStepRunner;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FlexiblePublisher extends Recorder implements MatrixAggregatable{

    public static final String PROMOTION_JOB_TYPE = "hudson.plugins.promoted_builds.PromotionProcess";

    private List<ConditionalPublisher> publishers;

    @DataBoundConstructor
    public FlexiblePublisher(final List<ConditionalPublisher> publishers) {
        this.publishers = publishers;
    }

    public List<ConditionalPublisher> getPublishers() {
        return publishers;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        final Set<BuildStepMonitor> monitors = new HashSet<BuildStepMonitor>();
        for (ConditionalPublisher cp : publishers)
            monitors.add(cp.getPublisher().getRequiredMonitorService());
        if (monitors.contains(BuildStepMonitor.BUILD)) return BuildStepMonitor.BUILD;
        if (monitors.contains(BuildStepMonitor.STEP)) return BuildStepMonitor.STEP;
        return BuildStepMonitor.NONE;
    }

    @Override
    public Collection getProjectActions(final AbstractProject<?, ?> project) {
        final List actions = new ArrayList();
        for (ConditionalPublisher publisher : publishers)
            actions.addAll(publisher.getProjectActions(project));
        return actions;
    }

    @Override
    public boolean prebuild(final AbstractBuild<?, ?> build, final BuildListener listener) {
        for (ConditionalPublisher publisher : publishers)
            if (!publisher.prebuild(build, listener))
                setResult(build, Result.FAILURE);
        return true;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                                                                                throws InterruptedException, IOException {
        for (ConditionalPublisher publisher : publishers)
            if (!publisher.perform(build, launcher, listener))
                setResult(build, Result.FAILURE);
        return true;
    }

    private static void setResult(final AbstractBuild<?, ?> build, final Result result) {
        if (build.getResult() == null)
            build.setResult(result);
        else
            build.setResult(result.combine(build.getResult()));
    }

    @Extension(ordinal = Integer.MAX_VALUE - 500)
    public static class FlexiblePublisherDescriptor extends BuildStepDescriptor<Publisher> {

        public static DescriptorExtensionList<PublisherDescriptorLister, Descriptor<PublisherDescriptorLister>>
                                                                                        getAllPublisherDescriptorListers() {
            return Hudson.getInstance().<PublisherDescriptorLister, Descriptor<PublisherDescriptorLister>>
                                                                                        getDescriptorList(PublisherDescriptorLister.class);
        }

        private PublisherDescriptorLister publisherLister;

        @DataBoundConstructor
        public FlexiblePublisherDescriptor(final PublisherDescriptorLister publisherLister) {
            this.publisherLister = publisherLister;
        }

        public FlexiblePublisherDescriptor() {
            load();
            if (publisherLister == null)
                publisherLister = new DefaultPublisherDescriptorLister();
        }

        public PublisherDescriptorLister getPublisherLister() {
            return publisherLister;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            final FlexiblePublisherDescriptor newConfig = req.bindJSON(FlexiblePublisherDescriptor.class, json);
            if (newConfig.publisherLister != null)
                publisherLister = newConfig.publisherLister;
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.publisher_displayName();
        }

        public boolean isApplicable(final Class<? extends AbstractProject> aClass) {
            return !PROMOTION_JOB_TYPE.equals(aClass.getCanonicalName());
        }

        public Object readResolve() {
            if (publisherLister == null)
                publisherLister = new DefaultPublisherDescriptorLister();
            return this;
        }

    }

    /**
     * Return an aggregator worked for multi-configuration projects.
     * 
     * {@link Publisher#perform(AbstractBuild, Launcher, BuildListener)} is called
     * for each axe combination builds.
     * 
     * For whole the multi-configuration project, {@link MatrixAggregator#endBuild()}
     * is called instead.
     * 
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @see hudson.matrix.MatrixAggregatable#createAggregator(hudson.matrix.MatrixBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public MatrixAggregator createAggregator(
            MatrixBuild build, Launcher launcher, BuildListener listener
    ) {
        List<ConditionalMatrixAggregator> aggregatorList
            = new ArrayList<ConditionalMatrixAggregator>();
        
        for (ConditionalPublisher cp: getPublishers()) {
            if (!(cp.getPublisher() instanceof MatrixAggregatable)) {
                continue;
            }
            
            // First, decide whether the condition is satisfied
            // in the parent scope.
            RunCondition cond;
            BuildStepRunner runner;
            if (cp.isConfiguredAggregation()) {
                cond = cp.getAggregationCondition();
                runner = cp.getAggregationRunner();
            } else {
                cond = cp.getCondition();
                runner = cp.getRunner();
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
                continue;
            }
            
            MatrixAggregator baseAggregator
                = ((MatrixAggregatable)cp.getPublisher()).createAggregator(build, launcher, listener);
            if (baseAggregator == null) {
                continue;
            }
            aggregatorList.add(new ConditionalMatrixAggregator(
                    build, launcher, listener, cp, baseAggregator
            ));
        }
        
        if (aggregatorList.isEmpty()) {
            return null;
        }
        
        return new FlexibleMatrixAggregator(
                build, launcher, listener, aggregatorList
        );
    }
}
