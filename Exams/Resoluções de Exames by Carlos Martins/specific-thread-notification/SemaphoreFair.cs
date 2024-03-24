/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Fair semaphore C#'s implementation using the "delegation of execution style"
 *  and optimizing thread context switches with specific notification.
 *
 *  Generate executable with: csc SemaphoreFair.cs TimeoutHolder.cs MonitorEx.cs
 *
 *  Carlos Martins, April 2018
 *
 **/

using System;
using System.Collections.Generic;
using System.Threading;

public sealed class SemaphoreFair {
	private readonly object _lock = new object();
	private int permits = 0;
	
	// Queue of waiting threads.
	// For each waiting thread we hold an integer with the number of
	// permits requested, and a boolean to signal that the request was satisfied.
	
	private class Request {
		internal int request;
		internal bool done;
	}

	private readonly LinkedList<Request> queue = new LinkedList<Request>();
	
	// The constructor.
	public SemaphoreFair(int initial) {
		if (initial > 0)
			permits = initial;
	}
	
	// Release the waiting threads which request can be satified with the actual permits.
	private void ReleaseSatisfiedWaiters() {
		LinkedListNode<Request> wnode;
		while (queue.Count > 0 && permits >= (wnode = queue.First).Value.request) {
			permits -= wnode.Value.request;
			queue.RemoveFirst();
			wnode.Value.done = true;
			MonitorEx.Pulse(_lock, wnode);	// specific notification
		}
	}
	
	// Acquire n permits within the specified time out
	public bool Acquire(int acquires, int timeout = Timeout.Infinite) {
		lock(_lock) {
			// if the queue is empty and there are sufficient permits,
			// acquire them immediately
			if (queue.Count == 0 && permits >= acquires) { // entry predicate
				permits -= acquires;
				return true;
			}
			// if a null timeout was specified, return failure
			if (timeout == 0)
				return false;
				
			// enqueue our request item
			LinkedListNode<Request> rn = queue.AddLast(new Request() { request = acquires });

			// use a TimeoutHolder instance in order to support timeout adjustments
			TimeoutHolder th = new TimeoutHolder(timeout);
			do {
				try {
					if ((timeout = th.Value) == 0) {
						// timeout expired: give up processing
						queue.Remove(rn); // remove request item from queue
						ReleaseSatisfiedWaiters(); // release satisfied waiters, if any
						return false;						
					}
					MonitorEx.Wait(_lock, rn, timeout);
				} catch (ThreadInterruptedException) {
					if (rn.Value.done) {
						Thread.CurrentThread.Interrupt();
						return true;
					}
					// interrupted exception: give up processing
					queue.Remove(rn); // remove request node from the queue
					ReleaseSatisfiedWaiters();  // release satisfied waiters, if any
					throw; // re-throw interrupted exception
				}
			} while (!rn.Value.done);
			return true;
		}
	}
		
	// Release n permits
	public void Release(int releases){
		lock(_lock) {
			//update the available permits and notify waiters because the shared
			// state was mofified
			permits += releases;
			ReleaseSatisfiedWaiters();
		}
	}
}

internal class SemaphoreFairStressTests {
	
	private static bool TestFairness() {

		const int RUN_TIME = 10 * 1000;
		const int SETUP_TIME = 20;
		const int THREADS = 50;
		const int MIN_TIMEOUT = 5;
		const int MAX_TIMEOUT = 20;
		

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		ManualResetEventSlim startEvent = new ManualResetEventSlim(false);
		SemaphoreFair sem = new SemaphoreFair(THREADS);
		int totalTimeouts = 0;
		
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Random random = new Random(tid);
			
				// Wait until start event is set
				startEvent.Wait();
			
				int endTime = Environment.TickCount + RUN_TIME;
				
				do {
					do {
						if (sem.Acquire(tid + 1, random.Next(MIN_TIMEOUT, MAX_TIMEOUT)))
							break;
						Interlocked.Increment(ref totalTimeouts);
					} while (true);
					Thread.Yield();
					sem.Release(tid + 1);
					if ((++privateCounters[tid] % 200) == 0)
						Console.Write($"[#{tid}]");
				} while (Environment.TickCount < endTime);					
			});
			tthrs[i].Start();
		}
		
		// Wait until all test threads have been started and then set the
		// start event.
		Thread.Sleep(SETUP_TIME);
		startEvent.Set();
		
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();
		
		// Show results
		int total = 0;
		Console.WriteLine("\n\nPrivate counters:");
		for (int i = 0; i < THREADS; i++) {
			if (i != 0 && (i % 5) == 0)
				Console.WriteLine();
			Console.Write($"[#{i:D2}:{privateCounters[i],4}] ");
			total += privateCounters[i];
		}
		Console.WriteLine($"\n-- total acquisitions/releases: {total}, timeouts: {totalTimeouts}");
		return true;
	}


	static void Main() {
		Console.WriteLine("\n-->Test semaphore fairness: {0}",
						  TestFairness() ? "passed" : "failed");
	}
}
