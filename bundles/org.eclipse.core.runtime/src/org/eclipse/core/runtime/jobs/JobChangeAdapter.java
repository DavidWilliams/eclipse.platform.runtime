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

import org.eclipse.core.runtime.IStatus;

/**
 * This adapter class provides default implementations for the
 * methods described by the <code>IJobChangeListener</code> interface.
 * <p>
 * Classes that wish to listen to the progress of scheduled jobs can
 * extend this class and override only the methods which they are
 * interested in.
 * </p>
 *
 * @see IJobChangeListener
 * @since 3.0
 */
public class JobChangeAdapter implements IJobChangeListener {
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void aboutToRun(Job job) {
	}
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void awake(Job job) {
	}
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void done(Job job, IStatus result) {
	}
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void running(Job job) {
	}
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void scheduled(Job job) {
	}
	/* (non-Javadoc)
	 * Method declared on IJobChangeListener.
	 * This default implementation does nothing
	 */
	public void sleeping(Job job) {
	}
}