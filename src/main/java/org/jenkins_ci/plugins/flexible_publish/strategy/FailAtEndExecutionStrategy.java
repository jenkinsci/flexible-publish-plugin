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

package org.jenkins_ci.plugins.flexible_publish.strategy;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixRun;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.model.Result;

import java.io.IOException;

import org.jenkins_ci.plugins.flexible_publish.builder.FailAtEndBuilder;
import org.jenkins_ci.plugins.flexible_publish.builder.MarkPerformedBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Run all publishers even some of them fail.
 * To be exact, work as Jenkins core does:
 * <table>
 *   <caption>behavior</caption>
 *   <tr>
 *     <th>prebuild</th>
 *     <td>fail fast</td>
 *   </tr>
 *   <tr>
 *     <th>perform</th>
 *     <td>fail at end</td>
 *   </tr>
 *   <tr>
 *     <th>aggregation startBuild</th>
 *     <td>fail fast</td>
 *   </tr>
 *   <tr>
 *     <th>aggregation endRun</th>
 *     <td>fail fast</td>
 *   </tr>
 *   <tr>
 *     <th>aggregation endBuild</th>
 *     <td>fail at end</td>
 *   </tr>
 * </table>
 */
public class FailAtEndExecutionStrategy extends ConditionalExecutionStrategy {
    @DataBoundConstructor
    public FailAtEndExecutionStrategy() {
    }
    
    @Override
    public boolean prebuild(PublisherContext context, AbstractBuild<?, ?> build, BuildListener listener) {
        return context.getRunner().prebuild(
                context.getCondition(),
                new FailAtEndBuilder(context.getPublisherList()),
                build, listener
        );
    }
    
    @Override
    public boolean perform(PublisherContext context, AbstractBuild<?, ?> build,
            Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return context.getRunner().perform(
                context.getCondition(),
                new FailAtEndBuilder(context.getPublisherList()),
                build, launcher, listener
        );
    }
    
    @Override
    public boolean matrixAggregationStartBuild(AggregatorContext aggregatorContext) throws InterruptedException, IOException {
        for(MatrixAggregator aggregator: aggregatorContext.getAggregatorList()) {
            if (!aggregator.startBuild()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean matrixAggregationEndRun(
            AggregatorContext aggregatorContext, MatrixRun run) throws InterruptedException, IOException
    {
        MarkPerformedBuilder mpb = new MarkPerformedBuilder();
        boolean isSuccess = aggregatorContext.getRunner().perform(
                aggregatorContext.getCondition(),
                mpb,
                run, // watch out! not parent build.
                aggregatorContext.getLauncher(),
                aggregatorContext.getListener()
        );
        
        if(!isSuccess || !mpb.isPerformed()) {
            return isSuccess;
        }
        
        for (MatrixAggregator aggregator: aggregatorContext.getAggregatorList()) {
            if (!aggregator.endRun(run)) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean matrixAggregationEndBuild(AggregatorContext aggregatorContext) throws InterruptedException, IOException {
        boolean wholeResult = true;
        for(MatrixAggregator aggregator: aggregatorContext.getAggregatorList()) {
            try {
                if (!aggregator.endBuild()) {
                    aggregatorContext.getListener().error(String.format("[flexible-publish] aggregation with %s failed", aggregator.toString()));
                    wholeResult = false;
                }
            } catch (Exception e) {
                e.printStackTrace(aggregatorContext.getListener().error(String.format("[flexible-publish] aggregation with %s is aborted due to exception", aggregator.toString())));
                aggregatorContext.getBuild().setResult(Result.FAILURE);
                wholeResult = false;
            }
        }
        return wholeResult;
    }
    
    @Extension(ordinal=100)    // this is the default strategy >= 0.15
    public static class DescriptorImpl extends Descriptor<ConditionalExecutionStrategy> {
        @Override
        public String getDisplayName() {
            return Messages.FailAtEndExecutionStragery_DisplayName();
        }
        
    }
}
