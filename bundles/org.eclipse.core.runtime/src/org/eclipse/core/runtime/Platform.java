/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.runtime;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Map;
import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.osgi.framework.Bundle;

/**
 * The central class of the Eclipse Platform Runtime. This class cannot
 * be instantiated or subclassed by clients; all functionality is provided 
 * by static methods.  Features include:
 * <ul>
 * <li>the platform registry of installed plug-ins</li>
 * <li>the platform adapter manager</li>
 * <li>the platform log</li>
 * <li>the authorization info management</li>
 * </ul>
 * <p>
 * The platform is in one of two states, running or not running, at all
 * times. The only ways to start the platform running, or to shut it down,
 * are on the bootstrap <code>BootLoader</code> class. Code in plug-ins will
 * only observe the platform in the running state. The platform cannot
 * be shutdown from inside (code in plug-ins have no access to
 * <code>BootLoader</code>).
 * </p>
 */
public final class Platform {
	/**
	 * The unique identifier constant (value "<code>org.eclipse.core.runtime</code>")
	 * of the Core Runtime (pseudo-) plug-in.
	 */
	public static final String PI_RUNTIME = "org.eclipse.core.runtime"; //$NON-NLS-1$
	public static final String PI_BOOT = "org.eclipse.core.boot"; //$NON-NLS-1$
	/** 
	 * The simple identifier constant (value "<code>applications</code>") of
	 * the extension point of the Core Runtime plug-in where plug-ins declare
	 * the existence of runnable applications. A plug-in may define any
	 * number of applications; however, the platform is only capable
	 * of running one application at a time.
	 * 
	 * @see org.eclipse.core.boot.BootLoader#run
	 */
	public static final String PT_APPLICATIONS = "applications"; //$NON-NLS-1$

	/** 
	 * Debug option value denoting the time at which the platform runtime
	 * was started.  This constant can be used in conjunction with
	 * <code>getDebugOption</code> to find the string value of
	 * <code>System.currentTimeMillis()</code> when the platform was started.
		 */
	public static final String OPTION_STARTTIME = PI_RUNTIME + "/starttime"; //$NON-NLS-1$

	/**
	 * Name of a preference for configuring the performance level for this system.
	 *
	 * <p>
	 * This value can be used by all components to customize features to suit the 
	 * speed of the user's machine.  The platform job manager uses this value to make
	 * scheduling decisions about background jobs.
	 * </p>
	 * <p>
	 * The preference value must be an integer between the constant values
	 * MIN_PERFORMANCE and MAX_PERFORMANCE
	 * </p>
	 * @see #MIN_PERFORMANCE
	 * @see #MAX_PERFORMANCE
	 * @since 3.0
	 */
	public static final String PREF_PLATFORM_PERFORMANCE = "runtime.performance"; //$NON-NLS-1$

	/** 
	 * Constant (value 1) indicating the minimum allowed value for the 
	 * <code>PREF_PLATFORM_PERFORMANCE</code> preference setting.
	 * @since 3.0
	 */
	public static final int MIN_PERFORMANCE = 1;

	/** 
	 * Constant (value 5) indicating the maximum allowed value for the 
	 * <code>PREF_PLATFORM_PERFORMANCE</code> preference setting.
	 * @since 3.0
	 */
	public static final int MAX_PERFORMANCE = 5;

	/** 
	 * Status code constant (value 1) indicating a problem in a plug-in
	 * manifest (<code>plugin.xml</code>) file.
		 */
	public static final int PARSE_PROBLEM = 1;

	/**
	 * Status code constant (value 2) indicating an error occurred while running a plug-in.
		 */
	public static final int PLUGIN_ERROR = 2;

	/**
	 * Status code constant (value 3) indicating an error internal to the
	 * platform has occurred.
		 */
	public static final int INTERNAL_ERROR = 3;

	/**
	 * Status code constant (value 4) indicating the platform could not read
	 * some of its metadata.
		 */
	public static final int FAILED_READ_METADATA = 4;

	/**
	 * Status code constant (value 5) indicating the platform could not write
	 * some of its metadata.
		 */
	public static final int FAILED_WRITE_METADATA = 5;

	/**
	 * Status code constant (value 6) indicating the platform could not delete
	 * some of its metadata.
		 */
	public static final int FAILED_DELETE_METADATA = 6;
	/**
	 * Private constructor to block instance creation.
	 */
	private Platform() {
	}
	/**
	 * Adds the given authorization information to the keyring. The
	 * information is relevant for the specified protection space and the
	 * given authorization scheme. The protection space is defined by the
	 * combination of the given server URL and realm. The authorization 
	 * scheme determines what the authorization information contains and how 
	 * it should be used. The authorization information is a <code>Map</code> 
	 * of <code>String</code> to <code>String</code> and typically
	 * contains information such as usernames and passwords.
	 *
	 * @param serverUrl the URL identifying the server for this authorization
	 *		information. For example, "http://www.example.com/".
	 * @param realm the subsection of the given server to which this
	 *		authorization information applies.  For example,
	 *		"realm1@example.com" or "" for no realm.
	 * @param authScheme the scheme for which this authorization information
	 *		applies. For example, "Basic" or "" for no authorization scheme
	 * @param info a <code>Map</code> containing authorization information 
	 *		such as usernames and passwords (key type : <code>String</code>, 
	 *		value type : <code>String</code>)
	 * @exception CoreException if there are problems setting the
	 *		authorization information. Reasons include:
	 * <ul>
	 * <li>The keyring could not be saved.</li>
	 * </ul>
	 */
	public static void addAuthorizationInfo(URL serverUrl, String realm, String authScheme, Map info) throws CoreException {
		InternalPlatform.getDefault().addAuthorizationInfo(serverUrl, realm, authScheme, info);
	}
	/** 
	 * Adds the given log listener to the notification list of the platform.
	 * <p>
	 * Once registered, a listener starts receiving notification as entries
	 * are added to plug-in logs via <code>ILog.log()</code>. The listener continues to
	 * receive notifications until it is replaced or removed.
	 * </p>
	 *
	 * @param listener the listener to register
	 * @see ILog#addLogListener
	 * @see #removeLogListener
	 */
	public static void addLogListener(ILogListener listener) {
		InternalPlatform.getDefault().addLogListener(listener);
	}
	/**
	 * Adds the specified resource to the protection space specified by the
	 * given realm. All targets at or deeper than the depth of the last
	 * symbolic element in the path of the given resource URL are assumed to
	 * be in the same protection space.
	 *
	 * @param resourceUrl the URL identifying the resources to be added to
	 *		the specified protection space. For example,
	 *		"http://www.example.com/folder/".
	 * @param realm the name of the protection space. For example,
	 *		"realm1@example.com"
	 * @exception CoreException if there are problems setting the
	 *		authorization information. Reasons include:
	 * <ul>
	 * <li>The keyring could not be saved.</li>
	 * </ul>
	 */
	public static void addProtectionSpace(URL resourceUrl, String realm) throws CoreException {
		InternalPlatform.getDefault().addProtectionSpace(resourceUrl, realm);
	}
	/**
	 * Returns a URL which is the local equivalent of the
	 * supplied URL. This method is expected to be used with 
	 * plug-in-relative URLs returned by IPluginDescriptor.
	 * If the specified URL is not a plug-in-relative URL, it 
	 * is returned asis. If the specified URL is a plug-in-relative
	 * URL of a file (incl. .jar archive), it is returned as 
	 * a locally-accessible URL using "file:" or "jar:file:" protocol
	 * (caching the file locally, if required). If the specified URL
	 * is a plug-in-relative URL of a directory,
	 * an exception is thrown.
	 *
	 * @param url original plug-in-relative URL.
	 * @return the resolved URL
	 * @exception IOException if unable to resolve URL
	 * @see #resolve
	 * @see IPluginDescriptor#getInstallURL
	 */
	public static URL asLocalURL(URL url) throws IOException {
		return InternalPlatform.getDefault().asLocalURL(url);
	}

	/**
	 * Takes down the splash screen if one was put up.
	 */
	public static void endSplash() {
		InternalPlatform.getDefault().endSplash();
	}

	/**
	 * Removes the authorization information for the specified protection
	 * space and given authorization scheme. The protection space is defined
	 * by the given server URL and realm.
	 *
	 * @param serverUrl the URL identifying the server to remove the
	 *		authorization information for. For example,
	 *		"http://www.example.com/".
	 * @param realm the subsection of the given server to remove the
	 *		authorization information for. For example,
	 *		"realm1@example.com" or "" for no realm.
	 * @param authScheme the scheme for which the authorization information
	 *		to remove applies. For example, "Basic" or "" for no
	 *		authorization scheme.
	 * @exception CoreException if there are problems removing the
	 *		authorization information. Reasons include:
	 * <ul>
	 * <li>The keyring could not be saved.</li>
	 * </ul>
	 */
	public static void flushAuthorizationInfo(URL serverUrl, String realm, String authScheme) throws CoreException {
		InternalPlatform.getDefault().flushAuthorizationInfo(serverUrl, realm, authScheme);
	}
	/**
	 * Returns the adapter manager used for extending
	 * <code>IAdaptable</code> objects.
	 *
	 * @return the adapter manager for this platform
	 * @see IAdapterManager
	 */
	public static IAdapterManager getAdapterManager() {
		return InternalPlatform.getDefault().getAdapterManager();
	}
	/**
	 * Returns the authorization information for the specified protection
	 * space and given authorization scheme. The protection space is defined
	 * by the given server URL and realm. Returns <code>null</code> if no
	 * such information exists.
	 *
	 * @param serverUrl the URL identifying the server for the authorization
	 *		information. For example, "http://www.example.com/".
	 * @param realm the subsection of the given server to which the
	 *		authorization information applies.  For example,
	 *		"realm1@example.com" or "" for no realm.
	 * @param authScheme the scheme for which the authorization information
	 *		applies. For example, "Basic" or "" for no authorization scheme
	 * @return the authorization information for the specified protection
	 *		space and given authorization scheme, or <code>null</code> if no
	 *		such information exists
	 */
	public static Map getAuthorizationInfo(URL serverUrl, String realm, String authScheme) {
		return InternalPlatform.getDefault().getAuthorizationInfo(serverUrl, realm, authScheme);
	}
	/**
	 * Returns the command line args provided to the platform when it was first run.
	 * Note that individual platform runnables may be provided with different arguments
	 * if they are being run individually rather than with <code>Platform.run()</code>.
	 * 
	 * @return the command line used to start the platform
	 */
	public static String[] getCommandLineArgs() {
		return InternalPlatform.getDefault().getAppArgs();
	}
	/**
	 * Returns the identified option.  <code>null</code>
	 * is returned if no such option is found.   Options are specified
	 * in the general form <i>&ltplug-in id&gt/&ltoption-path&gt</i>.  
	 * For example, <code>org.eclipse.core.runtime/debug</code>
	 *
	 * @param option the name of the option to lookup
	 * @return the value of the requested debug option or <code>null</code>
	 */
	public static String getDebugOption(String option) {
		return InternalPlatform.getDefault().getOption(option);
	}
	/**
	 * Returns the location of the platform working directory.  This 
	 * corresponds to the <i>-data</i> command line argument if
	 * present or, if not, the current working directory when the platform
	 * was started.
	 *
	 * @return the location of the platform
	 */
	public static IPath getLocation() {
		return InternalPlatform.getDefault().getLocation();
	}
	/**
	 * Returns the location of the platform log file.  This file may contain information
	 * about errors that have previously occurred during this invocation of the Platform.
	 * 
	 * Note: it is very important that users of this method do not leave the log
	 * file open for extended periods of time.  Doing so may prevent others
	 * from writing to the log file, which could result in important error messages
	 * being lost.  It is strongly recommended that clients wanting to read the
	 * log file for extended periods should copy the log file contents elsewhere,
	 * and immediately close the original file.
	 * 
	 * @return the path of the log file on disk.
	 */
	public static IPath getLogFileLocation() {
		return InternalPlatform.getDefault().getMetaArea().getLogLocation();
	}
	/**
	 * Returns the plug-in runtime object for the identified plug-in
	 * or <code>null</code> if no such plug-in can be found.  If
	 * the plug-in is defined but not yet activated, the plug-in will
	 * be activated before being returned.
	 *
	 * @param id the unique identifier of the desired plug-in 
	 *		(e.g., <code>"com.example.acme"</code>).
	 * @return the plug-in runtime object, or <code>null</code>
	 * @deprecated TODO
	 */
	public static Plugin getPlugin(String id) {
		try {
			IPluginDescriptor pd = getPluginRegistry().getPluginDescriptor(id);
			if (pd == null)
				return null;

			return pd.getPlugin();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * Returns the plug-in registry for this platform.
	 *
	 * @return the plug-in registry
	 * @see IPluginRegistry
	 * @deprecated Use @link #getExtensionRegistry() getExtensionRegistry
	 */
	public static IPluginRegistry getPluginRegistry() {
		Bundle compatibility = org.eclipse.core.internal.runtime.InternalPlatform.getDefault().getBundle(IPlatform.PI_RUNTIME_COMPATIBILITY);
		if (compatibility == null)
			return null;
		
		Class oldInternalPlatform = null;
		try {
			oldInternalPlatform = compatibility.loadClass("org.eclipse.core.internal.plugins.InternalPlatform"); //$NON-NLS-1$
			Method getPluginRegistry = oldInternalPlatform.getMethod("getPluginRegistry", null); //$NON-NLS-1$
			return (IPluginRegistry) getPluginRegistry.invoke(oldInternalPlatform, null);
		} catch (Exception e) {
			//Ignore the exceptions, return false
		}
		return null;

	}
	/**
	 * Returns the location in the local file system of the plug-in 
	 * state area for the given plug-in.
	 * The platform must be running.
	 * <p>
	 * The plug-in state area is a file directory within the
	 * platform's metadata area where a plug-in is free to create files.
	 * The content and structure of this area is defined by the plug-in,
	 * and the particular plug-in is solely responsible for any files
	 * it puts there. It is recommended for plug-in preference settings.
	 * </p>
	 *
	 * @param plugin the plug-in whose state location is returned
	 * @return a local file system path
	 * @deprecated 
	 */
	public static IPath getPluginStateLocation(Plugin plugin) {
		return plugin.getStateLocation();
	}
	/**
	 * Returns the protection space (realm) for the specified resource, or
	 * <code>null</code> if the realm is unknown.
	 *
	 * @param resourceUrl the URL of the resource whose protection space is
	 *		returned. For example, "http://www.example.com/folder/".
	 * @return the protection space (realm) for the specified resource, or
	 *		<code>null</code> if the realm is unknown
	 */
	public static String getProtectionSpace(URL resourceUrl) {
		return InternalPlatform.getDefault().getProtectionSpace(resourceUrl);
	}
	/** 
	 * Removes the indicated (identical) log listener from the notification list
	 * of the platform.  If no such listener exists, no action is taken.
	 *
	 * @param listener the listener to deregister
	 * @see ILog#removeLogListener
	 * @see #addLogListener
	 */
	public static void removeLogListener(ILogListener listener) {
		InternalPlatform.getDefault().removeLogListener(listener);
	}
	/**
	 * Returns a URL which is the resolved equivalent of the
	 * supplied URL. This method is expected to be used with 
	 * plug-in-relative URLs returned by IPluginDescriptor.
	 * If the specified URL is not a plug-in-relative URL, it is returned
	 * as is. If the specified URL is a plug-in-relative URL, it is
	 * resolved to a URL using the actual URL protocol
	 * (eg. file, http, etc)
	 *
	 * @param url original plug-in-relative URL.
	 * @return the resolved URL
	 * @exception IOException if unable to resolve URL
	 * @see #asLocalURL
	 * @see IPluginDescriptor#getInstallURL
	 */
	public static URL resolve(URL url) throws java.io.IOException {
		return InternalPlatform.getDefault().resolve(url);
	}
	/**
	 * Runs the given runnable in a protected mode.   Exceptions
	 * thrown in the runnable are logged and passed to the runnable's
	 * exception handler.  Such exceptions are not rethrown by this method.
	 *
	 * @param code the runnable to run
	 */
	public static void run(ISafeRunnable runnable) {
		InternalPlatform.getDefault().run(runnable);
	}
	/**
	 * Returns the platform job manager.
	 */
	public static IJobManager getJobManager() {
		return InternalPlatform.getDefault().getJobManager();
	}
	/**
	 * Returns the extension registry for this platform.
	 *
	 * @return the extension registry
	 * @see IExtensionRegistry
	 * @since 3.0
	 */
	public static IExtensionRegistry getExtensionRegistry() {
		return InternalPlatform.getDefault().getRegistry();
	}
}
