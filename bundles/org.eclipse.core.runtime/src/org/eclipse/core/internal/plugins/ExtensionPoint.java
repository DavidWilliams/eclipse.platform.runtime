package org.eclipse.core.internal.plugins;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.model.*;
import org.eclipse.core.internal.plugins.IModel;
import java.util.*;
import java.io.File;

public class ExtensionPoint extends ExtensionPointModel implements IExtensionPoint {
  public ExtensionPoint()
  {
	super();
  }  
public IConfigurationElement[] getConfigurationElements() {
	ExtensionModel[] list = getDeclaredExtensions();
	if (list == null)
		return new IConfigurationElement[0];
	ArrayList result = new ArrayList();
	for (int i = 0; i < list.length; i++) {
		ConfigurationElementModel[] configs = list[i].getSubElements();
		if (configs != null)
			for (int j = 0; j < configs.length; j++)
				result.add(configs[j]);
	}
	return (IConfigurationElement[]) result.toArray(new IConfigurationElement[result.size()]);
}
public IPluginDescriptor getDeclaringPluginDescriptor() {
	return (IPluginDescriptor) getParentPluginDescriptor();
}
public IExtension getExtension(String id) {
	if (id == null)
		return null;
	ExtensionModel[] list = getDeclaredExtensions();
	if (list == null)
		return null;
	for (int i = 0; i < list.length; i++) {
		if (id.equals(((Extension) list[i]).getUniqueIdentifier()))
			return (IExtension) list[i];
	}
	return null;
}
public IExtension[] getExtensions() {
	ExtensionModel[] list = getDeclaredExtensions();
	if (list == null)
		return new IExtension[0];
	IExtension[] newValues = new IExtension[list.length];
	System.arraycopy(list, 0, newValues, 0, list.length);
	return newValues;
}
public String getLabel() {
	String s = getName();
	return s == null ? "" : ((PluginDescriptor) getDeclaringPluginDescriptor()).getResourceString(s);
}
public java.lang.String getSchemaReference() {
	String s = getSchema();
	return s == null ? "" : s.replace(File.separatorChar, '/');
}
public String getSimpleIdentifier() {
	return getId();
}
public String getUniqueIdentifier() {
	return getParentPluginDescriptor().getId() + "." + getSimpleIdentifier();
}
public String toString() {
	return getParent().getPluginId() + "." + getSimpleIdentifier();
}
}
