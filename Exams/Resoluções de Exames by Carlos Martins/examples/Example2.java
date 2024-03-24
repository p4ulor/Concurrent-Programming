/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Unfair semaphore implementation using the pattern presented in the
 *  Example 2 of "Sincronização com Monitores na CLI e na Infra-estrutura Java".
 *
 *  Generate executable with: javac Example2.java TimeoutHolder.java
 *  Execute with: java Example2
 *
 *  Carlos Martins, April 2018
 *
 **/

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

final class Semaphore_ {
	private final Object monitor = new Object();
	private int permits;
	
	// Constructors
	public Semaphore_(int initial) {
		if (initial > 0)
			permits = initial;
	}
	
	public Semaphore_() {}

	// Acquire "acquires" permits with timeout
	public boolean acquire(int acquires, long timeout, TimeUnit unit) throws InterruptedException {
		synchronized(monitor) {
			// if there are sufficient permits, update semaphore value and return success
			if (permits >= acquires) {
				permits -= acquires;
				return true;
			}
			
			// test if it is about immediate acquisition and return failure if it is the case
			if (timeout == 0)
				return false;

			// create an instance of TimeoutHolder to support timeout adjustments
			TimeoutHolder th = new TimeoutHolder(timeout, unit);
			
			// loop until acquire n permits, the specified timeout expires or thread
			// is interrupted
			do {
				if (th.isTimed()) {
					if ((timeout = th.value()) == 0)
						return false;			// timeout expired
					monitor.wait(timeout);
				} else
					monitor.wait();
			} while	(permits < acquires);
			// permits available
			permits -= acquires;
			return true;
		}
	}
	
	// Acquire "acquires" permits with a millis timeout 
	public boolean acquire(int acquires, long millisTimeout) throws InterruptedException {
		return acquire(acquires, millisTimeout, TimeUnit.MILLISECONDS);
	}
	
	// Acquire "acquires" permits without timeout 
	public void acquire(int acquires) throws InterruptedException {
		acquire(acquires, -1L, TimeUnit.MILLISECONDS);
	}
	
	// Release "releases" permits
	public void release(int releases){
		synchronized(monitor) {
			permits += releases;
			monitor.notifyAll();
		}
	}
}

public class Example2 {
	
	private static boolean testUnfairness() throws InterruptedException {

		final int RUN_TIME = 10 * 1000;
		final int SETUP_TIME = 20;
		final int THREADS = 50;
		final int MIN_TIMEOUT = 1;
		final int MAX_TIMEOUT = 10;
		

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		CountDownLatch start = new CountDownLatch(1);
		Semaphore_ sem = new Semaphore_(THREADS);
		AtomicInteger totalTimeouts = new AtomicInteger(0);
		
		System.out.println("-->test semaphore unfairness");
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {
				Random random = new Random(tid);			
				long deadline = System.currentTimeMillis() + RUN_TIME;
				try {
					// Wait until start event is set
					start.await();
					do {
						do {
							if (sem.acquire(tid + 1, random.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT))
								break;
							totalTimeouts.incrementAndGet();
						} while (true);
						Thread.yield();
						sem.release(tid + 1);
						if ((++privateCounters[tid] % 1000) == 0)
							System.out.printf("[#%02d]", tid);
					} while (System.currentTimeMillis() < deadline);
				} catch (InterruptedException ie) {
					// this never happens
				}				
			});
			tthrs[i].start();
		}
		
		// Wait until all test threads have been started and then set the
		// start event.
		Thread.sleep(SETUP_TIME);
		start.countDown();
		
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		
		// Show results
		int total = 0;
		System.out.println("\n\nPrivate counters:");
		for (int i = 0; i < THREADS; i++) {
			if ((i % 5) == 0)
				System.out.println();
			else if (i != 0)
				System.out.print(' ');
			System.out.printf("[#%02d:%5d]", i, privateCounters[i]);
			total += privateCounters[i];
		}
		System.out.printf("%n--total acquisitions/releases: %d, timeouts: %d%n", total, totalTimeouts.get());
		return true;
	}


	public static void main(String... args) throws InterruptedException {
		System.out.printf("%n-->Test semaphore unfairness: %s%n",
						  testUnfairness() ? "passed" : "failed");
	}
}
