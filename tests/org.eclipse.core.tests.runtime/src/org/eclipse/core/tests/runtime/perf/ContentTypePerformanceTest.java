/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime.perf;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.content.ContentType;
import org.eclipse.core.internal.content.ContentTypeBuilder;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.tests.harness.BundleTestingHelper;
import org.eclipse.core.tests.harness.PerformanceTestRunner;
import org.eclipse.core.tests.runtime.*;
import org.eclipse.core.tests.runtime.content.NaySayerContentDescriber;
import org.eclipse.core.tests.session.PerformanceSessionTestSuite;
import org.eclipse.core.tests.session.SessionTestSuite;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class ContentTypePerformanceTest extends RuntimeTest {

	private static Random random;
	private static final String TEST_DATA_ID = "org.eclipse.core.tests.runtime.contenttype.perf.testdata";

	public static int computeNumberOfElements(int elementsPerLevel, int numberOfLevels) {
		double sum = 0;
		for (int i = 1; i <= numberOfLevels; i++)
			sum += Math.pow(elementsPerLevel, i);
		return (int) sum;
	}

	private static String createContentType(String id, String baseTypeId, String[] fileNames, String[] fileExtensions, String describer) {
		StringBuffer result = new StringBuffer();
		result.append("<content-type id=\"");
		result.append(id);
		result.append("\" name=\"");
		result.append(id);
		result.append("\" ");
		if (baseTypeId != null) {
			result.append("base-type=\"");
			result.append(baseTypeId);
			result.append("\" ");
		}
		if (fileNames != null && fileNames.length > 0) {
			result.append("file-names=\"");
			result.append(toListString(fileNames));
			result.append("\" ");
		}
		if (fileExtensions != null && fileExtensions.length > 0) {
			result.append("file-extensions=\"");
			result.append(toListString(fileExtensions));
			result.append("\" ");
		}
		result.append("describer=\"");
		result.append(describer);
		result.append("\"/>");
		return result.toString();
	}

	public static int createContentTypes(Writer writer, String baseTypeId, int created, int numberOfLevels, int minimumPerLevel, int maximumPerLevel) throws IOException {
		if (numberOfLevels == 0)
			return 0;
		int nodes = nextInt(minimumPerLevel, maximumPerLevel);
		int local = nodes;
		for (int i = 1; i < nodes + 1; i++) {
			String id = "performance" + (created + i);
			String definition = createContentType(id, baseTypeId, null, baseTypeId == null ? new String[] {id} : null, NaySayerContentDescriber.class.getName());
			writer.write(definition);
			writer.write(System.getProperty("line.separator"));
			local += createContentTypes(writer, id, created + local, numberOfLevels - 1, minimumPerLevel, maximumPerLevel);
		}
		return local;
	}

	private static String getContentTypeId(int i) {
		return TEST_DATA_ID + ".performance" + i;
	}

	private static Random getRandom() {
		if (random == null)
			random = new Random(PI_RUNTIME_TESTS.hashCode());
		return random;
	}

	private static int nextInt(int minimumPerLevel, int maximumPerLevel) {
		if (maximumPerLevel == minimumPerLevel)
			return maximumPerLevel;
		return minimumPerLevel + getRandom().nextInt(maximumPerLevel - minimumPerLevel);
	}
	
	public static Test suite() {
		TestSuite suite = new TestSuite(ContentTypePerformanceTest.class.getName());

		SessionTestSuite setUp = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoSetUp");
		setUp.addTest(new ContentTypePerformanceTest("testDoSetUp"));
		suite.addTest(setUp);

		SessionTestSuite singleRun = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 1, "singleSessionTests");
		singleRun.addTest(new ContentTypePerformanceTest("testContentMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testNameMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testIsKindOf"));
		suite.addTest(singleRun);

		TestSuite loadCatalog = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 5, "multipleSessionTests");
		loadCatalog.addTest(new ContentTypePerformanceTest("testLoadCatalog"));
		suite.addTest(loadCatalog);

		TestSuite tearDown = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoTearDown");
		tearDown.addTest(new ContentTypePerformanceTest("testDoTearDown"));
		suite.addTest(tearDown);
		return suite;
	}

	static String toListString(Object[] list) {
		if (list.length == 0)
			return ""; //$NON-NLS-1$
		StringBuffer result = new StringBuffer();
		for (int i = 0; i < list.length; i++) {
			result.append(list[i]);
			result.append(',');
		}
		// ignore last comma
		return result.substring(0, result.length() - 1);
	}

	public ContentTypePerformanceTest(String name) {
		super(name);
	}

	private Bundle installContentTypes(String tag, int numberOfLevels, int minimumPerLevel, int maximumPerLevel) {
		TestRegistryChangeListener listener = new TestRegistryChangeListener(Platform.PI_RUNTIME, ContentTypeBuilder.PT_CONTENTTYPES, null, null);
		listener.register();
		IPath pluginLocation = getRandomLocation();
		pluginLocation.toFile().mkdirs();
		URL installURL = null;
		try {
			installURL = pluginLocation.toFile().toURL();
		} catch (MalformedURLException e) {
			fail(tag + ".0.5", e);
		}
		Writer writer = null;
		Bundle installed = null;
		try {
			try {
				writer = new BufferedWriter(new FileWriter(pluginLocation.append("plugin.xml").toFile()), 0x10000);
				writer.write("<plugin id=\"" + TEST_DATA_ID + "\" name=\"Content Type Performance Test Data\" version=\"1\">");
				writer.write(System.getProperty("line.separator"));
				writer.write("<requires><import plugin=\"" + PI_RUNTIME_TESTS + "\"/></requires>");
				writer.write(System.getProperty("line.separator"));
				writer.write("<extension point=\"org.eclipse.core.runtime.contentTypes\">");
				writer.write(System.getProperty("line.separator"));
				createContentTypes(writer, null, 0, numberOfLevels, minimumPerLevel, maximumPerLevel);
				writer.write("</extension></plugin>");
			} catch (IOException e) {
				fail(tag + ".1.0", e);
			} finally {
				if (writer != null)
					try {
						writer.close();
					} catch (IOException e) {
						fail("1.1", e);
					}
			}
			try {
				installed = RuntimeTestsPlugin.getContext().installBundle(installURL.toExternalForm());
			} catch (BundleException e) {
				fail(tag + ".3.0", e);
			}
			BundleTestingHelper.refreshPackages(RuntimeTestsPlugin.getContext(), new Bundle[] {installed});
			assertNotNull(tag + ".4.0", listener.getEvent(10000));
		} finally {
			listener.unregister();
		}
		return installed;
	}
	
	/**
	 * Returns a loaded content type manager. Except for load time tests, this method should
	 * be called outside the scope of performance monitoring.
	 */
	private IContentTypeManager loadContentTypeManager() {
		// the content type catalog is built right away
		return Platform.getContentTypeManager();
	}

	/** Forces all describers to be loaded.*/
	private void loadDescribers() {
		final IContentTypeManager manager = Platform.getContentTypeManager();
		IContentType[] allTypes = manager.getAllContentTypes();
		for (int i = 0; i < allTypes.length; i++)
			((ContentType) allTypes[i]).getDescriber();
	}
	

	/** Tests how much the size of the catalog affects the performance of content type matching by content analysis */
	public void testContentMatching() {
		final IContentTypeManager manager = loadContentTypeManager();
		loadDescribers();
		new PerformanceTestRunner() {
			protected void test() {
				IContentType[] associated = null;
				try {
					associated = manager.findContentTypesFor(getRandomContents(), null);
				} catch (IOException e) {
					fail("2.0", e);
				}
				// we know at least the text content type should be there
				assertTrue("2.1", associated.length >= 1);
				for (int i = 0; i < associated.length; i++)
					if (associated[i].getId().equals(IContentTypeManager.CT_TEXT))
						return;
				fail("2.2");
			}
		}.run(this, 10, 1);
	}

	public void testDoSetUp() {
		final int numberOfLevels = 10;
		int elementsPerLevel = 2;
		installContentTypes("1.0", numberOfLevels, elementsPerLevel, elementsPerLevel);
	}

	public void testDoTearDown() {
		Bundle bundle = Platform.getBundle(TEST_DATA_ID);
		if (bundle == null)
			// there is nothing to clean up (install failed?) 
			fail("1.0 nothing to clean-up");
		try {
			bundle.uninstall();
			ensureDoesNotExistInFileSystem(new File(new URL(bundle.getLocation()).getFile()));
		} catch (MalformedURLException e) {
			fail("2.0", e);
		} catch (BundleException e) {
			fail("3.0", e);
		}
	}

	public void testIsKindOf() {
		int numberOfLevels = 10;
		int elementsPerLevel = 2;
		IContentTypeManager manager = loadContentTypeManager();
		final IContentType lastRoot = manager.getContentType(getContentTypeId(elementsPerLevel));
		assertNotNull("2.0", lastRoot);
		final IContentType lastLeaf = manager.getContentType(getContentTypeId(computeNumberOfElements(elementsPerLevel, numberOfLevels)));
		assertNotNull("2.1", lastLeaf);
		new PerformanceTestRunner() {
			protected void test() {
				assertTrue("3.0", lastLeaf.isKindOf(lastRoot));
			}
		}.run(this, 10, 100000);
	}

	/**
	 * This test is intended for running as a session test.
	 */
	public void testLoadCatalog() {
		new PerformanceTestRunner() {
			protected void test() {
				loadContentTypeManager();
			}
		}.run(this, 1, /* must run only once - the suite controls how many sessions are run */1);
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by name */
	public void testNameMatching() {
		final IContentTypeManager manager = loadContentTypeManager();
		new PerformanceTestRunner() {
			protected void test() {
				IContentType[] associated = manager.findContentTypesFor("foo.txt");
				// we know at least the etxt content type should be here
				assertTrue("2.0", associated.length >= 1);
				// and it is supposed to be the first one (since it is at the root)
				assertEquals("2.1", IContentTypeManager.CT_TEXT, associated[0].getId());
			}
		}.run(this, 10, 20000);
	}
}