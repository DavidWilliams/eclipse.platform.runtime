/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.runtime;

import java.io.*;
import java.net.*;
import java.util.*;
import org.eclipse.core.internal.boot.*;
import org.eclipse.core.internal.jobs.JobManager;
import org.eclipse.core.internal.registry.BundleModel;
import org.eclipse.core.internal.registry.ExtensionRegistry;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.osgi.framework.*;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Bootstrap class for the platform. It is responsible for setting up the
 * platform class loader and passing control to the actual application class
 */
public final class InternalPlatform implements IPlatform {
	private BundleContext context;
	private IExtensionRegistry registry;
	private static IAdapterManager adapterManager;
	private Plugin runtimeInstance; //Keep track of the plugin object for runtime in case the backward compatibility is run.
	
	private static final InternalPlatform singleton = new InternalPlatform();

	private IPath configMetadataLocation;

	static ServiceRegistration platformRegistration;
	static EnvironmentInfo infoService;
	static URLConverter urlConverter;

	// registry index - used to store last modified times for
	// registry caching
	// ASSUMPTION:  Only the plugin registry in 'registry' above will be cached
	private static Map regIndex = null;

	private static ArrayList logListeners = new ArrayList(5);
	private static Map logs = new HashMap(5);
	private static PlatformLogWriter platformLog = null;
	private static DataArea metaArea;
	private static boolean initialized;
	private static Runnable endOfInitializationHandler = null;
	private static IPath location;	//The location as set on the command line - this is just used as a temporary location
	private static String password = "";
	private static String keyringFile;
	private static boolean noData = false;
	private static boolean noDefaultData = false;
	private ServiceTracker debugTracker;
	private DebugOptions options = null;

	// Command line args as seen by the Eclipse runtime. allArgs does NOT
	// include args consumed by the underlying framework (e.g., OSGi)
	private static String[] allArgs = new String[0];
	private static String[] appArgs = new String[0];
	private static String[] frameworkArgs = new String[0];

	// the default workspace directory name
	static final String WORKSPACE = "workspace"; //$NON-NLS-1$	

	private static ILogListener consoleLog = null;

	private static boolean splashDown = false;
	private static String pluginCustomizationFile = null;
	private static URL installLocation = null;

	/**
	 * Whether to write the version.ini file on shutdown.
	 */
	private static boolean writeVersion = true;

	private ArrayList groupProviders = new ArrayList(3);
	private IProduct product;

	/**
	 * Name of the plug-in customization file (value "plugin_customization.ini")
	 * located in the root of the primary feature plug-in and it's 
	 * companion nl-specific file with externalized strings (value
	 * "plugin_customization.properties").  The companion file can
	 * be contained in any nl-specific subdirectories of the primary
	 * feature or any fragment of this feature.
	 */
	private static final String PLUGIN_CUSTOMIZATION_BASE_NAME = "plugin_customization"; //$NON-NLS-1$
	private static final String PLUGIN_CUSTOMIZATION_FILE_NAME = PLUGIN_CUSTOMIZATION_BASE_NAME + ".ini"; //$NON-NLS-1$

	// execution options
	private static final String OPTION_DEBUG = PI_RUNTIME + "/debug"; //$NON-NLS-1$
	private static final String OPTION_DEBUG_SYSTEM_CONTEXT = PI_RUNTIME + "/debug/context"; //$NON-NLS-1$
	private static final String OPTION_DEBUG_SHUTDOWN = PI_RUNTIME + "/timing/shutdown"; //$NON-NLS-1$
	private static final String OPTION_DEBUG_REGISTRY = PI_RUNTIME + "/registry/debug"; //$NON-NLS-1$
	private static final String OPTION_REGISTRY_CACHE_TIMING = IPlatform.PI_RUNTIME + "/registry/cache/timing"; //$NON-NLS-1$
	private static final String OPTION_DEBUG_REGISTRY_DUMP = PI_RUNTIME + "/registry/debug/dump"; //$NON-NLS-1$
	private static final String OPTION_DEBUG_PREFERENCES = PI_RUNTIME + "/preferences/debug"; //$NON-NLS-1$

	// command line options
	private static final String NO_DEFAULT_DATA = "-noDefaultData"; //$NON-NLS-1$
	private static final String NO_DATA = "-noData"; //$NON-NLS-1$
	private static final String PRODUCT = "-product"; //$NON-NLS-1$	
	private static final String APPLICATION = "-application"; //$NON-NLS-1$	
	private static final String DATA = "-data"; //$NON-NLS-1$	
	private static final String LOG = "-consolelog"; //$NON-NLS-1$
	private static final String KEYRING = "-keyring"; //$NON-NLS-1$
	protected static final String PASSWORD = "-password"; //$NON-NLS-1$
	private static final String NOREGISTRYCACHE = "-noregistrycache"; //$NON-NLS-1$	
	private static final String NO_LAZY_REGISTRY_CACHE_LOADING = "-noLazyRegistryCacheLoading"; //$NON-NLS-1$		
	private static final String PLUGIN_CUSTOMIZATION = "-plugincustomization"; //$NON-NLS-1$
	private static final String NO_PACKAGE_PREFIXES = "-noPackagePrefixes"; //$NON-NLS-1$
	private static final String NO_VERSION_CHECK = "-noversioncheck"; //$NON-NLS-1$
	private static final String CLASSLOADER_PROPERTIES = "-classloaderProperties"; //$NON-NLS-1$	

	// debug support:  set in loadOptions()
	public static boolean DEBUG = false;
	public static boolean DEBUG_CONTEXT = false;
	public static boolean DEBUG_REGISTRY = false;
	public static boolean DEBUG_STARTUP = false;
	public static boolean DEBUG_SHUTDOWN = false;
	public static String DEBUG_REGISTRY_DUMP = null;
	public static boolean DEBUG_PREFERENCES = false;

	private static final String KEY_PREFIX = "%"; //$NON-NLS-1$
	private static final String KEY_DOUBLE_PREFIX = "%%"; //$NON-NLS-1$

	private static final String PLUGIN_PATH = ".plugin-path"; //$NON-NLS-1$
	private static final String METADATA_VERSION_KEY = "org.eclipse.core.runtime"; //$NON-NLS-1$
	private static final int METADATA_VERSION_VALUE = 1;

	// Eclipse System Properties
	public static final String PROP_PRODUCT = "eclipse.product"; //$NON-NLS-1$
	public static final String PROP_APPLICATION = "eclipse.application"; //$NON-NLS-1$
	public static final String PROP_CONSOLE_LOG = "eclipse.consoleLog"; //$NON-NLS-1$
	public static final String PROP_NO_VERSION_CHECK = "eclipse.noVersionCheck"; //$NON-NLS-1$
	public static final String PROP_NO_DATA = "eclipse.noData"; //$NON-NLS-1$
	public static final String PROP_NO_DEFAULT_DATA = "eclipse.noDefaultData"; //$NON-NLS-1$
	public static final String PROP_NO_REGISTRY_CACHE = 	"eclipse.noRegistryCache"; //$NON-NLS-1$
	public static final String PROP_NO_LAZY_CACHE_LOADING = 	"eclipse.noLazyRegistryCacheLoading"; //$NON-NLS-1$
	public static final String PROP_EXITCODE = "eclipse.exitcode"; //$NON-NLS-1$

	// OSGI system properties.  Copied from EclipseStarter
	public static final String PROP_INSTALL_LOCATION = "osgi.installLocation"; //$NON-NLS-1$
	public static final String PROP_CONFIG_AREA = "osgi.configuration.area"; //$NON-NLS-1$
	public static final String PROP_INSTANCE_AREA = "osgi.instance.area"; //$NON-NLS-1$
	public static final String PROP_USER_AREA = "osgi.user.area"; //$NON-NLS-1$
	public static final String PROP_MANIFEST_CACHE = "osgi.manifest.cache"; //$NON-NLS-1$
	public static final String PROP_DEBUG = "osgi.debug"; //$NON-NLS-1$
	public static final String PROP_DEV = "osgi.dev"; //$NON-NLS-1$
	public static final String PROP_CONSOLE = "osgi.console"; //$NON-NLS-1$
	public static final String PROP_CONSOLE_CLASS= "osgi.consoleClass"; //$NON-NLS-1$
	public static final String PROP_OS = "osgi.os"; //$NON-NLS-1$
	public static final String PROP_WS = "osgi.ws"; //$NON-NLS-1$
	public static final String PROP_NL = "osgi.nl"; //$NON-NLS-1$
	public static final String PROP_ARCH = "osgi.arch"; //$NON-NLS-1$
	public static final String PROP_ADAPTOR = "osgi.adaptor"; //$NON-NLS-1$
	public static final String PROP_SYSPATH= "osgi.syspath"; //$NON-NLS-1$

	/**
	 * Private constructor to block instance creation.
	 */
	private InternalPlatform() {
		super();
	}

	public static InternalPlatform getDefault() {
		return singleton;
	}


	/**
	 * @see Platform#addLogListener
	 */
	public void addLogListener(ILogListener listener) {
		assertInitialized();
		synchronized (logListeners) {
			// replace if already exists (Set behaviour but we use an array
			// since we want to retain order)
			logListeners.remove(listener);
			logListeners.add(listener);
		}
	}
	/**
	 * @see Platform
	 */
	public URL asLocalURL(URL url) throws IOException {
		//TODO: this is bogus - only to satisfy clients that want to resolve bundle2 URLs
		if (url.getProtocol().equals("bundle2")) { //$NON-NLS-1$
			String bundleName = url.getHost();
			Bundle bundle = this.context.getBundle(bundleName.substring(0, bundleName.indexOf('_')));

			if (bundle != null) {
				URL localURL = bundle.getEntry(url.getPath());
				if (localURL != null)
					return localURL;
			}
			return url;
		}

		URL result = url;

		// If this is a platform URL get the local URL from the PlatformURLConnection
		if (result.getProtocol().equals(PlatformURLHandler.PROTOCOL)){
			result = asActualURL(url);
		}

		// If the result is a bundleentry or bundleresouce URL then 
		// convert it to a file URL.  This will end up extracting the 
		// bundle entry to cache if the bundle is packaged as a jar.
		if (result.getProtocol().startsWith(PlatformURLHandler.BUNDLE)) {
			URLConverter urlConverter = getURLConverter();
			if (urlConverter == null) {
				throw new IOException("url.noaccess");
			}
			result = urlConverter.convertToFileURL(result);
		}

		return result;
	}

	private URL asActualURL(URL url) throws IOException {
		if (!url.getProtocol().equals(PlatformURLHandler.PROTOCOL))
			return url;
		URLConnection connection = url.openConnection();
		if (connection instanceof PlatformURLConnection)
			return ((PlatformURLConnection) connection).getResolvedURL();
		else
			return url;
	}

	private void assertInitialized() {
		//avoid the Policy.bind if assertion is true
		if (!initialized)
			Assert.isTrue(false, Policy.bind("meta.appNotInit")); //$NON-NLS-1$
	}
	/**
	 * @see Platform
	 */
	public void endSplash() {
		if (DEBUG) {
			String startString = System.getProperty("eclipse.debug.startupTime"); //$NON-NLS-1$
			if (startString != null)
				try {
					long start = Long.parseLong(startString);
					long end = System.currentTimeMillis();
					System.out.println("Startup complete: " + (end - start) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
				} catch (NumberFormatException e) {
					//this is just debugging code -- ok to swallow exception
				}
		}
		if (splashDown)
			return;
		splashDown = true;
		run(endOfInitializationHandler);
	}
	
	/**
	 * @see Platform#getAdapterManager
	 */
	public IAdapterManager getAdapterManager() {
		assertInitialized();
		if (adapterManager == null)
			adapterManager = new AdapterManager();
		return adapterManager;
	}

	public boolean getBooleanOption(String option, boolean defaultValue) {
		String value = getOption(option);
		return (value != null && value.equalsIgnoreCase("true")) || defaultValue; //$NON-NLS-1$
	}

	public int getIntegerOption(String option, int defaultValue) {
		String value = getOption(option);
		if (value == null)
			return defaultValue;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public String[] getAllArgs() {
		return allArgs;
	}

	public String[] getAppArgs() {
		return appArgs;
	}

	public String[] getFrameworkArgs() {
		return frameworkArgs;
	}

	/**
	 * @see Platform
	 */
	public String getOption(String option) {
		if (options != null)
			return options.getOption(option);
		return null;
	}

	public IJobManager getJobManager() {
		return JobManager.getInstance();
	}

	public IPath getLogFileLocation() {
		return getMetaArea().getLogLocation();
	}

	/**
	 * @see Platform#getLocation
	 */
	public IPath getLocation() throws IllegalStateException {
		return getInstanceLocation();
	}


	/**
	 * Returns a log for the given plugin or <code>null</code> if none exists.
	 */
	public ILog getLog(Bundle bundle) {
		ILog result = (ILog) logs.get(bundle);
		if (result != null)
			return result;
		result = new Log(bundle);
		logs.put(bundle, result);
		return result;
	}
	/**
	 * Returns the object which defines the location and organization
	 * of the platform's meta area.
	 */
	public DataArea getMetaArea() {
		if (metaArea != null) 
			return metaArea;
		
		if (noData) {
			metaArea = new NoDataArea();
			return metaArea;
		}
		
		if (noDefaultData) {
			metaArea = new NoDefaultDataArea();
		} else {
			metaArea = new DataArea();
			metaArea.setInstanceDataLocation(location);
			try {
				metaArea.createLockFile();
			} catch (CoreException e) {
				throw new IllegalStateException(e.getStatus().getMessage());
			}
		}
		metaArea.setKeyringFile(keyringFile);
		metaArea.setPasswork(password);			
		return metaArea;
	}
	private  void handleException(ISafeRunnable code, Throwable e) {
		if (!(e instanceof OperationCanceledException)) {
			String pluginId = PI_RUNTIME;
			String message = Policy.bind("meta.pluginProblems", pluginId); //$NON-NLS-1$
			IStatus status;
			if (e instanceof CoreException) {
				status = new MultiStatus(pluginId, IPlatform.PLUGIN_ERROR, message, e);
				((MultiStatus)status).merge(((CoreException)e).getStatus());
			} else {
				status = new Status(IStatus.ERROR, pluginId, IPlatform.PLUGIN_ERROR, message, e);
			}
			log(status); //$NON-NLS-1$
		}
		code.handleException(e);
	}

	public IExtensionRegistry getRegistry() {
		return registry;
	}
	/**
	 * Check whether the workspace metadata version matches the expected version. 
	 * If not, prompt the user for whether to proceed, or exit with no changes.
	 * Side effects: 
	 * <ul>
	 * <li>remember whether to write the metadata version on exit</li>
	 * <li>bring down the splash screen if exiting</li>
	 * </ul> 
	 * 
	 * @return <code>true</code> to proceed, <code>false</code> to exit with no changes
	 */
	public boolean loaderCheckVersion() {
		// if not doing the version check, then proceed with no check or prompt
		boolean noVersionCheck = "true".equals(System.getProperty("eclipse.noVersionCheck")); //$NON-NLS-1$
		boolean proceed = noVersionCheck || checkVersionPrompt();
		// remember whether to write the version on exit;
		// don't write it if the user cancelled
		writeVersion = proceed;
		// bring down the splash screen if the user cancelled,
		// since the application won't
		if (!proceed)
			endSplash();
		return proceed;
	}

	/**
	 * Internal method for finding and returning a runnable instance of the 
	 * given class as defined in the specified plug-in.
	 * The returned object is initialized with the supplied arguments.
	 * <p>
	 * This method is used by the platform boot loader; is must
	 * not be called directly by client code.
	 * </p>
	 * @see BootLoader
	 */
	public IPlatformRunnable loaderGetRunnable(String applicationName) {
		assertInitialized();
		IExtension extension = getRegistry().getExtension(PI_RUNTIME, PT_APPLICATIONS, applicationName);
		if (extension == null)
			return null;
		IConfigurationElement[] configs = extension.getConfigurationElements();
		if (configs.length == 0)
			return null;
		try {
			IConfigurationElement config = configs[0];
			return (IPlatformRunnable) config.createExecutableExtension("run"); //$NON-NLS-1$
		} catch (CoreException e) {
			getLog(context.getBundle()).log(e.getStatus());
			return null;
		} catch (Throwable t) {
			t.printStackTrace(System.err);
			return null;
		}
	}

	/**
	 * Internal method for starting up the platform.  The platform is started at the 
	 * given location.  The plug-ins found at the supplied 
	 * collection of plug-in locations are loaded into the newly started platform.
	 * <p>
	 * This method is used by the platform boot loader; is must
	 * not be called directly by client code.
	 * </p>
	 * @param pluginPath the list of places to look for plug-in specifications.  This may
	 *		identify individual plug-in files or directories containing directories which contain
	 *		plug-in files.
	 * @param location the local filesystem location at which the newly started platform
	 *		should be started.  If the location does not contain the saved state of a platform,
	 *		the appropriate structures are created on disk (if required).
	 * @param bootOptions the debug options loaded by the boot loader.  If the argument
	 *		is <code>null</code> then debugging enablement was not requested by the
	 *		person starting the platform.
	 * @see BootLoader
	 */

	public void start(BundleContext context) throws Exception {
		this.context = context;
		// TODO figure out how to do the splash.  This really should be something that is in the OSGi implementation
		endOfInitializationHandler = getSplashHandler();
		processCommandLine(infoService.getAllArgs());
		processSystemProperties();
		debugTracker = new ServiceTracker(context, DebugOptions.class.getName(), null);
		debugTracker.open();
		options = (DebugOptions) debugTracker.getService(); //TODO This is not good, but is avoids problems
		initializeDebugFlags();
		initialized = true;
		platformLog = new PlatformLogWriter(getMetaArea().getLogLocation().toFile());
		addLogListener(platformLog);
		if ("true".equals(System.getProperty("eclipse.consoleLog"))) {
			consoleLog = new PlatformLogWriter(System.out);
			addLogListener(consoleLog);
		}
		platformRegistration = context.registerService(IPlatform.class.getName(), this, null);
	}
	/**
	 * 
	 */
	private void processSystemProperties() {
		noData = "true".equalsIgnoreCase(System.getProperties().getProperty(PROP_NO_DATA));
		noDefaultData = "true".equalsIgnoreCase(System.getProperties().getProperty(PROP_NO_DEFAULT_DATA));
	}

	private Runnable getSplashHandler() {
		ServiceReference[] ref;
		try {
			ref = context.getServiceReferences(Runnable.class.getName(), null);
		} catch (InvalidSyntaxException e) {
			return null;
		}
		// assumes the endInitializationHandler is available as a service
		// see EclipseStarter.publishSplashScreen
		for (int i = 0; i < ref.length; i++) {
			String name = (String) ref[i].getProperty("name"); //$NON-NLS-1$
			if (name != null && name.equals("splashscreen")) { //$NON-NLS-1$
				Runnable result = (Runnable) context.getService(ref[i]);
				context.ungetService(ref[i]);
				return result;
			}
		}
		return null;
	}

	/**
	 * Check whether the workspace metadata version matches the expected version. 
	 * If not, prompt the user for whether to proceed, or exit with no changes.
	 * Side effects: none
	 * 
	 * @return <code>true</code> to proceed, <code>false</code> to exit with no changes
	 */
	private boolean checkVersionPrompt() {
		if (checkVersionNoPrompt())
			return true;

		// run the version check ui class to prompt the user
		String appId = "org.eclipse.ui.versioncheck.prompt"; //$NON-NLS-1$
		IPlatformRunnable runnable = loaderGetRunnable(appId);
		// If there is no UI to confirm the metadata version difference, then just proceed.		
		if (runnable == null)
			return true;
		try {
			Object result = runnable.run(null);
			return Boolean.TRUE.equals(result);
		} catch (Exception e) {
			// Fail silently since we don't have a UI, but don't proceed if we can't prompt the user.
			log(new Status(IStatus.ERROR, PI_RUNTIME, 1, Policy.bind("meta.versionCheckRun", appId), null)); //$NON-NLS-1$
			return false;
		}
	}

	//TODO: what else must be done during the platform shutdown? See #loaderShutdown
	public void stop(BundleContext bundleContext) {
		assertInitialized();
		//shutdown all running jobs
		JobManager.shutdown();
		debugTracker.close();
		if (writeVersion)
			writeVersion();
		if (platformLog != null)
			platformLog.shutdown();
		initialized = false;
	}

	/**
	 * Return whether the workspace metadata version matches the expected version. 
	 * 
	 * @return <code>true</code> if they match, <code>false</code> if not
	 */
	private boolean checkVersionNoPrompt() {
		File pluginsDir = getMetaArea().getMetadataLocation().append(DataArea.F_PLUGIN_DATA).toFile();
		if (!pluginsDir.exists())
			return true;

		int version = -1;
		File versionFile = getMetaArea().getVersionPath().toFile();
		if (versionFile.exists()) {
			try {
				// Although the version file is not spec'ed to be a Java properties file,
				// it happens to follow the same format currently, so using Properties
				// to read it is convenient.
				Properties props = new Properties();
				FileInputStream is = new FileInputStream(versionFile);
				try {
					props.load(is);
				} finally {
					try {
						is.close();
					} finally {
						// ignore
					}
				}
				String prop = props.getProperty(METADATA_VERSION_KEY);
				// let any NumberFormatException be caught below
				if (prop != null)
					version = Integer.parseInt(prop);
			} catch (Exception e) {
				// Fail silently. Not a catastrophe if we can't read the version file. We don't
				// want to fail execution.
				log(new Status(IStatus.ERROR, PI_RUNTIME, 1, Policy.bind("meta.checkVersion", versionFile.toString()), e)); //$NON-NLS-1$
			}
		}
		return version == METADATA_VERSION_VALUE;
	}

	/** 
	 * Write out the version of the metadata into a known file. Overwrite
	 * any existing file contents.
	 */
	private void writeVersion() {
		File versionFile = getMetaArea().getVersionPath().toFile();
		try {
			OutputStream output = new BufferedOutputStream(new FileOutputStream(versionFile));
			try {
				String versionLine = METADATA_VERSION_KEY + "=" + METADATA_VERSION_VALUE; //$NON-NLS-1$
				output.write(versionLine.getBytes("UTF-8")); //$NON-NLS-1$
			} finally {
				output.close();
			}
		} catch (Exception e) {
			// Fail silently. Not a catastrophe if we can't write the version file. We don't
			// want to fail execution.
			log(new Status(IStatus.ERROR, IPlatform.PI_RUNTIME, 1, Policy.bind("meta.writeVersion", versionFile.toString()), e)); //$NON-NLS-1$
		}
	}
	/*
	 * Finds and loads the options file 
	 */
	void initializeDebugFlags() {
		// load runtime options
		DEBUG = getBooleanOption(OPTION_DEBUG, false);
		if (DEBUG) {
			DEBUG_CONTEXT = getBooleanOption(OPTION_DEBUG_SYSTEM_CONTEXT, false);
			DEBUG_SHUTDOWN = getBooleanOption(OPTION_DEBUG_SHUTDOWN, false);
			DEBUG_REGISTRY = getBooleanOption(OPTION_DEBUG_REGISTRY, false);
			DEBUG_REGISTRY_DUMP = getOption(OPTION_DEBUG_REGISTRY_DUMP);
			DEBUG_PREFERENCES = getBooleanOption(OPTION_DEBUG_PREFERENCES, false);
		}
	}
	/**
	 * Notifies all listeners of the platform log.  This includes the console log, if 
	 * used, and the platform log file.  All Plugin log messages get funnelled
	 * through here as well.
	 */
	public void log(final IStatus status) {
		assertInitialized();
		// create array to avoid concurrent access
		ILogListener[] listeners;
		synchronized (logListeners) {
			listeners = (ILogListener[]) logListeners.toArray(new ILogListener[logListeners.size()]);
		}
		for (int i = 0; i < listeners.length; i++) {
			final ILogListener listener = listeners[i];
			ISafeRunnable code = new ISafeRunnable() {
				public void run() throws Exception {
					listener.logging(status, PI_RUNTIME);
				}
				public void handleException(Throwable e) {
				}
			};
			run(code);
		}
	}

	private String[] processCommandLine(String[] args) {
		if (args == null)
			return args;
		allArgs = args;
		int[] configArgs = new int[100];
		//need to initialize the first element to something that could not be an index.
		configArgs[0] = -1;
		int configArgIndex = 0;
		for (int i = 0; i < args.length; i++) {
			boolean found = false;
			// check for args without parameters (i.e., a flag arg)

			// look for the log flag
			if (args[i].equalsIgnoreCase(LOG)) {
				System.setProperty(PROP_CONSOLE_LOG, "true"); //$NON-NLS-1$
				found = true;
			}

			// look for the no registry cache flag
			if (args[i].equalsIgnoreCase(NOREGISTRYCACHE)) {
				System.setProperty(PROP_NO_REGISTRY_CACHE, "true"); //$NON-NLS-1$
				found = true;
			}

			// check to see if we should NOT be lazily loading plug-in definitions from the registry cache file.
			// This will be processed below.
			if (args[i].equalsIgnoreCase(NO_LAZY_REGISTRY_CACHE_LOADING)) {
				System.setProperty(PROP_NO_LAZY_CACHE_LOADING, "true"); //$NON-NLS-1$
				found = true;
			}

			// look for the flag to turn off using package prefixes
			if (args[i].equalsIgnoreCase(NO_PACKAGE_PREFIXES)) {
				// ignored
				// PluginClassLoader.usePackagePrefixes = false;
				found = true;
			}
			
			// look for the flag to run without workspace 
			if (args[i].equalsIgnoreCase(NO_DATA)) {
				System.setProperty(PROP_NO_DATA, "true");
				found = true;
			}
			
			// look for the flag to run with a workspace specified programmatically
			if (args[i].equalsIgnoreCase(NO_DEFAULT_DATA)) {
				System.setProperty(PROP_NO_DEFAULT_DATA, "true");
				found = true;
			}
			
			// look for the flag to turn off the workspace metadata version check
			if (args[i].equalsIgnoreCase(NO_VERSION_CHECK)) {
				System.setProperty(PROP_NO_VERSION_CHECK, "true"); //$NON-NLS-1$
				found = true;
			}

			// look for the flag to turn off using package prefixes
			if (args[i].equalsIgnoreCase(NO_PACKAGE_PREFIXES))
				found = true;  				// ignored

			// this option (may have and argument) comes from InternalBootLoader.processCommandLine
			if (args[i].equalsIgnoreCase(CLASSLOADER_PROPERTIES)) 
				found = true;				// ignored

			// done checking for args.  Remember where an arg was found 
			if (found) {
				configArgs[configArgIndex++] = i;
				continue;
			}
			// check for args with parameters
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			// look for the default data location
			if (args[i - 1].equalsIgnoreCase(DATA)) {
				location = new Path(arg);
				found = true;
			}

			// look for the keyring file
			if (args[i - 1].equalsIgnoreCase(KEYRING)) {
				keyringFile = arg;
				found = true;
			}

			// look for the user password.  
			if (args[i - 1].equalsIgnoreCase(PASSWORD)) {
				password = arg;
				found = true;
			}

			// look for the product to run
			if (args[i - 1].equalsIgnoreCase(PRODUCT)) {
				System.setProperty(PROP_PRODUCT, arg); //$NON-NLS-1$
				found = true;
			}

			// look for the application to run.  
			if (args[i - 1].equalsIgnoreCase(APPLICATION)) {
				System.setProperty(PROP_APPLICATION, arg); //$NON-NLS-1$
				found = true;
			}

			// look for the plug-in customization file
			if (args[i - 1].equalsIgnoreCase(PLUGIN_CUSTOMIZATION)) {
				pluginCustomizationFile = arg;
				found = true;
			}

			if (args[i - 1].equalsIgnoreCase(CLASSLOADER_PROPERTIES)) {
				// ignored
				found = true;
			}

			// done checking for args.  Remember where an arg was found 
			if (found) {
				configArgs[configArgIndex++] = i - 1;
				configArgs[configArgIndex++] = i;
			}
		}

		// remove all the arguments consumed by this argument parsing
		if (configArgIndex == 0) {
			appArgs = args;
			return args;
		}
		appArgs = new String[args.length - configArgIndex];
		frameworkArgs = new String[configArgIndex];
		configArgIndex = 0;
		int j = 0;
		int k = 0;
		for (int i = 0; i < args.length; i++) {
			if (i == configArgs[configArgIndex]) {
				frameworkArgs[k++] = args[i];
				configArgIndex++;
			} else
				appArgs[j++] = args[i];
		}
		return appArgs;
	}

	/**
	 * @see Platform#removeLogListener
	 */
	public void removeLogListener(ILogListener listener) {
		assertInitialized();
		synchronized (logListeners) {
			logListeners.remove(listener);
		}
	}
	/**
	 * @see Platform
	 */
	public URL resolve(URL url) throws IOException {
		URL result = asActualURL(url);
		if (!result.getProtocol().startsWith(PlatformURLHandler.BUNDLE))
			return result;

		URLConverter urlConverter = getURLConverter();
		if (urlConverter == null) {
			throw new IOException("url.noaccess");
		}
		result = urlConverter.convertToLocalURL(result);

		return result;
	}
	public void run(ISafeRunnable code) {
		Assert.isNotNull(code);
		try {
			code.run();
		} catch (Exception e) {
			handleException(code, e);
		} catch (LinkageError e) {
			handleException(code, e);
		}
	}
	private void run(Runnable handler) {
		// run end-of-initialization handler
		if (handler == null)
			return;

		final Runnable finalHandler = handler;
		ISafeRunnable code = new ISafeRunnable() {
			public void run() throws Exception {
				finalHandler.run();
			}
			public void handleException(Throwable e) {
				// just continue ... the exception has already been logged by
				// the platform (see handleException(ISafeRunnable)
			}
		};
		run(code);
	}
	public void setOption(String option, String value) {
		if (options != null)
			options.setOption(option, value);
	}
	public void addLastModifiedTime(String pathKey, long lastModTime) {
		if (regIndex == null)
			regIndex = new HashMap(30);
		regIndex.put(pathKey, new Long(lastModTime));
	}
	public Map getRegIndex() {
		return regIndex;
	}
	public void clearRegIndex() {
		regIndex = null;
	}

	/**
	 * Look for the companion preference translation file for a group
	 * of preferences.  This method will attempt to find a companion 
	 * ".properties" file first.  This companion file can be in an
	 * nl-specific directory for this plugin or any of its fragments or 
	 * it can be in the root of this plugin or the root of any of the
	 * plugin's fragments. This properties file can be used to translate
	 * preference values.
	 * 
	 * @param pluginDescriptor the descriptor of the plugin
	 *   who has the preferences
	 * @param basePrefFileName the base name of the preference file
	 *   This base will be used to construct the name of the 
	 *   companion translation file.
	 *   Example: If basePrefFileName is "plugin_customization",
	 *   the preferences are in "plugin_customization.ini" and
	 *   the translations are found in
	 *   "plugin_customization.properties".
	 * @return the properties file
	 * 
	 * @since 2.0
	 */
	public Properties getPreferenceTranslator(String uniqueIdentifier, String basePrefFileName) {
		return new Properties();
	}

	/**
	 * Takes a preference value and a related resource bundle and
	 * returns the translated version of this value (if one exists).
	 * 
	 * @param value the preference value for potential translation
	 * @param bundle the bundle containing the translated values
	 * 
	 * @since 2.0
	 */
	public String translatePreference(String value, Properties props) {
		value = value.trim();
		if (props == null || value.startsWith(KEY_DOUBLE_PREFIX))
			return value;
		if (value.startsWith(KEY_PREFIX)) {

			int ix = value.indexOf(" "); //$NON-NLS-1$
			String key = ix == -1 ? value : value.substring(0, ix);
			String dflt = ix == -1 ? value : value.substring(ix + 1);
			return props.getProperty(key.substring(1), dflt);
		}
		return value;
	}

	/**
	 * Applies primary feature-specific overrides to default preferences for the
	 * plug-in with the given id.
	 * <p>
	 * Note that by the time this method is called, the default settings
	 * for the plug-in itself should have already have been filled in.
	 * </p>
	 * 
	 * @param id the unique identifier of the plug-in
	 * @param preferences the preference store for the specified plug-in
	 * 
	 * @since 2.0
	 */
	public void applyPrimaryFeaturePluginDefaultOverrides(String id, Preferences preferences) {
	}

	/**
	 * Applies command line-supplied overrides to default preferences for the
	 * plug-in with the given id.
	 * <p>
	 * Note that by the time this method is called, the default settings
	 * for the plug-in itself should have already have been filled in, along
	 * with any default overrides supplied by the primary feature.
	 * </p>
	 * 
	 * @param id the unique identifier of the plug-in
	 * @param preferences the preference store for the specified plug-in
	 * 
	 * @since 2.0
	 */
	public void applyCommandLinePluginDefaultOverrides(String id, Preferences preferences) {

		if (pluginCustomizationFile == null) {
			// no command line overrides to process
			if (DEBUG_PREFERENCES) {
				System.out.println("Command line argument -pluginCustomization not used."); //$NON-NLS-1$
			}
			return;
		}

		try {
			URL pluginCustomizationURL = new File(pluginCustomizationFile).toURL();
			if (DEBUG_PREFERENCES) {
				System.out.println("Loading preferences from " + pluginCustomizationURL); //$NON-NLS-1$
			}
			applyPluginDefaultOverrides(pluginCustomizationURL, id, preferences, null);
		} catch (MalformedURLException e) {
			// fail silently
			if (DEBUG_PREFERENCES) {
				System.out.println("MalformedURLException creating URL for plugin customization file " //$NON-NLS-1$
				+pluginCustomizationFile);
				e.printStackTrace();
			}
			return;
		}
	}

	/**
	 * Applies overrides to default preferences for the plug-in with the given id.
	 * The data is contained in the <code>java.io.Properties</code> style file at 
	 * the given URL. The property names consist of "/'-separated plug-in id and
	 * name of preference; e.g., "com.example.myplugin/mypref".
	 * 
	 * @param propertiesURL the URL of a <code>java.io.Properties</code> style file
	 * @param id the unique identifier of the plug-in
	 * @param preferences the preference store for the specified plug-in
	 * 
	 * @since 2.0
	 */
	private void applyPluginDefaultOverrides(URL propertiesURL, String id, Preferences preferences, Properties props) {

		// read the java.io.Properties file at the given URL
		Properties overrides = new Properties();
		SafeFileInputStream in = null;

		try {
			File inFile = new File(propertiesURL.getFile());
			if (!inFile.exists()) {
				// We don't have a preferences file to worry about
				if (DEBUG_PREFERENCES) {
					System.out.println("Preference file " + //$NON-NLS-1$
					propertiesURL + " not found."); //$NON-NLS-1$
				}
				return;
			}

			in = new SafeFileInputStream(inFile);
			if (in == null) {
				// fail quietly
				if (DEBUG_PREFERENCES) {
					System.out.println("Failed to open " + //$NON-NLS-1$
					propertiesURL);
				}
				return;
			}
			overrides.load(in);
		} catch (IOException e) {
			// cannot read ini file - fail silently
			if (DEBUG_PREFERENCES) {
				System.out.println("IOException reading preference file " + //$NON-NLS-1$
				propertiesURL);
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
				if (DEBUG_PREFERENCES) {
					System.out.println("IOException closing preference file " + //$NON-NLS-1$
					propertiesURL);
					e.printStackTrace();
				}
			}
		}

		for (Iterator it = overrides.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			String qualifiedKey = (String) entry.getKey();
			// Keys consist of "/'-separated plug-in id and name of preference
			// e.g., "com.example.myplugin/mypref"
			int s = qualifiedKey.indexOf('/');
			if (s < 0 || s == 0 || s == qualifiedKey.length() - 1) {
				// skip mangled entry
				continue;
			}
			// plug-in id is non-empty string before "/" 
			String pluginId = qualifiedKey.substring(0, s);
			if (pluginId.equals(id)) {
				// override property in the given plug-in
				// plig-in-specified property name is non-empty string after "/" 
				String propertyName = qualifiedKey.substring(s + 1);
				String value = (String) entry.getValue();
				value = translatePreference(value, props);
				preferences.setDefault(propertyName, value);
			}
		}
		if (DEBUG_PREFERENCES) {
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
	public void setExtensionRegistry(IExtensionRegistry value) {
		registry = value;
	}
	public BundleContext getBundleContext() {
		return context;
	}
	public Bundle getBundle(String id) {
		return getBundleContext().getBundle(id);
	}

	public URL getInstallURL() {
		if (installLocation == null)
			try {
				installLocation = new URL(System.getProperty(PROP_INSTALL_LOCATION)); //$NON-NLS-1$
			} catch (MalformedURLException e) {
				//This can't fail because the location was set coming in
			}
		return installLocation;
	}
	public EnvironmentInfo getEnvironmentInfoService() {
		return infoService;
	}

	public URLConverter getURLConverter(){
		return urlConverter;
	}

	public boolean isRunning() {
		int state = context.getBundle(PI_RUNTIME).getState();
		return state == Bundle.ACTIVE;
	}

	/*
	 * This method is retained for R1.0 compatibility because it is defined as API.
	 * It's function matches the API description (returns <code>null</code> when
	 * argument URL is <code>null</code> or cannot be read).
	 */
	public URL[] getPluginPath(URL pluginPathLocation /*R1.0 compatibility*/
	) {
		InputStream input = null;
		// first try and see if the given plugin path location exists.
		if (pluginPathLocation == null)
			return null;
		try {
			input = pluginPathLocation.openStream();
		} catch (IOException e) {
			//fall through
		}

		// if the given path was null or did not exist, look for a plugin path
		// definition in the install location.
		if (input == null)
			try {
				URL url = new URL(PlatformURLBaseConnection.PLATFORM_URL_STRING + PLUGIN_PATH);
				input = url.openStream();
			} catch (MalformedURLException e) {
				//fall through
			} catch (IOException e) {
				//fall through
			}

		// nothing was found at the supplied location or in the install location
		if (input == null)
			return null;
		// if we found a plugin path definition somewhere so read it and close the location.
		URL[] result = null;
		try {
			try {
				result = readPluginPath(input);
			} finally {
				input.close();
			}
		} catch (IOException e) {
			//let it return null on failure to read
		}
		return result;
	}

	private URL[] readPluginPath(InputStream input) {
		Properties ini = new Properties();
		try {
			ini.load(input);
		} catch (IOException e) {
			return null;
		}
		Vector result = new Vector(5);
		for (Enumeration groups = ini.propertyNames(); groups.hasMoreElements();) {
			String group = (String) groups.nextElement();
			for (StringTokenizer entries = new StringTokenizer(ini.getProperty(group), ";"); entries.hasMoreElements();) { //$NON-NLS-1$
				String entry = (String) entries.nextElement();
				if (!entry.equals("")) //$NON-NLS-1$
					try {
						result.addElement(new URL(entry));
					} catch (MalformedURLException e) {
						//intentionally ignore bad URLs
						System.err.println(Policy.bind("ignore.plugin", entry)); //$NON-NLS-1$
					}
			}
		}
		return (URL[]) result.toArray(new URL[result.size()]);
	}
	public IPath getConfigurationMetadataLocation() {
		if (configMetadataLocation == null)
			configMetadataLocation = new Path(System.getProperty("osgi.configuration.area")); //$NON-NLS-1$
		return configMetadataLocation;
	}
	public IPath getStateLocation(Bundle bundle, boolean create) throws IllegalStateException {
		assertInitialized();
		IPath result = getMetaArea().getStateLocation(bundle);
		if (create)
			result.toFile().mkdirs();
		return result;
	}
	public URL find(Bundle b, IPath path) {
		return FindSupport.find(b, path);
	}
	public URL find(Bundle bundle, IPath path, Map override) {
		return FindSupport.find(bundle, path, override);
	}
	public InputStream openStream(Bundle bundle, IPath file) throws IOException {
		return FindSupport.openStream(bundle, file, false);
	}
	public InputStream openStream(Bundle bundle, IPath file, boolean localized) throws IOException {
		return FindSupport.openStream(bundle, file, localized);
	}
	public IPath getStateLocation(Bundle bundle) {
		return getStateLocation(bundle, true);
	}
	public ResourceBundle getResourceBundle(Bundle bundle) throws MissingResourceException {
		BundleModel model = (BundleModel) ((ExtensionRegistry) getRegistry()).getElement(bundle.getGlobalName());
		return model != null ? model.getResourceBundle() : null ;
	}
	public String getResourceString(Bundle bundle, String value) {
		BundleModel model = (BundleModel) ((ExtensionRegistry) getRegistry()).getElement(bundle.getGlobalName());
		return model != null ? model.getResourceString(value) : value;
	}
	public String getResourceString(Bundle bundle, String value, ResourceBundle resourceBundle) {
		BundleModel model =  (BundleModel) ((ExtensionRegistry) getRegistry()).getElement(bundle.getGlobalName());
		return model != null ? model.getResourceString(value, resourceBundle) : value;
	}
	public String getOSArch() {
		return getEnvironmentInfoService().getOSArch();
	}
	public String getNL() {
		return getEnvironmentInfoService().getNL();
	}
	public String getOS() {
		return getEnvironmentInfoService().getOS();
	}
	public String getWS() {
		return getEnvironmentInfoService().getWS();
	}
	public String[] getApplicationArgs() {
		return getEnvironmentInfoService().getApplicationArgs();
	}
	//Those two methods are only used to register runtime once compatibility has been started.
	public void setRuntimeInstance(Plugin runtime) {
		runtimeInstance = runtime;
	}
	public Plugin getRuntimeInstance() {
		return runtimeInstance;
	}
	public long getStateTimeStamp() {
		ServiceReference platformAdminReference = context.getServiceReference(PlatformAdmin.class.getName());
		if (platformAdminReference == null)
			return -1;
		else
			return ((PlatformAdmin) context.getService(platformAdminReference)).getState().getTimeStamp();
	}
	public PlatformAdmin getPlatformAdmin() {
		ServiceReference platformAdminReference = context.getServiceReference(PlatformAdmin.class.getName());
		if (platformAdminReference == null)
			return null;
		return (PlatformAdmin) context.getService(platformAdminReference);
	}
	public void lockInstanceData() throws CoreException {
		getMetaArea().createLockFile();
	}
	public void unlockInstanceData() {
		getMetaArea().clearLockFile();
	}
	public boolean hasInstanceData() {
		return getMetaArea().hasInstanceData();
	}
	public void addAuthorizationInfo(URL serverUrl, String realm, String authScheme, Map info) throws CoreException {
		getMetaArea().addAuthorizationInfo(serverUrl, realm, authScheme, info);	
	}
	public void addProtectionSpace(URL resourceUrl, String realm) throws CoreException {
		getMetaArea().addProtectionSpace(resourceUrl, realm);
	}
	public void flushAuthorizationInfo(URL serverUrl, String realm, String authScheme) throws CoreException {
		getMetaArea().flushAuthorizationInfo(serverUrl, realm, authScheme);
	}
	public Map getAuthorizationInfo(URL serverUrl, String realm, String authScheme) {
		return getMetaArea().getAuthorizationInfo(serverUrl, realm, authScheme);
	}
	public String getProtectionSpace(URL resourceUrl) {
		return getMetaArea().getProtectionSpace(resourceUrl);
	}
	public void setKeyringLocation(String keyringFile) {
		getMetaArea().setKeyringFile(keyringFile);
	}
	public void setInstanceLocation(IPath location) throws IllegalStateException {
		getMetaArea().setInstanceDataLocation(location);	
	}
	public IPath getInstanceLocation() throws IllegalStateException {
		assertInitialized();
		if (! metaArea.isInstanceDataLocationInitiliazed()) {
			if (noData) 
				throw new IllegalStateException(Policy.bind("meta.noDataModeSpecified"));
			if (noDefaultData)
				throw new IllegalStateException(Policy.bind("meta.instanceDataUnspecified"));
			try {
				metaArea.initializeLocation();
			} catch (CoreException e) {
				throw new IllegalStateException(e.getLocalizedMessage());
			} 
		}
		return metaArea.getInstanceDataLocation();
	}	
	public IBundleGroupProvider[] getBundleGroupProviders() {
		return (IBundleGroupProvider[])groupProviders.toArray(new IBundleGroupProvider[groupProviders.size()]);
	}

	public IProduct getProduct() {
		if (product != null)
			return product;
		String productId = System.getProperty("eclipse.product");
		if (productId == null)
			return null;
		IConfigurationElement[] entries = getRegistry().getConfigurationElementsFor(PI_RUNTIME, "products", productId);
		if (entries == null || entries.length == 0)
			return null;
		// There should only be one product with the given id so just take the first element
		product = new Product(entries[0]);
		return product;
	}	

	public void registerBundleGroupProvider(IBundleGroupProvider provider) {
		groupProviders.add(provider);		
	}
	public void unregisterBundleGroupProvider(IBundleGroupProvider provider) {
		groupProviders.remove(provider);		
	}
	
}
