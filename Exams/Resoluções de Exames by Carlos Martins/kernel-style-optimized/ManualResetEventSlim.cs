/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Manual reset event implementation "kernel style" and following a
 *  reasoning based on the idea of "generation".
 *
 *  Generate executable with: csc ManualResetEventSlim.cs TimeoutHolder.cs
 *
 *  Carlos Martins, October 2018
 *
 **/

using System;
using System.Threading;

public class ManualResetEventSlim_ {
	// synchronization state
	private bool state;	
	// current generation
	private int stateGeneration;
	// implicit .NET monitor
	private Object monitor = new Object();
	
	public ManualResetEventSlim_(bool initial) { state = initial; }
	
	// Wait until the event is signalled
	public bool Wait(int timeout = Timeout.Infinite) {	
		lock(monitor) {
			// If the event is already signalled, return true
			if (state)
				return true;
		
			// create an instance of TimeoutHolder to support timeout adjustments
			TimeoutHolder th = new TimeoutHolder(timeout);
		
			// loop until the event is signalled, the specified timeout expires or
			// the thread is interrupted.
			
			int arriveGeneration = stateGeneration;
			do {
				if ((timeout = th.Value) == 0)
					return false;		// timeout expired
				Monitor.Wait(monitor, timeout);
			} while (arriveGeneration == stateGeneration);
			return true;
		}
	}
	
	// Set the event to the signalled state
	public void Set(){
		lock(monitor) {
			if (!state) {
				state = true;
				stateGeneration++;
				Monitor.PulseAll(monitor);
			}
		}
	}

	// Reset the event
	public void Reset() {
		lock(monitor)
			state = false;
	}
}
			
public static class ManualResetEventSlimTests {
	
	private const int MIN_TIMEOUT = 30;
	private const int MAX_TIMEOUT = 500;
	private const int SETUP_TIME = 50;
	private const int DEVIATION_TIME = 20;
	private const int EXIT_TIME = 100;
	private const int THREADS = 20;

	/*
	 * Test normal wait
	 */

	private static bool TestWait() {
		Thread[] tthrs = new Thread[THREADS];
		ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);	// our implementation
//		ManualResetEventSlim mrevs = new ManualResetEventSlim(false);	// BCL implementation
		
		Console.WriteLine("-->Test wait");
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Console.WriteLine($"--{tid}, started...");
				try {
					mrevs.Wait();
				} catch (ThreadInterruptedException) {
					Console.WriteLine($"-->{tid} was interrupted while waiting!");
				}
				try {
					Console.WriteLine($"{tid}, exiting...");
				} catch (ThreadInterruptedException) {}
				/*
				if (tid == 7) 
					do {
						try {
							Thread.Sleep(0);
						} catch (ThreadInterruptedException) { break; }
					} while(true);
				*/
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep for a while before set the manual-reset event.
		//
		
		Thread.Sleep(SETUP_TIME);
		Console.WriteLine("-- set event");
		mrevs.Set();
		Thread.Sleep(EXIT_TIME);
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine($"++#{i} is still alive, so it will be interrupted");
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
		ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);		// our implementation
//		ManualResetEventSlim mrevs = new ManualResetEventSlim(false);		// BCL implementation
					
		Console.WriteLine("-->Test timed wait");
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Random rnd = new Random(tid);
				
				Console.WriteLine($"{tid}, started...");
				bool timedOut = false;
				try {
					timedOut = !mrevs.Wait(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
				} catch (ThreadInterruptedException) {
					Console.WriteLine($"-->{tid}, was interrupted while waiting!");
				}
				try {
					Console.WriteLine($"{tid}, timed out = {timedOut}");
					Console.WriteLine($"{tid}, exiting...");
				} catch (ThreadInterruptedException) {}
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep ...
		//
		
		//Thread.Sleep(MAX_TIMEOUT + DEVIATION_TIME);			// all waiters must time out
		//Thread.Sleep(MIN_TIMEOUT - DEVIATION_TIME);		// none waiter must times out
		Thread.Sleep((MIN_TIMEOUT + MAX_TIMEOUT) / 2);	// some waiters time out
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine($"++#{i} is still alive, so it will be interrupted");
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
        ManualResetEventSlim_ mrevs = new ManualResetEventSlim_(false);		// our implementation
    	//ManualResetEventSlim mrevs = new ManualResetEventSlim(false);		// BCL implementation - fails!

        Console.WriteLine("-->Test set followed by reset");
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Console.WriteLine($"--{tid}, started...");
				try {
                    mrevs.Wait();
                } catch (ThreadInterruptedException) {
					Console.WriteLine($"-->{tid} was interrupted while waiting!");
				}
				try {
					Console.WriteLine($"{tid}, exiting...");
				} catch (ThreadInterruptedException) {}
			});
			tthrs[i].Start();
		}
		
		//
		// Sleep for a while before set the manual-reset event.
		//
		
		Thread.Sleep(SETUP_TIME);
		mrevs.Set();
		//Thread.Sleep(20);
		mrevs.Reset();
		Thread.Sleep(EXIT_TIME + 500);
		bool success = true;
		for (int i = 0; i < THREADS; i++) {
			if (tthrs[i].IsAlive) {
				success = false;
				Console.WriteLine($"++#{i} is still alive so it will be interrupted");
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
	//	Console.WriteLine("\n-->Test wait: {0}\n", TestWait() ? "passed" : "failed");
	//	Console.WriteLine("\n-->Test timed wait: {0}\n", TestTimedWait() ? "passed" : "failed");
		Console.WriteLine("\n-->Test set followed by reset: {0}\n", TestSetFollowedByReset() ? "passed" : "failed");
	}
}
