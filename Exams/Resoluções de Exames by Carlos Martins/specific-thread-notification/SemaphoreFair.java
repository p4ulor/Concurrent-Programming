/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Fair semaphore Java's implementation using "delegation of execution style" and
 *  optimizing the thread context switches using specific notifications.
 *
 *  Generate the class file with: javac SemaphoreFair.java
 *
 *  Carlos Martins, April 2017
 *
 **/

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

public final class SemaphoreFair {
	private final Lock mlock = new ReentrantLock();
	private int permits = 0;
	
	// For each waiting thread we hold an integer with the number of permits requested,
	// a condition variable where the thread is blocked and a boolean to signal that the
	// request was satisfied.
	private class Request {
		public int request;
		public Condition okToAcquire;
		public boolean done;
		
		public Request(int r) {
			request = r;
			okToAcquire = mlock.newCondition();
		}
	}

	// Queue that holds a node for each waiting thread.
	private final LinkedList<Request> queue = new LinkedList<Request>();
	
	// Initialize the semaphore.
	public SemaphoreFair(int initial) {
		if (initial > 0)
			permits = initial;
	}
	
	public SemaphoreFair() { this(0); }
	
	
	// Release all the waiting threads which request can be satified with the
	// actual available permits.
	private void releaseSatisfiedWaiters() {
		Request requester;
		while (queue.size() > 0 && permits >= (requester = queue.peek()).request) {
			permits -= requester.request;
			requester.done = true;
			queue.removeFirst();
			requester.okToAcquire.signal();		// specific thread notification
		}
	}
	
	// Acquire "acquires" permits within the specified time out
	private boolean doAcquire(int acquires, boolean timed, long nanosTimeout) throws InterruptedException {
		mlock.lock();
		try {
			// if the queue is empty and there are sufficient permits, acquire them immediately
			if (queue.size() == 0 && permits >= acquires) { // entry predicate
				permits -= acquires;
				return true;
			}
			// if a zero timeout was specified, return failure
			if (timed && nanosTimeout == 0)
				return false;
				
			// create a request item and enqueue it on request queue
			Request req = new Request(acquires);
			queue.addLast(req);
			do {
				try {
					if (timed) {
						if (nanosTimeout <= 0) {
							// timeout expired: give up processing
							queue.remove(req); 			// remove request item from queue
							releaseSatisfiedWaiters();	// release satisfied waiters, if any
							return false;						
						}
						nanosTimeout = req.okToAcquire.awaitNanos(nanosTimeout);
					} else
						req.okToAcquire.await();
				} catch (InterruptedException ie) {
					// if the request was already statisfied, we return normal delaying the exception
					if (req.done) {
						Thread.currentThread().interrupt();
						return true;
					}
					// the request was not satisfied, give up and propagate interrupt exception
					queue.remove(req); 			// remove request node from the queue
					releaseSatisfiedWaiters();  // release satisfied waiters, if any
					throw ie; 					// re-throw interrupted exception
				}
			} while (!req.done);
			return true;
		} finally {
			mlock.unlock();
		}
	}
	
	// Acquire "acquires" permits, without time out, that is infinite timeout
	public void acquire(int acquires) throws InterruptedException {
		doAcquire(acquires, false, 0);
	}

	public boolean acquire(int acquires, long millisTimeout) throws InterruptedException {
		boolean timed = millisTimeout >= 0;
		return doAcquire(acquires, timed, timed ? TimeUnit.MILLISECONDS.toNanos(millisTimeout) : 0L);
	}

	public boolean acquire(int acquires, long timeout, TimeUnit unit) throws InterruptedException {
		boolean timed = timeout >= 0;
		return doAcquire(acquires, timed, timed ? unit.toNanos(timeout) : 0L);
	}
	
	// Release "releases" permits
	public void release(int releases) {
		mlock.lock();
		try {
			//update the available permits and notify waiters because the shared
			// state was mofified
			permits += releases;
			releaseSatisfiedWaiters();
		} finally {
			mlock.unlock();
		}
	}
	
	
	// Test code !!!
	
	private static boolean testFairness() throws InterruptedException {

		final int RUN_TIME = 10 * 1000;
		final int SETUP_TIME = 20;
		final int THREADS = 50;
		final int MIN_TIMEOUT = 1;
		final int MAX_TIMEOUT = 10;
		

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		CountDownLatch startEvent = new CountDownLatch(1);	// control simultaneus start of all thredas
		SemaphoreFair sem = new SemaphoreFair(THREADS);
		AtomicInteger totalTimeouts = new AtomicInteger(0);
		
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {
				Random random = new Random(tid);
			
				// Wait until start event is set
				try {
					startEvent.await();								
					long endTime = System.currentTimeMillis() + RUN_TIME;				
					do {
						do {
							// each threads acquires the number of permite equal to its index plus one
							if (sem.acquire(tid + 1, random.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT))
								break;
							totalTimeouts.incrementAndGet();
						} while (true);
						Thread.yield();
						sem.release(tid + 1);
						if ((++privateCounters[tid] % 1000) == 0) {
							System.out.printf("[#%02d]", tid);
						}
					} while (System.currentTimeMillis() < endTime);
				} catch (InterruptedException ie) {
					// this never happens!
				}					
			});
			tthrs[i].start();
		}
		
		// Wait until all test threads have been started and then set the
		// start event.
		Thread.sleep(SETUP_TIME);
		startEvent.countDown();
		
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		
		// Show results
		int total = 0;
		System.out.println("\nPrivate counters:");
		for (int i = 0; i < THREADS; i++) {
			if (i != 0 && (i % 6) == 0)
				System.out.println();
			System.out.printf("[#%02d:%4d] ", i, privateCounters[i]);
			total += privateCounters[i];
		}
		System.out.printf("\n-- total acquisitions/releases: %d, timeouts: %d", total, totalTimeouts.get());
		return true;
	}


	public static void main(String... args) throws InterruptedException {
		System.out.printf("\n-->Test semaphore fairness: %s%n", testFairness() ? "passed" : "failed");
	}
}
