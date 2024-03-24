/**
 *  ISEL, LEIC, Concurrent Programming
 *
 * Test code for the Semaphore implemented by the code presented in
 * the Example 1 described in "Sincronização com Monitores na CLI e na Infra-estrutura Java".
 *
 *  Generate executable with: csc Example1.cs TimeoutHolder.cs
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
	public Semaphore_(int initial = 0) {
		if (initial > 0)
			permits = initial;
	}
	
	// Acquire one permit from the semaphore
	public bool Wait(int timeout = Timeout.Infinite) {
		lock(monitor) {
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
				if ((timeout = th.Value) == 0)
					return false;
				try {
					Monitor.Wait(monitor, timeout);
				} catch (ThreadInterruptedException) {
					// if we were interrupted and there are permits available, we can have
					// been notified and interrupted.
					// so, we leave this method throwing ThreadInterruptException, but before
					// we regenerate the notification, if there are available permits
					if (permits > 0) 
						Monitor.Pulse(monitor);
					throw; // re-throw exception
				}
				if (permits > 0) { // permits available, decrement and return
					permits--;
					return true;
				}
			} while (permits == 0);
			// permits available, decrement and return success
			permits--;
			return true;
		}
	}
	
	public void Acquire(int timeout = Timeout.Infinite) {
		Wait(timeout);
	}
	
	// Release one permit
	public void Release(){
		lock(monitor) {
			permits++;
			// only one thread can proceed execution
			// however, we must be aware that .NET monitors may lose notifications! 
			Monitor.Pulse(monitor);
		}
	}
}

public class Example1 {
	
	private static bool TestSemaphoreAsLock() {

		const int RUN_TIME = 20 * 1000;
		const int THREADS = 20;
		const int MIN_TIMEOUT = 0;
		const int MAX_TIMEOUT = 2;

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		int[] timeouts = new int[THREADS];
		int sharedCounter = 0;
		Semaphore_ slock = new Semaphore_(1);		
		//
		// Create and start acquirer/releaser threads
		//
		Console.WriteLine("\n--> test semaphore as a lock");
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Random rnd = new Random(tid);
				int endTime = Environment.TickCount + RUN_TIME;
				do {
					do {
						try {
							if (slock.Wait(rnd.Next(MAX_TIMEOUT)))
								break;
							timeouts[tid]++;
						} catch (ThreadInterruptedException) {}
					} while (true);
					sharedCounter++;
					Thread.Yield();
					slock.Release();
					if ((++privateCounters[tid] % 250) == 0)
						Console.Write($"[#{tid:D2}]");
					else
						Thread.Sleep(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
				
				} while (Environment.TickCount < endTime);					
			});
			tthrs[i].Start();
		}
		
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();
		
		// Compute results		
		Console.WriteLine("\nPrivate counters:");
		int sum = 0;
		for (int i = 0; i < THREADS; i++) {
			sum += privateCounters[i];
			if (i != 0 && (i % 4) == 0)
				Console.WriteLine();
			else if (i != 0)
				Console.Write(' ');
			Console.Write($"[#{i:D2}: {privateCounters[i],4}/{timeouts[i]}]");
		}
		return sum == sharedCounter;
	}

	private static bool TestSemaphoreInAProducerConsumerContext() {

		const int RUN_TIME = 10 * 1000;
		const int EXIT_TIME = 50;		
		const int PRODUCER_THREADS = 10;
		const int CONSUMER_THREADS = 20;
		const int MIN_TIMEOUT = 0;
		const int MAX_TIMEOUT = 2;
 
		Thread[] consumers = new Thread[CONSUMER_THREADS];
		Thread[] producers = new Thread[PRODUCER_THREADS];
		int[] consumerCounters = new int[CONSUMER_THREADS];
		int[] producerCounters = new int[PRODUCER_THREADS];
		Semaphore_ free = new Semaphore_(1), occupied = new Semaphore_(0);
		bool running = true;

		Console.WriteLine("\n--> test semaphore in a producer/consumer context");
		// Create and start consumer threads.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			int tid = i;
			consumers[i] = new Thread(() => {
				Random rnd = new Random(tid);
				do {
					try {
						occupied.Wait();
					} catch (ThreadInterruptedException) {
						break;
					}
					Thread.Yield();
					free.Release();
					if ((++consumerCounters[tid] % 250) == 0) {
						Console.Write($"[#c{tid:D2}]");
					} else {
						try {
							Thread.Sleep(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
						} catch (ThreadInterruptedException) {
							break;
						}
					}
				} while (Volatile.Read(ref running));					
			});
			consumers[i].Priority = ThreadPriority.Highest;
			consumers[i].Start();
		}
		
		// Create and start producer threads.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			int tid = i;
			producers[i] = new Thread(() => {
				Random rnd = new Random(tid);
				do {
					try {
						free.Wait();
					} catch (ThreadInterruptedException) {
						break;
					}
					Thread.Yield();
					occupied.Release();
					if ((++producerCounters[tid] % 250) == 0) {
						Console.Write($"[#p{tid:D2}]");
					} else {
						try {
							Thread.Sleep(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
						} catch (ThreadInterruptedException) {
							break;
						}
					}
				} while (Volatile.Read(ref running));					
			});
			producers[i].Start();
		}
		
		// run the test for a while
		Thread.Sleep(RUN_TIME);
		Volatile.Write(ref running, false);
		Thread.Sleep(EXIT_TIME);
		
		// Wait until all producer threads have been terminated.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			if (producers[i].IsAlive)
				producers[i].Interrupt();
			producers[i].Join();
		}

		// Wait until all consumer threads have been terminated.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			if (consumers[i].IsAlive)
				consumers[i].Interrupt();
			consumers[i].Join();
		}
		
		// Compute results
		
		Console.WriteLine("\nConsumer counters:");
		int consumptions = 0;
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			consumptions += consumerCounters[i];
			if (i != 0 && (i % 4) == 0)
				Console.WriteLine();
			else if (i != 0)
				Console.Write(' ');
			Console.Write($"[#c{i:D2}: {consumerCounters[i],4}]");
		}
		if (occupied.Wait(0)) {
			consumptions++;
		}
		
		Console.WriteLine("\nProducer counters:");
		int productions = 0;
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			productions += producerCounters[i];
			if (i != 0 && (i % 5) == 0) {
				Console.WriteLine();
			} else if (i != 0){
				Console.Write(' ');
			}
			Console.Write($"[#p{i:D2}: {producerCounters[i],4}]");
		}
		Console.WriteLine($"\n--productions: {productions}, consumptions: {consumptions}");
		return consumptions == productions;
	}
	
	static void Main() {
		Console.WriteLine("\n-->test semaphore as a lock: {0}",
							  TestSemaphoreAsLock() ? "passed" : "failed");
	
		Console.WriteLine("\n-->test semaphore in a producer/consumer context: {0}",
						  TestSemaphoreInAProducerConsumerContext() ? "passed" : "failed");
	}
}
