package org.eclipse.core.internal.plugins;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.core.runtime.model.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.internal.runtime.Policy;
import org.apache.xerces.parsers.SAXParser;
import java.util.Stack;
import java.util.Vector;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

public class PluginParser extends DefaultHandler implements IModel {

	// concrete object factory
	Factory factory;

	// Current State Information
	Stack stateStack = new Stack();

	// Current object stack (used to hold the current object we are
	// populating in this plugin descriptor
	Stack objectStack = new Stack();

	// model parser
	private static SAXParser parser;
	
	static {
		initializeParser();
	}

	// Valid States
	private final int IGNORED_ELEMENT_STATE = 0;
	private final int INITIAL_STATE = 1;
	private final int PLUGIN_STATE = 2;
	private final int PLUGIN_RUNTIME_STATE = 3;
	private final int PLUGIN_REQUIRES_STATE = 4;
	private final int PLUGIN_EXTENSION_POINT_STATE = 5;
	private final int PLUGIN_EXTENSION_STATE = 6;
	private final int RUNTIME_LIBRARY_STATE = 7;
	private final int LIBRARY_EXPORT_STATE = 8;
	private final int PLUGIN_REQUIRES_IMPORT_STATE = 9;
	private final int CONFIGURATION_ELEMENT_STATE = 10;
	private final int FRAGMENT_STATE = 11;
	private final int CONFIGURATION_STATE = 12;
	private final int COMPONENT_STATE = 13;
	private final int DESCRIPTION_STATE = 14;
	private final int URL_STATE = 15;

	// Keep a group of vectors as a temporary scratch space.  These
	// vectors will be used to populate arrays in the plugin descriptor
	// once processing of the XML file is complete.
	private final int EXTENSION_POINT_INDEX = 0;
	private final int EXTENSION_INDEX = 1;
	private final int LAST_INDEX = 1;
	private Vector scratchVectors[] = new Vector[LAST_INDEX + 1];
	
public PluginParser(Factory factory) {
	super();
	this.factory = factory;
	parser.setContentHandler(this);
	parser.setDTDHandler(this);
	parser.setEntityResolver(this);
	parser.setErrorHandler(this);
}

private static void initializeParser() {
	parser = new SAXParser();
	try {
	 	((SAXParser)parser).setFeature("http://xml.org/sax/features/string-interning", true);
	} catch (SAXException e) {
	}
}

public void characters(char[] ch, int start, int length) {
	int state = ((Integer) stateStack.peek()).intValue();
	if (state == CONFIGURATION_ELEMENT_STATE) {
		// Accept character data within an element, is when it is
		// part of a configuration element (i.e. an element within an EXTENSION element
		ConfigurationElementModel currentConfigElement = (ConfigurationElementModel) objectStack.peek();
		String value = new String(ch, start, length);
		String newValue = value.trim();
		if (!newValue.equals("") || newValue.length() != 0)
			currentConfigElement.setValue(newValue);
		return;
	} 
	if (state == DESCRIPTION_STATE) {
		// Accept character data within an element, is when it is part of a component or configuration 
		// description element (i.e. an element within a COMPONENT or CONFIGURATION element
		InstallModel model = (InstallModel) objectStack.peek();
		String value = new String(ch, start, length).trim();
		if (!value.equals("") || value.length() != 0)
			model.setDescription(value);
		return;
	} 		
}
public void endDocument() {
}
public void endElement(String uri, String elementName, String qName) {
	switch (((Integer) stateStack.peek()).intValue()) {
		case IGNORED_ELEMENT_STATE :
			stateStack.pop();
			break;
		case INITIAL_STATE :
			// shouldn't get here
			internalError (Policy.bind("parse.internalStack", elementName));
			break;
		case PLUGIN_STATE :
		case FRAGMENT_STATE :
			if (elementName.equals(PLUGIN) || elementName.equals(FRAGMENT)) {
				stateStack.pop();
				PluginModel root = (PluginModel) objectStack.peek();

				// Put the extension points into this plugin
				Vector extPointVector = scratchVectors[EXTENSION_POINT_INDEX];
				if (extPointVector.size() > 0) {
					root.setDeclaredExtensionPoints((ExtensionPointModel[]) extPointVector.toArray(new ExtensionPointModel[extPointVector.size()]));
					scratchVectors[EXTENSION_POINT_INDEX].removeAllElements();
				}

				// Put the extensions into this plugin too
				Vector extVector = scratchVectors[EXTENSION_INDEX];
				if (extVector.size() > 0) {
					root.setDeclaredExtensions((ExtensionModel[]) extVector.toArray(new ExtensionModel[extVector.size()]));
					scratchVectors[EXTENSION_INDEX].removeAllElements();
				}
			}
			break;
		case PLUGIN_RUNTIME_STATE :
			if (elementName.equals(RUNTIME)) {
				stateStack.pop();
				// take the vector of library entries and put them into the plugin
				// descriptor
				Vector libVector = (Vector)objectStack.pop();
				if (libVector.size() > 0) {
					PluginModel model = (PluginModel) objectStack.peek();
					model.setRuntime((LibraryModel[]) libVector.toArray(new LibraryModel[libVector.size()]));
					}
			}
			break;
		case PLUGIN_REQUIRES_STATE :
			if (elementName.equals(PLUGIN_REQUIRES)) {
				stateStack.pop();
				// take the vector of prerequisites and put them into the plugin
				// descriptor
				Vector importVector = (Vector)objectStack.pop();
				if (importVector.size() > 0) {
					PluginModel parentDescriptor = (PluginModel) objectStack.peek();
					parentDescriptor.setRequires((PluginPrerequisiteModel[]) importVector.toArray(new PluginPrerequisiteModel[importVector.size()]));
				}
			}
			break;
		case PLUGIN_EXTENSION_POINT_STATE :
			if (elementName.equals(EXTENSION_POINT)) {
				stateStack.pop();
			}
			break;
		case PLUGIN_EXTENSION_STATE :
			if (elementName.equals(EXTENSION)) {
				stateStack.pop();
				// Finish up extension object
				ExtensionModel currentExtension = (ExtensionModel) objectStack.pop();
				PluginModel parent = (PluginModel) objectStack.peek();
				currentExtension.setParent(parent);
				scratchVectors[EXTENSION_INDEX].addElement(currentExtension);
			}
			break;
		case RUNTIME_LIBRARY_STATE :
			if (elementName.equals(LIBRARY)) {
				LibraryModel curLibrary = (LibraryModel) objectStack.pop();
				// Clean up the exports for this library entry
				Vector exportsVector = (Vector) objectStack.pop();
				if (exportsVector.size() > 0) {
					curLibrary.setExports((String[]) exportsVector.toArray(new String[exportsVector.size()]));
				}

				// Add this library element to the vector "runtime" on the stack
				Vector libraryVector = (Vector) objectStack.peek();
				libraryVector.addElement(curLibrary);
				stateStack.pop();
			}
			break;
		case LIBRARY_EXPORT_STATE :
			if (elementName.equals(LIBRARY_EXPORT)) {
				stateStack.pop();
			}
			break;
		case PLUGIN_REQUIRES_IMPORT_STATE :
			if (elementName.equals(PLUGIN_REQUIRES_IMPORT)) {
				stateStack.pop();
			}
			break;
		case CONFIGURATION_ELEMENT_STATE :
			// We don't care what the element name was
			stateStack.pop();
			// Now finish up the configuration element object
			ConfigurationElementModel currentConfigElement = (ConfigurationElementModel) objectStack.pop();
			Object parent = objectStack.peek();
			currentConfigElement.setParent(parent);
			if (((Integer) stateStack.peek()).intValue() == PLUGIN_EXTENSION_STATE) {
				// Want to add this configuration element to the subelements of an extension
				ConfigurationElementModel[] oldValues = (ConfigurationElementModel[]) ((ExtensionModel) parent).getSubElements();
				int size = (oldValues == null) ? 0 : oldValues.length;
				ConfigurationElementModel[] newValues = new ConfigurationElementModel[size + 1];
				for (int i = 0; i < size; i++) {
					newValues[i] = oldValues[i];
				}
				newValues[size] = currentConfigElement;
				((ExtensionModel) parent).setSubElements(newValues);
			} else {
				ConfigurationElementModel[] oldValues = (ConfigurationElementModel[]) ((ConfigurationElementModel) parent).getSubElements();
				int size = (oldValues == null) ? 0 : oldValues.length;
				ConfigurationElementModel[] newValues = new ConfigurationElementModel[size + 1];
				for (int i = 0; i < size; i++) {
					newValues[i] = oldValues[i];
				}
				newValues[size] = currentConfigElement;
				((ConfigurationElementModel) parent).setSubElements(newValues);
			}
			break;
		case DESCRIPTION_STATE:
			if (elementName.equals(COMPONENT_DESCRIPTION) || elementName.equals(CONFIGURATION_DESCRIPTION))
				stateStack.pop();
			break;
		case URL_STATE:
			if (elementName.equals(COMPONENT_URL) || elementName.equals(CONFIGURATION_URL)) 
				stateStack.pop();
			break;
	}
}
public void error(SAXParseException ex) {
	logStatus(ex);
}
public void fatalError(SAXParseException ex) throws SAXException {
	logStatus(ex);
	throw ex;
}

public void handleComponentState(String elementName, Attributes attributes) {

	if (elementName.equals(COMPONENT_DESCRIPTION)) {
		stateStack.push(new Integer(DESCRIPTION_STATE));
		return;
	}
	if (elementName.equals(COMPONENT_URL)) {
		stateStack.push(new Integer(URL_STATE));
		return;
	}
	if (elementName.equals(COMPONENT_PLUGIN)) {
		parseComponentPluginAttributes(attributes);
		return;
	}
	if (elementName.equals(COMPONENT_FRAGMENT)) {
		parseComponentFragmentAttributes(attributes);
		return;
	}
	// If we get to this point, the element name is one we don't currently accept.
	// Set the state to indicate that this element will be ignored
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", COMPONENT, elementName));
}

public void handleConfigurationState(String elementName, Attributes attributes) {

	if (elementName.equals(CONFIGURATION_DESCRIPTION)) {
		stateStack.push(new Integer(DESCRIPTION_STATE));
		return;
	}
	if (elementName.equals(CONFIGURATION_URL)) {
		stateStack.push(new Integer(URL_STATE));
		return;
	}
	if (elementName.equals(CONFIGURATION_COMPONENT)) {
		parseComponentAttributes(attributes);
		ComponentModel component = (ComponentModel)objectStack.pop();
		ConfigurationModel config = (ConfigurationModel)objectStack.peek();
		Object components = addObject(component, config.getComponents());
		config.setComponents((ComponentModel[])components);
		return;
	}
	// If we get to this point, the element name is one we don't currently accept.
	// Set the state to indicate that this element will be ignored
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", CONFIGURATION, elementName));
}


public void handleDescriptionState(String elementName, Attributes attributes) {

	// We ignore all elements (if there are any)
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", CONFIGURATION_DESCRIPTION, elementName));
}

public void handleExtensionPointState(String elementName, Attributes attributes) {

	// We ignore all elements under extension points (if there are any)
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", EXTENSION_POINT, elementName));
}
public void handleExtensionState(String elementName, Attributes attributes) {

	// You need to change the state here even though we will be executing the same
	// code for ExtensionState and ConfigurationElementState.  We ignore the name
	// of the element for ConfigurationElements.  When we are wrapping up, we will
	// want to add each configuration element object to the subElements vector of
	// its parent configuration element object.  However, the first configuration
	// element object we created (the last one we pop off the stack) will need to
	// be added to a vector in the extension object called _configuration.
	stateStack.push(new Integer(CONFIGURATION_ELEMENT_STATE));

	// create a new Configuration Element and push it onto the object stack
	ConfigurationElementModel currentConfigurationElement = factory.createConfigurationElement();
	objectStack.push(currentConfigurationElement);
	currentConfigurationElement.setName(elementName);

	// Processing the attributes of a configuration element involves creating
	// a new configuration property for each attribute and populating the configuration
	// property with the name/value pair of the attribute.  Note there will be one
	// configuration property for each attribute
	parseConfigurationElementAttributes(attributes);
}
public void handleInitialState(String elementName, Attributes attributes) {
	if (elementName.equals(PLUGIN)) {
		stateStack.push(new Integer(PLUGIN_STATE));
		parsePluginAttributes(attributes);
	} else
		if (elementName.equals(FRAGMENT)) {
			stateStack.push(new Integer(FRAGMENT_STATE));
			parseFragmentAttributes(attributes);
		} else
			if (elementName.equals(COMPONENT)) {
				stateStack.push(new Integer(COMPONENT_STATE));
				parseComponentAttributes(attributes);
			} else
				if (elementName.equals(CONFIGURATION)) {
					stateStack.push(new Integer(CONFIGURATION_STATE));
					parseConfigurationAttributes(attributes);
				} else {
					stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
					internalError(Policy.bind("parse.unknownTopElement", elementName));
				}
}
public void handleLibraryExportState(String elementName, Attributes attributes) {

	// All elements ignored.
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", LIBRARY_EXPORT, elementName));
}
public void handleLibraryState(String elementName, Attributes attributes) {
	// The only valid element at this stage is a export
	if (!elementName.equals(LIBRARY_EXPORT)) {
		stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
		internalError(Policy.bind("parse.unknownElement", LIBRARY, elementName));
		return;
	}

	// Change State
	stateStack.push(new Integer(LIBRARY_EXPORT_STATE));
	// The top element on the stack much be a library element
	LibraryModel currentLib = (LibraryModel) objectStack.peek();

	if (attributes == null)
		return;

	String maskValue = null;

	// Process Attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(LIBRARY_EXPORT_MASK))
			maskValue = attrValue;
		else 
			internalError(Policy.bind("parse.unknownAttribute", LIBRARY, attrName));
	}

	// set up mask tables
	// pop off the library - already in currentLib
	objectStack.pop();
	Vector exportMask = (Vector)objectStack.peek();
	// push library back on
	objectStack.push(currentLib);
	if ((maskValue != null) && (!exportMask.contains(maskValue)))
		exportMask.addElement(maskValue);
}
public void handlePluginState(String elementName, Attributes attributes) {

	if (elementName.equals(RUNTIME)) {
		// We should only have one Runtime element in a plugin or fragment
		Object whatIsIt = objectStack.peek();
		if ( ( (whatIsIt instanceof PluginDescriptorModel) && (((PluginDescriptorModel)objectStack.peek()).getRuntime() != null) ) ||
		     ( (whatIsIt instanceof PluginFragmentModel) && (((PluginFragmentModel)objectStack.peek()).getRuntime() != null) ) ) {
			// This is at least the 2nd Runtime element we have
			// hit.  Ignore it and give an error.
			stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
			return;
		}
		stateStack.push(new Integer(PLUGIN_RUNTIME_STATE));
		// Push a new vector to hold all the library entries
		objectStack.push(new Vector());
		return;
	}
	if (elementName.equals(PLUGIN_REQUIRES)) {
		stateStack.push(new Integer(PLUGIN_REQUIRES_STATE));
		// Push a new vector to hold all the prerequisites
		objectStack.push(new Vector());
		parseRequiresAttributes(attributes);
		return;
	}
	if (elementName.equals(EXTENSION_POINT)) {
		stateStack.push(new Integer(PLUGIN_EXTENSION_POINT_STATE));
		parseExtensionPointAttributes(attributes);
		return;
	}
	if (elementName.equals(EXTENSION)) {
		stateStack.push(new Integer(PLUGIN_EXTENSION_STATE));
		parseExtensionAttributes(attributes);
		return;
	}

	// If we get to this point, the element name is one we don't currently accept.
	// Set the state to indicate that this element will be ignored
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", PLUGIN + " / " + FRAGMENT, elementName));
}
public void handleRequiresImportState(String elementName, Attributes attributes) {

	// All elements ignored.
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", PLUGIN_REQUIRES_IMPORT, elementName));
}
public void handleRequiresState(String elementName, Attributes attributes) {

	if (elementName.equals(PLUGIN_REQUIRES_IMPORT)) {
		parsePluginRequiresImport(attributes);
		return;
	}
	// If we get to this point, the element name is one we don't currently accept.
	// Set the state to indicate that this element will be ignored
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", PLUGIN_REQUIRES, elementName));
}
public void handleRuntimeState(String elementName, Attributes attributes) {

	if (elementName.equals(LIBRARY)) {
		// Change State
		stateStack.push(new Integer(RUNTIME_LIBRARY_STATE));
		// Process library attributes
		parseLibraryAttributes(attributes);
		return;
	}
	// If we get to this point, the element name is one we don't currently accept.
	// Set the state to indicate that this element will be ignored
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
	internalError(Policy.bind("parse.unknownElement", RUNTIME, elementName));
}

private Object addObject(Object newElement, Object[] container) {
	Object[] result = new ComponentModel[container == null ? 1 : container.length + 1];
	if (container != null) 
		System.arraycopy(container, 0, result, 0, container.length);
	result[result.length - 1] = newElement;
	return result;
}
private URLModel[] addURLElement(URLModel newElement, URLModel[] container) {
	URLModel[] result = new URLModel[container == null ? 1 : container.length + 1];
	if (container != null) 
		System.arraycopy(container, 0, result, 0, container.length);
	result[result.length - 1] = newElement;
	return result;
}
private PluginDescriptorModel[] addPluginDescriptorElement(PluginDescriptorModel newElement, PluginDescriptorModel[] container) {
	PluginDescriptorModel[] result = new PluginDescriptorModel[container == null ? 1 : container.length + 1];
	if (container != null) 
		System.arraycopy(container, 0, result, 0, container.length);
	result[result.length - 1] = newElement;
	return result;
}
private PluginFragmentModel[] addPluginFragmentElement(PluginFragmentModel newElement, PluginFragmentModel[] container) {
	PluginFragmentModel[] result = new PluginFragmentModel[container == null ? 1 : container.length + 1];
	if (container != null) 
		System.arraycopy(container, 0, result, 0, container.length);
	result[result.length - 1] = newElement;
	return result;
}
public void handleURLState(String elementName, Attributes attributes) {
	URLModel url = null;
	InstallModel model = (InstallModel)objectStack.peek();
	if (elementName.equals(URL_UPDATE)) {
		url = parseURLAttributes(attributes);
		model.setUpdates((URLModel [])addURLElement(url, model.getUpdates()));
		return; 
	} else
		if (elementName.equals(URL_DISCOVERY)) {
			url = parseURLAttributes(attributes);
			model.setDiscoveries((URLModel [])addURLElement(url, model.getDiscoveries()));
			return; 
		}
	// We ignore all elements (if there are any)
	stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
}
public void ignoreableWhitespace(char[] ch, int start, int length) {
}
private void logStatus(SAXParseException ex) {
	String name = ex.getSystemId();
	if (name == null)
		name = "";
	else
		name = name.substring(1 + name.lastIndexOf("/"));

	String msg;
	if (name.equals(""))
		msg = Policy.bind("parse.error", ex.getMessage());
	else
		msg = Policy.bind("parse.errorNameLineColumn", new String[] { name, Integer.toString(ex.getLineNumber()), Integer.toString(ex.getColumnNumber()), ex.getMessage()});
	factory.error(new Status(IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, msg, ex));
}
synchronized public InstallModel parseInstall(InputSource in) throws Exception {
	parser.parse(in);
	return (InstallModel) objectStack.pop();
}

synchronized public PluginModel parsePlugin(InputSource in) throws Exception {
	parser.parse(in);
	return (PluginModel) objectStack.pop();
}

public void parseComponentAttributes(Attributes attributes) {

	ComponentModel current = factory.createComponentModel();
	objectStack.push(current);

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(COMPONENT_ID))
			current.setId(attrValue);
		else
			if (attrName.equals(COMPONENT_LABEL))
				current.setName(attrValue);
			else
				if (attrName.equals(COMPONENT_VERSION))
					current.setVersion(attrValue);
				else
					if (attrName.equals(COMPONENT_PROVIDER))
						current.setProviderName(attrValue);
					else
						internalError(Policy.bind("parse.unknownAttribute", COMPONENT, attrName));
	}
}

public void parseComponentFragmentAttributes(Attributes attributes) {

	PluginFragmentModel current = factory.createPluginFragment();

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(COMPONENT_FRAGMENT_ID))
			current.setId(attrValue);
		else
			if (attrName.equals(COMPONENT_FRAGMENT_LABEL))
				current.setName(attrValue);
			else
				if (attrName.equals(COMPONENT_FRAGMENT_VERSION))
					current.setVersion(attrValue);
				else
					internalError(Policy.bind("parse.unknownAttribute", COMPONENT_FRAGMENT, attrName));
	}
	
	ComponentModel componentModel = (ComponentModel)objectStack.peek();
	PluginFragmentModel fragments[] = componentModel.getFragments();
	fragments = (PluginFragmentModel [])addPluginFragmentElement(current,fragments);
	componentModel.setFragments(fragments);
}

public void parseComponentPluginAttributes(Attributes attributes) {

	PluginDescriptorModel current = factory.createPluginDescriptor();

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(COMPONENT_PLUGIN_ID))
			current.setId(attrValue);
		else
			if (attrName.equals(COMPONENT_PLUGIN_LABEL))
				current.setName(attrValue);
			else
				if (attrName.equals(COMPONENT_PLUGIN_VERSION))
					current.setVersion(attrValue);
				else
					internalError(Policy.bind("parse.unknownAttribute", COMPONENT_PLUGIN, attrName));
	}
	
	ComponentModel componentModel = (ComponentModel)objectStack.peek();
	PluginDescriptorModel plugins[] = componentModel.getPlugins();
	plugins = (PluginDescriptorModel [])addPluginDescriptorElement(current,plugins);
	componentModel.setPlugins(plugins);
}

public void parseConfigurationAttributes(Attributes attributes) {

	ConfigurationModel current = factory.createConfiguration();
	objectStack.push(current);

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(CONFIGURATION_ID))
			current.setId(attrValue);
		else
			if (attrName.equals(CONFIGURATION_LABEL))
				current.setName(attrValue);
			else
				if (attrName.equals(CONFIGURATION_VERSION))
					current.setVersion(attrValue);
				else
					if (attrName.equals(CONFIGURATION_PROVIDER))
						current.setProviderName(attrValue);
					else
						if (attrName.equals(CONFIGURATION_APPLICATION))
							current.setApplication(attrValue);
					else
						internalError(Policy.bind("parse.unknownAttribute", CONFIGURATION, attrName));
	}
}

public void parseConfigurationElementAttributes(Attributes attributes) {

	ConfigurationElementModel parentConfigurationElement = (ConfigurationElementModel) objectStack.peek();
	Vector propVector = null;

	// process attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	if (len == 0)
		return;
	propVector = new Vector();

	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i);

		ConfigurationPropertyModel currentConfigurationProperty = factory.createConfigurationProperty();
		currentConfigurationProperty.setName(attrName);
		currentConfigurationProperty.setValue(attrValue);
		propVector.addElement(currentConfigurationProperty);
	}
	parentConfigurationElement.setProperties((ConfigurationPropertyModel[]) propVector.toArray(new ConfigurationPropertyModel[propVector.size()]));
	propVector = null;
}
public void parseExtensionAttributes(Attributes attributes) {

	PluginModel parent = (PluginModel) objectStack.peek();
	ExtensionModel currentExtension = factory.createExtension();
	objectStack.push(currentExtension);

	// Process Attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(EXTENSION_NAME))
			currentExtension.setName(attrValue);
		else
			if (attrName.equals(EXTENSION_ID))
				currentExtension.setId(attrValue);
			else
				if (attrName.equals(EXTENSION_TARGET)) {
					// check if point is specified as a simple or qualified name
					String targetName;
					if (attrValue.lastIndexOf('.') == -1) {
						String baseId = parent instanceof PluginDescriptorModel ? parent.getId() : ((PluginFragmentModel) parent).getPlugin();
						targetName = baseId + "." + attrValue;
					} else
						targetName = attrValue;
					currentExtension.setExtensionPoint(targetName);
				}
				else
					internalError(Policy.bind("parse.unknownAttribute", EXTENSION, attrName));
	}
}
public void parseExtensionPointAttributes(Attributes attributes) {

	ExtensionPointModel currentExtPoint = factory.createExtensionPoint();

	// Process Attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(EXTENSION_POINT_NAME))
			currentExtPoint.setName(attrValue);
		else
			if (attrName.equals(EXTENSION_POINT_ID))
				currentExtPoint.setId(attrValue);
			else
				if (attrName.equals(EXTENSION_POINT_SCHEMA))
					currentExtPoint.setSchema(attrValue);
				else
					internalError(Policy.bind("parse.unknownAttribute", EXTENSION_POINT, attrName));
	}
	// currentExtPoint contains a pointer to the parent plugin descriptor.
	PluginModel root = (PluginModel) objectStack.peek();
	currentExtPoint.setParent(root);

	// Now populate the the vector just below us on the objectStack with this extension point
	scratchVectors[EXTENSION_POINT_INDEX].addElement(currentExtPoint);
}

public void parseFragmentAttributes(Attributes attributes) {
	PluginFragmentModel current = factory.createPluginFragment();
	objectStack.push(current);

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(FRAGMENT_ID))
			current.setId(attrValue);
		else if (attrName.equals(FRAGMENT_NAME))
			current.setName(attrValue);
		else if (attrName.equals(FRAGMENT_VERSION))
			current.setVersion(attrValue);
		else if (attrName.equals(FRAGMENT_PROVIDER))
			current.setProviderName(attrValue);
		else if (attrName.equals(FRAGMENT_PLUGIN_ID))
			current.setPlugin(attrValue);
		else if (attrName.equals(FRAGMENT_PLUGIN_VERSION))
			current.setPluginVersion(attrValue);
		else if (attrName.equals(FRAGMENT_PLUGIN_MATCH)) {
			if (FRAGMENT_PLUGIN_MATCH_PERFECT.equals(attrValue))
				current.setMatch(PluginFragmentModel.FRAGMENT_MATCH_PERFECT);
			else if (FRAGMENT_PLUGIN_MATCH_EQUIVALENT.equals(attrValue))
				current.setMatch(PluginFragmentModel.FRAGMENT_MATCH_EQUIVALENT);
			else if (FRAGMENT_PLUGIN_MATCH_COMPATIBLE.equals(attrValue))
				current.setMatch(PluginFragmentModel.FRAGMENT_MATCH_COMPATIBLE);
			else if (FRAGMENT_PLUGIN_MATCH_GREATER_OR_EQUAL.equals(attrValue))
				current.setMatch(PluginFragmentModel.FRAGMENT_MATCH_GREATER_OR_EQUAL);
			else
				internalError(Policy.bind("parse.validMatch", attrValue));
		}
		else
			internalError(Policy.bind("parse.unknownAttribute", FRAGMENT, attrName));
	}
}

public void parseLibraryAttributes(Attributes attributes) {
	// Push a vector to hold the export mask 
	objectStack.push (new Vector());
	LibraryModel current = factory.createLibrary();
	objectStack.push(current);
	
	// Now the objectStack should contain the following:
	//	plugin descriptor or fragment (bottom of the stack)
	//	vector to hold all the library entries
	//  vector to hold the export mask for this library entry
	//  this library entry (top of the stack)

	// process attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(LIBRARY_NAME))
			current.setName(attrValue);
		else
			if (attrName.equals(LIBRARY_TYPE)) {
				attrValue = attrValue.toLowerCase();
				if (attrValue.equals(LibraryModel.CODE) || 
				     attrValue.equals(LibraryModel.RESOURCE))
					current.setType(attrValue.toLowerCase());
				else
					internalError(Policy.bind("parse.unknownLibraryType", attrValue, current.getName()));
			} else
				internalError(Policy.bind("parse.unknownAttribute", LIBRARY, attrName));
	}
}
public void parsePluginAttributes(Attributes attributes) {

	PluginDescriptorModel current = factory.createPluginDescriptor();
	objectStack.push(current);

	// process attributes
	int len = attributes.getLength();
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(PLUGIN_ID))
			current.setId(attrValue);
		else
			if (attrName.equals(PLUGIN_NAME))
				current.setName(attrValue);
			else
				if (attrName.equals(PLUGIN_VERSION))
					current.setVersion(attrValue);
				else
					if (attrName.equals(PLUGIN_VENDOR) || (attrName.equals(PLUGIN_PROVIDER)))
						current.setProviderName(attrValue);
					else
						if (attrName.equals(PLUGIN_CLASS))
							current.setPluginClass(attrValue);
						else
							internalError(Policy.bind("parse.unknownAttribute", PLUGIN, attrName));
	}
}

public void parsePluginRequiresImport(Attributes attributes) {
	PluginPrerequisiteModel current = factory.createPluginPrerequisite();

	// process attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(PLUGIN_REQUIRES_PLUGIN))
			current.setPlugin(attrValue);
		else
			if (attrName.equals(PLUGIN_REQUIRES_PLUGIN_VERSION))
				current.setVersion(attrValue);
			else
				if (attrName.equals(PLUGIN_REQUIRES_OPTIONAL))
					current.setOptional("true".equalsIgnoreCase(attrValue));
				else
					if (attrName.equals(PLUGIN_REQUIRES_MATCH)) {
						if (PLUGIN_REQUIRES_MATCH_PERFECT.equals(attrValue))
							current.setMatchByte(PluginPrerequisiteModel.PREREQ_MATCH_PERFECT);
						else if ((PLUGIN_REQUIRES_MATCH_EQUIVALENT.equals(attrValue)) ||
						          (PLUGIN_REQUIRES_MATCH_EXACT.equals(attrValue)))
							current.setMatchByte(PluginPrerequisiteModel.PREREQ_MATCH_EQUIVALENT);
						else if (PLUGIN_REQUIRES_MATCH_COMPATIBLE.equals(attrValue))
							current.setMatchByte(PluginPrerequisiteModel.PREREQ_MATCH_COMPATIBLE);
						else if (PLUGIN_REQUIRES_MATCH_GREATER_OR_EQUAL.equals(attrValue))
							current.setMatchByte(PluginPrerequisiteModel.PREREQ_MATCH_GREATER_OR_EQUAL);
						else
							internalError(Policy.bind("parse.validMatch", attrValue));
					} else
						if (attrName.equals(PLUGIN_REQUIRES_EXPORT)) {
							if (TRUE.equals(attrValue))
								current.setExport(true);
							else
								if (FALSE.equals(attrValue))
									current.setExport(false);
								else
									internalError(Policy.bind("parse.validExport", attrValue));
						} else
							internalError(Policy.bind("parse.unknownAttribute", PLUGIN_REQUIRES_IMPORT, attrName));

	}
	// Populate the vector of prerequisites with this new element
	((Vector)objectStack.peek()).addElement(current);
}
public void parseRequiresAttributes(Attributes attributes) {
}

public URLModel parseURLAttributes(Attributes attributes) {
	URLModel current = factory.createURL();

	// process attributes
	int len = (attributes != null) ? attributes.getLength() : 0;
	for (int i = 0; i < len; i++) {
		String attrName = attributes.getLocalName(i);
		String attrValue = attributes.getValue(i).trim();

		if (attrName.equals(URL_URL))
			current.setURL(attrValue);
		else
			if (attrName.equals(URL_LABEL))
				current.setName(attrValue);
	}
	return current;
}

static String replace(String s, String from, String to) {
	String str = s;
	int fromLen = from.length();
	int toLen = to.length();
	int ix = str.indexOf(from);
	while (ix != -1) {
		str = str.substring(0, ix) + to + str.substring(ix + fromLen);
		ix = str.indexOf(from, ix + toLen);
	}
	return str;
}
public void startDocument() {
	stateStack.push(new Integer(INITIAL_STATE));
	for (int i = 0; i <= LAST_INDEX; i++) {
		scratchVectors[i] = new Vector();
	}
}
public void startElement(String uri, String elementName, String qName, Attributes attributes) {
	switch (((Integer) stateStack.peek()).intValue()) {
		case INITIAL_STATE :
			handleInitialState(elementName, attributes);
			break;
		case FRAGMENT_STATE :
			handlePluginState(elementName, attributes);
			break;
		case PLUGIN_STATE :
			handlePluginState(elementName, attributes);
			break;
		case PLUGIN_RUNTIME_STATE :
			handleRuntimeState(elementName, attributes);
			break;
		case PLUGIN_REQUIRES_STATE :
			handleRequiresState(elementName, attributes);
			break;
		case PLUGIN_EXTENSION_POINT_STATE :
			handleExtensionPointState(elementName, attributes);
			break;
		case PLUGIN_EXTENSION_STATE :
		case CONFIGURATION_ELEMENT_STATE :
			handleExtensionState(elementName, attributes);
			break;
		case RUNTIME_LIBRARY_STATE :
			handleLibraryState(elementName, attributes);
			break;
		case LIBRARY_EXPORT_STATE :
			handleLibraryExportState(elementName, attributes);
			break;
		case PLUGIN_REQUIRES_IMPORT_STATE :
			handleRequiresImportState(elementName, attributes);
			break;
		case COMPONENT_STATE:
			handleComponentState(elementName, attributes);
			break;
		case CONFIGURATION_STATE :
			handleConfigurationState(elementName, attributes);
			break;
		case DESCRIPTION_STATE :
			handleDescriptionState(elementName, attributes);
			break;
		case URL_STATE :
			handleURLState(elementName, attributes);
			break;
		default :
			stateStack.push(new Integer(IGNORED_ELEMENT_STATE));
			internalError (Policy.bind("parse.unknownTopElement", elementName));
	}
}
public void warning(SAXParseException ex) {
	logStatus(ex);
}
private void internalError(String message) {
	factory.error(new Status(IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, message, null));
}
}
