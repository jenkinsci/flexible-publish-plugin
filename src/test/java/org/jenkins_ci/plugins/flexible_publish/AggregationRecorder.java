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
import java.util.ArrayList;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

/**
 *
 */
public class AggregationRecorder extends Recorder implements MatrixAggregatable {
    public AggregationRecorder() {
    }
    /**
     * @return
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        build.addAction(new RecorderAction(build.getParent().getFullName()));
        return true;
    }
    
    /**
     * @param build
     * @param launcher
     * @param listener
     * @return
     * @see hudson.matrix.MatrixAggregatable#createAggregator(hudson.matrix.MatrixBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public MatrixAggregator createAggregator(MatrixBuild build,
            Launcher launcher, BuildListener listener) {
        return new Aggregator(build, launcher, listener);
    }
    
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
        
        @Override
        public String getDisplayName() {
            return "Publisher for Testing";
        }
    }
    
    /**
     * Action to record AggregationRecorder is performed on a project.
     * Records the name of the project.
     */
    public static class RecorderAction implements Action {
        private String projectName;
        
        public RecorderAction(String projectName) {
            this.projectName = projectName;
        }
        
        public String getProjectName() {
            return projectName;
        }
        
        @Override
        public String getIconFileName() {
            return null;
        }
        
        @Override
        public String getDisplayName() {
            return String.format("RecorderAction for %s", projectName);
        }
        
        @Override
        public String getUrlName() {
            return null;
        }
    }
    
    public static class AggregatorAction extends ArrayList<String> implements Action {
        private static final long serialVersionUID = -8108872391528668975L;
        
        @Override
        public String getIconFileName() {
            return null;
        }
        
        @Override
        public String getDisplayName() {
            return "Aggregated Action of RecorderAction";
        }
        
        @Override
        public String getUrlName() {
            return null;
        }
    }
    
    public static class Aggregator extends MatrixAggregator {
        private AggregatorAction aggregate = null;
        protected Aggregator(MatrixBuild build, Launcher launcher,
                BuildListener listener) {
            super(build, launcher, listener);
        }
        
        @Override
        public boolean startBuild() throws InterruptedException, IOException {
            this.aggregate = new AggregatorAction();
            return true;
        }
        
        @Override
        public boolean endRun(MatrixRun run)
                throws InterruptedException, IOException {
            String projectNameFromAction = null;
            RecorderAction action = run.getAction(RecorderAction.class);
            if(action != null) {
                projectNameFromAction = action.getProjectName();
            }
            aggregate.add(projectNameFromAction);
            return true;
        }
        
        @Override
        public boolean endBuild() throws InterruptedException, IOException {
            build.addAction(aggregate);
            return true;
        }
    }
}
