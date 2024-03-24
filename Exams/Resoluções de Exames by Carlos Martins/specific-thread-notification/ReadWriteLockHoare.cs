/***
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Read writer lock with Hoare's semantics in .NET based in MonitorEx extension, but
 * not using specific thread notification
 *
 * Generate executable with: csc .\ReadWriteLockHoare.cs ..\utils\MonitorEx.cs
 *
 * Carlos Martins, April 2017
 *
 ***/

using System;
using System.Threading;
using System.Collections.Generic;

public class ReadWriteLockHoare {
	
	// the request node used for enqueue shared lock requests
	private class RdReqNode {
		internal int waiters;
		internal bool done;
	}
	
	private readonly object mlock;			// monitor and okToRead condition variable
	private readonly object okToWrite;		// condition variable to block writers
	
	// the state of the read/write lock
	private int readers;					// current number of readers
	private  bool writing;				// true when writing
	
	// all waiting readers same queue node. We do not need a linked list at all
	private RdReqNode waitingReaders;
	
	// We use a queue for waiting writers.
	// For each queue node holds a  bool that says if the requested
	// access was already granted or not. The LinkedListNode<bool> instance is used as condition variable
	private readonly LinkedList<bool> waitingWriters;
	

	// Constructor.
	public ReadWriteLockHoare() {
		mlock = new object();
		okToWrite = new object();
		readers = 0;
		writing = false;
		waitingReaders = null;
		waitingWriters = new LinkedList<bool>();
	}
	
	
	// grant access to all waiting readers
	private  bool GrantAccessToAllWaitingReaders() {
		if (waitingReaders != null && waitingReaders.waiters > 0) {
			readers += waitingReaders.waiters;		// account with all new active readers
			waitingReaders.done = true;				// mark all lock read requests as granted
			waitingReaders = null;					// remove all waiting readers from the wait queue
			MonitorEx.PulseAll(mlock, mlock);		// notify all waiting readers
			return true;
		}
		return false;
	}
	
	// grant access to the waiting writer that is at front of queue
	private void GrantAccessToOneWaitingWriter() {
		if (waitingWriters.Count > 0) {
			LinkedListNode<bool> writer = waitingWriters.First;		// get first writer
			waitingWriters.RemoveFirst();		// remoev writer from the wait queue
			writer.Value = true;				// mark write request as granted;
			writing = true;						// exclusive lock was taken
			// notify all waiting writers to be sure that the targeted writer is notified
			MonitorEx.PulseAll(mlock, okToWrite);
		}
	}
	
	// Acquire the lock for read (shared) access
	public void LockRead()  {
		lock(mlock) {
			// if there isn’t blocked writers and the resource isn’t being written, grant
			// read access immediately
			if (waitingWriters.Count == 0 && !writing) {
				readers++;
				return;
			}
			
			// otherwise, enqueue a read access request
			RdReqNode rdnode;
			if ((rdnode = waitingReaders) == null)
				 waitingReaders = rdnode = new RdReqNode();
			rdnode.waiters++;
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					MonitorEx.Wait(mlock, mlock);
				} catch (ThreadInterruptedException) {
					// if the requested shared access was granted, we must re-assert exception,
					// and return normally.
					if (rdnode.done) {
						Thread.CurrentThread.Interrupt();
						return;
					}
					// otherwise, we remove the request from the queue and re-throw the exception
					if (--rdnode.waiters == 0)
						waitingReaders = null;		// queue becomes empty
					throw;
				}
				// if shared access was granted then return; otherwise, re-wait
			} while (!rdnode.done);
		}
	}
		
	// Acquire the lock for write (exclusive) access
	public void LockWrite() {
		lock(mlock) {
			// if the lokc isn’t held for read nor for writing and the writers’ wait queue is
			// empty, grant the access immediately
			if (readers == 0 && !writing && waitingWriters.Count == 0) {
				writing = true;
				return;
			}
			// enqueue a request for exclusive access
			LinkedListNode<bool> wrnode = waitingWriters.AddLast(false);
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					MonitorEx.Wait(mlock, okToWrite);
				} catch (ThreadInterruptedException) {
					// if exclusive access was granted, then we re-assert exception, and return normally
					if (wrnode.Value) {
						Thread.CurrentThread.Interrupt();
						return;
					}
					// othwewise, remove the request from the queue, and return throwing the exception.
					waitingWriters.Remove(wrnode);
					
					// when a waiting writer gives up, we must grant shared access to all
					// waiting readers that has been blocked by this waiting writer
					if (!writing && waitingWriters.Count == 0 && waitingReaders != null)
						GrantAccessToAllWaitingReaders();
					throw;
				}
				// if the request was granted return, else re-wait
			} while (!wrnode.Value);
		}
	}
	
	// Release read (shared) lock
	public void UnlockRead() {
		lock(mlock) {
			readers--; // decrement the number of active readers
			// if this is the last active reader, and there is at least a blocked writer, grant access
			// to the writer that is at front of the queue 
			if (readers == 0 && waitingWriters.Count > 0)
				GrantAccessToOneWaitingWriter();
		}
	}

	// Release the write (exclusive) lock
	public void UnlockWrite() {
		lock(mlock)  {
			// grant access to all currently waiting readers; if none reader waiting, grant access to
			// a waiting writer, if any 
			if (!GrantAccessToAllWaitingReaders())
				GrantAccessToOneWaitingWriter();
		}
	}
	
	/**
	 * Test code
	 */
	
	public static void Main() {}
}
