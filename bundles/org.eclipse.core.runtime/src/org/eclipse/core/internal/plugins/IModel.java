package org.eclipse.core.internal.plugins;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

public interface IModel {

	public static final int INDENT = 2;
	public static final int RADIX = 36;

	public static final String TRUE = "true";
	public static final String FALSE = "false";

	public static final String REGISTRY = "plugin-registry";
	public static final String REGISTRY_PATH = "path";

	public static final String FRAGMENT = "fragment";
	public static final String FRAGMENT_ID = "id";
	public static final String FRAGMENT_NAME = "name";
	public static final String FRAGMENT_PROVIDER = "provider-name";
	public static final String FRAGMENT_VERSION = "version";
	public static final String FRAGMENT_PLUGIN_ID = "plugin-id";
	public static final String FRAGMENT_PLUGIN_VERSION = "plugin-version";

	public static final String PLUGIN = "plugin";
	public static final String PLUGIN_ID = "id";
	public static final String PLUGIN_NAME = "name";
	public static final String PLUGIN_VENDOR = "vendor-name";
	public static final String PLUGIN_PROVIDER = "provider-name";
	public static final String PLUGIN_VERSION = "version";
	public static final String PLUGIN_CLASS = "class";

	public static final String PLUGIN_REQUIRES = "requires";
	public static final String PLUGIN_REQUIRES_PLATFORM = "platform-version";
	public static final String PLUGIN_REQUIRES_PLUGIN = "plugin";
	public static final String PLUGIN_REQUIRES_PLUGIN_VERSION = "version";
	public static final String PLUGIN_REQUIRES_OPTIONAL = "optional";
	public static final String PLUGIN_REQUIRES_IMPORT = "import";
	public static final String PLUGIN_REQUIRES_EXPORT = "export";
	public static final String PLUGIN_REQUIRES_MATCH = "match";
	public static final String PLUGIN_REQUIRES_MATCH_EXACT = "exact";
	public static final String PLUGIN_REQUIRES_MATCH_COMPATIBLE = "compatible";

	public static final String PLUGIN_KEY_VERSION_SEPARATOR = "_";

	public static final String RUNTIME = "runtime";

	public static final String LIBRARY = "library";
	public static final String LIBRARY_NAME = "name";
	public static final String LIBRARY_SOURCE = "source";
	public static final String LIBRARY_TYPE = "type";
	public static final String LIBRARY_EXPORT = "export";
	public static final String LIBRARY_EXPORT_MASK = "name";

	public static final String EXTENSION_POINT = "extension-point";
	public static final String EXTENSION_POINT_NAME = "name";
	public static final String EXTENSION_POINT_ID = "id";
	public static final String EXTENSION_POINT_SCHEMA = "schema";

	public static final String EXTENSION = "extension";
	public static final String EXTENSION_NAME = "name";
	public static final String EXTENSION_ID = "id";
	public static final String EXTENSION_TARGET = "point";

	public static final String ELEMENT = "element";
	public static final String ELEMENT_NAME = "name";
	public static final String ELEMENT_VALUE = "value";

	public static final String PROPERTY = "property";
	public static final String PROPERTY_NAME = "name";
	public static final String PROPERTY_VALUE = "value";

	public static final String COMPONENT = "component";
	public static final String COMPONENT_LABEL = "label";
	public static final String COMPONENT_ID = "id";
	public static final String COMPONENT_VERSION = "version";
	public static final String COMPONENT_PROVIDER = "provider-name";

	public static final String COMPONENT_DESCRIPTION = "description";

	public static final String COMPONENT_URL = "url";

	public static final String COMPONENT_PLUGIN = "plugin";
	public static final String COMPONENT_PLUGIN_LABEL = "label";
	public static final String COMPONENT_PLUGIN_ID = "id";
	public static final String COMPONENT_PLUGIN_VERSION = "version";

	public static final String COMPONENT_FRAGMENT = "fragment";
	public static final String COMPONENT_FRAGMENT_LABEL = "label";
	public static final String COMPONENT_FRAGMENT_ID = "id";
	public static final String COMPONENT_FRAGMENT_VERSION = "version";

	public static final String CONFIGURATION = "configuration";
	public static final String CONFIGURATION_LABEL = "label";
	public static final String CONFIGURATION_ID = "id";
	public static final String CONFIGURATION_VERSION = "version";
	public static final String CONFIGURATION_PROVIDER = "provider-name";
	public static final String CONFIGURATION_APPLICATION = "application";

	public static final String CONFIGURATION_DESCRIPTION = "description";

	public static final String CONFIGURATION_URL = "url";

	public static final String CONFIGURATION_COMPONENT = "component";
	public static final String CONFIGURATION_COMPONENT_LABEL = "label";
	public static final String CONFIGURATION_COMPONENT_ID = "id";
	public static final String CONFIGURATION_COMPONENT_VERSION = "version";
	public static final String CONFIGURATION_COMPONENT_ALLOW_UPGRADE = "allowUpgrade";
	public static final String CONFIGURATION_COMPONENT_OPTIONAL = "optional";

	public static final String URL_UPDATE = "update";
	public static final String URL_DISCOVERY = "discovery";
	public static final String URL_URL = "url";
	public static final String URL_LABEL = "label";

}
