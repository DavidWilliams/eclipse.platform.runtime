/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.internal.expressions.tests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.IPluginDescriptor;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.internal.expressions.Expressions;
import org.eclipse.core.internal.expressions.SystemTestExpression;


public class ExpressionTests extends TestCase {

	public static Test suite() {
		return new TestSuite(ExpressionTests.class);
	}
	
	public void testArgumentConversion() throws Exception {
		assertNull(Expressions.convertArgument(null));
		assertEquals("", Expressions.convertArgument(""));
		assertEquals("", Expressions.convertArgument("''"));
		assertEquals("eclipse", Expressions.convertArgument("eclipse"));
		assertEquals("true", Expressions.convertArgument("'true'"));
		assertEquals("1.7", Expressions.convertArgument("'1.7'"));
		assertEquals("007", Expressions.convertArgument("'007'"));
		assertEquals(Boolean.TRUE, Expressions.convertArgument("true"));
		assertEquals(Boolean.FALSE, Expressions.convertArgument("false"));
		assertEquals(new Integer(100), Expressions.convertArgument("100"));
		assertEquals(new Float(1.7f), Expressions.convertArgument("1.7"));
	}
	
	public void testArgumentParsing() throws Exception {
		Object[] result= null;
		
		result= Expressions.parseArguments("");
		assertEquals(0, result.length);
		
		result= Expressions.parseArguments("s1");
		assertEquals("s1", result[0]);
		
		result= Expressions.parseArguments(" s1 ");
		assertEquals("s1", result[0]);
		
		result= Expressions.parseArguments("s1,s2");
		assertEquals("s1", result[0]);
		assertEquals("s2", result[1]);
		
		result= Expressions.parseArguments(" s1 , s2 ");
		assertEquals("s1", result[0]);
		assertEquals("s2", result[1]);
		
		result= Expressions.parseArguments("' s1 ',' s2 '");
		assertEquals(" s1 ", result[0]);
		assertEquals(" s2 ", result[1]);
		
		result= Expressions.parseArguments(" s1 , ' s2 '");
		assertEquals("s1", result[0]);
		assertEquals(" s2 ", result[1]);
		
		result= Expressions.parseArguments("' s1 ', s2 ");
		assertEquals(" s1 ", result[0]);
		assertEquals("s2", result[1]);
		
		result= Expressions.parseArguments("''''");
		assertEquals("'", result[0]);
	}
	
	public void testSystemProperty() throws Exception {
		SystemTestExpression expression= new SystemTestExpression("os.name", System.getProperty("os.name"));
		EvaluationResult result= expression.evaluate(new EvaluationContext(null, new Object()));
		assertTrue(result == EvaluationResult.TRUE);
	}
	
	public void testResolvePluginDescriptor() throws Exception {
		IEvaluationContext context= new EvaluationContext(null, new Object());
		IPluginDescriptor descriptor= (IPluginDescriptor)context.resolveVariable(
			IEvaluationContext.PLUGIN_DESCRIPTOR,
			new String[] { "org.eclipse.jdt.ui.tests.refactoring" });
		assertNotNull(descriptor);
	}
}