package org.eclipse.core.internal.jobs;

public class Semaphore {
	protected long notifications;
	protected Runnable runnable;
	public Semaphore(Runnable runnable) {
		this.runnable = runnable;
		notifications = 0;
	}
	/**
	 * Attempts to acquire this semaphore.  Returns true if it was successfully acquired,
	 * and false otherwise.
	 */
	public synchronized boolean acquire(long delay) throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();
		long start = System.currentTimeMillis();
		long timeLeft = delay;
		while (true) {
			if (notifications > 0) {
				notifications--;
				return true;
			}
			if (timeLeft < 0)
				return false;
			wait(timeLeft);
			timeLeft = start + delay - System.currentTimeMillis();
		}
	}
	public boolean equals(Object obj) {
		return (runnable == ((Semaphore) obj).runnable);
	}
	public Runnable getRunnable() {
		return runnable;
	}
	public int hashCode() {
		return runnable.hashCode();
	}
	public synchronized void release() {
		notifications++;
		notifyAll();
	}
	// for debug only
	public String toString() {
		return runnable.toString();
	}
}