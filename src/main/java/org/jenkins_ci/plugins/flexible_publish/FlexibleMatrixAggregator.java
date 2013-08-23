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
import java.util.List;

import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;

/**
 * {@link MatrixAggregator} for {@link FlexiblePublisher}.
 * 
 * Just calls {@link ConditionalMatrixAggregator} for each {@link ConditionalPublisher}s.
 */
public class FlexibleMatrixAggregator extends MatrixAggregator {
    private List<ConditionalMatrixAggregator> aggregatorList;
    
    protected FlexibleMatrixAggregator(MatrixBuild build,
            Launcher launcher, BuildListener listener,
            List<ConditionalMatrixAggregator> aggregatorList) {
        super(build, launcher, listener);
        this.aggregatorList = aggregatorList;
    }
    
    /**
     * Called when the parent build is started.
     * 
     * @return false to abort the build.s
     * @throws InterruptedException
     * @throws IOException
     */
    @Override
    public boolean startBuild() throws InterruptedException, IOException {
        for (ConditionalMatrixAggregator cma: aggregatorList) {
            if (!cma.startBuild()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Called when a build with an axe combination is finished.
     * 
     * @param run
     * @return false to abort the build.s
     * @throws InterruptedException
     * @throws IOException
     * @see hudson.matrix.MatrixAggregator#endRun(hudson.matrix.MatrixRun)
     */
    @Override
    public boolean endRun(MatrixRun run) throws InterruptedException,
            IOException
    {
        for (ConditionalMatrixAggregator cma: aggregatorList) {
            if (!cma.endRun(run)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Called when the parent build is finished.
     * 
     * @return false to abort the build.
     * @throws InterruptedException
     * @throws IOException
     * @see hudson.matrix.MatrixAggregator#endBuild()
     */
    @Override
    public boolean endBuild() throws InterruptedException, IOException {
        for (ConditionalMatrixAggregator cma: aggregatorList) {
            if (!cma.endBuild()) {
                return false;
            }
        }
        return true;
    }
}
