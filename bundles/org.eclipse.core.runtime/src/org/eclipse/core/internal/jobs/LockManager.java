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

import java.util.HashMap;
import java.util.Stack;
import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.internal.runtime.Policy;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.LockListener;

/**
 * Stores the only reference to the graph that contains all the known
 * relationships between locks, rules, and the threads that own them.
 * Synchronizes all access to the graph on the only instance that exists in this class.
 * 
 * Also stores the state of suspended locks so that they can be reacquired with 
 * the proper lock depth. 
 */
public class LockManager {

	/**
	 * This class captures the state of suspended locks.
	 * Locks are suspended if deadlock is detected.
	 */
	private static class LockState {
		private int depth;
		private OrderedLock lock;
		/**
		 * Suspends ownership of the given lock, and returns the saved state.
		 */
		protected static LockState suspend(OrderedLock lock) {
			LockState state = new LockState();
			state.lock = lock;
			state.depth = lock.forceRelease();
			return state;
		}
		/**
		 * Re-acquires a suspended lock and reverts to the correct lock depth.
		 */
		public void resume() {
			//spin until the lock is successfully acquired
			//NOTE: spinning here allows the UI thread to service pending syncExecs
			//if the UI thread is waiting to acquire a lock.
			while (true) {
				try {
					if (lock.acquire(Long.MAX_VALUE))
						break;
				} catch (InterruptedException e) {
				}
			}
			lock.setDepth(depth);
		}
	}

	//the lock listener for this lock manager
	protected LockListener lockListener;
	/* 
	 * The internal data structure that stores all the relationships 
	 * between the locks (or rules) and the threads that own them.
	 */
	private DeadlockDetector locks = new DeadlockDetector();
	/* 
	 * Stores thread - stack pairs where every entry in the stack is an array 
	 * of locks that were suspended while the thread was aquiring more locks
	 * (a stack is needed because when a thread tries to reaquire suspended locks,
	 * it can cause deadlock, and some locks it owns can be suspended again)
	 */
	private HashMap suspendedLocks = new HashMap();

	public LockManager() {
	}
	/* (non-Javadoc)
	 * Method declared on LockListener
	 */
	public void aboutToRelease() {
		if (lockListener == null)
			return;
		try {
			lockListener.aboutToRelease();
		} catch (Exception e) {
			handleException(e);
		} catch (LinkageError e) {
			handleException(e);
		}
	}
	/* (non-Javadoc)
	 * Method declared on LockListener
	 */
	public boolean aboutToWait(Thread lockOwner) {
		if (lockListener == null)
			return false;
		try {
			return lockListener.aboutToWait(lockOwner);
		} catch (Exception e) {
			handleException(e);
		} catch (LinkageError e) {
			handleException(e);
		}
		return false;
	}
	/**
	 * This thread has just acquired a lock.  Update graph.
	 */
	void addLockThread(Thread thread, ISchedulingRule lock) {
		synchronized (locks) {
			locks.lockAcquired(thread, lock);
		}
	}
	/**
	 * This thread has just been refused a lock.  Update graph and check for deadlock.
	 */
	void addLockWaitThread(Thread thread, ISchedulingRule lock) {
		synchronized (locks) {
			Deadlock found = locks.lockWaitStart(thread, lock);
			// if deadlock was detected, the found variable will contain all the information about it,
			// including which locks to suspend for which thread to resolve the deadlock.
			if (found != null) {
				ISchedulingRule[] toSuspend = found.getLocks();
				LockState[] suspended = new LockState[toSuspend.length];
				for (int i = 0; i < toSuspend.length; i++) 
					suspended[i] = LockState.suspend((OrderedLock) toSuspend[i]);
				synchronized (suspendedLocks) {
					Stack prevLocks = (Stack) suspendedLocks.get(found.getCandidate());
					if (prevLocks == null)
						prevLocks = new Stack();
					prevLocks.push(suspended);
					suspendedLocks.put(found.getCandidate(), prevLocks);
				}
			}
		}
	}
	private static void handleException(Throwable e) {
		String message = Policy.bind("jobs.internalError"); //$NON-NLS-1$
		IStatus status;
		if (e instanceof CoreException) {
			status = new MultiStatus(IPlatform.PI_RUNTIME, IPlatform.PLUGIN_ERROR, message, e);
			((MultiStatus) status).merge(((CoreException) e).getStatus());
		} else {
			status = new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, IPlatform.PLUGIN_ERROR, message, e);
		}
		InternalPlatform.getDefault().log(status);
	}
	/**
	 * Returns true IFF the underlying graph is empty.
	 * For debugging purposes only.
	 */
	public boolean isEmpty() {
		return locks.isEmpty();
	}
	/**
	 * Returns true IFF this thread either owns, or is waiting for, any locks or rules.
	 */
	public boolean isLockOwner() {
		//all job threads have to be treated as lock owners because UI thread 
		//may try to join a job
		Thread current = Thread.currentThread();
		if (current instanceof Worker)
			return true;
		synchronized (locks) {
			return locks.contains(Thread.currentThread());
		}
	}
	/**
	 * Creates and returns a new lock.
	 */
	public synchronized OrderedLock newLock() {
		return new OrderedLock(this);
	}
	/**
	 * Releases all the acquires that were called on the given rule. Needs to be called only once.
	 */
	void removeLockCompletely(Thread thread, ISchedulingRule rule) {
		synchronized (locks) {
			locks.lockReleasedCompletely(thread, rule);
		}
	}
	/**
	 * This thread has just released a lock.  Update graph.
	 */
	void removeLockThread(Thread thread, ISchedulingRule lock) {
		synchronized (locks) {
			locks.lockReleased(thread, lock);
		}
	}
	/**
	 * This thread has just stopped waiting for a lock. Update graph.
	 */
	void removeLockWaitThread(Thread thread, ISchedulingRule lock) {
		synchronized (locks) {
			locks.lockWaitStop(thread, lock);
		}
	}
	/**
	 * Resumes all the locks that were suspended while this thread was waiting to acquire another lock.
	 */
	void resumeSuspendedLocks(Thread owner) {
		LockState[] toResume;
		synchronized (suspendedLocks) {
			Stack prevLocks = (Stack) suspendedLocks.get(owner);
			if (prevLocks == null)
				return;
			toResume = (LockState[]) prevLocks.pop();
			if (prevLocks.empty())
				suspendedLocks.remove(owner);
		}
		for (int i = 0; i < toResume.length; i++)
			toResume[i].resume();
	}
	public void setLockListener(LockListener listener) {
		this.lockListener = listener;
	}
}