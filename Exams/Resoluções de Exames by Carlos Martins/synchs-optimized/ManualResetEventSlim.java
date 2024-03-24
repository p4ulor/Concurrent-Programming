/**
 *
 *  ISEL, LEIC, Concurrent Programming
 *
 *  ManualResetEventSlim with fast-path optimization
 * 
 *  Carlos Martins, May 2018
 *
 **/

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.io.IOException;

public final class ManualResetEventSlim {
	private volatile boolean signaled;
	private int setVersion;
	private volatile int waiters;
	private final Lock lock = new ReentrantLock();
	private final Condition wcv = lock.newCondition();
	
	
	public ManualResetEventSlim(boolean initial) {
		signaled = initial;
	}
	
	// Wait until the event is signalled
	public boolean await(int timeout, TimeUnit unit) throws InterruptedException {
	
		// If the event is signalled, return true
		if (signaled)
			return true;
		
		// the event is not signalled; if a null time out was specified, return failure.
		if (timeout == 0)
			return false;

		// process timeout
		boolean timed = timeout > 0;
		long nanosTimeout = timed ? unit.toNanos(timeout) : 0L;

		lock.lock();	
		try {
		
			// loop until the event is signalled, the specified timeout expires or
			// the thread is interrupted.
			
			int sv = setVersion;
			waiters += 1;
			
			// Java guarantees the previous volatile write is made visible before the
			// next volatile read.
			
			try {
				do {
					if (signaled || sv != setVersion)		/* this works fine! */
						return true;
				
					// check for timeout
					if (timed) {
						if (nanosTimeout <= 0)
							// the specified time out elapsed, so return failure
							return false;
						nanosTimeout = wcv.awaitNanos(nanosTimeout);
					} else
						wcv.await();
				} while (true);
			} finally {
				// anyway, decrement the number of waiters, and check the event again.
				waiters--;
			}
		} finally {
			lock.unlock();
		}
	}

	public boolean await(int timeoutMillis)  throws InterruptedException {
		return await(timeoutMillis, TimeUnit.MILLISECONDS);
	}
	
	public void await()  throws InterruptedException {
		await(-1, TimeUnit.MILLISECONDS);
	}
	
	// Set the event to the signalled state
	public void set(){
		if (!signaled) {
			signaled = true;

			// Java guarantees the previous volatile write is made visible before the
			// next volatile read.
			if (waiters != 0) {		
				lock.lock();
				try {
					if (waiters > 0) {
						setVersion++;
						wcv.signalAll();
					}
				} finally {
					lock.unlock();
				}
			}
		}
	}

	// Reset the event
	public void reset() { signaled = false; }
	

	/**
	 * Test Code
	 */
	
	private static final int MIN_TIMEOUT = 30;
	private static final int MAX_TIMEOUT = 500;
	private static final int SETUP_TIME = 50;
	private static final int DEVIATION_TIME = 20;
	private static final int EXIT_TIME = 100;
	private static final int THREADS = 10;

	/*
	 * Test normal wait
	 */		
	private static boolean testWait() throws InterruptedException {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim mrevs = new ManualResetEventSlim(false);
		
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {				
				System.out.printf("-->%02d, started...%n", tid);
				try {
					mrevs.await();
				} catch (InterruptedException ie) {
					System.out.printf("=>#%02d was interrupted while waiting!%n", tid);
				}
				System.out.printf("<--%02d, exiting...%n", tid);
			});
			tthrs[i].start();
		}
		
		// Sleep for a while before set the manual-reset event.	
		Thread.sleep(SETUP_TIME);
		mrevs.set();
		Thread.sleep(EXIT_TIME);
		boolean success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].isAlive()) {
				success = false;
				System.out.printf("***#%d is still alive so it will be interrupted!%n", i);
				tthrs[i].interrupt();
			}
		}

		// Wait until all test threads have been exited.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		return success;
	}
	
	/*
	 * Test timed wait.
	 */ 
	private static boolean testTimedWait() throws InterruptedException {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim mrevs = new ManualResetEventSlim(false);
				
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {
				Random rnd = new Random(tid);
				
				System.out.printf("-->#%02d, started...%n", tid);
				boolean timedOut = false;
				try {
					timedOut = !mrevs.await(rnd.nextInt(MAX_TIMEOUT + MIN_TIMEOUT));
				} catch (InterruptedException ie) {
					System.out.printf("=>#%02d, was interrupted while waiting!%n", tid);
				}
				System.out.printf("--#%02d, %s%n", tid, timedOut ? "timed out" : "interrupted");
				System.out.printf("<--#%02d, exiting...%n", tid);
			});
			tthrs[i].start();
		}
		
		// Sleep ...
		
		Thread.sleep(MAX_TIMEOUT + DEVIATION_TIME);			// test succeeds!
		//Thread.sleep(MIN_TIMEOUT - DEVIATION_TIME);		// test fails!
		boolean success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].isAlive()) {
				success = false;
				System.out.printf("***#%02d is still alive so it will be interrupted%n", i);
				tthrs[i].interrupt();
			}
		}
		
		// Wait until all test threads have been exited.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		
		return success;
	}
	
	/*
	 * Test Set followed immediately by Reset
	 */
	private static boolean testSetFollowedByReset() throws InterruptedException {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim mrevs = new ManualResetEventSlim(false);
		
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {
				System.out.printf("-->#%02d, started...%n", tid);
				try {
					mrevs.await();
				} catch (InterruptedException ie) {
					System.out.printf("=>#%02d, was interrupted while waiting!%n", tid);
				}
				System.out.printf("<--#%02d, exiting...%n", tid);
			});
			tthrs[i].start();
		}
		
		// Sleep for a while before set the manual-reset event.
		Thread.sleep(SETUP_TIME);
		mrevs.set();
		mrevs.reset();
		Thread.sleep(EXIT_TIME + 500);
		boolean success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].isAlive()) {
				success = false;
				System.out.printf("***#%02d is still alive so it will be interrupted%n", i);
				tthrs[i].interrupt();
			}
		}

		// Wait until all test threads have been exited.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		return success;
	}
	
	//
	// Run manual-reset event slim tests.
	//
	
	public static void main(String... args) throws InterruptedException {
		System.out.printf("%n>> Test Wait: %s%n", testWait() ? "passed" : "failed");
		System.out.printf("%n>> Test Timed Wait: %s\n", testTimedWait() ? "passed" : "failed");
		System.out.printf("%n>> Test Set Followed by Reset: %s\n", testSetFollowedByReset() ? "passed" : "failed");
	}
}
