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
package org.eclipse.core.tests.internal.preferences;

import java.io.*;
import java.util.*;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.utils.UniversalUniqueIdentifier;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.tests.runtime.RuntimeTest;
import org.osgi.framework.Bundle;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * Test suite for API class org.eclipse.core.runtime.Preferences
 */
public class EclipsePreferencesTest extends RuntimeTest {

	class NodeTracer implements IEclipsePreferences.INodeChangeListener {
		StringBuffer log = new StringBuffer();

		public void added(IEclipsePreferences.NodeChangeEvent event) {
			log.append("[A:");
			log.append(event.getParent().absolutePath());
			log.append(',');
			log.append(event.getChild().absolutePath());
			log.append(']');
		}

		public void removed(IEclipsePreferences.NodeChangeEvent event) {
			log.append("[R:");
			log.append(event.getParent().absolutePath());
			log.append(',');
			log.append(event.getChild().absolutePath());
			log.append(']');
		}
	}

	class PreferenceTracer implements IEclipsePreferences.IPreferenceChangeListener {
		public StringBuffer log = new StringBuffer();

		private String typeCode(Object value) {
			if (value == null)
				return "";
			if (value instanceof Boolean)
				return "B";
			if (value instanceof Integer)
				return "I";
			if (value instanceof Long)
				return "L";
			if (value instanceof Float)
				return "F";
			if (value instanceof Double)
				return "D";
			if (value instanceof String)
				return "S";
			if (value instanceof byte[])
				return "b";
			assertTrue("0.0: " + value, false);
			return null;
		}

		public void preferenceChange(IEclipsePreferences.PreferenceChangeEvent event) {
			log.append("[");
			log.append(event.getKey());
			log.append(":");
			log.append(typeCode(event.getOldValue()));
			log.append(event.getOldValue() == null ? "null" : event.getOldValue());
			log.append("->");
			log.append(typeCode(event.getNewValue()));
			log.append(event.getNewValue() == null ? "null" : event.getNewValue());
			log.append("]");
		}
	}

	public EclipsePreferencesTest(String name) {
		super(name);
	}

	public static Test suite() {
		// all test methods are named "test..."
		return new TestSuite(EclipsePreferencesTest.class);
		//		TestSuite suite = new TestSuite();
		//		suite.addTest(new EclipsePreferencesTest("testLegacy"));
		//		return suite;
	}

	private String getUniqueString() {
		return new UniversalUniqueIdentifier().toString();
	}

	private IEclipsePreferences getScopeRoot() {
		return (IEclipsePreferences) Platform.getPreferencesService().getRootNode().node(TestScope.SCOPE);
	}

	public void testString() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final String defaultValue = null;
		final String[] values = {"", "hello", " x ", "\n"};

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.get(key, defaultValue));

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				String v1 = values[i];
				String v2 = values[i] + "x";
				prefs.put(key, v1);
				assertEquals("1.2." + i, v1, prefs.get(key, defaultValue));
				prefs.put(key, v2);
				assertEquals("1.3." + i, v2, prefs.get(key, defaultValue));
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.get(key, defaultValue));
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.get(null, defaultValue);
				fail("1.5.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.put(null, defaultValue);
				fail("1.5.1");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if value is null
			try {
				prefs.put(key, null);
				fail("1.5.2");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.get(key, defaultValue);
			fail("1.6");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testLong() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final long defaultValue = 42L;
		final long[] values = {-12345L, 0L, 12345L, Long.MAX_VALUE, Long.MIN_VALUE};

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getLong(key, defaultValue));

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				long v1 = values[i];
				long v2 = 54L;
				prefs.putLong(key, v1);
				assertEquals("1.2." + i, v1, prefs.getLong(key, defaultValue));
				prefs.putLong(key, v2);
				assertEquals("1.3." + i, v2, prefs.getLong(key, defaultValue));
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.getLong(key, defaultValue));
			}

			String stringValue = "foo";
			prefs.put(key, stringValue);
			assertEquals("1.5", stringValue, prefs.get(key, null));
			assertEquals("1.6", defaultValue, prefs.getLong(key, defaultValue));

			// spec'd to throw a NPE if key is null
			try {
				prefs.getLong(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putLong(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getLong(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testBoolean() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final boolean defaultValue = false;

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getBoolean(key, defaultValue));

			prefs.putBoolean(key, true);
			assertEquals("1.2", true, prefs.getBoolean(key, defaultValue));
			prefs.putBoolean(key, false);
			assertEquals("1.3", false, prefs.getBoolean(key, defaultValue));
			prefs.remove(key);
			assertEquals("1.4", defaultValue, prefs.getBoolean(key, defaultValue));

			String stringValue = "foo";
			prefs.put(key, stringValue);
			assertEquals("1.5", stringValue, prefs.get(key, null));
			assertEquals("1.6", defaultValue, prefs.getBoolean(key, defaultValue));

			// spec'd to throw a NPE if key is null
			try {
				prefs.getBoolean(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putBoolean(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getBoolean(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	private byte[][] getByteValues() {
		ArrayList result = new ArrayList();
		result.add(new byte[0]);
		//TODO		result.add(new byte[]{127});
		//TODO		result.add(new byte[]{-128});
		result.add(new byte[]{0});
		result.add(new byte[]{5});
		//TODO		result.add(new byte[]{-23});
		return (byte[][]) result.toArray(new byte[result.size()][]);
	}

	public void testBytes() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final byte[] defaultValue = new byte[]{42};
		final byte[][] values = getByteValues();

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getByteArray(key, defaultValue));

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				byte[] v1 = values[i];
				byte[] v2 = new byte[]{54};
				prefs.putByteArray(key, v1);
				assertEquals("1.2." + i, v1, prefs.getByteArray(key, defaultValue));
				prefs.putByteArray(key, v2);
				assertEquals("1.3." + i, v2, prefs.getByteArray(key, defaultValue));
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.getByteArray(key, defaultValue));
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.getByteArray(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putByteArray(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putByteArray(key, null);
				fail("2.2");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getByteArray(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testFloat() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final float defaultValue = 42f;
		final float[] values = {-12345f, 0f, 12345f, Float.MAX_VALUE, Float.MIN_VALUE};
		final float tol = 1.0e-20f;

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getFloat(key, defaultValue), tol);

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				float v1 = values[i];
				float v2 = 54f;
				prefs.putFloat(key, v1);
				assertEquals("1.2." + i, v1, prefs.getFloat(key, defaultValue), tol);
				prefs.putFloat(key, v2);
				assertEquals("1.3." + i, v2, prefs.getFloat(key, defaultValue), tol);
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.getFloat(key, defaultValue), tol);
			}

			String stringValue = "foo";
			prefs.put(key, stringValue);
			assertEquals("1.5", stringValue, prefs.get(key, null));
			assertEquals("1.6", defaultValue, prefs.getFloat(key, defaultValue), tol);

			// spec'd to throw a NPE if key is null
			try {
				prefs.getFloat(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putFloat(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getFloat(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testDouble() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final double defaultValue = 42.0;
		final double[] values = {0.0, 1002.5, -201788.55, Double.MAX_VALUE, Double.MIN_VALUE};
		final double tol = 1.0e-20;

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getDouble(key, defaultValue), tol);

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				double v1 = values[i];
				double v2 = 54.0;
				prefs.putDouble(key, v1);
				assertEquals("1.2." + i, v1, prefs.getDouble(key, defaultValue), tol);
				prefs.putDouble(key, v2);
				assertEquals("1.3." + i, v2, prefs.getDouble(key, defaultValue), tol);
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.getDouble(key, defaultValue), tol);
			}

			String stringValue = "foo";
			prefs.put(key, stringValue);
			assertEquals("1.5", stringValue, prefs.get(key, null));
			assertEquals("1.6", defaultValue, prefs.getDouble(key, defaultValue), tol);

			// spec'd to throw a NPE if key is null
			try {
				prefs.getDouble(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putDouble(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getDouble(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testInt() {
		String qualifier = getUniqueString();
		Preferences prefs = getScopeRoot().node(qualifier);
		final String key = "key1";
		final int defaultValue = 42;
		final int[] values = {0, 1002, -201788, Integer.MAX_VALUE, Integer.MIN_VALUE};

		try {

			// nothing there so expect the default
			assertEquals("1.1", defaultValue, prefs.getInt(key, defaultValue));

			// try for each value in the set
			for (int i = 0; i < values.length; i++) {
				int v1 = values[i];
				int v2 = 54;
				prefs.putInt(key, v1);
				assertEquals("1.2." + i, v1, prefs.getInt(key, defaultValue));
				prefs.putInt(key, v2);
				assertEquals("1.3." + i, v2, prefs.getInt(key, defaultValue));
				prefs.remove(key);
				assertEquals("1.4." + i, defaultValue, prefs.getInt(key, defaultValue));
			}

			String stringValue = "foo";
			prefs.put(key, stringValue);
			assertEquals("1.5", stringValue, prefs.get(key, null));
			assertEquals("1.6", defaultValue, prefs.getInt(key, defaultValue));

			// spec'd to throw a NPE if key is null
			try {
				prefs.getInt(null, defaultValue);
				fail("2.0");
			} catch (NullPointerException e) {
				// expected
			}

			// spec'd to throw a NPE if key is null
			try {
				prefs.putInt(null, defaultValue);
				fail("2.1");
			} catch (NullPointerException e) {
				// expected
			}
		} finally {
			// clean-up
			try {
				prefs.removeNode();
			} catch (BackingStoreException e) {
				fail("0.99", e);
			}
		}

		// spec'd to throw IllegalStateException if node has been removed
		try {
			prefs.getInt(key, defaultValue);
			fail("3.0");
		} catch (IllegalStateException e) {
			// expected
		}
	}

	public void testSync() {

	}

	public void testFlush() {

	}

	public void testRemoveNode() {
		Preferences root = getScopeRoot();
		ArrayList list = new ArrayList();
		for (int i = 0; i < 5; i++)
			list.add(root.node(getUniqueString()));

		// all exist
		for (Iterator i = list.iterator(); i.hasNext();) {
			Preferences node = (Preferences) i.next();
			try {
				assertTrue("1." + i, node.nodeExists(""));
			} catch (BackingStoreException e) {
				fail("1.99." + i, e);
			}
		}

		// remove each
		for (Iterator i = list.iterator(); i.hasNext();) {
			Preferences node = (Preferences) i.next();
			try {
				node.removeNode();
				assertTrue("2." + i, !node.nodeExists(""));
			} catch (BackingStoreException e) {
				fail("2.99." + i, e);
			}
		}
	}

	public void testName() {
		Preferences node = Platform.getPreferencesService().getRootNode();

		assertEquals("1.0", "", node.name());
		node = node.node(TestScope.SCOPE);
		assertEquals("2.0", TestScope.SCOPE, node.name());
		node = node.node("foo");
		assertEquals("3.0", "foo", node.name());
	}

	public void testNode() {
		Preferences node = Platform.getPreferencesService().getRootNode();

		// root node
		assertNotNull("1.0", node);
		assertEquals("1.1", "", node.name());
		assertEquals("1.2", "/", node.absolutePath());

		// scope root
		node = node.node(TestScope.SCOPE);
		assertNotNull("2.0", node);
		assertEquals("2.1", TestScope.SCOPE, node.name());
		assertEquals("2.2", "/" + TestScope.SCOPE, node.absolutePath());

		// child
		String name = getUniqueString();
		node = node.node(name);
		assertNotNull("3.0", node);
		assertEquals("3.1", name, node.name());
		assertEquals("3.2", "/" + TestScope.SCOPE + "/" + name, node.absolutePath());
	}

	public void testParent() {
		// parent of the root is null
		assertNull("1.0", Platform.getPreferencesService().getRootNode().parent());

		// parent of the scope root is the root
		Preferences node = Platform.getPreferencesService().getRootNode().node(TestScope.SCOPE);
		Preferences parent = node.parent();
		assertEquals("2.0", "/", parent.absolutePath());

		// parent of a child is the scope root
		node = getScopeRoot().node(getUniqueString());
		parent = node.parent();
		assertEquals("2.0", "/" + TestScope.SCOPE, parent.absolutePath());
	}

	public void testKeys() {
		String[] keys = new String[]{"foo", "bar", "quux"};
		Preferences node = getScopeRoot().node(getUniqueString());

		// ensure nothing exists to begin with
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			assertNull("1.0." + i, node.get(key, null));
		}

		// set all keys
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			node.put(key, getUniqueString());
		}

		// get the key list
		try {
			String[] result = node.keys();
			assertEquals("2.0", keys, result);
		} catch (BackingStoreException e) {
			fail("0.99", e);
		}
	}

	private void assertEquals(String message, byte[] one, byte[] two) {
		if (one == null && two == null)
			return;
		if (one == two)
			return;
		assertNotNull(message + ".1", one);
		assertNotNull(message + ".2", two);
		assertEquals(message + ".3", one.length, two.length);
		for (int i = 0; i < one.length; i++)
			assertEquals(message + ".4." + i, one[i], two[i]);
	}

	private void assertEquals(String message, Object[] one, Object[] two) {
		if (one == null && two == null)
			return;
		if (one == two)
			return;
		if (one == null || two == null)
			assertTrue(message + ".1", false);
		if (one.length != two.length)
			assertTrue(message + ".2", false);
		boolean[] found = new boolean[one.length];
		for (int i = 0; i < one.length; i++) {
			for (int j = 0; j < one.length; j++) {
				if (!found[j] && one[i].equals(two[j]))
					found[j] = true;
			}
		}
		for (int i = 0; i < found.length; i++)
			if (!found[i])
				assertTrue(message + ".3." + i, false);
	}

	public void testChildrenNames() {
		String[] childrenNames = new String[]{"foo", "bar", "quux"};
		Preferences node = getScopeRoot().node(getUniqueString());
		String[] result = null;

		// no children to start
		try {
			result = node.childrenNames();
		} catch (BackingStoreException e) {
			fail("1.0", e);
		}
		assertEquals("1.1", 0, result.length);

		// add children
		for (int i = 0; i < childrenNames.length; i++)
			node.node(childrenNames[i]);
		try {
			result = node.childrenNames();
		} catch (BackingStoreException e) {
			fail("2.0", e);
		}
		assertEquals("2.1", childrenNames, result);

	}

	public void testNodeExists() {
		Preferences parent = null;
		Preferences node = Platform.getPreferencesService().getRootNode();
		String[] childrenNames = new String[]{"foo", "bar", "quux"};
		String fake = "fake";

		// check the root node
		try {
			assertTrue("1.0", node.nodeExists(""));
			assertTrue("1.1", !node.nodeExists(fake));
		} catch (BackingStoreException e) {
			fail("1.99", e);
		}

		// check the scope root
		parent = node;
		node = getScopeRoot();
		try {
			assertTrue("2.0", parent.nodeExists(node.name()));
			assertTrue("2.1", node.nodeExists(""));
			assertTrue("2.2", !parent.nodeExists(fake));
			assertTrue("2.3", !node.nodeExists(fake));
		} catch (BackingStoreException e) {
			fail("2.99", e);
		}

		// check a child
		parent = node;
		node = parent.node(getUniqueString());
		try {
			assertTrue("3.0", parent.nodeExists(node.name()));
			assertTrue("3.1", node.nodeExists(""));
			assertTrue("3.2", !parent.nodeExists(fake));
			assertTrue("3.3", !node.nodeExists(fake));
		} catch (BackingStoreException e) {
			fail("3.99", e);
		}

		// create some more children and check
		parent = node;
		Preferences[] nodes = new Preferences[childrenNames.length];
		for (int i = 0; i < childrenNames.length; i++)
			nodes[i] = parent.node(childrenNames[i]);
		for (int i = 0; i < childrenNames.length; i++)
			try {
				assertTrue("4.0", parent.nodeExists(childrenNames[i]));
				assertTrue("4.1", !parent.nodeExists(fake));
			} catch (BackingStoreException e) {
				fail("4.99", e);
			}
		for (int i = 0; i < nodes.length; i++)
			try {
				assertTrue("4.2", nodes[i].nodeExists(""));
			} catch (BackingStoreException e) {
				fail("4.100", e);
			}

		// remove children and check
		for (int i = 0; i < nodes.length; i++) {
			try {
				nodes[i].removeNode();
				assertTrue("5.1", !parent.nodeExists(nodes[i].name()));
				assertTrue("5.2", !nodes[i].nodeExists(""));
			} catch (BackingStoreException e) {
				fail("5.99", e);
			}
		}
	}

	public void testClear() {
		Preferences node = getScopeRoot().node(getUniqueString());
		String[] keys = new String[]{"foo", "bar", "quux"};
		String[] values = new String[]{getUniqueString(), getUniqueString(), getUniqueString()};

		// none to start with
		try {
			assertEquals("1.0", 0, node.keys().length);
		} catch (BackingStoreException e) {
			fail("1.99", e);
		}

		// fill the node up with values
		try {
			for (int i = 0; i < keys.length; i++)
				node.put(keys[i], values[i]);
			assertEquals("2.0", keys.length, node.keys().length);
			assertEquals("2.1", keys, node.keys());
		} catch (BackingStoreException e) {
			fail("2.99", e);
		}

		// clear the values and check
		try {
			node.clear();
			assertEquals("3.0", 0, node.keys().length);
			for (int i = 0; i < keys.length; i++)
				assertNull("3.1." + i, node.get(keys[i], null));
		} catch (BackingStoreException e) {
			fail("3.99", e);
		}
	}

	public void testAbsolutePath() {
		IPath expected = Path.ROOT;
		Preferences node = Platform.getPreferencesService().getRootNode();

		// root node
		assertEquals("1.0", expected.toString(), node.absolutePath());

		// scope root
		expected = expected.append(TestScope.SCOPE);
		node = node.node(TestScope.SCOPE);
		assertEquals("2.0", expected.toString(), node.absolutePath());

		// another child
		String name = getUniqueString();
		expected = expected.append(name);
		node = node.node(name);
		assertEquals("3.0", expected.toString(), node.absolutePath());
	}

	public void testPreferenceChangeListeners() {
		IEclipsePreferences node = getScopeRoot();
		PreferenceTracer tracer = new PreferenceTracer();
		node.addPreferenceChangeListener(tracer);

		String key = "foo";

		// initial state
		assertEquals("0.0", "", tracer.log.toString());

		// add preference (string value)
		node.put(key, "bar");
		String string = node.get(key, null);
		assertNotNull("1.0", string);
		assertEquals("1.1", "bar", string);
		assertEquals("1.2", "[foo:null->Sbar]", tracer.log.toString());

		// change its value
		tracer.log.setLength(0);
		node.put(key, "quux");
		string = node.get(key, null);
		assertNotNull("2.0", string);
		assertEquals("2.1", "quux", string);
		assertEquals("2.2", "[foo:Sbar->Squux]", tracer.log.toString());

		// change its type
		tracer.log.setLength(0);
		node.putInt(key, 123);
		int i = node.getInt(key, 0);
		assertEquals("3.0", 123, i);
		assertEquals("3.1", "[foo:Squux->I123]", tracer.log.toString());

		// TODO finish these
	}

	public void testNodeChangeListeners() {
		IEclipsePreferences root = getScopeRoot();
		NodeTracer tracer = new NodeTracer();
		root.addNodeChangeListener(tracer);

		// initial state
		assertEquals("0.0", "", tracer.log.toString());

		// add a child
		String name = getUniqueString();
		IPath parent = new Path(root.absolutePath());
		IPath child = parent.append(name);
		Preferences node = root.node(name);
		assertEquals("1.0", "[A:" + parent + ',' + child + ']', tracer.log.toString());

		// remove the child
		tracer.log.setLength(0);
		try {
			node.removeNode();
			assertEquals("2.0", "[R:" + parent + ',' + child + ']', tracer.log.toString());
		} catch (BackingStoreException e) {
			fail("2.99", e);
		}

		// remove the listener and make sure we don't get any changes
		root.removeNodeChangeListener(tracer);
		tracer.log.setLength(0);
		root.node(name);
		assertEquals("3.0", "", tracer.log.toString());
	}

	/*
	 * @see junit.framework.TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		Preferences node = getScopeRoot();
		String[] children = node.childrenNames();
		for (int i = 0; i < children.length; i++)
			node.node(children[i]).removeNode();
	}

	/*
	 * Regression test for bug 56020 - [runtime] prefs: converted preferences not restored on second session
	 */
	public void testLegacy() {

		String pluginID = "org.eclipse.core.tests.preferences." + getUniqueString();
		String key = "key." + getUniqueString();
		String value = "value." + getUniqueString();
		String OLD_PREFS_FILENAME = "pref_store.ini";
		String NEW_PREFS_FILENAME = "prefs.ini";

		// create fake plug-in and store 2.1 format tests in legacy location
		Bundle runtimeBundle = Platform.getBundle(Platform.PI_RUNTIME);
		if (runtimeBundle == null)
			return;
		String runtimeStateLocation = Platform.getStateLocation(runtimeBundle).toString();
		IPath pluginStateLocation = new Path(runtimeStateLocation.replaceAll(Platform.PI_RUNTIME, pluginID));
		IPath oldFile = pluginStateLocation.append(OLD_PREFS_FILENAME);
		Properties oldProperties = new Properties();
		oldProperties.put(key, value);
		OutputStream output = null;
		try {
			oldFile.toFile().getParentFile().mkdirs();
			output = new BufferedOutputStream(new FileOutputStream(oldFile.toFile()));
			oldProperties.store(output, null);
		} catch (IOException e) {
			fail("1.0", e);
		} finally {
			if (output != null)
				try {
					output.close();
				} catch (IOException e) {
					// ignore
				}
		}

		// access fake plug-in via new preferences APIs which should invoke conversion
		Preferences node = Platform.getPreferencesService().getRootNode().node(InstanceScope.SCOPE).node(pluginID);

		// ensure values are in the workspace
		String actual = node.get(key, null);
		assertEquals("3.0", value, actual);

		// ensure the values have been flushed to disk
		// first indication is the new file exists on disk.
		IPath newFile = oldFile.removeLastSegments(1).append(NEW_PREFS_FILENAME);
		assertTrue("4.0", newFile.toFile().exists());
		// then check to see if the value is in the file
		String newKey = Path.ROOT.append(InstanceScope.SCOPE).append(pluginID).append(key).toString();
		Properties newProperties = new Properties();
		InputStream input = null;
		try {
			input = new BufferedInputStream(new FileInputStream(newFile.toFile()));
			newProperties.load(input);
		} catch (IOException e) {
			fail("4.1", e);
		} finally {
			if (input != null)
				try {
					input.close();
				} catch (IOException e) {
					// ignore
				}
		}
		actual = newProperties.getProperty(newKey);
		assertEquals("4.2", value, actual);
	}
}