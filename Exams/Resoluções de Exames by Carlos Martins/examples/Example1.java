/**
 *  ISEL, LEIC, Concurrent Programming
 *
 * Test code for the Semaphore implemented by the code presented in
 * the Example 1 described in "Sincronização com Monitores na CLI e na Infra-estrutura Java".
 *
 *  Generate class file: javac Example1.java TimeoutHolder.java
 *  Execute with: java Example1
 *
 *  Carlos Martins, April 2018
 *
 **/

import java.util.Random;

final class Semaphore_ {
	private final Object monitor = new Object();
	private int permits;

	// Constructors
	public Semaphore_(int initial) {
		if (initial > 0)
			permits = initial;
	}
	
	public Semaphore_() { this(0); }
	
	// Acquire one permit from the semaphore
	public boolean acquire(long timeout) throws InterruptedException {
		synchronized(monitor) {
			if (permits > 0) {
				permits--;
				return true;
			}
			// if a null time out was specified, return failure
			if (timeout == 0) 
				return false;
			
			// wraps the timeout ...
			TimeoutHolder th = new TimeoutHolder(timeout);
			
			// loop until one permit is available, the specified timeout expires or
			// the thread is interrupted.
			do {
				if (th.isTimed()) {
					if ((timeout = th.value()) == 0)
						return false;
					monitor.wait(timeout);
				} else
					monitor.wait();
			} while (permits == 0);
			// permits available, decrement and return success
			permits--;
			return true;
		}
	}
	
	public void acquire() throws InterruptedException {
		acquire(-1L);
	}
	
	// Release one permit
	public void release(){
		synchronized(monitor) {
			permits++;
			// only one thread can proceed execution
			// since Java monitors do not lose notifications it is not necessary to
			// regenerate notifications on the acquire method
			monitor.notify();
		}
	}
}

public class Example1 {
	
	private static boolean testSemaphoreAsLock() throws InterruptedException {

		final int RUN_TIME = 10 * 1000;
		final int THREADS = 20;
		final int MIN_TIMEOUT = 0;
		final int MAX_TIMEOUT = 10;

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		int[] timeouts = new int[THREADS];
		int[] sharedCounter = new int[1];
		Semaphore_ slock = new Semaphore_(1);

		//
		// Create and start acquirer/releaser threads
		//
		System.out.println("\n-->test semaphore as a lock");
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			tthrs[i] = new Thread(() -> {
				Random rnd = new Random(tid);
				long endTime = System.currentTimeMillis() + RUN_TIME;
				try {
					do {
						do {
							if (slock.acquire(rnd.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT))
								break;
							timeouts[tid]++;
						} while (true);
						sharedCounter[0]++;
						Thread.yield();
						slock.release();
						if ((++privateCounters[tid] % 500) == 0)
							System.out.printf("[#%02d]", tid);
						else
							Thread.sleep(rnd.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT);
					} while (System.currentTimeMillis() < endTime);
				} catch (InterruptedException ie) {
					// this never happens
				}				
			});
			tthrs[i].start();
		}
		
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].join();
		
		// Compute results
		
		System.out.println("\nPrivate counters:");
		int sum = 0;
		for (int i = 0; i < THREADS; i++) {
			sum += privateCounters[i];
			if ((i % 5) == 0)
				System.out.println();
			else
				System.out.print(' ');
			System.out.printf("[#%02d: %04d/%d]", i, privateCounters[i], timeouts[i]);
		}
		return sum == sharedCounter[0];
	}

	static volatile boolean running = true;

	private static boolean testSemaphoreInAProducerConsumerContext() throws InterruptedException {

		final int RUN_TIME = 10 * 1000;
		final int EXIT_TIME = 50;		
		final int PRODUCER_THREADS = 10;
		final int CONSUMER_THREADS = 20;
		final int MIN_TIMEOUT = 0;
		final int MAX_TIMEOUT = 2;
 
		Thread[] producers = new Thread[PRODUCER_THREADS];
		Thread[] consumers = new Thread[CONSUMER_THREADS];
		int[] producerCounters = new int[PRODUCER_THREADS];
		int[] consumerCounters = new int[CONSUMER_THREADS];
		Semaphore_ free = new Semaphore_(1), occupied = new Semaphore_(0);

		running = true;
		System.out.println("\n-->test semaphore in a producer/consumer context");
		// Create and start consumer threads.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			final int tid = i;
			consumers[i] = new Thread(() -> {
				Random rnd = new Random(tid);
				do {
					try {
						occupied.acquire();
					} catch (InterruptedException ie) {
						break;
					}
					Thread.yield();
					free.release();
					if ((++consumerCounters[tid] % 500) == 0) {
						System.out.printf("[#c%02d]", tid);
					} else {
						try {
							Thread.sleep(rnd.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT);
						} catch (InterruptedException ie) {
							break;
						}
					}
				} while (running);					
			});
			consumers[i].setPriority(Thread.MAX_PRIORITY);
			consumers[i].start();
		}
		
		// Create and start producer threads.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			int tid = i;
			producers[i] = new Thread(() -> {
				Random rnd = new Random(tid);
				do {
					try {
						free.acquire();
					} catch (InterruptedException ie) {
						break;
					}
					Thread.yield();
					occupied.release();
					if ((++producerCounters[tid] % 500) == 0) {
						System.out.printf("[#p%02d]", tid);
					} else {
						try {
							Thread.sleep(rnd.nextInt(MAX_TIMEOUT) + MIN_TIMEOUT);
						} catch (InterruptedException ie) {
							break;
						}
					}
				} while (running);					
			});
			producers[i].start();
		}
		
		// run the test for a while
		Thread.sleep(RUN_TIME);
		running = false;
		Thread.sleep(EXIT_TIME);
		
		// Wait until all producer threads have been terminated.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			if (producers[i].isAlive()) {
				producers[i].interrupt();
			}
			producers[i].join();
		}

		// Wait until all consumer threads have been terminated.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			if (consumers[i].isAlive()) {
				consumers[i].interrupt();
			}
			consumers[i].join();
		}

		
		// Compute results
		
		System.out.println("\nConsumer counters:");
		int consumptions = 0;
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			consumptions += consumerCounters[i];
			if (i != 0 && (i % 5) == 0)
				System.out.println();
			else if (i != 0)
				System.out.print(' ');
			System.out.printf("[#c%02d: %04d]", i, consumerCounters[i]);
		}
		if (occupied.acquire(0)) {
			consumptions++;
		}
		
		System.out.println("\n\nProducer counters:");
		int productions = 0;
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			productions += producerCounters[i];
			if (i != 0 && (i % 5) == 0)
				System.out.println();
			else if (i != 0)
				System.out.print(' ');
			System.out.printf("[#p%02d: %04d]", i, producerCounters[i]);
		}
		System.out.printf("%n--productions: %d, consumptions: %d", productions, consumptions);
		return consumptions == productions;
	}
		
	public static void main(String... args) throws InterruptedException {
		
		System.out.printf("\n-->test semaphore as a lock: %s%n",
							  testSemaphoreAsLock() ? "passed" : "failed");
		
		System.out.printf("\n-->test semaphore in a producer/consumer context: %s%n",
						  testSemaphoreInAProducerConsumerContext() ? "passed" : "failed");
	}
}
