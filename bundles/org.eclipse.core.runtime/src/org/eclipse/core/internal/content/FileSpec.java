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
package org.eclipse.core.internal.content;

import org.eclipse.core.runtime.content.IContentType;

/**
 * Provides a uniform representation for file specifications, such 
 * as file names, file extensions and regular expressions.
 */
class FileSpec {
	final static int BASIC_TYPE = IContentType.FILE_EXTENSION_SPEC | IContentType.FILE_NAME_SPEC;
	private String text;
	private int type;

	public FileSpec(String text, int type) {
		this.text = text;
		this.type = type;
	}

	public String getText() {
		return text;
	}

	public int getType() {
		return type;
	}

	public int getBasicType() {
		return BASIC_TYPE & type;
	}

	public boolean equals(Object other) {
		if (!(other instanceof FileSpec))
			return false;
		FileSpec otherFileSpec = (FileSpec) other;
		return getBasicType() == otherFileSpec.getBasicType() && text.equalsIgnoreCase(otherFileSpec.text);
	}

	public boolean equals(String text, int basicType) {
		return getBasicType() == basicType && this.text.equalsIgnoreCase(text);
	}

	public int hashCode() {
		return text.hashCode();
	}

	public static String getMappingKeyFor(String fileSpecText) {
		return fileSpecText.toLowerCase();
	}

	public String toString() {
		return getText();
	}
}