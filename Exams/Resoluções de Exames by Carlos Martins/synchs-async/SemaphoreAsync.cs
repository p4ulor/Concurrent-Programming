/**
 *
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Semaphore with asynchronous and synchronous interface
 *
 *  Carlos Martins, December 2019
 *
 **/

 /**
  * Comment the next to do not interrupt test threads
  */
#define SEND_INTERRUPTS

/**
 * Comment/uncomment lines to select the test
 */
//#define AS_LOCK_SYNCH
//#define AS_LOCK_ASYNC		
#define ON_PRODUCER_CONSUMER_SYNC	
//#define ON_PRODUCER_CONSUMER_ASYNC		

/**
 * Uncomment to run the test continously until <enter>; otherwise
 * the test runs for 10 seconds
 */
#define RUN_CONTINOUSLY		

using System;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.Threading;
using System.Threading.Tasks;
using System.Diagnostics;

public class SemaphoreAsync {
			
	// The type used to represent each asynchronous acquire
	private class AsyncAcquire: TaskCompletionSource<bool> {
		internal readonly int acquires;
		internal readonly CancellationToken cToken;
		internal CancellationTokenRegistration cTokenRegistration;
		internal Timer timer;
		const int PENDING = 0, LOCKED = 1;
		private volatile int _lock;		// the request lock
		
		internal AsyncAcquire(int acquires, CancellationToken cToken) : base() {
			this.acquires = acquires;
			this.cToken = cToken;
			this._lock = PENDING;
		}

		/**
		 * Tries to lock the request in order to satisfy it.
		 */
		internal bool TryLock() {
			return _lock == PENDING &&
				   Interlocked.CompareExchange(ref _lock, LOCKED, PENDING) == PENDING;
		}

		/**
		 * Disposes resources associated with this async acquire.
		 *
		 * Note: when this method is called we are sure that the fields "timer"
		 *       and "cTokenRegistration" are correctly affected
		 */
		internal void Dispose(bool canceling = false) {
			// The CancellationTokenRegistration is disposed off after the
			// cancellation handler is called.
			if (!canceling && cToken.CanBeCanceled)
				cTokenRegistration.Dispose();
			timer?.Dispose();
		}
	}

	// The lock - we do not use the monitor functionality
	private readonly object theLock = new object();
	
	// available and maximum number of permits	
	private int permits;
	private readonly int maxPermits;

	// the request queue
	private readonly LinkedList<AsyncAcquire> asyncAcquires;

    /**
	 * Delegates used as cancellation handlers for asynchrounous requests 
	 */
    private readonly Action<object> cancellationHandler;
	private readonly TimerCallback timeoutHandler;

    /**
	 *  Completed tasks use to return true and false results
	 */
    private static readonly Task<bool> trueTask = Task.FromResult<bool>(true);
	private static readonly Task<bool> falseTask = Task.FromResult<bool>(false);
    
	/**
     * Constructor
     */
    public SemaphoreAsync(int initial = 0, int maximum = Int32.MaxValue) {
		// Validate arguments
        if (initial < 0 || initial > maximum)
            throw new ArgumentOutOfRangeException("initial");
        if (maximum <= 0)
            throw new ArgumentOutOfRangeException("maximum");
        // Construct delegates used to describe the two cancellation handlers.
        cancellationHandler = new Action<object>((acquireNode) => AcquireCancellationHandler(acquireNode, true));
        timeoutHandler = new TimerCallback((acquireNode) => AcquireCancellationHandler(acquireNode, false));
        // Initialize the semaphore
        permits = initial;
        maxPermits = maximum;
        asyncAcquires = new LinkedList<AsyncAcquire>();
    }

	/**
	 * Auxiliary methods
	 */

    /**
	 * Returns the list of all pending async acquires that can be satisfied with
	 * the current number of permits owned by the semaphore.
     *
	 * Note: Tis method is called when the current thread owns the lock.
	 */
    private List<AsyncAcquire> SatisfyPendingAsyncAcquires() {
        List<AsyncAcquire> satisfied = null;
        while (asyncAcquires.Count > 0) {
            AsyncAcquire acquire = asyncAcquires.First.Value;
			// Check if available permits allow satisfy this request
            if (acquire.acquires > permits)
                break;
            // Remove the request from the queue
            asyncAcquires.RemoveFirst();
            // Try lock the request and complete it if succeeded
            if (acquire.TryLock()) {
                permits -= acquire.acquires;
                if (satisfied == null)
                    satisfied = new List<AsyncAcquire>(1);
                satisfied.Add(acquire);
            }
        }
        return satisfied;
    }

    /**
	 * Complete the tasks associated to the satisfied requests.
	 *
	 *  Note: This method is called when calling thread does not own the lock.
	 */
    private void CompleteSatisfiedAsyncAcquires(List<AsyncAcquire> toComplete) {
        if (toComplete != null) {
            foreach (AsyncAcquire acquire in toComplete) {
				// Dispose the resources associated with the async acquirer and
				// complete its task with success.
                acquire.Dispose();
                acquire.SetResult(true);	// complete the associated request's task
            }
        }
    }

	/**
	 * Try to cancel an async acquire request
	 */
    private void AcquireCancellationHandler(object _acquireNode, bool canceling) {
		LinkedListNode<AsyncAcquire> acquireNode = (LinkedListNode<AsyncAcquire>)_acquireNode;
		AsyncAcquire acquire = acquireNode.Value;
		if (acquire.TryLock()) {
			List<AsyncAcquire> satisfied = null;
			// To access shared mutable state we must acquire the lock
			lock(theLock) {
				if (acquireNode.List != null)
					asyncAcquires.Remove(acquireNode);
				if (asyncAcquires.Count > 0 && permits >= asyncAcquires.First.Value.acquires)
					satisfied = SatisfyPendingAsyncAcquires();
			}
			// Complete the satisfied async acquires
			CompleteSatisfiedAsyncAcquires(satisfied);

			// Release the resources associated with the async acquire.
			acquire.Dispose(canceling);

            // Complete the TaskCompletionSource to RanToCompletion (timeout)
			// or Canceled final state.
			if (canceling)
            	acquire.SetCanceled();	
			else
				acquire.SetResult(false);
        }
	}
		
    /**
	 * Asynchronous Task-based Asynchronous Pattern (TAP) interface.
	 */

    /**
	 * Acquires one or more permits asynchronously enabling, optionally,
	 * a timeout and/or cancellation.
	 */
    public Task<bool> AcquireAsync(int acquires = 1, int timeout = Timeout.Infinite,
							       CancellationToken cToken = default(CancellationToken)) {
		lock(theLock) {
			if (asyncAcquires.Count == 0 && permits >= acquires) {
				permits -= acquires;
				return trueTask;
			}
            // if the acquire was specified as immediate, return failure
            if (timeout == 0)
				return falseTask;
			
			// If a cancellation was already requested return a task in the Canceled state
			if (cToken.IsCancellationRequested)
				return Task.FromCanceled<bool>(cToken);
						
			// Create a request node and insert it in requests queue
			AsyncAcquire acquire = new AsyncAcquire(acquires, cToken);
			LinkedListNode<AsyncAcquire> acquireNode = asyncAcquires.AddLast(acquire);

			/**
			 * Activate the specified cancelers owning the lock.
			 */
			
			/**
			 * Since the timeout handler, that runs on a thread pool's worker thread,
			 * acquires the lock before access the "acquirer.timer" and "acquirer.cTokenRegistration"
			 * these assignements will be visible to timer handler.
			 */
			if (timeout != Timeout.Infinite)
				acquire.timer = new Timer(timeoutHandler, acquireNode, timeout, Timeout.Infinite);
			
			/**
			 * If the cancellation token is already in the canceled state, the cancellation
			 * handler will run immediately and synchronously, which *causes no damage* because
			 * this processing is terminal and the implicit locks can be acquired recursively.
			 */
			if (cToken.CanBeCanceled)
            	acquire.cTokenRegistration = cToken.Register(cancellationHandler, acquireNode);

			// Return the Task<bool> that represents the async acquire
			return acquire.Task;
		}
    }

    /**
	 * Wait until acquire multiple permits asynchronously enabling, optionally,
	 * a timeout and/or cancellation.
	 */
    public Task<bool> WaitAsync(int acquires = 1, int timeout = Timeout.Infinite,
							    CancellationToken cToken = default(CancellationToken)) {
		return AcquireAsync(acquires, timeout, cToken);
	}

	/**
	 * Releases the specified number of permits
	 */
	public void Release(int releases = 1) {
        // A list to hold temporarily satisfied asynchronous operations 
        List<AsyncAcquire> satisfied = null;
		lock(theLock) {
			if (permits + releases > maxPermits)
				throw new InvalidOperationException("Exceeded the maximum number of permits");	
			permits += releases;
			satisfied = SatisfyPendingAsyncAcquires();
		}
		// Complete satisfied requests without owning the lock
		CompleteSatisfiedAsyncAcquires(satisfied);
	}

    /**
	 *	Synchronous interface implemented using the asynchronous TAP interface.
	 */

	/**
	 * Try to cancel an asynchronous request identified by its task.
	 *
	 * Note: This is used to implement the synchronous interface.
	 */
	private bool CancelAcquireByTask(Task<bool> acquireTask) {
        AsyncAcquire acquire = null;
		List<AsyncAcquire> satisfied = null;
		// To access the shared mutable state we must acquire the lock
        lock(theLock) {
			foreach (AsyncAcquire _acquire in asyncAcquires) {
				if (_acquire.Task == acquireTask) {
                    if (_acquire.TryLock()) {
						acquire = _acquire;
                        asyncAcquires.Remove(_acquire);
					}
					break;
				}
			}
			// If the new state od semaphore allows waiting acquires, satisfy them
			if (asyncAcquires.Count > 0 && permits >= asyncAcquires.First.Value.acquires)
				satisfied = SatisfyPendingAsyncAcquires();
		}
        CompleteSatisfiedAsyncAcquires(satisfied);

		if (acquire != null) {
			// Dispose the resources associated with this async acquire and complete
			// its task to the Canceled state.
			acquire.Dispose();
            acquire.SetCanceled();
        }
		return acquire != null;
	}


    /**
	 * Acquire multiple permits synchronously, enabling, optionally, a timeout
	 * and/or cancellation.
	 */
    public bool Acquire(int acquires = 1, int timeout = Timeout.Infinite,
					    CancellationToken cToken = default(CancellationToken)) {
		Task<bool> acquireTask = AcquireAsync(acquires, timeout, cToken); 
		try {
            return acquireTask.Result;
        } catch (ThreadInterruptedException) {
			// The acquirer thread was interrupted!
			//  Try to cancel the async acquire operation
			if (CancelAcquireByTask(acquireTask))
				throw;
			
			// We known that the async acquire was already completed or cancelled,
			// so return the underlying result, ignoring possible interrupts.
			try {
				do {
					try {
						return acquireTask.Result;
					} catch (ThreadInterruptedException) {
						// ignore interrupts while waiting fro task's result
					} catch (AggregateException ae) {
                		throw ae.InnerException;
					}
				} while (true);
            } finally {
				// Anyway re-assert the interrupt on the current thead.
                Thread.CurrentThread.Interrupt();
            }
        } catch (AggregateException ae) {
			// The acquire thrown an exception, propagate it synchronously
            throw ae.InnerException;
        }
	}

   /**
	 * Wait until acquire multiple permits synchronously, enabling, optionally,
	 * a timeout and/or cancellation.
	 */
    public bool Wait(int acquires = 1, int timeout = Timeout.Infinite,
					 CancellationToken cToken = default(CancellationToken)) {
		return Acquire(acquires, timeout, cToken);
	}
	
	/**
	 * Return the current number of available permits
	 */
	public int CurrentCount {
		get { lock(theLock) return permits; }
	}
}

/**
 * Test code
 */

/**
 * A blocking queue with synchronous and asynchronous TAP interface
 */

internal class BlockingQueueAsync<T> where T : class {
    private readonly ConcurrentQueue<T> queue;
    private readonly SemaphoreAsync freeSlots, filledSlots;

    /**
	 * Construct the blocking queue
	 */
    public BlockingQueueAsync(int capacity) {
        queue = new ConcurrentQueue<T>();
        freeSlots = new SemaphoreAsync(capacity, capacity);
        filledSlots = new SemaphoreAsync(0, capacity);
    }

    /**
	 * Put an item in the queue asynchronously enabling, optionally,
	 * timeout and cancellation
	 */
    public async Task<bool> PutAsync(T item, int timeout = Timeout.Infinite,
								     CancellationToken cToken = default(CancellationToken)) {
        if (!await freeSlots.WaitAsync(timeout: timeout, cToken: cToken))
            return false;       // timed out
		// Add item to the queue and update the filled slots semaphore
        queue.Enqueue(item);
        filledSlots.Release();
        return true;
    }

    /**
	 * Put an item in the queue synchronously enabling, optionally,
	 * timeout and cancellation.
	 */
    public bool Put(T item, int timeout = Timeout.Infinite,
                    CancellationToken cToken = default(CancellationToken)) {
		if (!freeSlots.Wait(1, timeout, cToken))
			 return false;
		// add item to the queue and update the filled slots semaphore
		queue.Enqueue(item);
		filledSlots.Release();
		return true;
    }

    /**
	 * Take an item from the queue asynchronously enabling, optionally,
	 * timeout and cancellation.
	 */
    public async Task<T> TakeAsync(int timeout, CancellationToken cToken) {
        if (!await filledSlots.WaitAsync(timeout: timeout, cToken: cToken))
			 return null;	// timed out
		// Remove an intem from the queue and update the free slots semaphore
        T item;
		queue.TryDequeue(out item);	// we know that we have at least a data item
        freeSlots.Release();
        return item;
    }
	
	/**
	 * Take an item from the queue synchronously enabling, optionally,
	 * timeout and cancellation.
	 */
    public T Take(int timeout = Timeout.Infinite,
				  CancellationToken cToken = default(CancellationToken)) {
        if (!filledSlots.Wait(1, timeout: timeout, cToken: cToken))
			return null;
        T item;
		queue.TryDequeue(out item);	// we know that we have at least a data item
        freeSlots.Release();
        return item;
    }

    /**
	 * Returns the number of filled positions in the queue.
	 */
    public int Count { get { return queue.Count; } }
}

internal class SemaphoreTests {
		
	// test semaphore as a mutual exclusion lock using synchronous acquires
	private static bool TestSemaphoreAsLockSync() {

		const int SETUP_TIME = 50;

#if (!RUN_CONTINOUSLY)
		const int RUN_TIME = 10 * 1000;
#endif

		int THREADS = 50;
		const int MIN_TIMEOUT = 1;
		const int MAX_TIMEOUT = 50;
		const int MIN_CANCEL_INTERVAL = 1;
		const int MAX_CANCEL_INTERVAL = 50;

		Thread[] tthrs = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		int[] timeouts = new int[THREADS];
		int[] cancellations = new int[THREADS];
		int issuedInterrupts = 0;
		int[] sensedInterrupts = new int[THREADS];
		int sharedCounter = 0;
		bool exit = false;
		ManualResetEventSlim start = new ManualResetEventSlim();
		SemaphoreAsync _lock = new SemaphoreAsync(1, 1);

		/**
		 * Create and start acquirer/releaser threads
		 */
		
		for (int i = 0; i < THREADS; i++) {
			int tid = i;
			tthrs[i] = new Thread(() => {
				Random rnd = new Random(Thread.CurrentThread.ManagedThreadId);
				start.Wait();
				CancellationTokenSource cts =
					 new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL)); 
				do {
					do {
						try {
                          if (_lock.Wait(timeout: rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT), cToken: cts.Token))
								break;
							timeouts[tid]++;
                        } catch (OperationCanceledException) {
							cancellations[tid]++;
							cts.Dispose();
	   					 	cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL)); 
						} catch (ThreadInterruptedException) {
							sensedInterrupts[tid]++;
						}
					} while (true);
					try {
						Thread.Sleep(0);
					} catch (ThreadInterruptedException) {
                        sensedInterrupts[tid]++;
                    }
                    sharedCounter++;
					
					if (THREADS > 1) {
						if (rnd.Next(100) < 99) {
							Thread.Yield();
						} else {
							try {
								Thread.Sleep(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
							} catch (ThreadInterruptedException) {
								sensedInterrupts[tid]++;
							}							
						}
					}
					
					// release the lock
					_lock.Release();
					privateCounters[tid]++;
					if (THREADS > 1) {
						try {
							if ((privateCounters[tid] % 100) == 0)
								Console.Write("[#{0:D2}]", tid);
						} catch (ThreadInterruptedException) {
							sensedInterrupts[tid]++;
						}
					}
				} while (!Volatile.Read(ref exit));
				try {
					Thread.Sleep(10);
				} catch (ThreadInterruptedException) {
					sensedInterrupts[tid]++;
				}
			});
			tthrs[i].Start();
		}
		Thread.Sleep(SETUP_TIME);
		Stopwatch sw = Stopwatch.StartNew();
		start.Set();
		Random grnd = new Random(Thread.CurrentThread.ManagedThreadId);
		int startTime = Environment.TickCount;
		//...
		do {
			Thread.Sleep(grnd.Next(5));

#if SEND_INTERRUPTS
			if (THREADS > 1) {
				tthrs[grnd.Next(THREADS)].Interrupt();
				issuedInterrupts++;
			}
#endif

			if (Console.KeyAvailable) {
				Console.Read();
				break;
			}
#if RUN_CONTINOUSLY
		} while (true);
#else
		} while (Environment.TickCount - startTime < RUN_TIME);
#endif
		Volatile.Write(ref exit, true);				
		sw.Stop();
		// Wait until all threads have been terminated.
		for (int i = 0; i < THREADS; i++)
			tthrs[i].Join();

        // Compute results

        Console.WriteLine("\nPrivate counters:");
		int totalAcquisitons = 0, totalInterrupts = 0, totalCancellations = 0;
		for (int i = 0; i < THREADS; i++) {
            totalAcquisitons += privateCounters[i];
            totalInterrupts += sensedInterrupts[i];
            totalCancellations += cancellations[i];
            if (i != 0 && (i % 2) == 0) {
				Console.WriteLine();
			} else if (i != 0) {
				Console.Write(' ');
			}
			Console.Write("[#{0:D2}: {1}/{2}/{3}/{4}]", i,
				 privateCounters[i], timeouts[i], cancellations[i], sensedInterrupts[i]);
		}
		Console.WriteLine($"\n--shared/private: {sharedCounter}/{totalAcquisitons}");
		Console.WriteLine($"--interrupts issuded/sensed: {issuedInterrupts}/{totalInterrupts}");
        Console.WriteLine($"--cancellations: {totalCancellations}");

        long unitCost = (sw.ElapsedMilliseconds * 1000000L) / sharedCounter;
		
		Console.Write("--time per acquisition/release: {0} {1}",
					 unitCost >= 1000 ? unitCost / 1000 : unitCost,
					 unitCost >= 1000 ? "us" : "ns");
		return totalAcquisitons == sharedCounter;
	}

	// test semaphore as a mutual exclusion lock using asynchronous acquires

	delegate Task RunAsync(int tid);
	private static bool TestSemaphoreAsLockAsync() {

		const int SETUP_TIME = 50;

#if (!RUN_CONTINOUSLY)
		const int RUN_TIME = 10 * 1000;
#endif
		const int TASKS = 50;
		const int MIN_TIMEOUT = 1;
		const int MAX_TIMEOUT = 10;

		Task[] tasks = new Task[TASKS];
		int[] privateCounters = new int[TASKS];
		int[] timeouts = new int[TASKS];
		int[] cancellations = new int[TASKS];
		int sharedCounter = 0;
		bool exit = false;
		SemaphoreAsync _lock = new SemaphoreAsync(1, 1);

		//
		// Create and start acquirer/releaser threads
		//
		
		Func<int, Task>  asyncRun = async (int tid) => {
            Random rnd = new Random(tid);
            do {
				await Task.Delay(5);
                do {
                    using (CancellationTokenSource cts = new CancellationTokenSource()) {
                        try {
                            var result = await _lock.WaitAsync(timeout: rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT), cToken: cts.Token);
                            if (rnd.Next(100) < 10)
                                cts.Cancel();
                            if (result)
                                break;
                            timeouts[tid]++;
                        } catch (AggregateException ae) {
                            ae.Handle((e) => {
                                if (e is TaskCanceledException) {
                                    cancellations[tid]++;
                                    return true;
                                }
                                return false;
                            });
                        } catch (Exception ex) {
                            Console.WriteLine("*** Exception type: {0}", ex.GetType());
                        }
                    }
                } while (true);
                sharedCounter++;
                if (rnd.Next(100) > 95)
                    await Task.Delay(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT));
                privateCounters[tid]++;
                _lock.Release();
                if (privateCounters[tid] % 100 == 0)
                    Console.Write($"[#{tid:D2}]");
            } while (!Volatile.Read(ref exit));
        };
	
		// call al lasync methods
		for (int i = 0; i < TASKS; i++) {
			tasks[i] = asyncRun(i);
		}
		Thread.Sleep(SETUP_TIME);
		Stopwatch sw = Stopwatch.StartNew();
		int startTime = Environment.TickCount;
		do {
			Thread.Sleep(20);
			if (Console.KeyAvailable) {
				Console.Read();
				break;
			}
#if RUN_CONTINOUSLY
		} while (true);
#else
		} while (Environment.TickCount - startTime < RUN_TIME);
#endif

        Volatile.Write(ref exit, true);				
		int sharedSnapshot = Volatile.Read(ref sharedCounter);
		sw.Stop();
		// Wait until all async methods have been terminated.
		Task.WaitAll(tasks);
		
		// Compute results
		
		Console.WriteLine("\n\nPrivate counters:");
		int sum = 0;
		for (int i = 0; i < TASKS; i++) {
			sum += privateCounters[i];
			if (i != 0 && i % 3 == 0)
				Console.WriteLine();
			else if (i != 0)
				Console.Write(' ');
			Console.Write("[#{0:D2}: {1}/{2}/{3}]", i, privateCounters[i], timeouts[i], cancellations[i]);
		}
		Console.WriteLine();
		long unitCost = (sw.ElapsedMilliseconds * 1000000L) / sharedSnapshot;
		Console.WriteLine("--unit cost of acquire/release: {0} {1}",
							unitCost > 1000 ? unitCost / 1000 : unitCost,
							unitCost > 1000 ? "us" : "ns");
		return sum == sharedCounter;
	}

	
	// Test the semaphore in a producer/consumer context using the synchronous
	// interface	
	private static bool TestSemaphoreInATapProducerConsumerContextSync() {
#if (!RUN_CONTINOUSLY)
		const int RUN_TIME = 10 * 1000;
#endif
		const int EXIT_TIME = 50;
		const int PRODUCER_THREADS = 10;
		const int CONSUMER_THREADS = 20;
		const int QUEUE_SIZE = PRODUCER_THREADS / 2 + 1;
		const int MIN_TIMEOUT = 1;
		const int MAX_TIMEOUT = 50;
		const int MIN_CANCEL_INTERVAL = 50;
		const int MAX_CANCEL_INTERVAL = 100;		
		const int MIN_PAUSE_INTERVAL = 10;
		const int MAX_PAUSE_INTERVAL = 100;
		const int PRODUCTION_ALIVE = 500;
		const int CONSUMER_ALIVE = 10000;
		
		Thread[] pthrs = new Thread[PRODUCER_THREADS];
		Thread[] cthrs = new Thread[CONSUMER_THREADS];
		int[] productions = new int[PRODUCER_THREADS];
		int[] productionTimeouts = new int[PRODUCER_THREADS];
		int[] productionCancellations = new int[PRODUCER_THREADS];
		int[] consumptions = new int[CONSUMER_THREADS];
		int[] consumptionTimeouts = new int[CONSUMER_THREADS];
		int[] consumptionCancellations = new int[CONSUMER_THREADS];
		
		bool exit = false;
		BlockingQueueAsync<String> queue = new BlockingQueueAsync<String>(QUEUE_SIZE); 

		// Create and start consumer threads.
		
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			int ctid = i;
			cthrs[i] = new Thread(() => {
				Random rnd = new Random(ctid);
				CancellationTokenSource cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL)); 
				do {
					do {
						try {
							if (queue.Take(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT), cts.Token) != null) {
								consumptions[ctid]++;
								break;
							} else
								consumptionTimeouts[ctid]++;
						} catch (OperationCanceledException) {
							consumptionCancellations[ctid]++;
							cts.Dispose();
					 		cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
						} catch (ThreadInterruptedException) {
							break;
                    	} catch (Exception e) {
                        	Console.WriteLine($"***Exception: {e.GetType()}: {e.Message}");
                        	break;
						}
                    } while (true);
					if (consumptions[ctid] % CONSUMER_ALIVE == 0) {
						Console.Write($"[#c{ctid:D2}]");
						try {
							Thread.Sleep(rnd.Next(MIN_PAUSE_INTERVAL, MAX_PAUSE_INTERVAL));
						} catch (ThreadInterruptedException) {
							break;
						}
					}
				} while (!Volatile.Read(ref exit));
			});
			cthrs[i].Priority = ThreadPriority.Highest;
			cthrs[i].Start();
		}
		
		// Create and start producer threads.
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			int ptid = i;
			pthrs[i] = new Thread(() => {
				Random rnd = new Random(ptid);
				CancellationTokenSource cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL)); 
				do {
					do {
						try {
							if (queue.Put(rnd.Next().ToString(), rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT),
										  cts.Token)) {
								productions[ptid]++;
								break;
							} else
							productionTimeouts[ptid]++;
						} catch (OperationCanceledException) {
							productionCancellations[ptid]++;
							cts.Dispose();
					 		cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
						} catch (ThreadInterruptedException) {
							break;
                    	} catch (Exception e) {
                        	Console.WriteLine($"***Exception: {e.GetType()}: {e.Message}");
                        	break;
						}
                    } while (true);
					int sleepTime = 0;
					if (productions[ptid] % PRODUCTION_ALIVE == 0) {
						Console.Write($"[#p{ptid:D2}]");
						sleepTime = rnd.Next(MIN_PAUSE_INTERVAL, MAX_PAUSE_INTERVAL);
					}
					try {
						Thread.Sleep(sleepTime);
					} catch (ThreadInterruptedException) {
						break;
					}
				} while (!Volatile.Read(ref exit));
			});
			pthrs[i].Start();
		}
		
		// run the test for a while
		int startTime = Environment.TickCount;
		do {
			Thread.Sleep(50);
			if (Console.KeyAvailable) {
				Console.Read();
				break;
			}
#if RUN_CONTINOUSLY
		} while (true);
#else
		} while (Environment.TickCount - startTime < RUN_TIME);
#endif
        Volatile.Write(ref exit, true);
		Thread.Sleep(EXIT_TIME);
		
		// Wait until all producer have been terminated.
		int sumProductions = 0;
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			if (pthrs[i].IsAlive)
				pthrs[i].Interrupt();
			pthrs[i].Join();
			sumProductions += productions[i];
		}

		int sumConsumptions = 0;
		// Wait until all consumer have been terminated.
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			if (cthrs[i].IsAlive) {
				cthrs[i].Interrupt();
			}
			cthrs[i].Join();
			sumConsumptions += consumptions[i];
		}
		
		// Display consumer results
		Console.WriteLine("\nConsumer counters:");
		for (int i = 0; i < CONSUMER_THREADS; i++) {
			if (i != 0 && i % 2 == 0) {
				Console.WriteLine();
			} else if (i != 0) {
				Console.Write(' ');
			}
			Console.Write("[#c{0:D2}: {1}/{2}/{3}]", i, consumptions[i], consumptionTimeouts[i],
							consumptionCancellations[i]);
		}
		
		// consider not consumed productions
		sumConsumptions += queue.Count;
		
		Console.WriteLine("\nProducer counters:");
		for (int i = 0; i < PRODUCER_THREADS; i++) {
			if (i != 0 && i % 2 == 0) {
				Console.WriteLine();
			} else if (i != 0){
				Console.Write(' ');
			}
			Console.Write("[#p{0:D2}: {1}/{2}/{3}]", i, productions[i], productionTimeouts[i],
					 	  productionCancellations[i]);
		}
		Console.WriteLine("\n--productions: {0}, consumptions: {1}", sumProductions, sumConsumptions);
		return sumConsumptions == sumProductions;
	}

    // Test the semaphore in a producer/consumer context using asynchronous TAP acquires	
    private static bool TestSemaphoreInATapProducerConsumerContextAsync()
    {

#if (!RUN_CONTINOUSLY)
        const int RUN_TIME = 10 * 1000;
#endif
        const int EXIT_TIME = 50;
        const int PRODUCER_THREADS = 10;
        const int CONSUMER_THREADS = 20;
        const int QUEUE_SIZE = PRODUCER_THREADS / 2 + 1;
        const int MIN_TIMEOUT = 1;
        const int MAX_TIMEOUT = 50;
        const int MIN_CANCEL_INTERVAL = 50;
        const int MAX_CANCEL_INTERVAL = 100;
        const int MIN_PAUSE_INTERVAL = 10;
        const int MAX_PAUSE_INTERVAL = 100;
        const int PRODUCTION_ALIVE = 500;
        const int CONSUMER_ALIVE = 10000;

        Thread[] pthrs = new Thread[PRODUCER_THREADS];
        Thread[] cthrs = new Thread[CONSUMER_THREADS];
        int[] productions = new int[PRODUCER_THREADS];
        int[] productionTimeouts = new int[PRODUCER_THREADS];
        int[] productionCancellations = new int[PRODUCER_THREADS];
        int[] consumptions = new int[CONSUMER_THREADS];
        int[] consumptionTimeouts = new int[CONSUMER_THREADS];
        int[] consumptionCancellations = new int[CONSUMER_THREADS];

        bool exit = false;
        BlockingQueueAsync<String> queue = new BlockingQueueAsync<String>(QUEUE_SIZE);

        // Create and start consumer threads.

        for (int i = 0; i < CONSUMER_THREADS; i++) {
            int ctid = i;
            cthrs[i] = new Thread(() => {
                Random rnd = new Random(ctid);
                CancellationTokenSource cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
                do {
                    try {
                        if (queue.TakeAsync(rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT), cts.Token).Result != null)
                            consumptions[ctid]++;
                        else
                            consumptionTimeouts[ctid]++;
                    } catch (AggregateException ae) {
                        if (ae.InnerException is TaskCanceledException) {
                            consumptionCancellations[ctid]++;
                            cts.Dispose();
                            cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
                        } else {
                            Console.WriteLine($"***Exception: {ae.InnerException.GetType()}: {ae.InnerException.Message}");
                            break;
                        }
                    }
                    catch (ThreadInterruptedException) {
                        break;
                    }
                    if (consumptions[ctid] % CONSUMER_ALIVE == 0) {
                        Console.Write($"[#c{ctid:D2}]");
                    	try {
                        	Thread.Sleep(rnd.Next(MIN_PAUSE_INTERVAL, MAX_PAUSE_INTERVAL));
                   		} catch (ThreadInterruptedException) {
                        	break;
                    	}
					}
                } while (!Volatile.Read(ref exit));
            });
            cthrs[i].Priority = ThreadPriority.Highest;
            cthrs[i].Start();
        }

        // Create and start producer threads.
        for (int i = 0; i < PRODUCER_THREADS; i++) {
            int ptid = i;
            pthrs[i] = new Thread(() => {
                Random rnd = new Random(ptid);
                CancellationTokenSource cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
                do {
					do {
                    	try {
                        	var putTask = queue.PutAsync(rnd.Next().ToString(), rnd.Next(MIN_TIMEOUT, MAX_TIMEOUT), cts.Token);
                        	if (putTask.Result) {
                            	productions[ptid]++;
								break;
							} else 
                            	productionTimeouts[ptid]++;
                    	} catch (AggregateException ae) {
                        	if (ae.InnerException is TaskCanceledException) {
                            	productionCancellations[ptid]++;
                            	cts.Dispose();
                            	cts = new CancellationTokenSource(rnd.Next(MIN_CANCEL_INTERVAL, MAX_CANCEL_INTERVAL));
                        	} else {
                            	Console.WriteLine($"***Exception: {ae.InnerException.GetType()}: { ae.InnerException.Message}");
                            	break;
                       		}
                    	} catch (ThreadInterruptedException) {
                        	break;
                    	}
					} while (true);
                    if (productions[ptid] % PRODUCTION_ALIVE == 0) {
                        Console.Write($"[#p{ptid:D2}]");
                    	try {
                        	Thread.Sleep(rnd.Next(MIN_PAUSE_INTERVAL, MAX_PAUSE_INTERVAL));
                    	} catch (ThreadInterruptedException) {
                       		break;
                    	}
					}
                } while (!Volatile.Read(ref exit));
            });
            pthrs[i].Start();
        }

        // run the test for a while
        int startTime = Environment.TickCount;
        do {
            Thread.Sleep(50);
            if (Console.KeyAvailable) {
                Console.Read();
                break;
            }
#if RUN_CONTINOUSLY
        } while (true);
#else
		} while (Environment.TickCount - startTime < RUN_TIME);
#endif
        Volatile.Write(ref exit, true);
        Thread.Sleep(EXIT_TIME);

        // Wait until all producer have been terminated.
        int sumProductions = 0;
        for (int i = 0; i < PRODUCER_THREADS; i++)
        {
            if (pthrs[i].IsAlive)
                pthrs[i].Interrupt();
            pthrs[i].Join();
            sumProductions += productions[i];
        }

        int sumConsumptions = 0;
        // Wait until all consumer have been terminated.
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            if (cthrs[i].IsAlive)
                cthrs[i].Interrupt();
            cthrs[i].Join();
            sumConsumptions += consumptions[i];
        }
		// consider not consumed productions
		sumConsumptions += queue.Count;

        // Display consumer results
        Console.WriteLine("\nConsumer counters:");
        for (int i = 0; i < CONSUMER_THREADS; i++) {
            if (i != 0 && i % 2 == 0)
                Console.WriteLine();
            else if (i != 0)
                Console.Write(' ');
            Console.Write($"[#c{i:D2}: {consumptions[i]}/{consumptionTimeouts[i]}/{consumptionCancellations[i]}]");
        }

        Console.WriteLine("\nProducer counters:");
        for (int i = 0; i < PRODUCER_THREADS; i++) {
            if (i != 0 && i % 2 == 0)
                Console.WriteLine();
            else if (i != 0)
                Console.Write(' ');
            Console.Write($"[#p{i:D2}: {productions[i]}/{productionTimeouts[i]}/{productionCancellations[i]}]");
        }
        Console.WriteLine($"\n--productions: {sumProductions}, consumptions: {sumConsumptions}");
        return sumConsumptions == sumProductions;
    }

    static void Main() {
		
#if AS_LOCK_SYNCH
		Console.WriteLine("\n-->test semaphore as lock using synchronous acquires: {0}",
							  TestSemaphoreAsLockSync() ? "passed" : "failed");
#endif

#if AS_LOCK_ASYNC		
		
		Console.WriteLine("\n-->test semaphore as lock using asynchronous acquires: {0}",
							  TestSemaphoreAsLockAsync() ? "passed" : "failed");
#endif

#if ON_PRODUCER_CONSUMER_SYNC
		
		Console.WriteLine("\n-->test semaphore in a synchronous producer/consumer context: {0}",
						  TestSemaphoreInATapProducerConsumerContextSync() ? "passed" : "failed");
#endif

#if ON_PRODUCER_CONSUMER_ASYNC
		
		Console.WriteLine("\n-->test semaphore in a asynchronous producer/consumer context: {0}",
						  TestSemaphoreInATapProducerConsumerContextAsync() ? "passed" : "failed");
#endif
    }
}


