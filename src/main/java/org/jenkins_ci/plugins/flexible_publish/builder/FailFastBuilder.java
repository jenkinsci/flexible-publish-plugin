/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

package org.jenkins_ci.plugins.flexible_publish.builder;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jenkins_ci.plugins.flexible_publish.FlexiblePublisher;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;

import org.jenkins_ci.plugins.run_condition.BuildStepRunner;

/**
 * Used with {@link BuildStepRunner}.
 * 
 * Run build steps, but stops execution immediately when any of them fail.
 */
public class FailFastBuilder extends Builder {
    private static final Logger LOGGER = Logger.getLogger(FailFastBuilder.class.getName());
    private final List<BuildStep> buildsteps;
    
    public FailFastBuilder(List<BuildStep> buildsteps) {
        this.buildsteps = buildsteps;
    }
    /**
     * Run {@link BuildStep#prebuild(AbstractBuild, BuildListener)} of all build steps.
     * 
     * Doesn't run following build steps when a build step fails.
     * 
     * @param build
     * @param listener
     * @return
     * @see hudson.tasks.BuildStep#prebuild(hudson.model.AbstractBuild, hudson.model.BuildListener)
     */
    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        for (BuildStep buildstep: buildsteps) {
            if (!buildstep.prebuild(build, listener)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Run {@link BuildStep#prebuild(AbstractBuild, BuildListener)} of all build steps.
     * 
     * Runs following build steps even when a build step fails.
     * 
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @throws InterruptedException
     * @throws IOException
     * @see hudson.tasks.BuildStep#perform(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        for (BuildStep buildstep: buildsteps) {
            try {
                if (!buildstep.perform(build, launcher, listener)) {
                    listener.error(String.format(
                            "[flexible-publish] %s failed",
                            FlexiblePublisher.getBuildStepDetailedName(buildstep)
                    ));
                    build.setResult(Result.FAILURE);
                    return false;
                }
            } catch(AbortException e) {
                listener.error(String.format(
                        "[flexible-publish] %s aborted: %s",
                        FlexiblePublisher.getBuildStepDetailedName(buildstep),
                        e.getMessage()
                ));
                LOGGER.log(Level.FINE, "[flexible-publish] %s aborted", e); // for diagnostic purpose.
                build.setResult(Result.FAILURE);
                return false;
            } catch (Exception e) {
                listener.error(String.format(
                        "[flexible-publish] %s failed due to exception",
                        FlexiblePublisher.getBuildStepDetailedName(buildstep)
                ));
                e.printStackTrace(listener.error(String.format(
                        "[flexible-publish] %s aborted due to exception",
                        FlexiblePublisher.getBuildStepDetailedName(buildstep)
                )));
                build.setResult(Result.FAILURE);
                return false;
            }
        }
        return true;
    }
    
    @Override
    public Descriptor<Builder> getDescriptor() {
        return new Descriptor<Builder>() {
            @Override
            public String getDisplayName() {
                return String.format("[%s]", StringUtils.join(
                        Lists.transform(
                                buildsteps,
                                new Function<BuildStep, String>() {
                                    @Override
                                    public String apply(BuildStep input) {
                                        if (input instanceof Describable) {
                                            return ((Describable<?>)input).getDescriptor().getDisplayName();
                                        }
                                        return input.getClass().getName();
                                    }
                                }
                        ),
                        ", "
                ));
            }
        };
    }
    
    /**
     * Not Supported
     * 
     * @param project
     * @return
     * @deprecated
     * @see hudson.tasks.BuildStep#getProjectAction(hudson.model.AbstractProject)
     */
    @Override
    @Deprecated
    public Action getProjectAction(AbstractProject<?, ?> project) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * @param project
     * @return
     * @see hudson.tasks.BuildStep#getProjectActions(hudson.model.AbstractProject)
     */
    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * @return
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        throw new UnsupportedOperationException();
    }
    
}
