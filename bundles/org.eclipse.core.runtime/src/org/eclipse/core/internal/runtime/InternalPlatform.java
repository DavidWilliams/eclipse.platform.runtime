package org.eclipse.core.internal.runtime;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.boot.BootLoader;
import org.eclipse.core.boot.IPlatformRunnable;
import org.eclipse.core.internal.boot.*;
import org.eclipse.core.runtime.model.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.internal.runtime.*;
import org.eclipse.core.internal.plugins.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Bootstrap class for the platform. It is responsible for setting up the
 * platform class loader and passing control to the actual application class
 */
public final class InternalPlatform {
	private static IAdapterManager adapterManager;
	private static PluginRegistry registry;
	private static Set logListeners = new HashSet(5);
	private static Map logs = new HashMap(5);
	private static PlatformLogListener platformLog = null;
	private static PlatformMetaArea metaArea;
	private static boolean initialized;
	private static IPath location;
	private static PluginClassLoader xmlClassLoader = null;

	private static boolean debugEnabled = false;
	private static boolean consoleLogEnabled = false;
	private static ILogListener consoleLog = null;
	private static Properties options = null;
	private static AuthorizationDatabase keyring = null;
	private static String keyringFile = null;
	private static String password = "";
	private static boolean inDevelopmentMode = false;
	private static boolean splashDown = false;
	private static boolean cacheRegistry = false;

	// default plugin data
	private static final String PI_XML = "org.apache.xerces";
	private static final String PLUGINSDIR = "plugins/";
	private static final String XML_VERSION = "1.2.1";
	private static final String XML_JAR = "xerces.jar";
	private static final String XML_LOCATION = "plugins/" + PI_XML + "/";

	// execution options
	private static final String OPTION_DEBUG = Platform.PI_RUNTIME + "/debug";
	private static final String OPTION_DEBUG_PLUGINS = Platform.PI_RUNTIME + "/registry/debug";

	// command line options
	private static final String LOG = "-consolelog";
	private static final String KEYRING = "-keyring";
	private static final String PASSWORD = "-password";
	private static final String DEV = "-dev";
	private static final String ENDSPLASH = "-endsplash";
	private static final String REGISTRYCACHE = "-registrycache";

	// debug support:  set in loadOptions()
	public static boolean DEBUG = false;
	public static boolean DEBUG_PLUGINS = false;

	private static boolean inVAJ;
	static {
		try {
			Class.forName("com.ibm.uvm.lang.ProjectClassLoader");
			inVAJ = true;
		} catch (Exception e) {
			inVAJ = false;
		}
	}
	private static boolean inVAME;
	static {
		try {
			Class.forName("com.ibm.eclipse.core.VAME");
			inVAME = true;
		} catch (Exception e) {
			inVAME = false;
		}
	}
/**
 * Private constructor to block instance creation.
 */
private InternalPlatform() {
}
/**
 * The runtime plug-in is not totally real due to bootstrapping problems.
 * This method builds the required constructs to activate and install
 * the runtime plug-in.
 */
private static void activateDefaultPlugins() throws CoreException {
	// for now, simply do the default activation.  This does not do the right thing
	// wrt the plugin class loader.
	PluginDescriptor descriptor = (PluginDescriptor) registry.getPluginDescriptor(Platform.PI_RUNTIME);
	descriptor.setPluginClassLoader(PlatformClassLoader.getDefault());
	descriptor.getPlugin();

	descriptor = (PluginDescriptor) registry.getPluginDescriptor(PI_XML);
	descriptor.setPluginClassLoader(xmlClassLoader);
	xmlClassLoader.setPluginDescriptor(descriptor);
	descriptor.getPlugin();
}
/**
 * @see Platform
 */
public static void addAuthorizationInfo(URL serverUrl, String realm, String authScheme, Map info) throws CoreException {
	keyring.addAuthorizationInfo(serverUrl, realm, authScheme, new HashMap(info));
	keyring.save();
}
/**
 * @see Platform#addLogListener
 */
public static void addLogListener(ILogListener listener) {
	assertInitialized();
	synchronized (logListeners) {
		logListeners.add(listener);
	}
}
/**
 * @see Platform
 */
public static void addProtectionSpace(URL resourceUrl, String realm) throws CoreException {
	keyring.addProtectionSpace(resourceUrl, realm);
	keyring.save();
}
/**
 * @see Platform
 */
public static URL asLocalURL(URL url) throws IOException {
	URLConnection connection = url.openConnection();
	if (!(connection instanceof PlatformURLConnection))
		return url;
	String file = connection.getURL().getFile();
	if (file.endsWith("/") && !file.endsWith(PlatformURLHandler.JAR_SEPARATOR))
		throw new IOException();
	return ((PlatformURLConnection) connection).getURLAsLocal();
}
private static void assertInitialized() {
	Assert.isTrue(initialized, "meta.appNotInit");
}
private static String findPlugin(LaunchInfo.VersionedIdentifier[] list, String name, String version) {
	LaunchInfo.VersionedIdentifier result = null;
	for (int i = 0; i < list.length; i++) {
		if (list[i].getIdentifier().equals(name)) {
			if (version != null)
				// we are looking for a particular version, compare.  If the current element 
				// has no version, save it for later in case we don't fine what we are looking for.
				if (list[i].getVersion().equals(version))
					return list[i].toString();
				else
					if (result == null && list[i].getVersion().length() == 0)
						result = list[i];
			else 
				// remember the element with the latest version number.
				if (result == null)
					result = list[i];
				else 
					if (result.getVersion().compareTo(list[i].getVersion()) == -1)
						result = list[i];
		}
	}
	return result == null ? null : result.toString();
}

/**
 * Creates and remembers a spoofed up class loader which loads the
 * classes from a predefined XML plugin.
 */
private static void createXMLClassLoader() {
	// create a plugin descriptor which is sufficient to be able to create
	// the class loader through the normal channels.
	Factory factory = new InternalFactory(null);
	PluginDescriptor descriptor = (PluginDescriptor) factory.createPluginDescriptor();
	descriptor.setEnabled(true);
	descriptor.setId(PI_XML);
	descriptor.setVersion(XML_VERSION);

	try {
		LaunchInfo launch = LaunchInfo.getCurrent();
		String plugin = findPlugin(launch.getPlugins(), PI_XML, XML_VERSION);
		URL url = null;
		if (plugin == null)
			url = new URL(BootLoader.getInstallURL(), XML_LOCATION);
		else
			url = new URL(BootLoader.getInstallURL(), PLUGINSDIR + plugin + "/");
		descriptor.setLocation(url.toExternalForm());
	} catch (MalformedURLException e) {
		// ISSUE: What to do when this fails.  It's pretty serious
	}

	LibraryModel lib = factory.createLibrary();
	lib.setName(XML_JAR);
	lib.setExports(new String[] { "*" });
	descriptor.setRuntime(new LibraryModel[] { lib });

	// use the fake plugin descriptor to create the desired class loader.
	// Since this class loader will be used before the plugin registry is installed,
	// ensure that the URLs on its class path are raw as opposed to eclipse:
	xmlClassLoader = (PluginClassLoader) descriptor.getPluginClassLoader(false);
}
/**
 * @see Platform
 */
public static void endSplash() {
	if (DEBUG) {
		String startString = Platform.getDebugOption(Platform.OPTION_STARTTIME);
		if (startString != null) 
			try {
				long start = Long.parseLong(startString);
				long end = System.currentTimeMillis();
				System.out.println("Startup complete: " + (end - start) + "ms");
			} catch (NumberFormatException e) {
			}
	}	
	if (splashDown) 
		return;
	String[] args = BootLoader.getCommandLineArgs();
	String splash = null;
	for (int i = 0; i < args.length; i++)
        if (args[i].equalsIgnoreCase(ENDSPLASH) && (i + 1) < args.length)
            splash = args[i + 1];
	if (splash != null)
	try {
		splashDown = true;
		Runtime.getRuntime().exec(splash);
	} catch (Exception e) {
	}
}

/**
 * @see Platform
 */
public static void flushAuthorizationInfo(URL serverUrl, String realm, String authScheme) throws CoreException {
	keyring.flushAuthorizationInfo(serverUrl, realm, authScheme);
	keyring.save();
}
/**
 * @see Platform#getAdapterManager
 */
public static IAdapterManager getAdapterManager() {
	assertInitialized();
	return adapterManager;
}

/**
 * Augments the plugin path with extra entries.
 */
private static URL[] getAugmentedPluginPath(URL[] pluginPath) {
	
	// ISSUE: this code needs to be reworked so that the platform
	//        does not have logical reference to plug-in-specific
	//        function
		
	IPath result = metaArea.getLocation().append(PlatformMetaArea.F_PLUGIN_DATA).append("org.eclipse.scripting").append("plugin.xml");
	String userScriptName = result.toString();
	URL userScriptUrl = null;
	try {
		userScriptUrl = new URL("file",null,0,userScriptName);
	} catch(MalformedURLException e) {
		return pluginPath;
	}
		
	URL[] newPath = new URL[pluginPath.length+1];
	System.arraycopy(pluginPath,0,newPath,0, pluginPath.length);
	newPath[newPath.length-1] = userScriptUrl;	
	return newPath;
}
/**
 * @see Platform
 */
public static Map getAuthorizationInfo(URL serverUrl, String realm, String authScheme) {
	Map info = keyring.getAuthorizationInfo(serverUrl, realm, authScheme);
	return info == null ? null : new HashMap(info);
}
public static boolean getBooleanOption(String option, boolean defaultValue) {
	String optionValue = options.getProperty(option);
	return (optionValue != null && optionValue.equalsIgnoreCase("true"))  || defaultValue;
}
/**
 * @see Platform
 */
public static String getDebugOption(String option) {
	return debugEnabled ? options.getProperty(option) : null;
}
public static int getIntegerOption(String option, int defaultValue) {
	String value = getDebugOption(option);
	try {
		return value == null ? defaultValue : Integer.parseInt(value);
	} catch (NumberFormatException e) {
		return defaultValue;
	}
}
/**
 * @see Platform#getLocation
 */
public static IPath getLocation() {
	assertInitialized();
	return location;
}
/**
 * Returns a log for the given plugin or <code>null</code> if none exists.
 */
public static ILog getLog(Plugin plugin) {
	ILog result = (ILog) logs.get(plugin);
	if (result != null)
		return result;
	result = new Log(plugin);
	logs.put(plugin, result);
	return result;
}
/**
 * Returns the object which defines the location and organization
 * of the platform's meta area.
 */
public static PlatformMetaArea getMetaArea() {
	return metaArea;
}
/**
 * @see Platform#getPlugin
 */
public static Plugin getPlugin(String id) {
	assertInitialized();
	IPluginDescriptor descriptor = getPluginRegistry().getPluginDescriptor(id);
	if (descriptor == null)
		return null;
	try {
		return descriptor.getPlugin();
	} catch (CoreException e) {
		return null;
	}
}
/**
 * @see Platform#getPluginRegistry
 */
public static IPluginRegistry getPluginRegistry() {
	assertInitialized();
	return registry;
}
/**
 * @see Platform#getPluginStateLocation
 */
public static IPath getPluginStateLocation(Plugin plugin) {
	assertInitialized();
	IPath result = metaArea.getPluginStateLocation(plugin);
	result.toFile().mkdirs();
	return result;
}
/**
 * @see Platform
 */
public static String getProtectionSpace(URL resourceUrl) {
	return keyring.getProtectionSpace(resourceUrl);
}
public static Plugin getRuntimePlugin() {
	try {
		return getPluginRegistry().getPluginDescriptor(Platform.PI_RUNTIME).getPlugin();
	} catch (CoreException e) {
		return null;
	}
}
private static void handleException(ISafeRunnable code, Throwable e) {
	if (!(e instanceof OperationCanceledException)) {
		// try to figure out which plugin caused the problem.  Derive this from the class
		// of the code arg.  Attribute to the Runtime plugin if we can't figure it out.
		Plugin plugin = getRuntimePlugin();
		try {
			plugin = ((PluginClassLoader)code.getClass().getClassLoader()).getPluginDescriptor().getPlugin();
		} catch (ClassCastException e1) {
		} catch (CoreException e1) {
		}
		String pluginId =  plugin.getDescriptor().getUniqueIdentifier();
		String message = Policy.bind("meta.pluginProblems", pluginId);
		IStatus status = new Status(Status.WARNING, pluginId, Platform.PLUGIN_ERROR, message, e);
		plugin.getLog().log(status);
	}
	code.handleException(e);
}
/**
 * Returns true if the platform is currently running in Development Mode.  If it is, there are 
 * special procedures that should be taken when defining plug-in class paths.
 */
public static boolean inDevelopmentMode() {
	return inDevelopmentMode || inVAJ() || inVAME();
}
/**
 * Returns true if the platform is currently running in  VA/Java.  If it is, there are 
 * typically some special procedures
 * that should be taken when dealing with plug-in activation and class loading.
 */
public static boolean inVAJ() {
	return inVAJ;
}
/**
 * Returns true if the platform is currently running in  VA/ME.  If it is, there are 
 * typically some special procedures
 * that should be taken when dealing with plug-in activation and class loading.
 */
public static boolean inVAME() {
	return inVAME;
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
public static IPlatformRunnable loaderGetRunnable(String applicationName) {
	assertInitialized();
	IExtension extension = registry.getExtension(Platform.PI_RUNTIME, Platform.PT_APPLICATIONS, applicationName);
	if (extension == null)
		return null;
	IConfigurationElement[] configs = extension.getConfigurationElements();
	if (configs.length == 0)
		return null;
	try {
		IConfigurationElement config = configs[0];
		return (IPlatformRunnable) config.createExecutableExtension("run");
	} catch (CoreException e) {
		if (DEBUG)
			getRuntimePlugin().getLog().log(e.getStatus());
		return null;
	}
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
public static IPlatformRunnable loaderGetRunnable(String pluginId, String className, Object args) {
	assertInitialized();
	PluginDescriptor descriptor = (PluginDescriptor) registry.getPluginDescriptor(pluginId);
	try {
		return (IPlatformRunnable) descriptor.createExecutableExtension(className, args, null, null);
	} catch (CoreException e) {
		if (DEBUG)
			getRuntimePlugin().getLog().log(e.getStatus());
		return null;
	}
}
/**
 * Internal method for shutting down the platform.  All active plug-ins 
 * are notified of the impending shutdown. 
 * The exact order of notification is unspecified;
 * however, each plug-in is assured that it will be told to shut down
 * before any of its prerequisites.  Plug-ins are expected to free any
 * shared resources they manage.  Plug-ins should not store state at
 * this time; a separate <b>save</b> lifecycle event preceding the
 * shutdown notice tells plug-ins the right time to be saving their state.
 * <p>
 * On exit, the platform will no longer be initialized and any objects derived from
 * or based on the running platform are invalidated.
 * </p>
 * <p>
 * This method is used by the platform boot loader; is must
 * not be called directly by client code.
 * </p>
 * @see BootLoader
 */
public static void loaderShutdown() {
	assertInitialized();
	registry.shutdown(null);
	if (DEBUG_PLUGINS) {
		// We are debugging so output the registry in XML
		// format.
		registry.debugRegistry();
	} else {
		// get rid of the debug file if it exists
		registry.flushDebugRegistry();
	}
	
	if (cacheRegistry) {
		// Write the registry in cache format
		try {
			registry.saveRegistry();
		} catch (IOException e) {
			String message = Policy.bind("meta.unableToWriteRegistry");
			IStatus status = new Status(IStatus.ERROR, Platform.PI_RUNTIME, Platform.PLUGIN_ERROR, message, e);
			getRuntimePlugin().getLog().log(status);
			if (DEBUG)
				System.out.println(status.getMessage());
		}
	} else {
		// get rid of the cache file if it exists
		registry.flushRegistry();
	}
	if (platformLog != null)
		platformLog.shutdown();
	initialized = false;
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
public static void loaderStartup(URL[] pluginPath, String locationString, Properties bootOptions, String[] args) throws CoreException {
	processCommandLine(args);
	setupMetaArea(locationString);
	adapterManager = new AdapterManager();
	loadOptions(bootOptions);
	createXMLClassLoader();
	MultiStatus problems = loadRegistry(pluginPath);
	initialized = true;
	// can't register url handlers until after the plugin registry is loaded
	PlatformURLPluginHandlerFactory.startup();
	activateDefaultPlugins();
	// can't install the log or log problems until after the platform has been initialized.
	platformLog = new PlatformLogListener();
	addLogListener(platformLog);
	if (consoleLogEnabled) {
		consoleLog = new PlatformLogListener(System.out);
		addLogListener(consoleLog);
	}
	if (!problems.isOK())
		getRuntimePlugin().getLog().log(problems);
	loadKeyring();
}
/**
 * Opens the password database (if any) initally provided to the platform at startup.
 */
private static void loadKeyring() {
	if (keyringFile != null)
		try {
			keyring = new AuthorizationDatabase(keyringFile, password);
		} catch (CoreException e) {
			log(e.getStatus());
		}
	if (keyring == null)
		keyring = new AuthorizationDatabase();
}
static void loadOptions(Properties bootOptions) {
	// If the boot loader passed <code>null</code> for the boot options, the user
	// did not specify debug options so no debugging should be enabled.
	if (bootOptions == null) {
		debugEnabled = false;
		return;
	}
	debugEnabled = true;
	options = new Properties(bootOptions);
	try {
		InputStream input = new FileInputStream(InternalPlatform.getMetaArea().getOptionsLocation().toFile());
		try {
			options.load(input);
		} finally {
			input.close();
		}
	} catch (FileNotFoundException e) {
		//	Its not an error to not find the options file
	} catch (IOException e) {
		//	Platform.RuntimePlugin.getLog().log();
	}
		// trim off all the blanks since properties files don't do that.
	for (Iterator i = options.keySet().iterator(); i.hasNext();) {
		Object key = i.next();
		options.put(key, ((String) options.get(key)).trim());
	}
	DEBUG = getBooleanOption(OPTION_DEBUG, false);
	DEBUG_PLUGINS = getBooleanOption(OPTION_DEBUG_PLUGINS, false);
	InternalBootLoader.setupOptions();
}
/**
 * Parses, resolves and rememberhs the plugin registry.  The multistatus returned
 * details any problems/issues encountered during this process.
 */
private static MultiStatus loadRegistry(URL[] pluginPath) {
	MultiStatus problems = new MultiStatus(Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, Policy.bind("parse.registryProblems"), null);
	InternalFactory factory = new InternalFactory(problems);

	IPath path = getMetaArea().getRegistryPath();
	IPath tempPath = getMetaArea().getBackupFilePathFor(path);
	DataInputStream input = null;
	registry = null;
	if (cacheRegistry) {
		try {
			input = new DataInputStream(new BufferedInputStream(new SafeFileInputStream(path.toOSString(), tempPath.toOSString())));
			try {
				long start = System.currentTimeMillis();
				RegistryCacheReader cacheReader = new RegistryCacheReader(factory);
				registry = (PluginRegistry)cacheReader.readPluginRegistry(input);
				if (DEBUG)
					System.out.println("Read registry cache: " + (System.currentTimeMillis() - start) + "ms");
			} finally {
				input.close();
			}
		} catch (IOException ioe) {
			IStatus status = new Status(IStatus.ERROR, Platform.PI_RUNTIME, Platform.PLUGIN_ERROR, Policy.bind("meta.unableToReadCache"), ioe);
			problems.merge(status);
		}
	}
	if (registry == null) {
		URL[] augmentedPluginPath = getAugmentedPluginPath(pluginPath);	// augment the plugin path with any additional platform entries	(eg. user scripts)
		registry = (PluginRegistry) parsePlugins(augmentedPluginPath, factory, DEBUG && DEBUG_PLUGINS);
		IStatus resolveStatus = registry.resolve(true, true);
		problems.merge(resolveStatus);
		registry.markReadOnly();
	}
	registry.startup(null);
	return problems;
}
/**
 * @see Platform#log
 */
public static void log(final IStatus status) {
	assertInitialized();
	// create array to avoid concurrent access
	ILogListener[] listeners;
	synchronized (logListeners) {
		listeners = (ILogListener[]) logListeners.toArray(new ILogListener[logListeners.size()]);
	}
	for (int i = 0; i < listeners.length; i++) {
		try {
			listeners[i].logging(status, Platform.PI_RUNTIME);
		} catch (Exception e) {
		} // no chance of exceptions for log listeners
	}
}
/**
 * @see Platform#parsePlugins
 */
public static PluginRegistryModel parsePlugins(URL[] pluginPath, Factory factory) {
	return parsePlugins(pluginPath, factory, false);
}
/**
 * @see Platform#parsePlugins
 */
public synchronized static PluginRegistryModel parsePlugins(URL[] pluginPath, Factory factory, boolean debug) {
	// If the platform is not running then simply parse the registry.  We don't need to play
	// any funny class loader games as we assume the XML classes are on the class path
	// This happens when we are running this code as part of a utility (as opposed to starting
	// or inside the platform).
	if (!(InternalBootLoader.isRunning() || InternalBootLoader.isStarting()))
		return RegistryLoader.parseRegistry(pluginPath, factory, debug);

	// If we are running the platform, we want to conserve class loaders.  
	// Temporarily install the xml class loader as a prerequisite of the platform class loader
	// This allows us to find the xml classes.  Be sure to reset the prerequisites after loading.
	PlatformClassLoader.getDefault().setImports(new DelegatingURLClassLoader[] { xmlClassLoader });
	try {
		return RegistryLoader.parseRegistry(pluginPath, factory, debug);
	} finally {
		PlatformClassLoader.getDefault().setImports(null);
	}
}
private static String[] processCommandLine(String[] args) {
	int[] configArgs = new int[100];
	configArgs[0] = -1; // need to initialize the first element to something that could not be an index.
	int configArgIndex = 0;
	for (int i = 0; i < args.length; i++) {
		boolean found = false;
		// check for args without parameters (i.e., a flag arg)

		// look for the log flag
		if (args[i].equalsIgnoreCase(LOG)) {
			consoleLogEnabled = true;
			found = true;
		}

		// look for the development mode flag
		if (args[i].equalsIgnoreCase(DEV)) {
			inDevelopmentMode = true;
			found = true;
		}

		// look for the registry cache flag
		if (args[i].equalsIgnoreCase(REGISTRYCACHE)) {
			cacheRegistry = true;
			found = true;
		}

		// done checking for args.  Remember where an arg was found 
		if (found) {
			configArgs[configArgIndex++] = i;
			continue;
		}
		// check for args with parameters
		if (i == args.length - 1 || args[i + 1].startsWith("-")) 
			continue;
		String arg = args[++i];

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

		// done checking for args.  Remember where an arg was found 
		if (found) {
			configArgs[configArgIndex++] = i - 1;
			configArgs[configArgIndex++] = i;
		}
	}
	// remove all the arguments consumed by this argument parsing
	if (configArgIndex == 0)
		return args;
	String[] passThruArgs = new String[args.length - configArgIndex];
	configArgIndex = 0;
	int j = 0;
	for (int i = 0; i < args.length; i++) {
		if (i == configArgs[configArgIndex])
			configArgIndex++;
		else
			passThruArgs[j++] = args[i];
	}
	return passThruArgs;
}
/**
 * @see Platform#removeLogListener
 */
public static void removeLogListener(ILogListener listener) {
	assertInitialized();
	synchronized (logListeners) {
		logListeners.remove(listener);
	}
}
/**
 * @see Platform
 */
public static URL resolve(URL url) throws IOException {
	URLConnection connection = url.openConnection();
	if (connection instanceof PlatformURLConnection)
		return ((PlatformURLConnection) connection).getResolvedURL();
	else
		return url;
}
public static void run(ISafeRunnable code) {
	Assert.isNotNull(code);
	try {
		code.run();
	} catch (Exception e) {
		handleException(code, e);
	} catch (LinkageError e) {
		handleException(code, e);
	}
}
public static void setDebugOption(String option, String value) {
	if (debugEnabled)
		options.setProperty(option, value);
}
/**
 * Sets the plug-in registry for the platform to the given value.
 * This method should only be called by the registry loader
 */
public static void setPluginRegistry(IPluginRegistry value) {
	registry = (PluginRegistry) value;
}
private static void setupMetaArea(String locationString) throws CoreException {
	location = new Path(locationString);
	if (!location.isAbsolute())
		location = new Path(System.getProperty("user.dir")).append(location);
	// must create the meta area first as it defines all the other locations.
	if (location.toFile().exists()) {
		if (!location.toFile().isDirectory()) {
			String message = Policy.bind("meta.notDir", location.toString());
			throw new CoreException(new Status(IStatus.ERROR, Platform.PI_RUNTIME, 13, message, null));
		}
	}
	metaArea = new PlatformMetaArea(location);
	metaArea.createLocation();
	if (keyringFile == null)
		keyringFile = metaArea.getLocation().append(PlatformMetaArea.F_KEYRING).toOSString();
}
}