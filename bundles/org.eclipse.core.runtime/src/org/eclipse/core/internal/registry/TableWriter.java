/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.registry;

import java.io.*;
import org.eclipse.core.internal.runtime.InternalPlatform;
import org.eclipse.core.runtime.*;

public class TableWriter {
	private static final byte fileError = 0;

	static File mainDataFile;
	static File extraDataFile;
	static File tableFile;
	static File namespaceFile;

	static void setMainDataFile(File main) {
		mainDataFile = main;
	}

	static void setExtraDataFile(File extra) {
		extraDataFile = extra;
	}

	static void setTableFile(File table) {
		tableFile = table;
	}

	static void setNamespaceFile(File namespace) {
		namespaceFile = namespace;
	}

	DataOutputStream mainOutput;
	DataOutputStream extraOutput;

	private HashtableOfInt offsets;

	private int getExtraDataPosition() {
		return extraOutput.size();
	}

	public boolean saveCache(RegistryObjectManager objectManager, long timestamp) {
		try {
			openFiles();
			try {
				saveExtensionRegistry(objectManager, timestamp);
				closeFiles();
			} catch (IOException e1) {
				InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error writing the registry cache", e1));
				return false;
			}
		} catch (FileNotFoundException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error writing the registry cache", e));
			return false;
		}
		return true;
	}

	private void openFiles() throws FileNotFoundException {
		mainOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(mainDataFile)));
		extraOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(extraDataFile)));
	}

	private void closeFiles() {
		try {
			mainOutput.close();
			extraOutput.close();
		} catch (IOException e) {
			InternalPlatform.getDefault().log(new Status(IStatus.ERROR, Platform.PI_RUNTIME, fileError, "Error closing the registry cache", e));
			e.printStackTrace();
		}
	}

	private void saveExtensionRegistry(RegistryObjectManager objectManager, long timestamp) throws IOException {
		ExtensionPointHandle[] points = objectManager.getExtensionPointsHandles();
		offsets = new HashtableOfInt(objectManager.nextId);
		for (int i = 0; i < points.length; i++) {
			saveExtensionPoint(points[i]);
		}
		saveTables(objectManager, timestamp);

		saveNamespaces(objectManager.newNamespaces);
	}

	private void saveNamespaces(KeyedHashSet newNamespaces) throws IOException {
		DataOutputStream outputNamespace = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(namespaceFile)));
		KeyedElement[] elements = newNamespaces.elements();
		outputNamespace.writeInt(elements.length);
		for (int i = 0; i < elements.length; i++) {
			Contribution elt = (Contribution) elements[i];
			outputNamespace.writeLong(elt.getContributingBundle().getBundleId());
			saveArray(elt.getRawChildren(), outputNamespace);
		}
		outputNamespace.close();
	}

	private void saveTables(RegistryObjectManager objectManager, long registryTimeStamp) throws IOException {
		DataOutputStream outputTable = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tableFile)));
		writeCacheHeader(outputTable, registryTimeStamp);
		outputTable.writeInt(objectManager.nextId);
		offsets.save(outputTable);
		objectManager.extensionPoints.save(outputTable);
		outputTable.close();
	}

	private void writeCacheHeader(DataOutputStream output, long registryTimeStamp) throws IOException {
		output.writeInt(TableReader.CACHE_VERSION);
		output.writeLong(InternalPlatform.getDefault().getStateTimeStamp());
		output.writeLong(registryTimeStamp);
		InternalPlatform info = InternalPlatform.getDefault();
		output.writeUTF(info.getOS());
		output.writeUTF(info.getWS());
		output.writeUTF(info.getNL());
	}

	private void saveArray(int[] array, DataOutputStream out) throws IOException {
		if (array == null) {
			out.writeInt(0);
			return;
		}
		out.writeInt(array.length);
		for (int i = 0; i < array.length; i++) {
			out.writeInt(array[i]);
		}
	}

	private void saveExtensionPoint(ExtensionPointHandle xpt) throws IOException {
		//save the file position
		offsets.put(xpt.getId(), mainOutput.size());

		//save the extensionPoint
		mainOutput.writeInt(xpt.getId());
		saveArray(xpt.getObject().getRawChildren(), mainOutput);
		mainOutput.writeInt(getExtraDataPosition());
		saveExtensionPointData(xpt);

		saveExtensions(xpt.getExtensions());
	}

	private void saveExtension(ExtensionHandle ext) throws IOException {
		offsets.put(ext.getId(), mainOutput.size());
		mainOutput.writeInt(ext.getId());
		writeStringOrNull(ext.getSimpleIdentifier(), mainOutput);
		writeStringOrNull(ext.getNamespace(), mainOutput);
		saveArray(ext.getObject().getRawChildren(), mainOutput);
		mainOutput.writeInt(getExtraDataPosition());
		saveExtensionData(ext);
	}

	private void writeStringArray(String[] array, DataOutputStream outputStream) throws IOException {
		outputStream.writeInt(array == null ? 0 : array.length);
		for (int i = 0; i < (array == null ? 0 : array.length); i++) {
			writeStringOrNull(array[i], outputStream);
		}
	}

	//Save Configuration elements depth first
	private void saveConfigurationElement(ConfigurationElementHandle element, DataOutputStream outputStream, DataOutputStream extraOutputStream, int depth) throws IOException {
		DataOutputStream currentOutput = outputStream;
		if (depth > 2)
			currentOutput = extraOutputStream;

		offsets.put(element.getId(), currentOutput.size());

		currentOutput.writeInt(element.getId());
		ConfigurationElement actualCe = (ConfigurationElement) element.getObject();
		currentOutput.writeLong(actualCe.getContributingBundle().getBundleId());
		writeStringOrNull(actualCe.getName(), currentOutput);
		currentOutput.writeInt(actualCe.parentId);
		currentOutput.writeByte(actualCe.info);
		currentOutput.writeInt(depth > 1 ? extraOutputStream.size() : -1);
		writeStringArray(actualCe.getPropertiesAndValue(), currentOutput);
		//save the children
		saveArray(actualCe.getRawChildren(), currentOutput);

		ConfigurationElementHandle[] childrenCEs = (ConfigurationElementHandle[]) element.getChildren();
		for (int i = 0; i < childrenCEs.length; i++) {
			saveConfigurationElement(childrenCEs[i], outputStream, extraOutputStream, depth + 1);
		}

	}

	private void saveExtensions(IExtension[] exts) throws IOException {
		for (int i = 0; i < exts.length; i++) {
			saveExtension((ExtensionHandle) exts[i]);
		}

		for (int i = 0; i < exts.length; i++) {
			IConfigurationElement[] ces = exts[i].getConfigurationElements();
			mainOutput.writeInt(ces.length); //this is not mandatory
			for (int j = 0; j < ces.length; j++) {
				saveConfigurationElement((ConfigurationElementHandle) ces[j], mainOutput, extraOutput, 1);
			}
		}
	}

	private void saveExtensionPointData(ExtensionPointHandle xpt) throws IOException {
		writeStringOrNull(xpt.getLabel(), extraOutput);
		writeStringOrNull(xpt.getSchemaReference(), extraOutput);
		writeStringOrNull(xpt.getUniqueIdentifier(), extraOutput);
		writeStringOrNull(xpt.getNamespace(), extraOutput);
		extraOutput.writeLong(((ExtensionPoint) xpt.getObject()).getBundleId());
	}

	private void saveExtensionData(ExtensionHandle extension) throws IOException {
		writeStringOrNull(extension.getLabel(), extraOutput);
		writeStringOrNull(extension.getExtensionPointUniqueIdentifier(), extraOutput);
	}

	private void writeStringOrNull(String string, DataOutputStream out) throws IOException {
		if (string == null)
			out.writeByte(TableReader.NULL);
		else {
			out.writeByte(TableReader.OBJECT);
			out.writeUTF(string);
		}
	}

}
