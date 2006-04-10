/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime.perf;

import junit.framework.*;
import org.eclipse.core.tests.runtime.RuntimeTestsPlugin;
import org.eclipse.core.tests.session.*;
import org.eclipse.core.tests.session.SetupManager.SetupException;

public class AllTests extends TestCase {
	public static Test suite() {
		TestSuite suite = new TestSuite(AllTests.class.getName());

		// make sure that the first run of the startup test is not recorded - it is heavily 
		// influenced by the presence and validity of the cached information
		try {
			PerformanceSessionTestSuite firstRun = new PerformanceSessionTestSuite(RuntimeTestsPlugin.PI_RUNTIME_TESTS, 1, StartupTest.class);
			Setup setup = firstRun.getSetup();
			setup.setSystemProperty("eclipseTest.ReportResults", "false");
			suite.addTest(firstRun);
		} catch (SetupException e) {
			fail("Unable to create warm up test");
		}

		suite.addTest(new PerformanceSessionTestSuite(RuntimeTestsPlugin.PI_RUNTIME_TESTS, 5, StartupTest.class));
		suite.addTest(new UIPerformanceSessionTestSuite(RuntimeTestsPlugin.PI_RUNTIME_TESTS, 5, UIStartupTest.class));
		suite.addTest(BenchPath.suite());
		suite.addTest(ContentTypePerformanceTest.suite());
		suite.addTest(PreferencePerformanceTest.suite());
		return suite;
	}
}
