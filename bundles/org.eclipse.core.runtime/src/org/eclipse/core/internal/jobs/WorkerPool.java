/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.jobs;

import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Maintains a pool of worker threads.  Threads are constructed lazily as required,
 * and are eventually discarded if not in use for awhile.  This class maintains the
 * thread creation/destruction policies for the job manager.
 */
class WorkerPool {
//	private static final int MIN_THREADS = 1;
	private static final int MAX_THREADS = 4;
	private boolean running = false;
	private ArrayList threads = new ArrayList();
	/**
	 * The number of threads that are currently sleeping
	 */
	private int sleepingThreads = 0;
	/**
	 * Use the busy thread count to avoid starting new threads when a living
	 * thread is just doing house cleaning (notifying listeners, etc).
	 */
	private int busyThreads = 0;
	/**
	 * Threads not used by their best before timestamp are destroyed.
	 */
	private static final int BEST_BEFORE = 60000;

	private JobManager manager;

	protected WorkerPool(JobManager manager) {
		this.manager = manager;
		running = true;
	}
	protected void debug(String msg) {
		System.out.println("[" + Thread.currentThread() + "]" + msg); //$NON-NLS-1$ //$NON-NLS-2$
	}
	protected synchronized void endJob(Job job, IStatus result) {
		busyThreads--;
		manager.endJob(job, result);
	}
	protected synchronized void endWorker(Worker worker) {
		threads.remove(worker);
		if (JobManager.DEBUG)
			debug("worker removed from pool: " + worker); //$NON-NLS-1$
	}
	protected IProgressMonitor getProgressHandler() {
		return manager.getProgressHandler();
	}
	/**
	 * Notfication that a job has been added to the queue.  Wake a worker,
	 * creating a new worker if necessary
	 */
	protected synchronized void jobQueued(InternalJob job) {
		//if there is a sleeping thread, wake it up
		if (sleepingThreads > 0) {
			if (JobManager.DEBUG)
				debug("notifiying a worker"); //$NON-NLS-1$
			notify();
			return;
		}
		int threadCount = threads.size();
		//create a thread if all threads are busy and we're under the max size
		//if the job is high priority, we start a thread no matter what
		if ((busyThreads == threadCount && threadCount < MAX_THREADS) || job.getPriority() == Job.INTERACTIVE) {
			Worker worker = new Worker(this);
			threads.add(worker);
			if (JobManager.DEBUG)
				debug("worker added to pool: " + worker); //$NON-NLS-1$
			worker.start();
			//threads are considered busy until they start their first job, this ensures
			//that if several jobs are queued at once, enough threads will be started
			//to handle them all
			busyThreads++;
			return;
		}
	}
	protected synchronized void shutdown() {
		running = false;
		notifyAll();
	}
	/**
	 * Sleep for the given duration or until woken.
	 */
	private synchronized void sleep(long duration) {
		sleepingThreads++;
		if (JobManager.DEBUG)
			debug("worker sleeping for: " + duration + "ms"); //$NON-NLS-1$ //$NON-NLS-2$ 
		try {
			wait(duration);
		} catch (InterruptedException e) {
			if (JobManager.DEBUG)
				debug("worker interrupted while waiting... :-|"); //$NON-NLS-1$
		} finally {
			sleepingThreads--;
		}
	}
	/**
	 * Returns a new job to run.  Returns null if the thread should die.
	 * This method is only called when the very first job is being started by
	 * a given worker.  This is so we can update the busyThread count
	 * correctly since threads are considered "busy" in the period between 
	 * constructor and commencement of their first job.
	 */
	protected synchronized Job startFirstJob() {
		busyThreads--;
		return startJob();
	}
	/**
	 * Returns a new job to run.  Returns null if the thread should die.
	 */
	protected synchronized Job startJob() {
		//if we're above capacity, kill the thread
		if (!running || threads.size() > MAX_THREADS)
			return null;
		Job job = manager.startJob();
		//spin until a job is found or until we have been idle for too long
		long idleStart = System.currentTimeMillis();
		while (running && job == null) {
			long hint = manager.sleepHint();
			boolean idle = hint == JobManager.NEVER;
			if (hint > 0)
				sleep(Math.min(hint, BEST_BEFORE));
			job = manager.startJob();
			//if we were already idle, and there are still no new jobs, then the thread can expire
			if (job == null && idle && (System.currentTimeMillis()-idleStart > BEST_BEFORE))
				break;
		}
		if (job != null)
			busyThreads++;
		return job;
	}
}