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
package org.eclipse.core.runtime;

import org.eclipse.core.runtime.IExtensionPoint;

/**
 * An extension delta represents changes to the extension registry.
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 
 * @since 3.0
 */
public interface IExtensionDelta {
	/**
	 * Delta kind constant indicating that the extension has been added to the extension registry.
	 * @see IExtensionDelta#getKind
	 */
	public int ADDED = 1;
	/**
	 * Delta kind constant indicating that the extension has been removed from the extension registry.
	 * @see IExtensionDelta#getKind
	 */
	public int REMOVED = 2;
	/**
	 * The kind of this extension delta.
	 * @see #ADDED
	 * @see #REMOVED
	 */
	public int getKind();
	/**
	 * Returns the affected extension.
	 * 
	 * @return the affected extension
	 */
	public IExtension getExtension();
	/**
	 * Returns the affected extension point.
	 * 
	 * @return the affected extension point
	 */
	public IExtensionPoint getExtensionPoint();
}