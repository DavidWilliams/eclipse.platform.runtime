/**********************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.tests.runtime.jobs;

import junit.framework.TestSuite;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.tests.harness.*;

/**
 * Tests API methods IJobManager.beginRule and IJobManager.endRule
 */
public class BeginEndRuleTest extends AbstractJobManagerTest {

	private class BlockingMonitor extends TestProgressMonitor implements IProgressMonitorWithBlocking {
		int[] status;
		int index;

		public BlockingMonitor(int[] status, int index) {
			this.status = status;
			this.index = index;
		}

		public void setBlocked(IStatus reason) {
			status[index] = TestBarrier.STATUS_BLOCKED;
		}

		public void clearBlocked() {
			//leave empty for now
		}
	}

	private class JobRuleRunner extends Job {
		private ISchedulingRule rule;
		private int[] status;
		private int index;
		private int numRepeats;
		private boolean reportBlocking;

		/**
		 * This job will start applying the given rule in the manager
		 */
		public JobRuleRunner(String name, ISchedulingRule rule, int[] status, int index, int numRepeats, boolean reportBlocking) {
			super(name);
			this.status = status;
			this.rule = rule;
			this.index = index;
			this.numRepeats = numRepeats;
			this.reportBlocking = reportBlocking;
		}

		protected IStatus run(IProgressMonitor monitor) {
			//begin executing the job
			monitor.beginTask(getName(), numRepeats);
			try {
				//set the status flag to START
				status[index] = TestBarrier.STATUS_START;
				for (int i = 0; i < numRepeats; i++) {
					//wait until the tester allows this job to run again
					TestBarrier.waitForStatusNoFail(status, index, TestBarrier.STATUS_WAIT_FOR_RUN);
					//create a hook that would notify this thread when this job was blocked on a rule (if needed)
					BlockingMonitor bMonitor = null;
					if (reportBlocking)
						bMonitor = new BlockingMonitor(status, index);

					//start the given rule in the manager
					manager.beginRule(rule, bMonitor);
					//set status to RUNNING
					status[index] = TestBarrier.STATUS_RUNNING;

					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;

					//wait until tester allows this job to finish
					TestBarrier.waitForStatusNoFail(status, index, TestBarrier.STATUS_WAIT_FOR_DONE);
					//end the given rule
					manager.endRule(rule);
					//set status to DONE
					status[index] = TestBarrier.STATUS_DONE;

					monitor.worked(1);
					Thread.yield();
				}

			} finally {
				monitor.done();
				Thread.yield();
			}
			return Status.OK_STATUS;
		}

	}

	/**
	 * This runnable will try to begin the given rule in the Job Manager.  It will
	 * end the rule before returning.
	 */
	private class SimpleRuleRunner implements Runnable {
		private ISchedulingRule rule;
		private IProgressMonitor monitor;
		private int[] status;
		RuntimeException exception;

		public SimpleRuleRunner(ISchedulingRule rule, int[] status, IProgressMonitor monitor) {
			this.rule = rule;
			this.monitor = monitor;
			this.status = status;
			this.exception = null;
		}

		public void run() {
			//tell the caller that we have entered the run method
			status[0] = TestBarrier.STATUS_RUNNING;
			try {
				try {
					manager.beginRule(rule, monitor);
				} finally {
					manager.endRule(rule);
				}
			} catch (OperationCanceledException e) {
				//ignore
			} catch (RuntimeException e) {
				exception = e;
			} finally {
				status[0] = TestBarrier.STATUS_DONE;
			}
		}
	}

	/**
	 * This runnable will try to end the given rule in the Job Manager
	 */
	private class RuleEnder implements Runnable {
		private ISchedulingRule rule;
		private int[] status;

		public RuleEnder(ISchedulingRule rule, int[] status) {
			this.rule = rule;
			this.status = status;
		}

		public void run() {
			try {
				status[0] = TestBarrier.STATUS_RUNNING;
				manager.endRule(rule);
				fail("Ending Rule");

			} catch (RuntimeException e) {
				//should fail
			}

		}
	}

	public static TestSuite suite() {
		return new TestSuite(BeginEndRuleTest.class);
		//		TestSuite suite = new TestSuite();
		//		suite.addTest(new BeginEndRuleTest("testComplexRuleStarting"));
		//		return suite;
	}

	public BeginEndRuleTest() {
		super();
	}

	public BeginEndRuleTest(String name) {
		super(name);
	}

	public void testComplexRuleStarting() {
		//test how the manager reacts when several different threads try to begin conflicting rules
		final int NUM_THREADS = 3;
		//array to communicate with the launched threads
		final int[] status = {TestBarrier.STATUS_WAIT_FOR_START, TestBarrier.STATUS_WAIT_FOR_START, TestBarrier.STATUS_WAIT_FOR_START};//, TestBarrier.STATUS_WAIT_FOR_START, TestBarrier.STATUS_WAIT_FOR_START };
		//number of times to start each rule
		int NUM_REPEATS = 10;

		Job[] jobs = new Job[NUM_THREADS];
		jobs[0] = new JobRuleRunner("ComplexJob1", new PathRule("/A"), status, 0, NUM_REPEATS, true);
		jobs[1] = new JobRuleRunner("ComplexJob2", new PathRule("/A/B"), status, 1, NUM_REPEATS, true);
		jobs[2] = new JobRuleRunner("ComplexJob3", new PathRule("/A/B/C"), status, 2, NUM_REPEATS, true);
		//jobs[3] = new JobRuleRunner("Job4", new RuleSetD(), status, 3, NUM_REPEATS, true);
		//jobs[4] = new JobRuleRunner("Job5", new RuleSetE(), status, 4, NUM_REPEATS, true);

		//schedule the jobs
		for (int i = 0; i < jobs.length; i++) {
			jobs[i].schedule();
		}

		//wait until all the jobs start
		for (int i = 0; i < jobs.length; i++) {
			TestBarrier.waitForStatus(status, i, TestBarrier.STATUS_START);
		}

		//all jobs should be running
		//the status flag should be set to START
		for (int i = 0; i < status.length; i++) {
			assertEquals("1." + i, Job.RUNNING, jobs[i].getState());
			assertEquals("2." + i, TestBarrier.STATUS_START, status[i]);
		}

		//the order that the jobs will be executed
		int[] order = {0, 1, 2};//, 3, 4 };

		for (int j = 0; j < NUM_REPEATS; j++) {
			//let the first job in the order run
			status[order[0]] = TestBarrier.STATUS_WAIT_FOR_RUN;
			//wait until the first job in the order reads the flag
			TestBarrier.waitForStatus(status, order[0], TestBarrier.STATUS_RUNNING);

			//let all subsequent jobs run (they will be blocked)
			//before starting next job, wait until previous job is blocked by JobManager (to preserve order)
			for (int i = 1; i < order.length; i++) {
				status[order[i]] = TestBarrier.STATUS_WAIT_FOR_RUN;
				TestBarrier.waitForStatus(status, order[i], TestBarrier.STATUS_BLOCKED);
			}

			for (int k = 0; k < order.length; k++) {
				//the current job should be running, the next jobs should be waiting
				assertEquals((3 + k) + ".0", TestBarrier.STATUS_RUNNING, status[order[k]]);
				for (int i = k + 1; i < order.length; i++) {
					assertEquals((3 + k) + "." + i, TestBarrier.STATUS_BLOCKED, status[order[i]]);
				}
				//let the current job finish			
				status[order[k]] = TestBarrier.STATUS_WAIT_FOR_DONE;
				//current job is done
				TestBarrier.waitForStatus(status, order[k], TestBarrier.STATUS_DONE);

				//wait until the next job runs
				if (k < order.length - 1)
					TestBarrier.waitForStatus(status, order[k + 1], TestBarrier.STATUS_RUNNING);
			}

			if (j < NUM_REPEATS - 1) {
				for (int i = 0; i < status.length; i++) {
					assertEquals("7." + (i + 1), TestBarrier.STATUS_DONE, status[i]);
					assertEquals("8." + (i + 1), Job.RUNNING, jobs[i].getState());
				}

				//don't change order on last iteration
				//change the order of the jobs, nothing should change in the execution
				int temp = order[0];

				order[0] = order[2];
				order[2] = order[1];
				order[1] = temp;
			}
		}

		//wait until all jobs are done
		for (int i = 0; i < order.length; i++) {
			waitForEnd(jobs[order[i]]);
		}

		for (int i = 0; i < jobs.length; i++) {
			//check that the final status of all jobs is correct		
			assertEquals("9." + i, TestBarrier.STATUS_DONE, status[i]);
			assertEquals("10." + i, Job.NONE, jobs[i].getState());
			assertEquals("11." + i, IStatus.OK, jobs[i].getResult().getSeverity());
		}
	}

	public void testSimpleRuleStarting() {
		//start two jobs, each of which will begin and end a rule several times
		//while one job starts a rule, the second job's call to begin rule should block that thread
		//until the first job calls end rule
		final int[] status = {TestBarrier.STATUS_WAIT_FOR_START, TestBarrier.STATUS_WAIT_FOR_START};
		//number of repetitions of beginning and ending the rule
		final int NUM_REPEATS = 10;
		Job[] jobs = new Job[2];
		jobs[0] = new JobRuleRunner("SimpleJob1", new PathRule("/A"), status, 0, NUM_REPEATS, false);
		jobs[1] = new JobRuleRunner("SimpleJob2", new PathRule("/A/B"), status, 1, NUM_REPEATS, false);

		//schedule both jobs to start their execution
		jobs[0].schedule();
		jobs[1].schedule();

		//make sure both jobs are running and their respective run methods have been invoked
		//waitForStart(jobs[1]);
		TestBarrier.waitForStatus(status, 0, TestBarrier.STATUS_START);
		TestBarrier.waitForStatus(status, 1, TestBarrier.STATUS_START);

		assertEquals("2.0", Job.RUNNING, jobs[0].getState());
		assertEquals("2.1", Job.RUNNING, jobs[1].getState());
		assertEquals("2.2", TestBarrier.STATUS_START, status[0]);
		assertEquals("2.3", TestBarrier.STATUS_START, status[1]);

		//the order of execution of the jobs (by their index in the status array)
		int first = 0;
		int second = 1;

		//now both jobs are waiting for the STATUS_WAIT_FOR_RUN flag
		for (int j = 0; j < NUM_REPEATS; j++) {
			//let the first job start executing
			status[first] = TestBarrier.STATUS_WAIT_FOR_RUN;

			//wait for the first job to read the flag
			TestBarrier.waitForStatus(status, first, TestBarrier.STATUS_RUNNING);

			//let the second job start, its thread will be blocked by the beginRule method
			status[second] = TestBarrier.STATUS_WAIT_FOR_RUN;

			//only the first job should be running
			//the other job should be blocked by the beginRule method
			assertEquals("3.1", TestBarrier.STATUS_RUNNING, status[first]);
			assertEquals("3.2", TestBarrier.STATUS_WAIT_FOR_RUN, status[second]);

			//let the first job finish execution and call endRule
			//the second thread will then become unblocked
			status[first] = TestBarrier.STATUS_WAIT_FOR_DONE;

			//wait until the first job is done
			TestBarrier.waitForStatus(status, first, TestBarrier.STATUS_DONE);

			//now wait until the second job begins execution
			TestBarrier.waitForStatus(status, second, TestBarrier.STATUS_RUNNING);

			//the first job is done, the second job is executing
			assertEquals("4.1", TestBarrier.STATUS_DONE, status[first]);
			assertEquals("4.2", TestBarrier.STATUS_RUNNING, status[second]);

			//let the second job finish execution
			status[second] = TestBarrier.STATUS_WAIT_FOR_DONE;

			//wait until the second job is finished
			TestBarrier.waitForStatus(status, second, TestBarrier.STATUS_DONE);

			//both jobs are done now
			assertEquals("5.1", TestBarrier.STATUS_DONE, status[first]);
			assertEquals("5.2", TestBarrier.STATUS_DONE, status[second]);

			//flip the order of execution of the jobs
			int temp = first;
			first = second;
			second = temp;
		}

		//wait until both jobs are done
		waitForEnd(jobs[second]);
		waitForEnd(jobs[first]);

		//check that the final status of both jobs is correct		
		assertEquals("6.1", TestBarrier.STATUS_DONE, status[0]);
		assertEquals("6.2", TestBarrier.STATUS_DONE, status[1]);
		assertEquals("6.3", Job.NONE, jobs[0].getState());
		assertEquals("6.4", Job.NONE, jobs[1].getState());
		assertEquals("6.5", IStatus.OK, jobs[0].getResult().getSeverity());
		assertEquals("6.6", IStatus.OK, jobs[1].getResult().getSeverity());
	}

	public void testComplexRuleContainment() {
		ISchedulingRule rules[] = new ISchedulingRule[4];

		rules[0] = new PathRule("/A");
		rules[1] = new PathRule("/A/B");
		rules[2] = new PathRule("/A/B/C");
		rules[3] = new PathRule("/A/D");

		//adding multiple rules in correct order
		int RULE_REPEATS = 10;
		try {
			for (int i = 0; i < rules.length - 1; i++) {
				for (int j = 0; j < RULE_REPEATS; j++) {
					manager.beginRule(rules[i], null);
				}
			}
			for (int i = rules.length - 1; i > 0; i--) {
				for (int j = 0; j < RULE_REPEATS; j++) {
					manager.endRule(rules[i - 1]);
				}
			}
		} catch (RuntimeException e) {
			fail("4.0");
		}

		//adding rules in proper order, then adding a rule from a bypassed branch
		//trying to end previous rules should not work
		for (int i = 0; i < rules.length; i++) {
			manager.beginRule(rules[i], null);
		}
		try {
			manager.endRule(rules[2]);
			fail("4.1");
		} catch (RuntimeException e) {
			//should fail
			try {
				manager.endRule(rules[1]);
				fail("4.2");
			} catch (RuntimeException e1) {
				//should fail
				try {
					manager.endRule(rules[0]);
					fail("4.3");
				} catch (RuntimeException e2) {
					//should fail
				}
			}
		}
		for (int i = rules.length; i > 0; i--) {
			manager.endRule(rules[i - 1]);
		}
	}

	public void _testEndNullRule() {
		//see bug #43460
		//end null IScheduleRule without begin
		try {
			manager.endRule(null);
			fail("1.1");
		} catch (RuntimeException e) {
			//should fail
		}
	}

	public void testFailureCase() {
		ISchedulingRule rule1 = new IdentityRule();
		ISchedulingRule rule2 = new IdentityRule();

		//end without begin
		try {
			manager.endRule(rule1);
			fail("1.0");
		} catch (RuntimeException e) {
			//should fail
		}
		//simple mismatched begin/end
		manager.beginRule(rule1, null);
		try {
			manager.endRule(rule2);
			fail("1.2");
		} catch (RuntimeException e) {
			//should fail
		}
		//should still be able to end the original rule
		manager.endRule(rule1);

		//mismatched begin/end, ending a null rule
		manager.beginRule(rule1, null);
		try {
			manager.endRule(null);
			fail("1.3");
		} catch (RuntimeException e) {
			//should fail
		}
		//should still be able to end the original rule
		manager.endRule(rule1);
	}

	public void testNestedCase() {
		ISchedulingRule rule1 = new PathRule("/A");
		ISchedulingRule rule2 = new PathRule("/A/B");

		//ending an outer rule before an inner one
		manager.beginRule(rule1, null);
		manager.beginRule(rule2, null);
		try {
			manager.endRule(rule1);
			fail("2.0");
		} catch (RuntimeException e) {
			//should fail
		}
		manager.endRule(rule2);
		manager.endRule(rule1);

		//ending a rule that is not open
		manager.beginRule(rule1, null);
		manager.beginRule(rule2, null);
		try {
			manager.endRule(null);
			fail("2.1");
		} catch (RuntimeException e) {
			//should fail
		}
		manager.endRule(rule2);
		manager.endRule(rule1);

		//adding rules starting with null rule
		try {
			manager.beginRule(null, null);
			manager.beginRule(rule1, null);
			manager.endRule(rule1);
			manager.beginRule(rule2, null);
			manager.endRule(rule2);
			manager.endRule(null);
		} catch (RuntimeException e) {
			//should not fail
			fail("2.2");
		}

		//adding numerous instances of the same rule
		int NUM_ADDITIONS = 100;
		try {
			for (int i = 0; i < NUM_ADDITIONS; i++) {
				manager.beginRule(rule1, null);
			}
			for (int i = 0; i < NUM_ADDITIONS; i++) {
				manager.endRule(rule1);
			}
		} catch (RuntimeException e) {
			//should not fail
			fail("2.3");
		}

		//adding numerous instances of the null rule
		try {
			for (int i = 0; i < NUM_ADDITIONS; i++) {
				manager.beginRule(null, null);
			}
			manager.beginRule(rule1, null);
			manager.endRule(rule1);
			for (int i = 0; i < NUM_ADDITIONS; i++) {
				manager.endRule(null);
			}
		} catch (RuntimeException e) {
			//should not fail
			fail("2.4");
		}
	}

	/**
	 * Tests a failure where canceling an attempt to beginRule resulted in implicit jobs
	 * being recycled before they were finished.
	 */
	public void testBug44299() {
		ISchedulingRule rule = new IdentityRule();
		FussyProgressMonitor monitor = new FussyProgressMonitor();
		manager.beginRule(rule, monitor);
		int[] status = new int[1];
		SimpleRuleRunner runner = new SimpleRuleRunner(rule, status, monitor);
		new Thread(runner).start();

		//wait for the job to start
		TestBarrier.waitForStatus(status, TestBarrier.STATUS_RUNNING);
		//give the job a chance to enter the wait loop
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}
		//cancel the monitor
		monitor.setCanceled(true);

		TestBarrier.waitForStatus(status, TestBarrier.STATUS_DONE);
		if (runner.exception != null)
			fail("1.0", runner.exception);

		//finally clear the rule
		manager.endRule(rule);
	}

	public void testRuleContainment() {
		ISchedulingRule rules[] = new ISchedulingRule[4];

		rules[0] = new PathRule("/A");
		rules[1] = new PathRule("/A/B");
		rules[2] = new PathRule("/A/B/C");
		rules[3] = new PathRule("/A/D");

		//simple addition of rules in incorrect containment order
		manager.beginRule(rules[1], null);
		try {
			manager.beginRule(rules[0], null);
			fail("3.0");
		} catch (RuntimeException e) {
			//should fail
		} finally {
			//still need to end the rule
			manager.endRule(rules[0]);
		}
		manager.endRule(rules[1]);

		//adding rules in proper order, then adding a rule from different hierarchy
		manager.beginRule(rules[1], null);
		manager.beginRule(rules[2], null);
		try {
			manager.beginRule(rules[3], null);
			fail("3.2");
		} catch (RuntimeException e) {
			//should fail
		} finally {
			manager.endRule(rules[3]);
		}
		//should still be able to end the rules
		manager.endRule(rules[2]);
		manager.endRule(rules[1]);
	}

	public void testSimpleOtherThreadAccess() {
		//ending a rule started on this thread from another thread
		ISchedulingRule rule1 = new IdentityRule();
		int[] status = {TestBarrier.STATUS_START};
		Thread endingThread = new Thread(new RuleEnder(rule1, status));
		manager.beginRule(rule1, null);

		endingThread.start();
		TestBarrier.waitForStatus(status, TestBarrier.STATUS_RUNNING);

		try {
			endingThread.join();
		} catch (InterruptedException e) {
		}
		//the thread should be dead now
		assertTrue("1.0", !endingThread.isAlive());

		//should be able to end the rule from this thread
		manager.endRule(rule1);

		//starting several rules on this thread, and trying to end them from other threads
		ISchedulingRule rules[] = new ISchedulingRule[3];

		rules[0] = new PathRule("/A");
		rules[1] = new PathRule("/A/B");
		rules[2] = new PathRule("/A/C");

		//end the rules right after starting them
		for (int i = 0; i < rules.length; i++) {
			manager.beginRule(rules[i], null);
			status[0] = TestBarrier.STATUS_START;
			Thread t = new Thread(new RuleEnder(rules[i], status));
			t.start();
			TestBarrier.waitForStatus(status, TestBarrier.STATUS_RUNNING);
			try {
				t.join();
			} catch (InterruptedException e1) {

			}
			//the thread should be dead now
			assertTrue("2." + i, !t.isAlive());
		}

		//try to end the rules when they are all started
		for (int i = 0; i < rules.length; i++) {
			status[0] = TestBarrier.STATUS_START;
			Thread t = new Thread(new RuleEnder(rules[i], status));
			t.start();
			TestBarrier.waitForStatus(status, TestBarrier.STATUS_RUNNING);
			try {
				t.join();
			} catch (InterruptedException e1) {

			}
			//the thread should be dead now
			assertTrue("3." + i, !t.isAlive());
		}

		//try to end the rules after manager.endRule() has been called
		for (int i = rules.length; i > 0; i--) {
			manager.endRule(rules[i - 1]);
			status[0] = TestBarrier.STATUS_START;
			Thread t = new Thread(new RuleEnder(rules[i - 1], status));
			t.start();
			TestBarrier.waitForStatus(status, TestBarrier.STATUS_RUNNING);
			try {
				t.join();
			} catch (InterruptedException e1) {

			}
			//the thread should be dead now
			assertTrue("4." + i, !t.isAlive());
		}
	}

	/**
	 * A job is running.  Wait until it is finished.
	 */
	private void waitForEnd(Job job) {
		int i = 0;
		while (job.getState() == Job.RUNNING) {
			Thread.yield();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {

			}
			Thread.yield();
			//sanity test to avoid hanging tests
			assertTrue("Timeout waiting for job to end", i++ < 100);
		}
	}

}