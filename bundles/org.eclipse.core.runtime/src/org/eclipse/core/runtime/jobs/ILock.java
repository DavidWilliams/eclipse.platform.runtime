/**********************************************************************
 * Copyright (c) 2003 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.runtime.jobs;

/**
 * A lock is used to control access to an exclusive resource.
 * <p>
 * Locks are reentrant.  That is, they can be acquired multiple times by the same thread
 * without releasing.  Locks are only released when the number of successful acquires 
 * equals the number of successful releases.
 * </p>
 * <p>
 * Locks avoid circular waiting deadlocks by employing a release and wait strategy.
 * If a group of threads are involved in a deadlock, one thread will lose control
 * of the locks it owns, thus breaking the deadlock and allowing other threads to 
 * proceed.  Once that thread's locks are all available, it will be given exclusive access
 * to all its locks and allowed to proceed.  A thread can only lose locks while it is
 * waiting on an acquire() call.
 * </p>
 * <p>
 * Successive acquire attempts by different threads are queued and serviced on
 * a first come, first served basis.
 * </p>
 * <p>
 * It is very important that acquired locks eventually get released.  Calls to release
 * should be done in a finally block to ensure they execute.  For example:
 * <pre>
 * lock.acquire();
 * try {
 * 	// ... do work here ...
 * } finally {
 * 	lock.release();
 * }
 * </pre>
 * 
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 * @see IJobManager#newLock
 * @since 3.0
 */
public interface ILock {
	/**
	 * Attempts to acquire this lock.  If the lock is in use and the specified delay is
	 * greather than zero, the calling thread will block until all locks this thread already 
	 * owns are simultaneously available, AND:
	 * <ul>
	 * <li>This lock is available</li>
	 * <li>The thread is interrupted</li>
	 * <li>The specified delay has elapsed</li>
	 * </ul>
	 * <p>
	 * While a thread is waiting,  locks it already owns may be granted to other threads 
	 * if necessary to break a deadlock.  In this situation, the calling thread may be blocked
	 * for longer than the specified delay.  On returning from this call, the calling thread 
	 * will once again have exclusive access to any other locks it owned upon entering 
	 * the acquire method.
	 * 
	 * @return <code>true</code> if the lock was successfully acquired, and 
	 * <code>false</code> otherwise.
	 */
	public boolean acquire(long delay) throws InterruptedException;
	/**
	 * Acquires this lock.  If the lock is in use, the calling thread will block until the lock 
	 * becomes available.  If the calling thread owns several locks, it will be blocked
	 * until all threads it requires become available, or until the thread is interrupted.
	 * While a thread is waiting, its locks may be granted to other threads if necessary
	 * to break a deadlock.  On returning from this call, the calling thread will 
	 * have exclusive access to this lock, and any other locks it owned upon
	 * entering the acquire method.
	 * <p>
	 * This implementation ignores attempts to interrupt the thread.  If response to
	 * interruption is needed, use the method <code>acquire(long)</code>
	 */
	public void acquire();
	/**
	 * Releases this lock. Releasing a lock the calling thread does not own will have
	 * no effect.
	 */
	public void release();
}