/*******************************************************************************
 * Copyright (c) 2000,2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 * IBM - Initial API and implementation
 ******************************************************************************/
package org.eclipse.core.internal.runtime;

import java.io.*;
import java.util.ArrayList;
import java.util.StringTokenizer;
import org.eclipse.core.internal.runtime.*;
import org.eclipse.core.runtime.*;

/**
 * Reads a structured log from disk and reconstructs status and exception objects.
 * General strategy: log entries that are malformed in any way are skipped, and an extra
 * status is returned mentioned that there were problems.
 */
public class PlatformLogReader {
	private ArrayList list = null;
	private String currentLine = "";
	private BufferedReader reader;

	// constants copied from the PlatformLogWriter (since they are private
	// to that class and this class should be used only in test suites)
	private static final String KEYWORD_SESSION = "!SESSION";
	private static final String KEYWORD_ENTRY = "!ENTRY";
	private static final String KEYWORD_SUBENTRY = "!SUBENTRY";
	private static final String KEYWORD_MESSAGE = "!MESSAGE";
	private static final String KEYWORD_STACK = "!STACK";

	private static final int NULL = -2;
	private static final int SESSION = 1;
	private static final int ENTRY = 2;
	private static final int SUBENTRY = 4;
	private static final int MESSAGE = 8;
	private static final int STACK = 16;
	private static final int UNKNOWN = 32;

/**
 * Given a stack trace without carriage returns, returns a pretty-printed stack.
 */
protected String formatStack(String stack) {
	StringWriter sWriter = new StringWriter();
	PrintWriter writer = new PrintWriter(sWriter);
	StringTokenizer tokenizer = new StringTokenizer(stack);
	//first entry has no indentation
	if (tokenizer.hasMoreTokens())
		writer.print(tokenizer.nextToken());
	while (tokenizer.hasMoreTokens()) {
		String next = tokenizer.nextToken();
		if (next != null && next.length() > 0) {
			if (next.equals("at")) {
				writer.println();
				writer.print('\t');
				writer.print(next);
			} else {
				writer.print(' ');
				writer.print(next);
			}
		}
	}
	writer.flush();
	writer.close();
	return sWriter.toString();
}
protected void log(Exception ex) {
	String msg = Policy.bind("meta.exceptionParsingLog", ex.getMessage());
	list.add(new Status(IStatus.WARNING, Platform.PI_RUNTIME, Platform.PARSE_PROBLEM, msg, ex));
}
protected Throwable readException(String message) throws IOException {
	if (currentLine == null || getLineType() != STACK)
		return null;
	StringBuffer buffer = new StringBuffer();
	buffer.append(currentLine.substring(KEYWORD_STACK.length()+1, currentLine.length()));
	currentLine = reader.readLine();
	buffer.append(readText());
	String stack = buffer.toString();
	return new FakeException(null, formatStack(stack));
}
/**
 * Reads the given log file and returns the contained status objects. 
 * If the log file could not be read, a status object indicating this fact
 * is returned.
 */
public synchronized IStatus[] readLogFile(String path) {
	list = new ArrayList();
	InputStream input = null;
	try {
		input = new FileInputStream(path);
		reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));//$NON-NLS-1$
		currentLine = reader.readLine();
		while (currentLine != null) {
			switch (getLineType()) {
				case ENTRY:
					IStatus status = readEntry();
					if (status != null)
						list.add(status);
					break;
				case SUBENTRY:
				case MESSAGE:
				case STACK:
				case SESSION:
				case UNKNOWN:
					currentLine = reader.readLine();
					break;
			}
		}
	} catch (IOException e) {
		log(e);
	} finally {
		try {
			if (input != null)
				input.close();
		} catch (IOException e) {
			log(e);
		}
	}
	return (IStatus[]) list.toArray(new IStatus[list.size()]);
}
protected int getLineType() {
	if (currentLine == null) 
		return NULL;
	StringTokenizer tokenizer = new StringTokenizer(currentLine);
	String token = tokenizer.nextToken();
	if (token.equals(KEYWORD_SESSION))
		return SESSION;
	if (token.equals(KEYWORD_ENTRY))
		return ENTRY;
	if (token.equals(KEYWORD_SUBENTRY))
		return SUBENTRY;
	if (token.equals(KEYWORD_MESSAGE))
		return MESSAGE;
	if (token.equals(KEYWORD_STACK))
		return STACK;
	return UNKNOWN;
}

/**
 * A reconsituted exception that only contains a stack trace and a message.
 */
class FakeException extends Throwable {
	private String message;
	private String stackTrace;
	FakeException(String msg, String stack) {
		this.message = msg;
		this.stackTrace = stack;
	}
	public String getMessage() {
		return message;
	}
	public void printStackTrace() {
		printStackTrace(System.out);
	}
	public void printStackTrace(PrintWriter writer) {
		writer.println(stackTrace);
	}
	public void printStackTrace(PrintStream stream) {
		stream.println(stackTrace);
	}		
}
protected IStatus readEntry() throws IOException {
	if (currentLine == null || getLineType() != ENTRY)
		return null;
	StringTokenizer tokens = new StringTokenizer(currentLine);
	// skip over the ENTRY keyword
	tokens.nextToken();
	String pluginID = tokens.nextToken();
	int severity = Integer.parseInt(tokens.nextToken());
	int code = Integer.parseInt(tokens.nextToken());
	// ignore the rest of the line since its the date
	currentLine = reader.readLine();
	String message = readMessage();
	Throwable exception = readException(message);
	if (currentLine == null || getLineType() != SUBENTRY)
		return new Status(severity, pluginID, code, message, exception);
	MultiStatus parent = new MultiStatus(pluginID, code, message, exception);
	readSubEntries(parent);
	return parent;
}
protected void readSubEntries(MultiStatus parent) throws IOException {
	while (getLineType() == SUBENTRY) {
		StringTokenizer tokens = new StringTokenizer(currentLine);
		// skip over the subentry keyword
		tokens.nextToken();
		int currentDepth = Integer.parseInt(tokens.nextToken());
		String pluginID = tokens.nextToken();
		int severity = Integer.parseInt(tokens.nextToken());
		int code = Integer.parseInt(tokens.nextToken());
		// ignore the rest of the line since its the date
		currentLine = reader.readLine();
		String message = readMessage();
		Throwable exception = readException(message);
	
		IStatus current = new Status(severity, pluginID, code, message, exception);
		if (currentLine == null || getLineType() != SUBENTRY) {
			parent.add(current);
			return;
		}

		tokens = new StringTokenizer(currentLine);
		tokens.nextToken();
		int depth = Integer.parseInt(tokens.nextToken());
		if (currentDepth == depth) {
			// next sub-entry is a sibling
			parent.add(current);
		} else if (currentDepth == (depth - 1)) {
			// next sub-entry is a child
			current = new MultiStatus(pluginID, code, message, exception);
			readSubEntries((MultiStatus) current);
			parent.add(current);
		} else {
			parent.add(current);
			return;
		}
	}
}
protected int readDepth() throws IOException {
	StringTokenizer tokens = new StringTokenizer(currentLine);
	// skip the keyword
	tokens.nextToken();
	return Integer.parseInt(tokens.nextToken());
}
protected String readMessage() throws IOException {
	if (currentLine == null || getLineType() != MESSAGE)
		return "";
	StringBuffer buffer = new StringBuffer();
	buffer.append(currentLine.substring(KEYWORD_MESSAGE.length()+1, currentLine.length()));
	currentLine = reader.readLine();
	buffer.append(readText());
	return buffer.toString();
}
protected String readText() throws IOException {
	StringBuffer buffer = new StringBuffer();
	if (currentLine == null || getLineType() != UNKNOWN)
		return "";
	else buffer.append(currentLine);
	boolean done = false;
	while (!done) {
		currentLine = reader.readLine();
		if (currentLine == null) {
			done = true;
			continue;
		}
		if (getLineType() == UNKNOWN)
			buffer.append(currentLine);
		else
			done = true;
	}
	return buffer.toString();
}
}
