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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.DataBoundConstructor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultPublisherDescriptorLister implements PublisherDescriptorLister {

    public static final List<String> EXCLUSIONS = Arrays.asList(
            "org.jenkins_ci.plugins.flexible_publish.FlexiblePublisher.FlexiblePublisherDescriptor"
        );

    @DataBoundConstructor
    public DefaultPublisherDescriptorLister() {
    }

    public List<? extends Descriptor<? extends BuildStep>> getAllowedPublishers(AbstractProject<?,?> project) {
        final List<BuildStepDescriptor<? extends Publisher>> publishers = new ArrayList<BuildStepDescriptor<? extends Publisher>>();
        if (project == null) return publishers;
        for (Descriptor descriptor : Publisher.all()) {
            if (!(descriptor instanceof BuildStepDescriptor)) continue;
            if (EXCLUSIONS.contains(descriptor.getClass().getCanonicalName())) continue;
            BuildStepDescriptor<? extends Publisher> buildStepDescriptor = (BuildStepDescriptor) descriptor;
            // would be nice to refuse if needsToRunAfterFinalized - but that's on the publisher which does not yet exist!
            if (buildStepDescriptor.isApplicable(project.getClass())) {
                if (hasDbc(buildStepDescriptor.clazz))
                    publishers.add(buildStepDescriptor);
            }
        }
        return publishers;
    }

    public DescriptorImpl getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    private boolean hasDbc(final Class<?> clazz) {
        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.isAnnotationPresent(DataBoundConstructor.class))
                return true;
        }
        return false;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PublisherDescriptorLister> {

        @Override
        public String getDisplayName() {
            return Messages.defaultPublisherDescriptor_displayName();
        }

    }

}
