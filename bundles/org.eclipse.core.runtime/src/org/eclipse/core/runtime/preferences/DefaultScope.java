/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.runtime.preferences;

import org.eclipse.core.internal.preferences.AbstractScope;
import org.eclipse.core.runtime.IPath;

/**
 * Object representing the default scope in the Eclipse preferences
 * hierarchy. Can be used as a context for searching for preference
 * values (in the IPreferencesService APIs) or for determining the 
 * correct preference node to set values in the store.
 * <p>
 * Default preferences are not persisted to disk.
 * </p>
 * <p>
 * The path for preferences defined in the default scope hierarchy
 * is as follows: <code>/default/&lt;qualifier&gt;</code>
 * </p>
 * @since 3.0
 */
public final class DefaultScope extends AbstractScope {

	/**
	 * String constant (value of <code>"default"</code>) used for the 
	 * scope name for the default preference scope.
	 */
	public static final String SCOPE = "default"; //$NON-NLS-1$

	/*
	 * @see org.eclipse.core.runtime.preferences.IScopeContext#getName()
	 */
	public String getName() {
		return SCOPE;
	}

	/*
	 * @see org.eclipse.core.runtime.preferences.IScopeContext#getLocation()
	 */
	public IPath getLocation() {
		// We don't persist defaults so return null.
		return null;
	}
}