/*******************************************************************************
 * Copyright (c) 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.jobs;

import java.util.*;

import org.eclipse.core.internal.runtime.Assert;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;

/**
 * Implementation of API type IJobManager
 * 
 * Implementation note: all the data structures of this class are protected
 * by a single lock object held as a private field in this class.  The JobManager
 * instance itself is not used because this class is publicly reachable, and third
 * party clients may try to sychronize on it.
 * 
 * The WorkerPool class uses its own monitor for synchronizing its data
 * structures. To avoid deadlock between the two classes, the JobManager
 * must NEVER call the worker pool while its own monitor is held.
 */
public class JobManager implements IJobManager {
	public static final boolean DEBUG = true;
	private static JobManager instance;
	protected static final long NEVER = Long.MAX_VALUE;

	/**
	 * Flag to indicate that the system is still up and running.
	 */
	private boolean alive = false;
	private final JobListeners jobListeners = new JobListeners();

	/**
	 * The lock for synchronizing all activity in the job manager.  To avoid deadlock,
	 * this lock must never be held for extended periods, and must never be
	 * held while third party code is being called.
	 */
	private final Object lock = new Object();

	private final LockManager lockManager = new LockManager();

	/**
	 * The pool of worker threads.
	 */
	private WorkerPool pool;

	private IProgressProvider progressProvider = null;
	/**
	 * Jobs that are currently running.
	 */
	private final HashSet running = new HashSet(10);

	/**
	 * Jobs that are sleeping.  Some sleeping jobs are scheduled to wake
	 * up at a given start time, while others will sleep indefinitely until woken.
	 */
	private final PriorityQueue sleeping = new PriorityQueue();
	/**
	 * jobs that are waiting to be run
	 */
	private final PriorityQueue waiting = new PriorityQueue();

	public static synchronized JobManager getInstance() {
		if (instance == null) {
			instance = new JobManager();
		}
		return instance;
	}
	public static synchronized void shutdown() {
		if (instance != null)
			instance.doShutdown();
		instance = null;
	}
	private JobManager() {
		synchronized (lock) {
			alive = true;
			pool = new WorkerPool(this);
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.IJobManager#addJobListener(org.eclipse.core.runtime.jobs.IJobListener)
	 */
	public void addJobListener(IJobListener listener) {
		jobListeners.add(listener);
	}
	/**
	 * Cancels a job
	 */
	protected boolean cancel(Job job) {
		boolean canceled = true;
		synchronized (lock) {
			switch (job.getState()) {
				case Job.WAITING:
					waiting.remove(job);
					break;
				case Job.SLEEPING:
					sleeping.remove(job);
					break;
				case Job.RUNNING:
					((InternalJob)job).getMonitor().setCanceled(true);
					canceled = false;
					break;
				case Job.NONE:
				default:
					return true;
			}
		}
		//only notify listeners if the job was waiting or sleeping
		if (canceled) {
			((InternalJob) job).setState(Job.NONE);
			jobListeners.done(job, Status.CANCEL_STATUS);
		}
		return canceled;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.IJobManager#cancel(java.lang.String)
	 */
	public void cancel(String family) {
		synchronized (lock) {
			for (Iterator it = select(family).iterator(); it.hasNext();) {
				((Job)it.next()).cancel();
			}
		}
	}
	/**
	 * Returns a new progress monitor for this job.  Never returns null.
	 */
	protected IProgressMonitor createMonitor(Job job) {
		IProgressMonitor monitor = null;
		if (progressProvider != null)
			monitor = progressProvider.createMonitor(job);
		if (monitor == null)
			monitor = new NullProgressMonitor();
		((InternalJob)job).setMonitor(monitor);
		return monitor;
	}
	public Job currentJob() {
		Thread current = Thread.currentThread();
		if (current instanceof Worker)
			return ((Worker) current).currentJob();
		return null;
	}
	/**
	 * Returns the delay in milliseconds that a job with a given priority can
	 * tolerate waiting.
	 */
	private long delayFor(int priority) {
		//these values may need to be tweaked based on machine speed
		switch (priority) {
			case Job.INTERACTIVE :
				return 0L;
			case Job.SHORT :
				return 50L;
			case Job.LONG :
				return 100L;
			case Job.BUILD :
				return 500L;
			case Job.DECORATE :
				return 1000L;
			default :
				Assert.isTrue(false, "Job has invalid priority: " + priority); //$NON-NLS-1$
				return 0;
		}
	}
	/**
	 * Shuts down the job manager.  Currently running jobs will be told
	 * to stop, but worker threads may still continue processing.
	 */
	private void doShutdown() {
		synchronized (lock) {
			alive = false;
			//cancel all running jobs
			for (Iterator it = running.iterator(); it.hasNext();) {
				Job job = (Job) it.next();
				job.cancel();
			}
			//clean up
			sleeping.clear();
			waiting.clear();
			running.clear();
		}
		pool.shutdown();
	}
	/**
	 * Indicates that a job was running, and has now finished.
	 */
	protected void endJob(Job job, IStatus result) {
		InternalJob internalJob = (InternalJob)job;
		ListEntry blocked = null;
		synchronized (lock) {
			//if the job is finishing asynchronously, there is nothing more to do for now
			if (result == Job.ASYNC_FINISH) {
				internalJob.setAsyncFinish();
				return;
			}
			internalJob.setState(Job.NONE);
			internalJob.setMonitor(null);
			running.remove(job);
			blocked = job.next();
			job.setNext(null);
		}
		//add any blocked jobs back to the wait queue
		while (blocked != null) {
			ListEntry next = blocked.next();
			waiting.enqueue(blocked);
			pool.jobQueued((InternalJob)blocked);
			blocked = next;
		}
		//notify listeners outside sync block
		jobListeners.done(job, result);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.IJobManager#find(java.lang.String)
	 */
	public Job[] find(String family) {
		ArrayList members = select(family);
		return (Job[]) members.toArray(new Job[members.size()]);
	}
	/**
	 * Returns a running job whose scheduling rule conflicts with the scheduling rule
	 * of the given waiting job.  Returns null if there are no conflicting jobs.
	 */
	private Job findBlockingJob(Job waiting) {
		ISchedulingRule waitingRule = waiting.getRule();
		if (waitingRule == null)
			return null;
		for (Iterator it = running.iterator(); it.hasNext();) {
			Job job = (Job) it.next();
			ISchedulingRule testRule = job.getRule();
			if (testRule != null && testRule.isConflicting(waitingRule))
				return job;
		}
		return null;
	}
	/* (non-Javadoc)
	 * @see IJobManager#newLock(java.lang.String)
	 */
	public ILock newLock() {
		return lockManager.newLock();
	}
	/**
	 * Removes and returns the first waiting job in the queue. Returns null if there
	 * are no items waiting in the queue.  If an item is removed from the queue,
	 * it is moved to the running jobs list.
	 */
	private Job nextJob() {
		synchronized (lock) {
			//tickle the sleep queue to see if anyone wakes up
			long now = System.currentTimeMillis();
			InternalJob job = (InternalJob)sleeping.peek();
			while (job != null && job.getStartTime() < now) {
				sleeping.dequeue();
				job.setState(Job.WAITING);
				job.setStartTime(now + delayFor(job.getPriority()));
				waiting.enqueue(job);
				job = (InternalJob)sleeping.peek();
			}
			//process the wait queue until we find a job whose rules are satisfied.
			Job next = (Job)waiting.dequeue();
			while (next != null) {
				Job blocker = findBlockingJob(next);
				if (blocker == null)
					break;
				//queue this job after the job that's blocking it
				blocker.addLast(next);
				next = (Job)waiting.dequeue();
			}
			//the job to run must be in the running list before we exit
			//the sync block, otherwise two jobs with conflicting rules could start at once
			if (next != null)
				running.add(next);
			return next;
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.IJobManager#removeJobListener(org.eclipse.core.runtime.jobs.IJobListener)
	 */
	public void removeJobListener(IJobListener listener) {
		jobListeners.remove(listener);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.Job#schedule(long)
	 */
	protected void schedule(InternalJob job, long delay) {
		Assert.isNotNull(job, "Job is null"); //$NON-NLS-1$
		synchronized (lock) {
			if (delay > 0) {
				job.setState(Job.SLEEPING);
				job.setStartTime(System.currentTimeMillis() + delay);
				sleeping.enqueue(job);
			} else {
				job.setState(Job.WAITING);
				job.setStartTime(System.currentTimeMillis() + delayFor(job.getPriority()));
				waiting.enqueue(job);
			}
		}
		//call the pool outside sync block to avoid deadlock
		pool.jobQueued(job);

		//notify listeners outside sync block
		jobListeners.scheduled((Job)job);
	}
	/**
	 * Adds all family members in the list of jobs to the collection
	 */
	private void select(ArrayList members, String family, Job job) {
		while (job != null) {
			if (job.belongsTo(family))
				members.add(job);
			job = (Job)job.next();
		}
	}
	/**
	 * Returns a list of all jobs known to the job manager that belong to the given family.
	 */
	private ArrayList select(String family) {
		ArrayList members = new ArrayList();
		synchronized (lock) {
			for (Iterator it = running.iterator(); it.hasNext();) {
				select(members, family, (Job)it.next());
			}
			select(members, family, (Job)waiting.peek());
			select(members, family, (Job)sleeping.peek());
		}
		return members;
	}
	/* (non-Javadoc)
	 * @see IJobManager#setLockListener(ILockListener)
	 */
	public void setLockListener(ILockListener listener) {
		lockManager.setLockListener(listener);
	}
	/**
	 * Changes a job priority.
	 */
	protected void setPriority(InternalJob job, int newPriority) {
		synchronized (lock) {
			int oldPriority = job.getPriority();
			if (oldPriority == newPriority)
				return;
			job.internalSetPriority(newPriority);
			//if the job is waiting to run, reshuffle the queue
			if (job.getState() == Job.WAITING) {
				long oldStart = job.getStartTime();
				job.setStartTime(oldStart + (delayFor(newPriority) - delayFor(oldPriority)));
				waiting.resort(job);
			}
		}
	}
	/* (non-Javadoc)
	 * @see IJobManager#setProgressProvider(IProgressProvider)
	 */
	public void setProgressProvider(IProgressProvider provider) {
		progressProvider = provider;
	}
	/**
	 * Puts a job to sleep. Returns true if the job was successfully put to sleep.
	 */
	protected boolean sleep(InternalJob job) {
		synchronized (lock) {
			switch (job.getState()) {
				case Job.RUNNING :
					//cannot be paused if it is already running
					return false;
				case Job.SLEEPING :
					//update the job wake time
					job.setStartTime(NEVER);
					return true;
				case Job.NONE:
					return true;
				case Job.WAITING :
					//put the job to sleep
					waiting.remove(job);
					job.setStartTime(NEVER);
					job.setState(Job.SLEEPING);
					sleeping.enqueue(job);
					//fall through and notify listeners
			}
		}
		jobListeners.sleeping((Job) job);
		return true;
	}
	/* (non-Javadoc)
	 * @see IJobManager#sleep(String)
	 */
	public void sleep(String family) {
		synchronized (lock) {
			for (Iterator it = select(family).iterator(); it.hasNext();) {
				((Job)it.next()).sleep();
			}
		}
	}
	/**
	 * Returns the estimated time in milliseconds before the next job is scheduled
	 * to wake up. The result may be negative.  Returns JobManager.NEVER if
	 * there are no sleeping or waiting jobs.
	 */
	protected long sleepHint() {
		synchronized (lock) {
			if (waiting.peek() != null)
				return 0L;
			InternalJob next = (InternalJob)sleeping.peek();
			return next == null ? NEVER : next.getStartTime() - System.currentTimeMillis();
		}
	}
	/**
	 * Returns the next job to be run, or null if no jobs are waiting to run.
	 * The worker must call endJob when the job is finished running.  
	 */
	protected Job startJob() {
		while (true) {
			Job job = nextJob();
			if (job == null)
				return null;
			//must perform this outside sync block because it is third party code
			if (job.shouldRun()) {
				//check for listener veto
				jobListeners.aboutToRun(job);
				//listeners may have canceled or put the job to sleep
				if (job.getState() == Job.WAITING) {
					((InternalJob)job).setState(Job.RUNNING);
					jobListeners.running(job);
					return job;
				}
			}
			if (job.getState() != Job.SLEEPING) {
				//job has been vetoed or canceled, so mark it as done
				endJob(job, Status.CANCEL_STATUS);
				continue;
			}
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.jobs.IJobManager#wait(org.eclipse.core.runtime.jobs.Job, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void wait(Job job, IProgressMonitor monitor) {
	}
	/* (non-Javadoc)
	 * @see IJobManager#wait(String, IProgressMonitor)
	 */
	public void wait(String family, IProgressMonitor monitor) {
	}
	/**
	 * Implementation of wakeUp()
	 */
	protected void wakeUp(InternalJob job) {
		synchronized (lock) {
			//cannot wake up if it is not sleeping
			if (job.getState() != Job.SLEEPING)
				return;
			sleeping.remove(job);
			job.setState(Job.WAITING);
			job.setStartTime(System.currentTimeMillis() + delayFor(job.getPriority()));
			waiting.enqueue(job);
		}
		//call the pool outside sync block to avoid deadlock
		pool.jobQueued(job);

		jobListeners.awake((Job) job);
	}
	/* (non-Javadoc)
	 * @see IJobFamily#wakeUp(String)
	 */
	public void wakeUp(String family) {
		synchronized (lock) {
			for (Iterator it = select(family).iterator(); it.hasNext();) {
				((Job)it.next()).wakeUp();
			}
		}
	}
}