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

import java.util.*;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobListener;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Responsible for notifying all job listeners about job lifecycle events.  Uses a
 * specialized iterator to ensure the complex iteration logic is contained in one place.
 */
public class JobListeners implements IJobListener {
	interface IListenerDoit {
		public void notify(IJobListener listener, Job job, IStatus result);
	}
	private final IListenerDoit aboutToRun = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.aboutToRun(job);
		}
	};
	private final IListenerDoit awake = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.awake(job);
		}
	};
	private final IListenerDoit done = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.done(job, result);
		}
	};
	private final IListenerDoit running = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.running(job);
		}
	};
	private final IListenerDoit scheduled = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.scheduled(job);
		}
	};
	private final IListenerDoit sleeping = new IListenerDoit() {
		public void notify(IJobListener listener, Job job, IStatus result) {
			listener.sleeping(job);
		}
	};
	/**
	 * The global job listeners.
	 */
	private final List global = Collections.synchronizedList(new ArrayList());

	/**
	 * Process the given doit for all global listeners and all local listeners
	 * on the given job.
	 */
	private void doNotify(IListenerDoit doit, Job job, IStatus result) {
		//notify all global listeners
		int size = global.size();
		for (int i = 0; i < size; i++) {
			//note: tolerate concurrent modification
			IJobListener listener = (IJobListener) global.get(i);
			if (listener != null)
				doit.notify(listener, job, result);
		}
		//notify all local listeners
		List local = ((InternalJob) job).getListeners();
		if (local != null) {
			size = local.size();
			for (int i = 0; i < size; i++) {
				//note: tolerate concurrent modification
				IJobListener listener = (IJobListener) global.get(i);
				if (listener != null)
					doit.notify(listener, job, result);
			}
		}
	}
	public void add(IJobListener listener) {
		global.add(listener);
	}
	public void remove(IJobListener listener) {
		global.remove(listener);
	}
	public void aboutToRun(Job job) {
		doNotify(aboutToRun, job, null);
	}
	public void awake(Job job) {
		doNotify(awake, job, null);
	}
	public void done(Job job, IStatus result) {
		doNotify(done, job, result);
	}
	public void running(Job job) {
		doNotify(running, job, null);
	}
	public void scheduled(Job job) {
		doNotify(scheduled, job, null);
	}
	public void sleeping(Job job) {
		doNotify(sleeping, job, null);
	}
}