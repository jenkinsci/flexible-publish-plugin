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

import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;
import hudson.tasks.Publisher;

/**
 * Wraps {@link MatrixAggregator} for the wrapped {@link Publisher}.
 * Evaluates the condition, and call wrapped {@link MatrixAggregator} if satisfied.
 */
public class ConditionalMatrixAggregator extends MatrixAggregator {
    private ConditionalPublisher conditionalPublisher;
    private MatrixAggregator baseAggregator;
    
    protected ConditionalMatrixAggregator(MatrixBuild build, Launcher launcher,
            BuildListener listener, ConditionalPublisher conditionalPublisher,
            MatrixAggregator baseAggregator) {
        super(build, launcher, listener);
        this.conditionalPublisher = conditionalPublisher;
        this.baseAggregator = baseAggregator;
    }
    
    @Override
    public boolean startBuild() throws InterruptedException, IOException {
        return baseAggregator.startBuild();
    }
    
    @Override
    public boolean endRun(MatrixRun run)
            throws InterruptedException, IOException {
        MarkPerformedBuilder mpb = new MarkPerformedBuilder();
        boolean isSuccess = conditionalPublisher.getRunner().perform(
                conditionalPublisher.getCondition(),
                mpb,
                run, // watch out! not parent build.
                launcher,
                listener
        );
        
        if(!isSuccess || !mpb.isPerformed()) {
            return true;
        }
        
        return baseAggregator.endRun(run);
    }
    
    @Override
    public boolean endBuild() throws InterruptedException, IOException {
        return baseAggregator.endBuild();
    }
}
