/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.expressions.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import org.eclipse.core.internal.expressions.ExpressionPlugin;
import org.eclipse.core.internal.expressions.Expressions;


/**
 * Tests for cache used in {@link Expressions#isInstanceOf(Object, String)}.
 * <p>
 * <b>WARNING:</b> These tests start, stop, and re-start the
 * <code>com.ibm.icu</code>, <code>org.junit</code>, and <code>org.junit4</code> bundles.
 * Don't include these in another test suite. 
 */
public class ExpressionTestsPluginUnloading extends TestCase {

	public static Test suite() {
		TestSuite suite= new TestSuite(ExpressionTestsPluginUnloading.class);
		// ensure lexicographical ordering:
		ArrayList tests= Collections.list(suite.tests());
		Collections.sort(tests, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((TestCase)o1).getName().compareTo(((TestCase)o2).getName());
			}
		});
		TestSuite result= new TestSuite();
		for (Iterator iter= tests.iterator(); iter.hasNext();) {
			result.addTest((TestCase) iter.next());
		}
		return result;
	}

	public ExpressionTestsPluginUnloading(String name) {
		super(name);
	}


	public void test01PluginStopping() throws Exception {
		Bundle bundle= getBundle("com.ibm.icu");

		assertEquals(Bundle.STARTING, bundle.getState());

		doTestInstanceofICUDecimalFormat(bundle);
		assertEquals(Bundle.ACTIVE, bundle.getState());

		bundle.stop();
		assertEquals(Bundle.RESOLVED, bundle.getState());
		
		bundle.start();
		assertEquals(Bundle.ACTIVE, bundle.getState());

		doTestInstanceofICUDecimalFormat(bundle);
	}

	public void test02MultipleClassloaders() throws Exception {
		String vmVersion= System.getProperty("java.vm.version");
		if (vmVersion == null || vmVersion.compareTo("1.5") < 0)
			return;
			
		Bundle junit= getBundle("org.junit");
		Bundle junit4= getBundle("org.junit4");
		
		Class junitClass= junit.loadClass("junit.framework.AssertionFailedError");
		Class junit4Class= junit4.loadClass("junit.framework.AssertionFailedError");
		assertNotSame(junitClass, junit4Class);
		
		Object junitObj= junitClass.newInstance();
		Object junit4Obj= junit4Class.newInstance();
		
		assertInstanceOf(junitObj, "java.lang.Error", "java.lang.AssertionError");
		
		assertInstanceOf(junit4Obj, "java.lang.AssertionError", "java.lang.String");
		assertInstanceOf(junit4Obj, "java.lang.Error", "java.lang.Cloneable");
	}

	private void assertInstanceOf(Object obj, String isInstance, String isNotInstance) throws Exception {
		Class clazz= obj.getClass();
		
		System.out.println("ExpressionTestsPluginUnloading#" + getName() + "() - " + clazz.getName() + ": " + clazz.hashCode());
		System.out.println("ExpressionTestsPluginUnloading#" + getName() + "() - ClassLoader: " + clazz.getClassLoader().hashCode());
		
		for (int i= 0; i < 2; i++) { // test twice, second time is cached:
			assertTrue(Expressions.isInstanceOf(obj, isInstance));
			assertFalse(Expressions.isInstanceOf(obj, isNotInstance));
		}
	}
	
	private void doTestInstanceofICUDecimalFormat(Bundle bundle) throws Exception {
		Class clazz= bundle.loadClass("com.ibm.icu.text.DecimalFormat");
		Object decimalFormat= clazz.newInstance();
		assertInstanceOf(decimalFormat, "com.ibm.icu.text.DecimalFormat", "java.text.NumberFormat");
	}
	
	private static Bundle getBundle(String bundleName) {
		BundleContext bundleContext= ExpressionPlugin.getDefault().getBundleContext();
		Bundle[] bundles= bundleContext.getBundles();
		for (int i= 0; i < bundles.length; i++) {
			Bundle bundle= bundles[i];
			if (bundleName.equals(bundle.getSymbolicName())) {
				return bundle;
			}
		}
		fail("Could not find bundle: " + bundleName);
		return null;
	}
}
