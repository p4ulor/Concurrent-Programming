/**
 *
 *  ISEL, LEIC, Concurrent Programming
 *
 *  ManualResetEvent slim with optimized fast-paths.
 *
 *  Carlos Martins, May 2018
 *
 **/

using System;
using System.Threading;

public class ManualResetEventSlim_ {
	private volatile bool signaled;
	private volatile int waiters;
	private int setVersion;
	
	public ManualResetEventSlim_(bool initial) {
		signaled = initial;
	}
	
	// Wait until the event is signalled
	public bool Wait(int timeout = Timeout.Infinite) {
	
		// If the event is signalled, return true
		if (signaled)
			return true;
		
		// the event is not signalled; if a null time out was specified, return failure.
		if (timeout == 0)
			return false;

		// if a time out was specified, get a time reference
		TimeoutHolder th  = new TimeoutHolder(timeout);
		
		lock(this) {
		
			// loop until the event is signalled, the specified timeout expires or
			// the thread is interrupted.
			
			int sv = setVersion;
			waiters++;
			
			// .NET : before we read the event state, we need to make sure that
			//		  the incremented of *waiters* is visible to the setter threads.
			//
			
			Interlocked.MemoryBarrier();
			try {
				do {
					if (signaled || sv != setVersion)
						return true;
				
					// check for timeout
					if ((timeout = th.Value) == 0)
						// the specified time out elapsed, so return failure
						return false;
				
					Monitor.Wait(this, timeout);
				} while (true);
			} finally {
				// anyway, decrement the number of waiters, and check the event again.
				waiters--;
			}
		}
	}
		
	// Set the event to the signalled state
	public void Set(){
		if (!signaled) {
			signaled = true;

			// .NET : before we read the *waiters*, we need to make sure that
			//		  the set of the event is visible to the waiter threads.
		
			Interlocked.MemoryBarrier();
			if (waiters != 0) {		
				lock(this) {
					if (waiters > 0) {
						setVersion++;
						Monitor.PulseAll(this);
					}
				}
			}
		}
	}

	// Reset the event
	public void Reset() { signaled = false; }
}
			
public static class ManualResetEventSlim_Tests {
	
	private const int MIN_TIMEOUT = 1;
	private const int MAX_TIMEOUT = 500;
	private const int SETUP_TIME = 50;
	private const int DEVIATION_TIME = 20;
	private const int EXIT_TIME = 100;
	private const int THREADS = 10;

	/*
	 * Test normal wait
	 */

	private static bool TestWait() {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);
		
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				string stid = string.Format("#{0}", tid);
				
				Console.WriteLine("--{0}, started...", stid);
				try {
					mrevs.Wait();
				} catch (ThreadInterruptedException) {
					Console.WriteLine("-->{0}, was interrupted while waiting!", stid);
				}
				try {
					Console.WriteLine("{0}, exiting...", stid);
				} catch (ThreadInterruptedException) {}
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep for a while before set the manual-reset event.
		//
		
		Thread.Sleep(SETUP_TIME);
		mrevs.Set();
		Thread.Sleep(EXIT_TIME);
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine("++#" + i + " is still alive so it will be interrupted");
				tthrs[i].Interrupt();
			}
		}

		//
		// Wait until all test threads have been exited.
		//
		
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();
		return success;
	}
	
	//
	// Test timed wait.
	//
	  
	private static bool TestTimedWait() {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);
				
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				string stid = string.Format("#{0}", tid);
				Random rnd = new Random(tid);
				
				Console.WriteLine("{0}, started...", stid);
				bool timedOut = false;
				try {
					timedOut = !mrevs.Wait(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
				} catch (ThreadInterruptedException) {
					Console.WriteLine("-->{0}, was interrupted while waiting!", stid);
				}
				try {
					Console.WriteLine("{0}, timed out = {1}", stid, timedOut);
					Console.WriteLine("{0}, exiting...", stid);
				} catch (ThreadInterruptedException) {}
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep ...
		//
		
		Thread.Sleep(MAX_TIMEOUT + DEVIATION_TIME);
		//Thread.Sleep(MIN_TIMEOUT - DEVIATION_TIME);
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine("++#" + i + " is still alive so it will be interrupted");
				tthrs[i].Interrupt();
			}
		}
		
		//
		// Wait until all test threads have been exited.
		//
		
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();
		
		return success;
	}
	
	/*
	 * Test Set followed immediately by Reset
	 */

	private static bool TestSetFollowedByReset() {
		Thread[] tthrs = new Thread[THREADS];
		//ManualResetEventSlim mrevs = new ManualResetEventSlim(false);
		ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);
		
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				string stid = string.Format("#{0}", tid);
				
				Console.WriteLine("--{0}, started...", stid);
				try {
					mrevs.Wait();
				} catch (ThreadInterruptedException) {
					Console.WriteLine("-->{0}, was interrupted while waiting!", stid);
				}
				try {
					Console.WriteLine("{0}, exiting...", stid);
				} catch (ThreadInterruptedException) {}
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep for a while before set the manual-reset event.
		//
		
		Thread.Sleep(SETUP_TIME);
		mrevs.Set();
		mrevs.Reset();
		Thread.Sleep(EXIT_TIME + 500);
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine("++#" + i + " is still alive so it will be interrupted");
				tthrs[i].Interrupt();
			}
		}

		//
		// Wait until all test threads have been exited.
		//
		
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();
		return success;
	}
			
	//
	// Run manual-reset event slim tests.
	//
	
	public static void Main() {
		Console.WriteLine("\n>> Test Wait: {0}\n", TestWait() ? "passed" : "failed");
		Console.WriteLine("\n>> Test Timed Wait: {0}\n", TestTimedWait() ? "passed" : "failed");
		Console.WriteLine("\n>> Test Set Followed by Reset: {0}\n", TestSetFollowedByReset() ? "passed" : "failed");
	}
}
