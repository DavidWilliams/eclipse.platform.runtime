/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.runtime;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;
import org.eclipse.core.internal.boot.PlatformURLBaseConnection;
import org.eclipse.core.internal.boot.PlatformURLHandler;
import org.eclipse.core.internal.registry.*;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.framework.log.FrameworkLog;
import org.eclipse.osgi.service.environment.EnvironmentInfo;
import org.eclipse.osgi.service.runnable.ParameterizedRunnable;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.osgi.framework.*;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * Activator for the Eclipse runtime.
 */
public class PlatformActivator extends Plugin implements BundleActivator {
	private static final String PROP_ECLIPSE_EXITCODE = "eclipse.exitcode"; //$NON-NLS-1$
	private static final String PROP_ECLIPSE_APPLICATION = "eclipse.application"; //$NON-NLS-1$
	
	private static BundleContext context;
	private EclipseBundleListener pluginBundleListener;
	private ExtensionRegistry registry;
	private ServiceReference environmentServiceReference;
	private ServiceReference urlServiceReference;
	private ServiceReference logServiceReference;
	private ServiceReference packageAdminReference;
	private long registryStamp;
	
	public static BundleContext getContext() {
		return context;
	}

	public void start(BundleContext context) throws Exception {
		PlatformActivator.context = context;
		acquireInfoService();
		acquireURLConverterService();
		acquireFrameworkLogService();
		acquirePackageAdminService();
		startInternalPlatform();		
		startRegistry(context);
		installPlatformURLSupport();
		registerApplicationService();
		InternalPlatform.getDefault().setRuntimeInstance(this);
		super.start(context);
	}

	/**
	 * Register the platform URL support as a service to the URLHandler service
	 */
	private void installPlatformURLSupport() {
		PlatformURLPluginConnection.startup();
		PlatformURLFragmentConnection.startup();

		PlatformURLBaseConnection.startup(InternalPlatform.getDefault().getInstallURL());

		Hashtable properties = new Hashtable(1);
		properties.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { PlatformURLHandler.PROTOCOL });
		context.registerService(URLStreamHandlerService.class.getName(), new PlatformURLHandler(), properties);
	}

	private void startRegistry(BundleContext context) {
		boolean fromCache = true;
		if (!"true".equals(System.getProperty(InternalPlatform.PROP_NO_REGISTRY_CACHE))) { //$NON-NLS-1$
			// Try to read the registry from the cache first. If that fails, create a new registry
			MultiStatus problems = new MultiStatus(IPlatform.PI_RUNTIME, ExtensionsParser.PARSE_PROBLEM, "Registry cache problems", null); //$NON-NLS-1$
			Factory factory = new Factory(problems);

			long start = 0;
			if (InternalPlatform.DEBUG)
				start = System.currentTimeMillis();
			
			boolean lazyLoading = !"true".equals(System.getProperty(InternalPlatform.PROP_NO_LAZY_CACHE_LOADING)); //$NON-NLS-1$
			File cacheFile = new File(InternalPlatform.getDefault().getConfigurationLocation().getURL().getPath());
			cacheFile = new File(cacheFile, ".registry"); //$NON-NLS-1$

			if (cacheFile.isFile()) {
				registryStamp = computeRegistryStamp(); //$NON-NLS-1$
				registry = new RegistryCacheReader(cacheFile, factory, lazyLoading).loadCache(registryStamp);
			}
			if (InternalPlatform.DEBUG && registry != null)
				System.out.println("Reading registry cache: " + (System.currentTimeMillis() - start));

			if (InternalPlatform.DEBUG_REGISTRY) {
				if (registry == null)
					System.out.println("Reloading registry from manifest files...");
				else
					System.out.println("Using registry cache " + (lazyLoading ? "with" : "without") + " lazy element loading...");
			}
			// TODO log any problems that occurred in loading the cache.
			if (!problems.isOK())
				System.out.println(problems);
		}
		if (registry == null) {
			fromCache = false;
			registry = new ExtensionRegistry(new ExtensionLinker());
		}

		// register a listener to catch new bundle installations/resolutions.
		pluginBundleListener = new EclipseBundleListener(registry);
		context.addBundleListener(pluginBundleListener);

		// populate the registry with all the currently installed bundles.
		if (!fromCache)
			pluginBundleListener.processBundles(context.getBundles());

		context.registerService(IExtensionRegistry.class.getName(), registry, new Hashtable()); //$NON-NLS-1$
		InternalPlatform.getDefault().setExtensionRegistry(registry);
	}
	private long computeRegistryStamp() {
		if (!"true".equalsIgnoreCase(System.getProperty("osgi.checkConfiguration"))) //$NON-NLS-1$
			return 0;
		Bundle[] allBundles = context.getBundles();
		long result = 0;
		for (int i = 0; i < allBundles.length; i++) {
			URL pluginManifest = allBundles[i].getEntry("plugin.xml"); //$NON-NLS-1$
			if (pluginManifest == null)
				pluginManifest = allBundles[i].getEntry("fragment.xml"); //$NON-NLS-1$
			if (pluginManifest == null)
				continue;		
			try {
				URLConnection connection = pluginManifest.openConnection();
				result ^= connection.getLastModified() + allBundles[i].getBundleId();
			} catch (IOException e) {
				return 0;
			}
		}
		return result;
	}	

	public void stop(BundleContext context) throws Exception {
		// Stop the registry
		stopRegistry(context);
		environmentInfoServiceReleased(environmentServiceReference);
		urlServiceReleased(urlServiceReference);
		logServiceReleased(logServiceReference);
		packageAdminServiceReleased(packageAdminReference);
		// Stop the platform orderly.		
		InternalPlatform.getDefault().stop(context);
		InternalPlatform.getDefault().setRuntimeInstance(null);
	}

	private void stopRegistry(BundleContext context) {
		context.removeBundleListener(this.pluginBundleListener);
		if (registry != null && registry.isDirty()) {
			File cacheFile = new File(InternalPlatform.getDefault().getConfigurationLocation().getURL().getPath());
			cacheFile = new File(cacheFile, ".registry"); //$NON-NLS-1$			
			new RegistryCacheWriter(cacheFile).saveCache(registry, computeRegistryStamp());
			registry = null;
		}
	}

	private void acquireInfoService() throws Exception{
		environmentServiceReference = context.getServiceReference(EnvironmentInfo.class.getName());
		if (environmentServiceReference == null)
			return;
		InternalPlatform.infoService  = (EnvironmentInfo) context.getService(environmentServiceReference);
	}

	private void acquireURLConverterService() throws Exception{
		urlServiceReference = context.getServiceReference(URLConverter.class.getName());
		if (urlServiceReference == null)
			return;
		InternalPlatform.urlConverter  = (URLConverter) context.getService(urlServiceReference);
	}

	private void acquireFrameworkLogService() throws Exception{
		logServiceReference = context.getServiceReference(FrameworkLog.class.getName());
		if (logServiceReference == null)
			return;
		InternalPlatform.frameworkLog  = (FrameworkLog) context.getService(logServiceReference);
	}

	private void acquirePackageAdminService() throws Exception{
		packageAdminReference = context.getServiceReference(PackageAdmin.class.getName());
		if (packageAdminReference == null)
			return;
		InternalPlatform.packageAdmin  = (PackageAdmin) context.getService(packageAdminReference);
	}

	private void startInternalPlatform() {
		InternalPlatform.getDefault().start(context);	
	}

	private void environmentInfoServiceReleased(ServiceReference reference) {
		if (environmentServiceReference == null)
			return;
		if (environmentServiceReference != reference)
			return;

		InternalPlatform.infoService = null;
		context.ungetService(environmentServiceReference);
		environmentServiceReference = null;
	}

	private void urlServiceReleased(ServiceReference reference) {
		if (urlServiceReference == null)
			return;
		if (urlServiceReference != reference)
			return;

		InternalPlatform.urlConverter = null;
		context.ungetService(urlServiceReference);
		urlServiceReference = null;
	}

	private void logServiceReleased(ServiceReference reference) {
		if (logServiceReference == null)
			return;
		if (logServiceReference != reference)
			return;

		InternalPlatform.frameworkLog = null;
		context.ungetService(logServiceReference);
		logServiceReference = null;
	}

	private void packageAdminServiceReleased(ServiceReference reference) {
		if (packageAdminReference == null)
			return;
		if (packageAdminReference != reference)
			return;

		InternalPlatform.packageAdmin = null;
		context.ungetService(packageAdminReference);
		packageAdminReference = null;
	}

	private void registerApplicationService() {
		ParameterizedRunnable work = new ParameterizedRunnable() {
			public Object run(Object arg) {
				IPlatformRunnable application = null;
				String applicationId = System.getProperty(PROP_ECLIPSE_APPLICATION);
				if (applicationId == null) {
					//Derive the application from the product information
					IProduct product = InternalPlatform.getDefault().getProduct();
					if (product != null) {
						applicationId = product.getApplication();
						System.setProperty(PROP_ECLIPSE_APPLICATION, applicationId);
					}
				}
				if (applicationId	== null)
					throw new RuntimeException("No application id found");
				IExtension applicationExtension = registry.getExtension(IPlatform.PI_RUNTIME, IPlatform.PT_APPLICATIONS, applicationId);
				if (applicationExtension == null)
					throw new RuntimeException("Unable to locate application extension: " + applicationId);
				IConfigurationElement[] configs = applicationExtension.getConfigurationElements();
				if (configs.length == 0)
					throw new RuntimeException("Invalid (empty) application extension: " + applicationId);
				IConfigurationElement config = configs[0];
				try {
					application = (IPlatformRunnable) config.createExecutableExtension("run"); //$NON-NLS-1$
				} catch (CoreException ce) {
					throw new RuntimeException("Error running application", ce); //$NON-NLS-1$
				}
				// if the given arg is null the pass in the left over command line args.
				if (arg == null)
					arg = InternalPlatform.getDefault().getApplicationArgs();
				try {
					Object result = application.run(arg);
					int exitCode = result instanceof Integer ? ((Integer) result).intValue() : 0;
					System.setProperty(PROP_ECLIPSE_EXITCODE, Integer.toString(exitCode));
					if (InternalPlatform.DEBUG)
						System.out.println(Policy.bind("application.returned", new String[]{applicationId, result.toString()})); //$NON-NLS-1$
					return result;
				} catch (RuntimeException re) {
					// catch separately to avoid unnecessary wrapping
					throw re;					
				} catch (Exception e) {
					// wrap into RuntimeException (TODO: not need after bug 56053 is fixed)
					throw new RuntimeException("Error running application", e); //$NON-NLS-1$
				}
			}
		};
		Hashtable properties = new Hashtable(1);
		properties.put(PROP_ECLIPSE_APPLICATION, "default"); //$NON-NLS-1$ 
		context.registerService(ParameterizedRunnable.class.getName(), work, properties);
	}
}