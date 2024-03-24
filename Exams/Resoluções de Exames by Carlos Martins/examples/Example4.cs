/**
 *  ISEL, LEIC, Concurrent Programming
 *
 *  Rreadwritelock with the semantics proposed by hoare and implemented using the
 *  code pattern presented in the Example 4 in "Sincronização com Monitores na CLI e
 *  na Infra-estrutura Java".
 *
 *  Generate executable with: csc Example4.cs
 *
 *  Carlos Martins, April 2018
 *
 **/

using System;
using System.Threading;
using System.Collections.Generic;

/*
 * Read/Writer lock with Hoare's semantics in order to prevent
 * readers and writers starvation.
 */

internal sealed class ReadWriteLock_ {
	private readonly object monitor = new object();	// the implicit monitor
	private int readers = 0;		// current number of readers
	private bool writing = false;	// true when writing
	
	// We use a queue for waiting readers and a queue for waiting writers.
	// For each queue node holds a Boolean that says if the requested
	// access was already granted or not.
	private readonly LinkedList<bool> rdq = new LinkedList<bool>();
	private readonly LinkedList<bool> wrq = new LinkedList<bool>();

	// Constructor.
	public ReadWriteLock_() { }

	// Acquire the lock for read (shared) access
	public void LockRead() {
		lock(monitor) {
			// if there isn’t blocked writers and the resource isn’t being written, grant
			// read access immediately
			if (wrq.Count == 0 && !writing) {
				readers++;
				return;
			}
			
			// otherwise, enqueue a read access request
			LinkedListNode<bool> rdwnode = rdq.AddLast(false);
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					Monitor.Wait(monitor);
				} catch (ThreadInterruptedException) {
					// if the requested shared access was granted, we must re-assert interrupt
					// exception, and return normally.
					if (rdwnode.Value) {
						Thread.CurrentThread.Interrupt();
						return;
					}
					// otherwise, we remove the request from the queue and re-throw the exception
					rdq.Remove(rdwnode);
					throw;
				}
				// if shared access was granted then return; otherwise, re-wait
			} while (!rdwnode.Value);
		}
	}
	
	// auxiliary method: grant access to all waiting readers
	private bool GrantAccessToWaitingReaders() {
		if (rdq.Count > 0) {
			readers += rdq.Count;		// account with all new active readers
			do {
				rdq.First.Value = true;	// mark read request as granted
				rdq.RemoveFirst();
			} while (rdq.Count > 0);
			Monitor.PulseAll(monitor);	// notify waiting readers (and writers)
			return true;
		}
		return false;
	}

	// auxiliary method: grant access to the first waiting writer
	private void GrantAccessToAWaitingWriter() {
		if (wrq.Count > 0) {
			wrq.First.Value = true;		// mark write request as granted
			writing = true;				// exclusive lock was taken	
			wrq.RemoveFirst();
			Monitor.PulseAll(monitor); 	// notify waiting readers (and writers)
		}
	}
	
	// Acquire the lock for write (exclusive) access
	public void LockWrite() {
		lock(monitor) {
			// if the lokc isn’t held for read nor for writing, grant the access immediately
			if (readers == 0 && !writing) {
				writing = true;
				return;
			}
			// enqueue a request for exclusive access
			LinkedListNode<bool> wrwnode = wrq.AddLast(false);
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					Monitor.Wait(monitor);
				} catch (ThreadInterruptedException) {
					// if exclusive access was granted, then we re-assert exception, and return normally
					if (wrwnode.Value) {
						Thread.CurrentThread.Interrupt();
						return;
					}
					// othwewise, remove the request from the queue, and return throwing the exception.
					wrq.Remove(wrwnode);
					
					// when a waiting writer gives up, we must grant shared access to all
					// waiting readers that has been blocked by this waiting writer
					if (!writing && wrq.Count == 0 && rdq.Count > 0)
						GrantAccessToWaitingReaders();
					throw;
				}
				// if the request was granted return, else re-wait
			} while (!wrwnode.Value);
		}
	}
	
	// Release read (shared) lock
	public void UnlockRead() {
		lock(monitor) {
			readers--; // decrement the number of active readers
			// if this is the last active reader, and there is at least a blocked writer, grant access
			// to the writer that is at front of queue
			if (readers == 0 && wrq.Count > 0)
				GrantAccessToAWaitingWriter();
		}
	}

	// Release the write (exclusive) lock
	public void UnlockWrite() {
		lock(monitor) {
			writing = false;
			if (!GrantAccessToWaitingReaders())
				GrantAccessToAWaitingWriter();
		}
	}
}

/*
 * Read/Writer lock with a naive implementation where readers can prevent
 * access to writers.
 */

internal sealed class ReadWriteLockStarveWriters {
	private readonly object monitor = new object();	// the implicit monitor
	private int readers = 0;		// current number of readers
	private bool writing = false;	// true when writing

	// Constructor.
	public ReadWriteLockStarveWriters() { }

	// Acquire the lock for read (shared) access
	public void LockRead() {
		lock(monitor) {
			while (writing)
				Monitor.Wait(monitor);
			readers++;
		}
	}

	// Acquire the lock for write (exclusive) access
	public void LockWrite() {
		lock(monitor) {
			while (writing || readers > 0)
				Monitor.Wait(monitor);
			writing = true;
		}
	}
	
	// Release read (shared) lock
	public void UnlockRead() {
		lock(monitor) {
			if (--readers == 0)
				Monitor.PulseAll(monitor);
		}
	}

	// Release the write (exclusive) lock
	public void UnlockWrite() {
		lock(monitor) {
			writing = false;
			Monitor.PulseAll(monitor);
		}
	}
}

/*
 * Read/Writer lock with a naive implementation where writers can prevent
 * access to readers.
 */

internal sealed class ReadWriteLockStarveReaders {
	private readonly object monitor = new object();	// the implicit monitor
	private int readers = 0;		// current number of readers
	private bool writing = false;	// true when writing
	private int waitingWriters = 0;	// the number of waiting writers	

	// Constructor.
	public ReadWriteLockStarveReaders() { }

	// Acquire the lock for read (shared) access
	public void LockRead() {
		lock(monitor) {
			while (writing || waitingWriters > 0)
				Monitor.Wait(monitor);
			readers++;
		}
	}

	// Acquire the lock for write (exclusive) access
	public void LockWrite() {
		lock(monitor) {
			if (readers == 0 && !writing) {
				writing = true;
				return;
			}
			waitingWriters++;
			try {
				do {
					Monitor.Wait(monitor);
				} while (readers > 0 || writing);
				writing = true;
			} finally {
				waitingWriters--;
			}
		}
	}
	
	// Release read (shared) lock
	public void UnlockRead() {
		lock(monitor) {
			if (--readers == 0)
				Monitor.PulseAll(monitor);
		}
	}

	// Release the write (exclusive) lock
	public void UnlockWrite() {
		lock(monitor) {
			writing = false;
			Monitor.PulseAll(monitor);
		}
	}
}

public class Example4 {
	
	private static bool TestReadWriteLock() {

		const int RUN_TIME = 10 * 1000;
		const int EXIT_TIME = 50;		
		const int READER_THREADS = 50;
		const int WRITER_THREADS = 25;
		const int MIN_BACKOFF = 0;
		const int MAX_BACKOFF = 1;
 
		Thread[] readers = new Thread[READER_THREADS];
		Thread[] writers = new Thread[WRITER_THREADS];
		int sharedWriteCounter = 0, sharedReadCounter = 0;
		int[] readCounters = new int[READER_THREADS];
		int[] writeCounters = new int[WRITER_THREADS];

		// the read/write lock
		ReadWriteLock_ rwlock = new ReadWriteLock_();
		//ReadWriteLockStarveWriters rwlock = new ReadWriteLockStarveWriters();
		//ReadWriteLockStarveReaders rwlock = new ReadWriteLockStarveReaders();
		
		bool running = true;
		Console.WriteLine("\n--> test read/write lock");
		// Create and start reader threads.
		for (int i = 0; i < READER_THREADS; i++) {
			int tid = i;
			readers[i] = new Thread(() => {
				Random rnd = new Random(tid);
				do {
					try {
						rwlock.LockRead();
					} catch (ThreadInterruptedException) {
						break;
					}
					Thread.Yield();
					Interlocked.Increment(ref sharedReadCounter);
					rwlock.UnlockRead();
					if ((++readCounters[tid] % 1000) == 0) {
						Console.Write($"[r#{tid:D2}]");
					} else {
						try {
							if (MAX_BACKOFF > 0)
								Thread.Sleep(rnd.Next(MIN_BACKOFF, MAX_BACKOFF));
						} catch (ThreadInterruptedException) {
							break;
						}
					}
				} while (Volatile.Read(ref running));					
			});
			readers[i].Start();
		}
		
		// Create and start writer threads.
		for (int i = 0; i < WRITER_THREADS; i++) {
			int tid = i;
			writers[i] = new Thread(() => {
				Random rnd = new Random(tid);
				do {
					try {
						rwlock.LockWrite();
					} catch (ThreadInterruptedException) {
						break;
					}
					Thread.Yield();
					sharedWriteCounter++;
					rwlock.UnlockWrite();
					if ((++writeCounters[tid] % 250) == 0) {
						Console.Write($"[w#{tid:D2}]");
					} else {
						try {
							if (MAX_BACKOFF > 0)
								Thread.Sleep(rnd.Next(MIN_BACKOFF, MAX_BACKOFF));
						} catch (ThreadInterruptedException) {
							break;
						}
					}
				} while (Volatile.Read(ref running));					
			});
			writers[i].Start();
		}
		
		// run the test for a while
		Thread.Sleep(RUN_TIME);
		Volatile.Write(ref running, false);
		Thread.Sleep(EXIT_TIME);
		
		// wait until all writer threads have been terminated.
		for (int i = 0; i < WRITER_THREADS; i++) {
			if (writers[i].IsAlive)
				writers[i].Interrupt();
			writers[i].Join();
		}

		// wait until all reader threads have been terminated.
		for (int i = 0; i < READER_THREADS; i++) {
			if (readers[i].IsAlive)
				readers[i].Interrupt();
			readers[i].Join();
		}
		
		// compute results
		Console.WriteLine("\n\nReader counters:");
		int reads = 0;
		for (int i = 0; i < READER_THREADS; i++) {
			reads += readCounters[i];
			if (i != 0 && (i % 5) == 0)
				Console.WriteLine();
			else if (i != 0)
				Console.Write(' ');
			Console.Write($"[r#{i:D2}: {readCounters[i],4}]");
		}
		
		Console.WriteLine("\n\nWriter counters:");
		int writes = 0;
		for (int i = 0; i < WRITER_THREADS; i++) {
			writes += writeCounters[i];
			if (i != 0 && (i % 5) == 0) {
				Console.WriteLine();
			} else if (i != 0){
				Console.Write(' ');
			}
			Console.Write($"[w#{i:D2}: {writeCounters[i],4}]");
		}
		Console.WriteLine($"\n\n--private/shared reads: {reads}/{sharedReadCounter}, private/shared writes: {writes}/{sharedWriteCounter}");
		return reads == sharedReadCounter && writes == sharedWriteCounter;
	}
	
	
	public static void Main() {
		Console.WriteLine("-->test read/write lock: {0}",
							TestReadWriteLock() ? "passed" : "failed");
	}
}


