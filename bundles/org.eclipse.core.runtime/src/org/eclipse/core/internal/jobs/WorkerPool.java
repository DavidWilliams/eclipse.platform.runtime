/**********************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.jobs;

import org.eclipse.core.internal.runtime.Assert;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.osgi.service.prefs.Preferences;

/**
 * Maintains a pool of worker threads. Threads are constructed lazily as
 * required, and are eventually discarded if not in use for awhile. This class
 * maintains the thread creation/destruction policies for the job manager.
 * 
 * Implementation note: all the data structures of this class are protected
 * by the instance's object monitor.  To avoid deadlock with third party code,
 * this lock is never held when calling methods outside this class that may in
 * turn use locks.
 */
class WorkerPool {
	/**
	 * Threads not used by their best before timestamp are destroyed. 
	 */
	private static final int BEST_BEFORE = 60000;
	private static final int DEFAULT_MAX_THREADS = 25;
	/**
	 * There will always be at least MIN_THREADS workers in the pool.
	 */
	private static final int MIN_THREADS = 1;
	/**
	 * Use the busy thread count to avoid starting new threads when a living
	 * thread is just doing house cleaning (notifying listeners, etc).
	 */
	private int busyThreads = 0;

	private JobManager manager;
	private int MAX_THREADS = DEFAULT_MAX_THREADS;
	/**
	 * The number of workers in the threads array
	 */
	private int numThreads = 0;
	/**
	 * The number of threads that are currently sleeping 
	 */
	private int sleepingThreads = 0;
	/**
	 * The living set of workers in this pool.
	 */
	private Worker[] threads = new Worker[10];

	protected WorkerPool(JobManager manager) {
		this.manager = manager;
		computeMaxThreads();
		Preferences node = Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE).node(IPlatform.PI_RUNTIME);
		IEclipsePreferences eclipseNode = (IEclipsePreferences) node;
		eclipseNode.addPreferenceChangeListener(new IEclipsePreferences.IPreferenceChangeListener() {
			public void preferenceChange(PreferenceChangeEvent event) {
				if (event.getKey().equalsIgnoreCase(IPlatform.PREF_PLATFORM_PERFORMANCE))
					computeMaxThreads();
			}
		});
	}

	/**
	 * Adds a worker to the list of workers.
	 */
	private synchronized void add(Worker worker) {
		int size = threads.length;
		if (numThreads + 1 > size) {
			Worker[] newThreads = new Worker[2 * size];
			System.arraycopy(threads, 0, newThreads, 0, size);
			threads = newThreads;
		}
		threads[numThreads++] = worker;
	}

	/**
	 * Computes the maximum number of threads based on the machine speed
	 * preference.
	 */
	protected void computeMaxThreads() {
		int speed = Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE).node(IPlatform.PI_RUNTIME).getInt(IPlatform.PREF_PLATFORM_PERFORMANCE, 3);
		switch (speed) {
			case 1 :
				MAX_THREADS = DEFAULT_MAX_THREADS / 5;
				break;
			case 2 :
				MAX_THREADS = DEFAULT_MAX_THREADS / 2;
				break;
			case 4 :
				MAX_THREADS = DEFAULT_MAX_THREADS * 2;
				break;
			case 5 :
				MAX_THREADS = DEFAULT_MAX_THREADS * 5;
				break;
			case 3 :
			default :
				MAX_THREADS = DEFAULT_MAX_THREADS;
		}
	}

	private synchronized void decrementBusyThreads() {
		//impossible to have less than zero busy threads
		if (--busyThreads < 0) {
			if (JobManager.DEBUG)
				Assert.isTrue(false, Integer.toString(busyThreads));
			busyThreads = 0;
		}
	}

	/**
	 * Signals the end of a job.  Note that this method can be called under
	 * OutOfMemoryError conditions and thus must be paranoid about allocating objects.
	 */
	protected void endJob(InternalJob job, IStatus result) {
		decrementBusyThreads();
		//need to end rule in graph before ending job so that 2 threads
		//do not become the owners of the same rule in the graph
		if ((job.getRule() != null) && !(job instanceof ImplicitJobs.ThreadJob)) {
			//remove any locks this thread may be owning on that rule
			manager.getLockManager().removeLockCompletely(Thread.currentThread(), job.getRule());
		}
		manager.endJob(job, result, true);
	}

	/**
	 * Signals the death of a worker thread.  Note that this method can be called under
	 * OutOfMemoryError conditions and thus must be paranoid about allocating objects.
	 */
	protected synchronized void endWorker(Worker worker) {
		if (remove(worker) && JobManager.DEBUG)
			JobManager.debug("worker removed from pool: " + worker); //$NON-NLS-1$
	}

	private synchronized void incrementBusyThreads() {
		//impossible to have more busy threads than there are threads
		if (++busyThreads > numThreads) {
			if (JobManager.DEBUG)
				Assert.isTrue(false, Integer.toString(busyThreads) + ',' + numThreads);
			busyThreads = numThreads;
		}
	}

	/**
	 * Notfication that a job has been added to the queue. Wake a worker,
	 * creating a new worker if necessary. The provided job may be null.
	 */
	protected synchronized void jobQueued(InternalJob job) {
		//if there is a sleeping thread, wake it up
		if (sleepingThreads > 0) {
			if (JobManager.DEBUG)
				JobManager.debug("notifiying a worker"); //$NON-NLS-1$
			notify();
			return;
		}
		int threadCount = numThreads;
		//create a thread if all threads are busy and we're under the max size
		//if the job is high priority, we start a thread no matter what
		if (busyThreads >= threadCount) {
			Worker worker = new Worker(this);
			add(worker);
			if (JobManager.DEBUG)
				JobManager.debug("worker added to pool: " + worker); //$NON-NLS-1$
			worker.start();
			return;
		} else if (JobManager.DEBUG && threadCount >= MAX_THREADS) {
			//this is a potential problem, but we don't want to flood the user with these messages
			JobManager.debug("Stopped allocating worker threads. Thread count: " + threadCount); //$NON-NLS-1$
		}
	}

	/**
	 * Remove a worker thread from our list.
	 * @return true if a worker was removed, and false otherwise.
	 */
	private boolean remove(Worker worker) {
		for (int i = 0; i < threads.length; i++) {
			if (threads[i] == worker) {
				System.arraycopy(threads, i + 1, threads, i, numThreads - i - 1);
				threads[--numThreads] = null;
				return true;
			}
		}
		return false;
	}

	protected synchronized void shutdown() {
		notifyAll();
	}

	/**
	 * Sleep for the given duration or until woken. 
	 */
	private synchronized void sleep(long duration) {
		sleepingThreads++;
		if (JobManager.DEBUG)
			JobManager.debug("worker sleeping for: " + duration + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			wait(duration);
		} catch (InterruptedException e) {
			if (JobManager.DEBUG)
				JobManager.debug("worker interrupted while waiting... :-|"); //$NON-NLS-1$
		} finally {
			sleepingThreads--;
		}
	}

	/**
	 * Returns a new job to run. Returns null if the thread should die. 
	 */
	protected InternalJob startJob(Worker worker) {
		//if we're above capacity, kill the thread
		synchronized (this) {
			if (!manager.isActive()) {
				//must remove the worker immediately to prevent all threads from expiring
				endWorker(worker);
				return null;
			}
		}
		Job job = manager.startJob();
		//spin until a job is found or until we have been idle for too long
		long idleStart = System.currentTimeMillis();
		while (manager.isActive() && job == null) {
			long hint = manager.sleepHint();
			if (hint > 0)
				sleep(Math.min(hint, BEST_BEFORE));
			job = manager.startJob();
			//if we were already idle, and there are still no new jobs, then
			// the thread can expire
			synchronized (this) {
				if (job == null && (System.currentTimeMillis() - idleStart > BEST_BEFORE) && (numThreads - busyThreads) > MIN_THREADS) {
					//must remove the worker immediately to prevent all threads from expiring
					endWorker(worker);
					return null;
				}
			}
		}
		if (job != null) {
			incrementBusyThreads();
			//if this job has a rule, then we are essentially acquiring a lock
			if ((job.getRule() != null) && !(job instanceof ImplicitJobs.ThreadJob)) {
				//don't need to reaquire locks because it was not recorded in the graph
				//that this thread waited to get this rule
				manager.getLockManager().addLockThread(Thread.currentThread(), job.getRule());
			}
			//see if we need to wake another worker
			if (manager.sleepHint() <= 0)
				jobQueued(null);
		}
		return job;
	}

	protected synchronized void startup() {
		computeMaxThreads();
	}
}