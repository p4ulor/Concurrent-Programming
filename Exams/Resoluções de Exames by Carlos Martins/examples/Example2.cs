/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Unfair semaphore implementation using the pattern presented in the
 *  Example 2 in "Sincronização com Monitores na CLI e na Infra-estrutura Java".
 *
 *  Generate executable with: csc Example2.cs TimeoutHolder.cs
 *
 *  Carlos Martins, April 2018
 *
 **/

using System;
using System.Threading;

internal sealed class Semaphore_ {
	private readonly object monitor = new object();
	private int permits;
	
	// Constructor
	public Semaphore_ (int initial = 0) {
		if (initial > 0)
			permits = initial;
	}
	
	// Acquire "acquires" permits
	public bool Acquire(int acquires, int timeout = Timeout.Infinite) {
		lock(monitor) {
			// if there are sufficient permits, update semaphore value and return success
			if (permits >= acquires) {
				permits -= acquires;
				return true;
			}
			
			// test if it is about immediate acquisition and return failure if it is the case
			if (timeout == 0)
				return false;

			// create an instance of TimeoutHolder to support timeout adjustments
			TimeoutHolder th = new TimeoutHolder(timeout);
			
			// loop until acquire n permits, the specified timeout expires or thread
			// is interrupted
			do {
				if ((timeout = th.Value) == 0)
					return false;			// timeout expired
				Monitor.Wait(monitor, timeout);
			} while	(permits < acquires);
			// permits available
			permits -= acquires;
			return true;
		}
	}
	
	// Release "releases" permits
	public void Release(int releases){
		lock(monitor) {
			permits += releases;
			Monitor.PulseAll(monitor);
		}
	}
}

public class Example2 {
	
	private static bool TestUnfairness() {

		const int RUN_TIME = 10 * 1000;
		const int SETUP_TIME = 20;
		const int THREADS = 50;
		const int MIN_TIMEOUT = 1;
		const int MAX_TIMEOUT = 10;
		

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		ManualResetEventSlim startEvent = new ManualResetEventSlim(false);
		Semaphore_ sem = new Semaphore_(THREADS);
		int totalTimeouts = 0;
		
		Console.WriteLine("-->test semaphore unfairness");
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
					if ((++privateCounters[tid] % 200) == 0) {
						Console.Write($"[#{tid:D2}]");
					}
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
			else if (i != 0)
				Console.Write(' ');
			Console.Write($"[#{i:D2}:{privateCounters[i],4}]");
			total += privateCounters[i];
		}
		Console.WriteLine($"\n--total acquisitions/releases: total, timeouts: {totalTimeouts}");
		return true;
	}


	public static void Main() {
		Console.WriteLine("\n-->Test semaphore unfairness: {0}",
						  TestUnfairness() ? "passed" : "failed");
	}
}
