/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.core.task;

import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.elvis;
import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.truth;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Throwables;

/**
 * A task which runs with a fixed period.
 * <p>
 * Note that some termination logic, including {@link #addListener(Runnable, java.util.concurrent.Executor)},
 * is not precisely defined. 
 */
// TODO ScheduledTask is a very pragmatic implementation; would be nice to tighten, 
// reduce external assumptions about internal structure, and clarify "done" semantics
public class ScheduledTask extends BasicTask {
    
    final Callable<Task<?>> taskFactory;

    /**
     * Initial delay before running, set as flag in constructor; defaults to 0
     */
    protected Duration delay;

    /**
     * The time to wait between executions, or null if not to repeat (default), set as flag to constructor;
     * this may be modified for subsequent submissions by a running task generated by the factory 
     * using {@link #getSubmittedByTask().setPeriod(Duration)}
     */
    protected Duration period = null;

    /**
     * Optional, set as flag in constructor; defaults to null meaning no limit.
     */
    protected Integer maxIterations = null;

    /**
     * Set false if the task should be rescheduled after throwing an exception; defaults to true.
     */
    protected boolean cancelOnException = true;

    protected int runCount=0;
    protected Task<?> recentRun, nextRun;
    Class<? extends Exception> lastThrownType;

    public int getRunCount() { return runCount; }
    public ScheduledFuture<?> getNextScheduled() { return (ScheduledFuture<?>)internalFuture; }

    public ScheduledTask(Callable<Task<?>> taskFactory) {
        this(MutableMap.of(), taskFactory);
    }

    public ScheduledTask(final Task<?> task) {
        this(MutableMap.of(), task);
    }

    public ScheduledTask(Map flags, final Task<?> task){
        this(flags, new Callable<Task<?>>(){
            @Override
            public Task<?> call() throws Exception {
                return task;
            }});
    }

    public ScheduledTask(Map flags, Callable<Task<?>> taskFactory) {
        super(flags);
        this.taskFactory = taskFactory;
        
        delay = Duration.of(elvis(flags.remove("delay"), 0));
        period = Duration.of(elvis(flags.remove("period"), null));
        maxIterations = elvis(flags.remove("maxIterations"), null);
        Object cancelFlag = flags.remove("cancelOnException");
        cancelOnException = cancelFlag == null || Boolean.TRUE.equals(cancelFlag);
    }
    
    public ScheduledTask delay(Duration d) {
        this.delay = d;
        return this;
    }

    public ScheduledTask delay(long val) {
        return delay(Duration.millis(val));
    }

    public ScheduledTask period(Duration d) {
        this.period = d;
        return this;
    }

    public ScheduledTask period(long val) {
        return period(Duration.millis(val));
    }

    public ScheduledTask maxIterations(int val) {
        this.maxIterations = val;
        return this;
    }

    public ScheduledTask cancelOnException(boolean cancel) {
        this.cancelOnException = cancel;
        return this;
    }

    public Callable<Task<?>> getTaskFactory() {
        return taskFactory;
    }

    public Task<?> newTask() {
        try {
            return taskFactory.call();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    protected String getActiveTaskStatusString(int verbosity) {
        StringBuilder rv = new StringBuilder("Scheduler");
        if (runCount > 0) {
            rv.append(", iteration ").append(runCount + 1);
        }
        if (recentRun != null) {
            Duration start = Duration.sinceUtc(recentRun.getStartTimeUtc());
            rv.append(", last run ").append(start).append(" ago");
        }
        if (truth(getNextScheduled())) {
            Duration untilNext = Duration.millis(getNextScheduled().getDelay(TimeUnit.MILLISECONDS));
            if (untilNext.isPositive())
                rv.append(", next in ").append(untilNext);
            else 
                rv.append(", next imminent");
        }
        return rv.toString();
    }
    
    @Override
    public boolean isDone() {
        return isCancelled() || (maxIterations!=null && maxIterations <= runCount) || (period==null && nextRun!=null && nextRun.isDone());
    }
    
    public synchronized void blockUntilFirstScheduleStarted() {
        // TODO Assumes that maxIterations is not negative!
        while (true) {
            if (isCancelled()) throw new CancellationException();
            if (recentRun==null)
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Throwables.propagate(e);
                }
            if (recentRun!=null) return;
        }
    }
    
    public void blockUntilEnded() {
        while (!isDone()) super.blockUntilEnded();
    }

    /** @return The value of the most recently run task */
    public Object get() throws InterruptedException, ExecutionException {
        blockUntilStarted();
        blockUntilFirstScheduleStarted();
        return (truth(recentRun)) ? recentRun.get() : internalFuture.get();
    }
    
    @Override
    public synchronized boolean cancel(boolean mayInterrupt) {
        boolean result = super.cancel(mayInterrupt);
        if (nextRun!=null) {
            nextRun.cancel(mayInterrupt);
            notifyAll();
        }
        return result;
    }
    
    /**
     * Internal method used to allow callers to wait for underlying tasks to finished in the case of cancellation.
     * @param timeout maximum time to wait
     */
    @Beta
    public boolean blockUntilNextRunFinished(Duration timeout) {
        return Tasks.blockUntilInternalTasksEnded(nextRun, timeout);
    }
}