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

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import org.eclipse.core.internal.registry.Extension;
import org.eclipse.core.internal.runtime.*;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The abstract superclass of all plug-in runtime class
 * implementations. A plug-in subclasses this class and overrides
 * the <code>startup</code> and <code>shutdown</code> methods 
 * in order to react to life cycle requests automatically issued
 * by the platform.
 * <p>
 * Conceptually, the plug-in runtime class represents the entire plug-in
 * rather than an implementation of any one particular extension the
 * plug-in declares. A plug-in is not required to explicitly
 * specify a plug-in runtime class; if none is specified, the plug-in
 * will be given a default plug-in runtime object that ignores all life 
 * cycle requests (it still provides access to the corresponding
 * plug-in descriptor).
 * </p>
 * <p>
 * In the case of more complex plug-ins, it may be desireable
 * to define a concrete subclass of <code>Plugin</code>.
 * However, just subclassing <code>Plugin</code> is not
 * sufficient. The name of the class must be explicitly configured
 * in the plug-in's manifest (<code>plugin.xml</code>) file
 * with the class attribute of the <code>&ltplugin&gt</code> element markup.
 * </p>
 * <p>
 * Instances of plug-in runtime classes are automatically created 
 * by the platform in the course of plug-in activation.
 * <b>Clients must never explicitly instantiate a plug-in runtime class</b>.
 * </p>
 * <p>
 * A typical implementation pattern for plug-in runtime classes is to
 * provide a static convenience method to gain access to a plug-in's
 * runtime object. This way, code in other parts of the plug-in
 * implementation without direct access to the plug-in runtime object
 * can easily obtain a reference to it, and thence to any plug-in-wide
 * resources recorded on it. An example follows:
 * <pre>
 *     package myplugin;
 *     public class MyPluginClass extends Plugin {
 *         private static MyPluginClass instance;
 *
 *         public static MyPluginClass getInstance() { return instance; }
 *
 *         public void MyPluginClass(IPluginDescriptor descriptor) {
 *             super(descriptor);
 *             instance = this;
 *             // ... other initialization
 *         }
 *         // ... other methods
 *     }
 * </pre>
 * In the above example, a call to <code>MyPluginClass.getInstance()</code>
 * will always return an initialized instance of <code>MyPluginClass</code>.
 * </p>
 * <p>
 * The static method <code>Platform.getPlugin()</code>
 * can be used to locate a plug-in's runtime object by name.
 * The extension initialization would contain the following code:
 * <pre>
 *     Plugin myPlugin = Platform.getPlugin("com.example.myplugin");
 * </pre>
 * 
 * Another typical implementation pattern for plug-in classes
 * is handling of any initialization files required by the plug-in.
 * Typically, each plug-in will ship one or more default files
 * as part of the plug-in install. The executing plug-in will
 * use the defaults on initial startup (or when explicitly requested
 * by the user), but will subsequently rewrite any modifications
 * to the default settings into one of the designated plug-in
 * working directory locations. An example of such an implementation
 * pattern is illustrated below:
 * <pre>
 * package myplugin;
 * public class MyPlugin extends Plugin {
 *
 *     private static final String INI = "myplugin.ini"; 
 *     private Properties myProperties = null;
 *
 *     public void startup() throws CoreException {
 *         try {
 *             InputStream input = null;
 *             // look for working properties.  If none, use shipped defaults 
 *             File file = getStateLocation().append(INI).toFile();
 *             if (!file.exists()) {			
 *                 URL base = getDescriptor().getInstallURL();
 *                 input = (new URL(base,INI)).openStream();
 *             } else 
 *                 input = new FileInputStream(file);
 * 
 *             // load properties 
 *             try {
 *                 myProperties = new Properties();
 *                 myProperties.load(input);
 *             } finally {
 *                 try {
 *                     input.close();
 *                 } catch (IOException e) {
 *                     // ignore failure on close
 *                 }
 *             }
 *         } catch (Exception e) {
 *             throw new CoreException(
 *                 new Status(IStatus.ERROR, getDescriptor().getUniqueIdentifier(),
 *                     0, "Problems starting plug-in myplugin", e));
 *         }
 *     }
 *
 *     public void shutdown() throws CoreException { 
 *         // save properties in plugin state location (r/w)
 *         try {
 *             FileOutputStream output = null; 
 *             try {
 *                 output = new FileOutputStream(getStateLocation().append(INI)); 
 *                 myProperties.store(output, null);
 *             } finally {
 *                 try {
 *                     output.close();
 *                 } catch (IOException e) {
 *                     // ignore failure on close
 *                 }
 *             }
 *         } catch (Exception e) {
 *             throw new CoreException(
 *                 new Status(IStatus.ERROR, getDescriptor().getUniqueIdentifier(),
 *                     0, "Problems shutting down plug-in myplugin", e));
 *         }
 *     }
 *
 *     public Properties getProperties() {	
 *         return myProperties; 
 *     }
 * }
 * </pre>
 * </p>
 */
public abstract class Plugin implements BundleActivator {
	protected Bundle bundle;
	private BundleContext context;
	/**
	 * The debug flag for this plug-in.  The flag is false by default.
	 * It can be set to true either by the plug-in itself or in the platform 
	 * debug options.
	 */
	private boolean debug = false;

	/** The plug-in descriptor.
	 */
	private IPluginDescriptor descriptor;

	/**
	 * The name of the file (value <code>"preferences.ini"</code>) in a
	 * plug-in's (read-only) directory that, when present, contains values that
	 * override the normal default values for this plug-in's preferences.
	 * <p>
	 * The format of the file is as per <code>java.io.Properties</code> where
	 * the keys are property names and values are strings.
	 * </p>
	 * 
	 * @since 2.0
	 */
	public static final String PREFERENCES_DEFAULT_OVERRIDE_BASE_NAME = "preferences"; //$NON-NLS-1$
	public static final String PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME = PREFERENCES_DEFAULT_OVERRIDE_BASE_NAME + ".ini"; //$NON-NLS-1$
	private static final String PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME_BACKUP = PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME + ".bak";

	/**
	 * The preference object for this plug-in; initially <code>null</code>
	 * meaning not yet created and initialized.
	 * 
	 * @since 2.0
	 */
	private Preferences preferences = null;

	/**
	 * Creates a new plug-in runtime object.
	 * <p>
	 * Plug-in runtime classes are <code>BundleActivators</code> and so must
	 * have an default constructor.  This method is called by the runtime when the associated 
	 * bundle is being activated.  The resultant instance is not managed by the runtime and
	 * so should be remembered by the client (typically using a Singleton pattern).
	 * This method will never be called for plug-ins operating in compatibility mode.
	 * <b>Clients must never explicitly call this method.</b>
	 * </p> 
	 * <p>
	 * Note: The class loader typically has monitors acquired during invocation of this method.  It is 
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 * @since 3.0
	 */
	public Plugin() {
	}
	/**
	 * Creates a new plug-in runtime object associated with the given bundle context.
	 * The resultant instance is not managed by the runtime and
	 * so should be remembered by the client (typically using a Singleton pattern).
	 * This method must never be called for plug-ins operating in compatibility mode.
	 * <p>
	 * Plug-in runtime classes are <code>BundleActivators</code> and so must
	 * have an default constructor.
	 * Clients may call this method if they are not using this class as their bundle activator.
	 * </p><p>
	 * Note: The class loader typically has monitors acquired during invocation of this method.  It is 
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 * @param context the bundle context
	 * @since 3.0
	 */
	public Plugin(BundleContext context) {
		this.context = context;
	}
	/**
	 * Creates a new plug-in runtime object for the given plug-in descriptor.
	 * <p>
	 * Instances of plug-in runtime classes are automatically created 
	 * by the platform in the course of plug-in activation.
	 * <b>Clients must never explicitly call this method.</b>
	 * </p>
	 * <p>
	 * Note: The class loader typically has monitors acquired during invocation of this method.  It is 
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 *
	 * @param descriptor the plug-in descriptor
	 * @see #getDescriptor
	 * @deprecated The new runtime no longer uses <code>IPluginDescriptors</code> or manages the instances
	 * of the plug-in runtime classes.  Legacy plug-ins using the compatibility layer can continue to implement 
	 * this method but new runtime plug-ins need not implement it as it will never be called.  Instead they should 
	 * implement one of @link #Plugin() Plugin() or @link #Plugin(BundleContext) Plugin(BundleContext).
	 */
	public Plugin(IPluginDescriptor descriptor) {
		Assert.isNotNull(descriptor);
		Assert.isTrue(!descriptor.isPluginActivated(), Policy.bind("plugin.deactivatedLoad", this.getClass().getName(), descriptor.getUniqueIdentifier() + " is not activated")); //$NON-NLS-1$ //$NON-NLS-2$
		this.descriptor = descriptor;
		String key = descriptor.getUniqueIdentifier() + "/debug"; //$NON-NLS-1$
		String value = InternalPlatform.getDefault().getOption(key);
		this.debug = value == null ? false : value.equalsIgnoreCase("true"); //$NON-NLS-1$

		// on plugin start, find and start the corresponding bundle.
		bundle = InternalPlatform.getDefault().getBundleContext().getBundle(descriptor.getUniqueIdentifier());
		try {
			if ((bundle.getState() & (Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) == 0)
				bundle.start();
		} catch (BundleException e) {
			// TODO do nothing for now
			e.printStackTrace();
		}
	}
	/**
	 * Returns a URL for the given path.  Returns <code>null</code> if the URL
	 * could not be computed or created.
	 * 
	 * @param file path relative to plug-in installation location 
	 * @return a URL for the given path or <code>null</code>
	 */
	public final URL find(IPath path) {
		return FindSupport.find(bundle, path, null);
	}
	/**
	 * Returns a URL for the given path.  Returns <code>null</code> if the URL
	 * could not be computed or created.
	 * 
	 * @param path file path relative to plug-in installation location
	 * @param override map of override substitution arguments to be used for
	 * any $arg$ path elements. The map keys correspond to the substitution
	 * arguments (eg. "$nl$" or "$os$"). The resulting
	 * values must be of type java.lang.String. If the map is <code>null</code>,
	 * or does not contain the required substitution argument, the default
	 * is used.
	 * @return a URL for the given path or <code>null</code>
	 */
	public final URL find(IPath path, Map override) {
		return FindSupport.find(bundle, path, override);
	}
	/**
	 * Returns the plug-in descriptor for this plug-in runtime object.
	 *
	 * @return the plug-in descriptor for this plug-in runtime object
	 * @deprecated
	 */
	public final IPluginDescriptor getDescriptor() {
		if (descriptor!=null)
			return descriptor;
		IPluginDescriptor descriptor = CompatibilityHelper.getPluginDescriptor(bundle.getGlobalName());
		if (descriptor != null)
			CompatibilityHelper.setPlugin(descriptor, this);
		return descriptor;
	}
	/**
	 * Returns the log for this plug-in.  If no such log exists, one is created.
	 *
	 * @return the log for this plug-in
	 */
	public final ILog getLog() {
		return InternalPlatform.getDefault().getLog(bundle);
	}
	/**
	 * Returns the location in the local file system of the 
	 * plug-in state area for this plug-in.
	 * If the plug-in state area did not exist prior to this call,
	 * it is created.
	 * <p>
	 * The plug-in state area is a file directory within the
	 * platform's metadata area where a plug-in is free to create files.
	 * The content and structure of this area is defined by the plug-in,
	 * and the particular plug-in is solely responsible for any files
	 * it puts there. It is recommended for plug-in preference settings and 
	 * other configuration parameters.
	 * </p>
	 *
	 * @return a local file system path
	 */
	public final IPath getStateLocation() {
		return InternalPlatform.getDefault().getStateLocation(bundle,true);
	}

	/**
	 * Returns the preference store for this plug-in.
	 * <p>
	 * Note that if an error occurs reading the preference store from disk, an empty 
	 * preference store is quietly created, initialized with defaults, and returned.
	 * </p>
	 * <p>
	 * Calling this method may cause the preference store to be created and
	 * initialized. Subclasses which reimplement the 
	 * <code>initializeDefaultPluginPreferences</code> method have this opportunity
	 * to initialize preference default values, just prior to processing override
	 * default values imposed externally to this plug-in (specified for the product,
	 * or at platform start up).
	 * </p>
	 * <p>
	 * After settings in the preference store are changed (for example, with 
	 * <code>Preferences.setValue</code> or <code>setToDefault</code>),
	 * <code>savePluginPreferences</code> should be called to store the changed
	 * values back to disk. Otherwise the changes will be lost on plug-in
	 * shutdown.
	 * </p>
	 *
	 * @return the preference store
	 * @see #savePluginPreferences
	 * @see Preferences#setValue
	 * @see Preferences#setToDefault
	 * @since 2.0
	 * @deprecated TODO 
	 */
	public final Preferences getPluginPreferences() {
		if (preferences != null) {
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("Plugin preferences already loaded for " + bundle.getGlobalName()); //$NON-NLS-1$
			}
			// N.B. preferences instance field set means already created
			// and initialized (or in process of being initialized)
			return preferences;
		}

		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Loading preferences for plugin " + bundle.getGlobalName()); //$NON-NLS-1$
		}
		// lazily create preference store
		// important: set preferences instance field to prevent re-entry
		preferences = new Preferences();
		// load settings into preference store 
		loadPluginPreferences();

		// 1. fill in defaults supplied by this plug-in
		initializeDefaultPluginPreferences();
		// 2. override with defaults stored with plug-in
		applyInternalPluginDefaultOverrides();
		// 3. override with defaults from primary feature or command line
		applyExternalPluginDefaultOverrides();
		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Completed loading preferences for plugin " + bundle.getGlobalName()); //$NON-NLS-1$
		}
		return preferences;
	}

	/**
	 * Loads preferences settings for this plug-in from the plug-in preferences
	 * file in the plug-in's state area. This plug-in must have a preference store
	 * object.
	 * 
	 * @see Preferences#load
	 * @since 2.0
	 * @deprecated Use @link BundleHelper#loadPluginPreferences() BundleHelper#loadPluginPreferences() 
	 */
	private void loadPluginPreferences() {
		// the preferences file is located in the plug-in's state area at a well-known name
		// don't need to create the directory if there are no preferences to load
		File prefFile = InternalPlatform.getDefault().getMetaArea().getPreferenceLocation(bundle, false).toFile();
		if (!prefFile.exists()) {
			// no preference file - that's fine
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("Plugin preference file " + prefFile + " not found."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return;
		}

		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Loading preferences from " + prefFile); //$NON-NLS-1$
		}
		// load preferences from file
		SafeFileInputStream in = null;
		try {
			in = new SafeFileInputStream(prefFile);
			preferences.load(in);
		} catch (IOException e) {
			// problems loading preference store - quietly ignore
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("IOException encountered loading preference file " + prefFile); //$NON-NLS-1$
				e.printStackTrace();
			}
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// ignore problems with close
					if (InternalPlatform.DEBUG_PREFERENCES) {
						System.out.println("IOException encountered closing preference file " + prefFile); //$NON-NLS-1$
						e.printStackTrace();
					}
				}
			}
		}
		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Preferences now set as follows:"); //$NON-NLS-1$
			String[] prefNames = preferences.propertyNames();
			for (int i = 0; i < prefNames.length; i++) {
				String value = preferences.getString(prefNames[i]);
				System.out.println("\t" + prefNames[i] + " = " + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
			prefNames = preferences.defaultPropertyNames();
			for (int i = 0; i < prefNames.length; i++) {
				String value = preferences.getDefaultString(prefNames[i]);
				System.out.println("\tDefault values: " + prefNames[i] + " = " + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	/**
	 * Saves preferences settings for this plug-in. Does nothing if the preference
	 * store does not need saving.
	 * <p>
	 * Plug-in preferences are <b>not</b> saved automatically on plug-in shutdown.
	 * </p>
	 * 
	 * @see Preferences#store
	 * @see Preferences#needsSaving
	 * @since 2.0
	 * @deprecated TODO
	 */
	public final void savePluginPreferences() {
		if (preferences == null || !preferences.needsSaving()) {
			// nothing to save
			return;
		}

		// preferences need to be saved
		// the preferences file is located in the plug-in's state area
		// at a well-known name (pref_store.ini)
		File prefFile = InternalPlatform.getDefault().getMetaArea().getPreferenceLocation(bundle, true).toFile();
		if (preferences.propertyNames().length == 0) {
			// there are no preference settings
			// rather than write an empty file, just delete any existing file
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("Removing saved preferences from " + prefFile); //$NON-NLS-1$
			}
			if (prefFile.exists()) {
				prefFile.delete();
				// don't worry if delete unsuccessful
			}
			return;
		}

		// write file, overwriting an existing one
		OutputStream out = null;
		try {
			// do it as carefully as we know how so that we don't lose/mangle
			// the setting in times of stress
			out = new SafeFileOutputStream(prefFile);
			preferences.store(out, null);
		} catch (IOException e) {
			// problems saving preference store - quietly ignore
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("IOException writing to preference file " + prefFile); //$NON-NLS-1$
				e.printStackTrace();
			}
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					// ignore problems with close
					if (InternalPlatform.DEBUG_PREFERENCES) {
						System.out.println("IOException closing preference file " + prefFile); //$NON-NLS-1$
						e.printStackTrace();
					}
				}
			}
		}
	}
	/**
	 * Initializes the default preferences settings for this plug-in.
	 * <p>
	 * This method is called sometime after the preference store for this
	 * plug-in is created. Default values are never stored in preference
	 * stores; they must be filled in each time. This method provides the
	 * opportunity to initialize the default values.
	 * </p>
	 * <p>
	 * The default implementation of this method does nothing. A subclass that needs
	 * to set default values for its preferences must reimplement this method.
	 * Default values set at a later point will override any default override
	 * settings supplied from outside the plug-in (product configuration or
	 * platform start up).
	 * </p>
	 * 
	 * @since 2.0
	 * @deprecated Use @link BundleHelper#initializeDefaultPluginPreferences() BundleHelper#initializeDefaultPluginPreferences()
	 */
	protected void initializeDefaultPluginPreferences() {
		// default implementation of this method - spec'd to do nothing
	}

	/**
	 * Applies external overrides to default preferences for this plug-in. By the
	 * time this method is called, the default settings for the plug-in itself will
	 * have already have been filled in.
	 * 
	 * @since 2.0
	 */
	private void applyExternalPluginDefaultOverrides() {
		// 1. InternalPlatform is central authority for platform configuration questions
		InternalPlatform.getDefault().applyPrimaryFeaturePluginDefaultOverrides(bundle.getGlobalName(), preferences);
		// 2. command line overrides take precedence over feature-specified overrides
		InternalPlatform.getDefault().applyCommandLinePluginDefaultOverrides(bundle.getGlobalName(), preferences);
	}

	/**
	 * Applies overrides to the default preferences for this plug-in. Looks
	 * for a file in the (read-only) plug-in directory. The default settings will
	 * have already have been applied.
	 * 
	 * @since 2.0
	 */
	private void applyInternalPluginDefaultOverrides() {
		// use URLs so we can find the file in fragments too
		URL baseURL = FindSupport.find(bundle, new Path(PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME));

		if (baseURL == null) {
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("Plugin preference file " + PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME + " not found."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			baseURL = FindSupport.find(bundle, new Path(PREFERENCES_DEFAULT_OVERRIDE_FILE_NAME_BACKUP));
			if (baseURL == null)
				// No backup file has ben found, but we do not log 
				return;
		}

		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Loading preferences from " + baseURL); //$NON-NLS-1$
		}
		Properties overrides = new Properties();
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(baseURL.openStream());
			overrides.load(in);
		} catch (IOException e) {
			// cannot read ini file - fail silently
			if (InternalPlatform.DEBUG_PREFERENCES) {
				System.out.println("IOException encountered loading preference file " + //$NON-NLS-1$
				baseURL);
				e.printStackTrace();
			}
			return;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				// ignore problems closing file
				if (InternalPlatform.DEBUG_PREFERENCES) {
					System.out.println("IOException encountered closing preference file " + //$NON-NLS-1$
					baseURL);
					e.printStackTrace();
				}
			}
		}

		// Now get the translation file for these preferences (if one
		// exists).
		Properties props = null;
		if (!overrides.isEmpty()) {
			props = InternalPlatform.getDefault().getPreferenceTranslator(bundle.getGlobalName(), PREFERENCES_DEFAULT_OVERRIDE_BASE_NAME);
		}

		for (Iterator it = overrides.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
			value = InternalPlatform.getDefault().translatePreference(value, props);
			preferences.setDefault(key, value);
		}
		if (InternalPlatform.DEBUG_PREFERENCES) {
			System.out.println("Preferences now set as follows:"); //$NON-NLS-1$
			String[] prefNames = preferences.propertyNames();
			for (int i = 0; i < prefNames.length; i++) {
				String value = preferences.getString(prefNames[i]);
				System.out.println("\t" + prefNames[i] + " = " + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
			prefNames = preferences.defaultPropertyNames();
			for (int i = 0; i < prefNames.length; i++) {
				String value = preferences.getDefaultString(prefNames[i]);
				System.out.println("\tDefault values: " + prefNames[i] + " = " + value); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	/**
	 * Returns whether this plug-in is in debug mode.
	 * By default plug-ins are not in debug mode.  A plug-in can put itself
	 * into debug mode or the user can set an execution option to do so.
	 *
	 * @return whether this plug-in is in debug mode
	 */
	public boolean isDebugging() {
		return debug;
	}
	/**
	 * Returns an input stream for the specified file. The file path
	 * must be specified relative this the plug-in's installation location.
	 *
	 * @param file path relative to plug-in installation location
	 * @return an input stream
	 * @see #openStream(IPath,boolean)
	 */
	public final InputStream openStream(IPath file) throws IOException {
		return FindSupport.openStream(bundle, file, false);
	}
	/**
	 * Returns an input stream for the specified file. The file path
	 * must be specified relative to this plug-in's installation location.
	 * Optionally, the platform searches for the correct localized version
	 * of the specified file using the users current locale, and Java
	 * naming convention for localized resource files (locale suffix appended 
	 * to the specified file extension).
	 * <p>
	 * The caller must close the returned stream when done.
	 * </p>
	 *
	 * @param file path relative to plug-in installation location
	 * @param localized <code>true</code> for the localized version
	 *   of the file, and <code>false</code> for the file exactly
	 *   as specified
	 * @return an input stream
	 */
	public final InputStream openStream(IPath file, boolean localized) throws IOException {
		return FindSupport.openStream(bundle, file, localized);
	}
	/**
	 * Sets whether this plug-in is in debug mode.
	 * By default plug-ins are not in debug mode.  A plug-in can put itself
	 * into debug mode or the user can set a debug option to do so.
	 *
	 * @param value whether or not this plugi-in is in debug mode
	 */
	public void setDebugging(boolean value) {
		debug = value;
	}
	/**
	 * Shuts down this plug-in and discards all plug-in state.
	 * <p>
	 * This method should be re-implemented in subclasses that need to do something
	 * when the plug-in is shut down.  Implementors should call the inherited method
	 * to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * Plug-in shutdown code should be robust. In particular, this method
	 * should always make an effort to shut down the plug-in. Furthermore,
	 * the code should not assume that the plug-in was started successfully,
	 * as this method will be invoked in the event of a failure during startup.
	 * </p>
	 * <p>
	 * Note 1: If a plug-in has been started, this method will be automatically
	 * invoked by the platform when the platform is shut down.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple termination
	 * of the plug-in environment. The platform may terminate invocations
	 * that do not complete in a timely fashion.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @exception CoreException if this method fails to shut down
	 *   this plug-in
	 * @deprecated Legacy plug-ins using the compatibility 
	 * layer will continue to be stopped using this method however the new runtime 
	 * no longer uses it.  As such, new runtime plug-ins need not implement it rather they should 
	 * implement @link #stop() stop().
	 */
	public void shutdown() throws CoreException {
		Method m;
		try {
			m = descriptor.getClass().getMethod("doPluginDeactivation", new Class[0]);
			m.invoke(descriptor, null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Starts up this plug-in.
	 * <p>
	 * This method should be overridden in subclasses that need to do something
	 * when this plug-in is started.  Implementors should call the inherited method
	 * to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * If this method throws an exception, it is taken as an indication that
	 * plug-in initialization has failed; as a result, the plug-in will not
	 * be activated; moreover, the plug-in will be marked as disabled and 
	 * ineligible for activation for the duration.
	 * </p>
	 * <p>
	 * Plug-in startup code should be robust. In the event of a startup failure,
	 * the plug-in's <code>shutdown</code> method will be invoked automatically,
	 * in an attempt to close open files, etc.
	 * </p>
	 * <p>
	 * Note 1: This method is automatically invoked by the platform 
	 * the first time any code in the plug-in is executed.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple initialization 
	 * of the plug-in environment. The platform may terminate initializers 
	 * that do not complete in a timely fashion.
	 * </p>
	 * <p>
	 * Note 3: The class loader typically has monitors acquired during invocation of this method.  It is 
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @exception CoreException if this plug-in did not start up properly
	 * @deprecated Legacy plug-ins using the compatibility 
	 * layer will continue to be started using this method however the new runtime 
	 * no longer uses it.  As such, new runtime plug-ins need not implement it rather they should 
	 * implement @link #start() start().
	 */
	public void startup() throws CoreException {
	}
	/**
	 * Returns a string representation of the plug-in, suitable 
	 * for debugging purposes only.
	 */
	public String toString() {
		return descriptor.toString();
	}
	
	/**
	 * Starts up this plug-in.
	 * <p>
	 * This method should be overridden in subclasses that need to do something
	 * when this plug-in is started.  Implementors should call the inherited method
	 * at the first possible point to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * If this method throws an exception, it is taken as an indication that
	 * plug-in initialization has failed; as a result, the plug-in will not
	 * be activated; moreover, the plug-in will be marked as disabled and 
	 * ineligible for activation for the duration.
	 * </p>
	 * <p>
	 * Plug-in startup code should be robust. In the event of a startup failure,
	 * the plug-in's <code>shutdown</code> method will be invoked automatically,
	 * in an attempt to close open files, etc.
	 * </p>
	 * <p>
	 * Note 1: This method is automatically invoked by the platform 
	 * the first time any code in the plug-in is executed.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple initialization 
	 * of the plug-in environment. The platform may terminate initializers 
	 * that do not complete in a timely fashion.
	 * </p>
	 * <p>
	 * Note 3: The class loader typically has monitors acquired during invocation of this method.  It is 
	 * strongly recommended that this method avoid synchronized blocks or other thread locking mechanisms,
	 * as this would lead to deadlock vulnerability.
	 * </p>
	 * <p>
	 * Note 4: This method is not called on plug-ins operating in compatibility mode.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @exception BundleException if this plug-in did not start up properly
	 * @since 3.0
	 */
	public void start(BundleContext context) throws BundleException{
		this.context = context;
		bundle = context.getBundle();
	}

	/**
	 * Stops this plug-in.
	 * <p>
	 * This method should be re-implemented in subclasses that need to do something
	 * when the plug-in is shut down.  Implementors should call the inherited method
	 * as late as possible to ensure that any system requirements can be met.
	 * </p>
	 * <p>
	 * Plug-in shutdown code should be robust. In particular, this method
	 * should always make an effort to shut down the plug-in. Furthermore,
	 * the code should not assume that the plug-in was started successfully,
	 * as this method will be invoked in the event of a failure during startup.
	 * </p>
	 * <p>
	 * Note 1: If a plug-in has been started, this method will be automatically
	 * invoked by the platform when the platform is shut down.
	 * </p>
	 * <p>
	 * Note 2: This method is intended to perform simple termination
	 * of the plug-in environment. The platform may terminate invocations
	 * that do not complete in a timely fashion.
	 * </p>
	 * <p>
	 * Note 3: This method is not called on plug-ins operating in compatibility mode.
	 * </p>
	 * <b>Clients must never explicitly call this method.</b>
	 *
	 * @exception BundleException if this method fails to shut down
	 *   this plug-in
	 * @since 3.0
	 */
	public void stop(BundleContext context) throws BundleException {
		context = null;
		bundle = null;
	}
	
	// TODO do we really want to support this?  What are the usecases?  Could the function just
	// be on Platform?
	public EnvironmentInfo getEnvironmentService() {
		ServiceTracker tracker = null;
		try {
			tracker = new ServiceTracker(context, EnvironmentInfo.class.getName(), null);
			tracker.open();
			return (EnvironmentInfo) tracker.getService();
		} finally {
			tracker.close();
		}
	}
	/**
	 * Returns the bundle
	 * 
	 * @return the associated bundle
	 * @since 3.0
	 */
	public Bundle getBundle() {
		return bundle;
	}
}
