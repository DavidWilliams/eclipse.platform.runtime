package org.eclipse.core.internal.plugins;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.internal.runtime.Policy;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.model.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class RegistryLoader {
	private Factory factory;

	// debug support
	private boolean debug = false;
	private long startTick = (new java.util.Date()).getTime(); // used for performance timings
	private long lastTick = startTick;
private RegistryLoader(Factory factory, boolean debug) {
	super();
	this.debug = debug;
	this.factory = factory;
}
private void debug(String msg) {
	long thisTick = System.currentTimeMillis();
	System.out.println("RegistryLoader: " + msg + " [+"+ (thisTick - lastTick) + "ms]");
	lastTick = thisTick;
}
private String[] getPathMembers(URL path) {
	String[] list = null;
	String protocol = path.getProtocol();
	if (protocol.equals("file") || (InternalPlatform.inVAJ() && protocol.equals("valoader"))) {
		list = (new File(path.getFile())).list();
	} else {
		// XXX: attempt to read URL and see if we got html dir page
	}
	return list == null ? new String[0] : list;
}
/**
 * Reports an error and returns true.
 */
private boolean parseProblem(String message) {
	factory.error(new Status(
		IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, message, null));
	return true;
}
private PluginRegistryModel parseRegistry(URL[] pluginPath) {
	long startTick = System.currentTimeMillis();
	PluginRegistryModel result = processManifestFiles(pluginPath);
	if (InternalPlatform.DEBUG) {
		long endTick = System.currentTimeMillis();
		debug("Parsed Registry: " + (endTick - startTick) + "ms");
	}
	return result;
}
public static PluginRegistryModel parseRegistry(URL[] pluginPath, Factory factory, boolean debug) {
	return new RegistryLoader(factory, debug).parseRegistry(pluginPath);
}
private PluginModel processManifestFile(URL manifest) {
	InputStream is = null;
	try {
		is = manifest.openStream();
	} catch (IOException e) {
		if (debug)
			debug("No plugin found for: " + manifest);
		return null;
	}
	PluginModel result = null;
	try {
		try {
			result = new PluginParser((Factory) factory).parsePlugin(new InputSource(is));
		} finally {
			is.close();
		}
	} catch (SAXParseException se) {
		/* exception details logged by parser */
		factory.error(new Status(IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, Policy.bind("parse.errorProcessing", manifest.toString()), null));
	} catch (Exception e) {
		factory.error(new Status(IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, Policy.bind("parse.errorProcessing", manifest.toString()), null));
	}
	return result;
}
private PluginRegistryModel processManifestFiles(URL[] pluginPath) {
	PluginRegistryModel result = factory.createPluginRegistry();
	for (int i = 0; i < pluginPath.length; i++)
		processPluginPathEntry(result, pluginPath[i]);
	return result;
}
private void processPluginPathEntry(PluginRegistryModel registry, URL location) {
	if (debug)
		debug("Path - " + location);
	if (location.getFile().endsWith("/")) {
		// directory entry - search for plugins
		String[] members = getPathMembers(location);
		for (int j = 0; j < members.length; j++) {
			boolean found = false;
			try {
				found = processPluginPathFile(registry, new URL(location, members[j] + "/plugin.xml"));
				if (!found)
					found = processPluginPathFile(registry, new URL(location, members[j] + "/fragment.xml"));
			} catch (MalformedURLException e) {
			}
			if (debug)
				debug(found ? "Processed - " : "Processed (not found) - " + members[j]);
		}
	} else {
		// specific file entry - load the given file
		boolean found = processPluginPathFile(registry, location);
		if (debug)
			debug(found ? "Processed - " : "Processed (not found) - " + location);
	}
}
/**
 * @return true if a file was found at the given location, and false otherwise.
 */
private boolean processPluginPathFile(PluginRegistryModel registry, URL location) {
	PluginModel entry = processManifestFile(location);
	if (entry == null)
		return false;
	entry.setVersion(getQualifiedVersion(entry, location)); // check for version qualifier
	if (entry instanceof PluginDescriptorModel) {
		if (entry.getId() == null || entry.getVersion() == null) {
			return parseProblem(Policy.bind("parse.nullPluginIdentifier", location.toString()));
		}
		//skip duplicate entries
		if (registry.getPlugin(entry.getId(), entry.getVersion()) != null) {
			return parseProblem(Policy.bind("parse.duplicatePlugin", entry.getId()));
		}
		registry.addPlugin((PluginDescriptorModel) entry);
	} else {
		if (entry.getId() == null || entry.getVersion() == null) {
			return parseProblem(Policy.bind("parse.nullFragmentIdentifier", location.toString()));
		}
		if (entry instanceof PluginFragmentModel) {
			registry.addFragment((PluginFragmentModel) entry);
		} else {
			return parseProblem(Policy.bind("parse.unknownEntry", location.toString()));
		}
	}
	String url = location.toString();
	url = url.substring(0, 1 + url.lastIndexOf('/'));
	entry.setRegistry(registry);
	entry.setLocation(url);
	InternalPlatform.addLastModifiedTime(location.getFile(), new File(location.getFile()).lastModified());
	return true;
}
private String getQualifiedVersion(PluginModel entry, URL base) {
	if (entry == null || entry.getVersion() == null || entry.getId() == null)
		return null;
		
	InputStream is = null;
	try {		
		// check to see if we have buildmanifest.properties for this plugin
		URL manifest = null;
		manifest = new URL(base, "buildmanifest.properties");
		Properties props = new Properties();
		is = manifest.openStream();
		props.load(is);
	
		// lookup qualifier for this plugin and "morph" the identifier if needed
		String key = "plugin@"+entry.getId();
		String qualifier = props.getProperty(key);
		if (qualifier == null)
			return entry.getVersion();
		PluginVersionIdentifier v = new PluginVersionIdentifier(entry.getVersion());
		if (!v.getQualifierComponent().equals(""))
			return entry.getVersion();
		else 
			return (new PluginVersionIdentifier(v.getMajorComponent(), v.getMinorComponent(), v.getServiceComponent(), qualifier)).toString();
	} catch(Exception e) {
		return entry.getVersion();
	} finally {		
		if (is != null) try { is.close(); } catch(IOException e) {}
	}
}
}
