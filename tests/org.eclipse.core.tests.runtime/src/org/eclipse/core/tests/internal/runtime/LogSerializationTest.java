/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.internal.runtime;

import java.io.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.runtime.PlatformLogWriter;
import org.eclipse.core.runtime.*;
import org.eclipse.core.tests.runtime.RuntimeTest;

public class LogSerializationTest extends RuntimeTest {
	
	protected File logFile = null;

public LogSerializationTest(String name) {
	super(name);
}
public LogSerializationTest() {
	super("");
}
public static Test suite() {
	return new TestSuite(LogSerializationTest.class);
//	TestSuite suite = new TestSuite();
//	suite.addTest(new LogSerializationTest("testDeepMultiStatus"));
//	return suite;
}
protected void assertEquals(String msg, IStatus[] expected, IStatus[] actual) {
	if (expected == null) {
		assertNull(msg + " expected null but got: " + actual, actual);
		return;
	}
	if (actual == null) {
		assertNull(msg + " expected " + expected + " but got null", expected);
		return;
	}
	assertEquals(msg + " different number of statuses", expected.length, actual.length);
	for (int i = 0, imax = expected.length; i < imax; i++) {
		assertEquals(msg + " differ at status " + i, expected[i], actual[i]);
	}
}
protected void assertEquals(String msg, IStatus expected, IStatus actual) {
	assertEquals(msg + " severity", expected.getSeverity(), actual.getSeverity());
	assertEquals(msg + " plugin-id", expected.getPlugin(), actual.getPlugin());
	assertEquals(msg + " code", expected.getCode(), actual.getCode());
	assertEquals(msg + " message", expected.getMessage(), actual.getMessage());
	assertEquals(msg + " exception", expected.getException(), actual.getException());
	assertEquals(msg + " children", expected.getChildren(), actual.getChildren());
}
protected void assertEquals(String msg, Throwable expected, Throwable actual) {
	if (expected == null) {
		assertNull(msg + " expected null but got: " + actual, actual);
		return;
	}
	if (actual == null) {
		assertNull(msg + " expected " + expected + " but got null", expected);
		return;
	}
	assertEquals(msg + " stack trace", encodeStackTrace(expected),encodeStackTrace(actual));
	assertEquals(msg + " message", expected.getMessage(), actual.getMessage());
}
protected String encodeStackTrace(Throwable t) {
	StringWriter sWriter = new StringWriter();
	PrintWriter pWriter = new PrintWriter(sWriter);
	pWriter.println();
	t.printStackTrace(pWriter);
	pWriter.flush();
	return canonicalizeStackTrace(sWriter.toString());
}
/**
 * Returns the given stack trace in a canonical format in order to make stack
 * trace comparisons easier. The canonical format is: each line is ended by a
 * <code>'\n'</code> character, each line (except the first one) starts with a
 * <code>'\t'</code> character, there are no other occurrences of space
 * characters other than ' ', and there are no consecutive occurrences of new-
 * line or space characters.
 */
protected String canonicalizeStackTrace(String stackTrace) {
	final char NEW_LINE = '\n';
	final char TAB = '\t';
	final char SPACE = ' ';
	final String LINE_SEPARATORS = "\r\n\f";
	final String SPACES = "\t ";
	StringBuffer sb = new StringBuffer(stackTrace.trim());
	sb.append(NEW_LINE);
	char lastChar = 0;
	for (int i = 0; i < sb.length();) {
		// only \n is used as line separator, with no consecutive occurrences
		if (LINE_SEPARATORS.indexOf(sb.charAt(i)) != -1)
			if (LINE_SEPARATORS.indexOf(lastChar) != -1) {
				sb.deleteCharAt(i);
				continue;
			} else
				sb.setCharAt(i, NEW_LINE);
		// each line (except the first one) starts with a tab
		else if (lastChar == NEW_LINE)
			sb.insert(i, TAB);
		// only ' ' is used as space, with no consecutive occurrences				
		else if (SPACES.indexOf(sb.charAt(i)) != -1)
			if (SPACES.indexOf(lastChar) != -1) {
				sb.deleteCharAt(i);
				continue;
			} else
				sb.setCharAt(i, SPACE);
		lastChar = sb.charAt(i);
		i++;
	}
	return sb.toString();
}
protected IStatus[] getInterestingMultiStatuses() {
	IStatus[] interesting = getInterestingStatuses();
	int len = interesting.length;
	IStatus[][] interestingChildren = new IStatus[len][];
	for (int i = 0; i < len; i++) {
		IStatus[] subArray = new IStatus[len];
		System.arraycopy(interesting, 0, subArray, 0, len);
		interestingChildren[i] = subArray;
	}
	int childOff = 0;
	return new IStatus[] {
		new MultiStatus("plugin-id", 1, interestingChildren[childOff++ % len], "message", null),
		new MultiStatus("org.foo.bar", 5, interestingChildren[childOff++ % len], "message", new NullPointerException()),
		new MultiStatus("plugin-id", 8, interestingChildren[childOff++ % len], "message", null),
		new MultiStatus("plugin-id", 0, interestingChildren[childOff++ % len], "message", new IllegalStateException()),
		new MultiStatus("plugin-id", 65756, interestingChildren[childOff++ % len], "message", null),
		new MultiStatus(".", 1, interestingChildren[childOff++ % len], "message", null),
		new MultiStatus("org.foo.blaz", 1, interestingChildren[childOff++ % len], "", null),
		new MultiStatus("plugin-id", 1, interestingChildren[childOff++ % len], "%$(% 98%(%(*^", null),
		new MultiStatus("plugin-id", 1, "message", null),
		new MultiStatus("..", 87326, "", null),
	};
}
protected IStatus[] getInterestingStatuses() {
	return new IStatus[] {
		new Status(IStatus.WARNING, "(#(*$%#", 1, "../\\\\\'\'\"", new NullPointerException()),
		new Status(IStatus.WARNING, "org.foo", 1, "This is the message", null),
		new Status(IStatus.ERROR, "org.foo", 1, "This is the message", new IllegalStateException()),
		new Status(IStatus.OK, ".", 1, "This is the message", new NullPointerException()),
		new Status(IStatus.INFO, "org.asdfhsfhsdf976dsf6sd0f6s", 1, "#*&^$(*&#@^$)(#&)(", null),
	};
}
protected void doTest(String msg, IStatus[] oldStats) {
	writeLog(oldStats);
	IStatus[] newStats = readLog();
	assertEquals(msg, oldStats, newStats);
}
protected void doTest(String msg, IStatus status) {
	doTest(msg, new IStatus[] {status});
}
protected IStatus[] readLog() {
	PlatformLogReader reader = new PlatformLogReader();
	return reader.readLogFile(logFile.getAbsolutePath());
}
protected void setUp() throws Exception {
	super.setUp();
	//setup the log file
	if (logFile == null) {
		logFile = Platform.getLogFileLocation().toFile();
	}
}
protected void tearDown() throws Exception {
	super.tearDown();
	logFile.delete();
}
public void testDeepMultiStatus() {
	MultiStatus multi = new MultiStatus("id", 1, getInterestingMultiStatuses(), "ok", null);
	for (int i = 0; i < 5; i++) {
		multi = new MultiStatus("id", 1, new IStatus[] {multi}, "ok", null);
		doTest("1." + i, multi);
	}
}
public void testMultiMultiStatusSerialize() {
	IStatus[] interesting = getInterestingMultiStatuses();
	int len = interesting.length;
	for (int i = 1; i < len; i++) {
		IStatus[] subArray = new IStatus[len];
		System.arraycopy(interesting, 0, subArray, 0, len);
		doTest("1." + i, subArray);
	}	
}
public void testMultiSerialize() {
	IStatus[] interesting = getInterestingStatuses();
	int len = interesting.length;
	for (int i = 1; i < len; i++) {
		IStatus[] subArray = new IStatus[len];
		System.arraycopy(interesting, 0, subArray, 0, len);
		doTest("1." + i, subArray);
	}	
}
public void testMultiStatus() {
	IStatus[] interesting = getInterestingMultiStatuses();
	for (int i = 0; i < interesting.length; i++) {
		doTest("1." + i, interesting[i]);
	}
}
public void testSimpleSerialize() {
	IStatus[] interesting = getInterestingStatuses();
	for (int i = 0; i < interesting.length; i++) {
		doTest("1." + i, interesting[i]);
	}
}

protected void writeLog(IStatus status) {
	writeLog(new IStatus[] {status});
}
protected void writeLog(IStatus[] statuses) {
	if (logFile.exists())
		logFile.delete();
	PlatformLogWriter writer = new PlatformLogWriter();
	for (int i = 0; i < statuses.length; i++) {
		writer.logging(statuses[i], "org.eclipse.core.tests.runtime");
	}
	writer.shutdown();
}
}
