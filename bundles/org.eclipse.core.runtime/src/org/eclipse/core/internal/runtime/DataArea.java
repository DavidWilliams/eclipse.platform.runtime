/**********************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.runtime;
import java.io.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.*;
import org.osgi.framework.Bundle;
public class DataArea {
	/* package */static final String F_DESCRIPTION = ".platform"; //$NON-NLS-1$
	/* package */static final String F_META_AREA = ".metadata"; //$NON-NLS-1$
	/* package */static final String F_PLUGIN_PATH = ".plugin-path"; //$NON-NLS-1$
	/* package */static final String F_PLUGIN_DATA = ".plugins"; //$NON-NLS-1$
	/* package */static final String F_LOG = ".log"; //$NON-NLS-1$
	/* package */static final String F_BACKUP = ".bak"; //$NON-NLS-1$
	/* package */static final String F_KEYRING = ".keyring"; //$NON-NLS-1$
	/* package */static final String F_LOCK_FILE = ".lock"; //$NON-NLS-1$
	/* package */static final String F_VERSION = "version.ini"; //$NON-NLS-1$
	/**
	 * Internal name of the preference storage file (value <code>"pref_store.ini"</code>) in this plug-in's (read-write) state area.
	 */
	/* package */static final String PREFERENCES_FILE_NAME = "pref_store.ini"; //$NON-NLS-1$
	
	private IPath location; //The location of the instance data
	private boolean locationCreated = false;
	private IPath tmpLog; //The name of the temporary log file
	private PlatformMetaAreaLock metaAreaLock = null;
	
	//Authorization related informations
	private AuthorizationDatabase keyring = null;
	private long keyringTimeStamp;
	private String keyringFile = null;
	private String password = ""; //$NON-NLS-1$
	private boolean initialized = false;
	
	public boolean hasInstanceData() {
		return isInstanceDataLocationInitiliazed();
	}
	boolean isInstanceDataLocationInitiliazed() {
		return location != null && initialized;
	}
	protected void assertLocationInitialized() throws IllegalStateException {
		try {
			if (location == null || ! initialized)
				initializeLocation();
			if (!locationCreated)
				createLocation();
		} catch (CoreException e) {
			throw new IllegalStateException(e.getMessage());
		}
		if (!isInstanceDataLocationInitiliazed()) {
			throw new IllegalStateException(Policy.bind("meta.instanceDataUnspecified")); //$NON-NLS-1$
		}
	}
	public IPath getBackupFilePathFor(IPath file) throws IllegalStateException {
		assertLocationInitialized();
		return file.removeLastSegments(1).append(file.lastSegment() + F_BACKUP);
	}
	public IPath getMetadataLocation() throws IllegalStateException {
		assertLocationInitialized();
		return location.append(F_META_AREA);
	}
	public IPath getInstanceDataLocation() throws IllegalStateException {
		assertLocationInitialized();
		return location;
	}
	public IPath getLogLocation() throws IllegalStateException {
		if (!isInstanceDataLocationInitiliazed()) {
			return getTemporaryLogLocation();
		}
		if (tmpLog != null)
			copyOldLog(getTemporaryLogLocation(), getMetadataLocation().append(F_LOG));
		return getMetadataLocation().append(F_LOG);
	}
	protected IPath getTemporaryLogLocation() {
		if (tmpLog == null)
			tmpLog = InternalPlatform.getDefault().getConfigurationMetadataLocation().append(Long.toString(System.currentTimeMillis()) + F_LOG);
		return tmpLog;
	}
	private void copyOldLog(IPath from, IPath to) {
		File source = from.toFile();
		if (!source.exists())
			return;
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(source);
			output = new FileOutputStream(to.toFile(), true);
			transferStreams(input, output);
		} catch (IOException e) {
		} finally {
			try {
			if (input != null)
				input.close();
			if (output != null)
				output.close();
			} catch(IOException e) {
			}
		}		
	}
	private void transferStreams(InputStream source, OutputStream destination) throws IOException {
		source = new BufferedInputStream(source);
		destination = new BufferedOutputStream(destination);
		try {
			byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = -1;
				if ((bytesRead = source.read(buffer)) == -1)
					break;
				destination.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				source.close();
			} catch (IOException e) {
			}
			try {
				destination.close();
			} catch (IOException e) {
			}
		}
	}
	
	/**
	 * Returns the read/write location in which the given bundle can manage private state.
	 */
	public IPath getStateLocation(Bundle bundle) throws IllegalStateException {
		assertLocationInitialized();
		return getMetadataLocation().append(F_PLUGIN_DATA).append(bundle.getGlobalName());
	}
	/**
	 * Returns the read/write location of the file for storing plugin preferences.
	 */
	public IPath getPreferenceLocation(Bundle bundle, boolean create) throws IllegalStateException {
		assertLocationInitialized();
		IPath result = getStateLocation(bundle);
		if (create)
			result.toFile().mkdirs();
		return result.append(PREFERENCES_FILE_NAME);
	}
	/**
	 * Return the path to the version.ini file.
	 */
	public IPath getVersionPath() throws IllegalStateException {
		assertLocationInitialized();
		return getMetadataLocation().append(F_VERSION);
	}
	public void setInstanceDataLocation(IPath loc) throws IllegalStateException {
		if (isInstanceDataLocationInitiliazed())
			throw new IllegalStateException(Policy.bind("meta.instanceDataAlreadySpecified", loc.toOSString()));
		location = loc;
	}
	protected void initializeLocation() throws CoreException {
		if (location == null) {
			// Default location for the workspace is <user.dir>/workspace/
			location = new Path(System.getProperty("user.dir")).append(InternalPlatform.WORKSPACE); //$NON-NLS-1$
		}
		// check if the location can be created
		if (location.toFile().exists()) {
			if (!location.toFile().isDirectory()) {
				String message = Policy.bind("meta.notDir", location.toString()); //$NON-NLS-1$
				throw new CoreException(new Status(IStatus.ERROR, Platform.PI_RUNTIME, IPlatform.FAILED_WRITE_METADATA, message, null));
			}
		}
		//try infer the device if there isn't one (windows)
		if (location.getDevice() == null)
			location = new Path(location.toFile().getAbsolutePath());
		
		initialized = true;
	}
	private void createLocation() throws CoreException {
		File file = location.toFile();
		try {
			file.mkdirs();
		} catch (Exception e) {
			String message = Policy.bind("meta.couldNotCreate", file.getAbsolutePath()); //$NON-NLS-1$
			throw new CoreException(new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, IPlatform.FAILED_WRITE_METADATA, message, e));
		}
		if (!file.canWrite()) {
			String message = Policy.bind("meta.readonly", file.getAbsolutePath()); //$NON-NLS-1$
			throw new CoreException(new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, IPlatform.FAILED_WRITE_METADATA, message, null));
		}
		locationCreated = true;
	}
	/**
	 * Creates a lock file in the meta-area that indicates the meta-area is in use, preventing other eclipse instances from concurrently using the same meta-area.
	 */
	public synchronized void createLockFile() throws CoreException, IllegalStateException {
		assertLocationInitialized();
		if (System.getProperty("org.eclipse.core.runtime.ignoreLockFile") != null) //$NON-NLS-1$
			return;
		String lockLocation = getMetadataLocation().append(F_LOCK_FILE).toOSString();
		metaAreaLock = new PlatformMetaAreaLock(new File(lockLocation));
		try {
			if (!metaAreaLock.acquire()) {
				String message = Policy.bind("meta.inUse", lockLocation); //$NON-NLS-1$
				throw new CoreException(new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, IPlatform.FAILED_WRITE_METADATA, message, null));
			}
		} catch (IOException e) {
			String message = Policy.bind("meta.failCreateLock", lockLocation); //$NON-NLS-1$
			throw new CoreException(new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, IPlatform.FAILED_WRITE_METADATA, message, e));
		}
	}
	/**
	 * Closes the open lock file handle, and makes a silent best attempt to delete the file.
	 */
	public synchronized void clearLockFile() throws IllegalStateException {
		assertLocationInitialized();
		if (metaAreaLock != null)
			metaAreaLock.release();
	}
	/**
	 * @see Platform
	 */
	public void addAuthorizationInfo(URL serverUrl, String realm, String authScheme, Map info) throws CoreException {
		loadKeyring();
		keyring.addAuthorizationInfo(serverUrl, realm, authScheme, new HashMap(info));
		keyring.save();
	}
	/**
	 * @see Platform
	 */
	public void addProtectionSpace(URL resourceUrl, String realm) throws CoreException {
		loadKeyring();
		keyring.addProtectionSpace(resourceUrl, realm);
		keyring.save();
	}
	/**
	 * @see Platform
	 */
	public void flushAuthorizationInfo(URL serverUrl, String realm, String authScheme) throws CoreException {
		loadKeyring();
		keyring.flushAuthorizationInfo(serverUrl, realm, authScheme);
		keyring.save();
	}
	/**
	 * @see Platform
	 */
	public Map getAuthorizationInfo(URL serverUrl, String realm, String authScheme) {
		loadKeyring();
		Map info = keyring.getAuthorizationInfo(serverUrl, realm, authScheme);
		return info == null ? null : new HashMap(info);
	}
	/**
	 * @see Platform
	 */
	public String getProtectionSpace(URL resourceUrl) {
		loadKeyring();
		return keyring.getProtectionSpace(resourceUrl);
	}
	/**
	 * Opens the password database (if any) initally provided to the platform at startup.
	 */
	private void loadKeyring() {
		if (keyring != null && new File(keyringFile).lastModified()==keyringTimeStamp)
			return;
		if (keyringFile == null) {
			keyringFile = InternalPlatform.getDefault().getConfigurationMetadataLocation().append(F_KEYRING).toOSString();
		}
		try {
			keyring = new AuthorizationDatabase(keyringFile, password);
		} catch (CoreException e) {
			InternalPlatform.getDefault().log(e.getStatus());
		}
		if (keyring == null) {
			//try deleting the file and loading again - format may have changed
			new java.io.File(keyringFile).delete();
			try {
				keyring = new AuthorizationDatabase(keyringFile, password);
			} catch (CoreException e) {
				//don't bother logging a second failure
			}
		}
		keyringTimeStamp = new File(keyringFile).lastModified();
	}
	public void setKeyringFile(String keyringFile) {
		if (this.keyringFile != null)
			throw new IllegalStateException(Policy.bind("meta.keyringFileAlreadySpecified", this.keyringFile));
		this.keyringFile = keyringFile;
	}
	public void setPasswork(String password) {
		this.password = password;
	}
}
