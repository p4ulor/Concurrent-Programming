/***
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Read writer lock with Hoare's semantics in Java using explicit monitors, but
 * not using specific thread notification
 *
 * Carlos Martins, April 2017
 *
 ***/

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.LinkedList;

public class ReadWriteLockHoare {
	
	// the request node used for enqueue shared lock requests
	private static class RdReqNode {
		int waiters;
		boolean done;
	}

	// the request node used for enqueue exclusive lock requests
	private static class WrReqNode {
		boolean done;
	}
	
	private final Lock mlock;				// explicit monitor
	private final Condition okToRead;		// condition variable to block readers
	private final Condition okToWrite;		// condition variable to block writers
	
	// the state of the read/write lock
	private int readers;					// current number of readers
	private boolean writing;				// true when writing
	
	// all waiting readers same queue node. We do not need a linked list at all
	private RdReqNode waitingReaders;
	
	// We use a queue for waiting writers.
	// For each queue node holds a boolean that says if the requested
	// access was already granted or not and the condition variable where the writer is waiting.
	private final LinkedList<WrReqNode> waitingWriters;
	

	// Constructor.
	public ReadWriteLockHoare() {
		mlock = new ReentrantLock();
		okToRead = mlock.newCondition();
		okToWrite = mlock.newCondition();
		readers = 0;
		writing = false;
		waitingReaders = null;
		waitingWriters = new LinkedList<WrReqNode>();
	}
	
	
	// grant access to all waiting readers
	private boolean grantAccessToAllWaitingReaders() {
		if (waitingReaders != null && waitingReaders.waiters > 0) {
			readers += waitingReaders.waiters;		// account with all new active readers
			waitingReaders.done = true;				// mark all lock read requests as granted
			waitingReaders = null;					// remove all waiting readers from the wait queue
			okToRead.signalAll();					// notify all waiting readers
			return true;
		}
		return false;
	}
	
	// grant access to the waiting writer that is at front of queue
	private void grantAccessToOneWaitingWriter() {
		if (waitingWriters.size() > 0) {
			waitingWriters.removeFirst().done = true;	// mark write request as granted;
			writing = true;					// exclusive lock was taken
			// notify all waiting writers to be sure that the targeted writer is notified
			okToWrite.signalAll();
		}
	}
	
	// Acquire the lock for read (shared) access
	public void lockRead() throws InterruptedException {
		mlock.lock();
		try {
			// if there isn’t blocked writers and the resource isn’t being written, grant
			// read access immediately
			if (waitingWriters.size() == 0 && !writing) {
				readers++;
				return;
			}
			
			// otherwise, enqueue a read access request
			RdReqNode rdnode;
			if ((rdnode = waitingReaders) == null);
				 waitingReaders = rdnode = new RdReqNode();
			rdnode.waiters++;
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					okToRead.await();
				} catch (InterruptedException ie) {
					// if the requested shared access was granted, we must re-assert exception,
					// and return normally.
					if (rdnode.done) {
						Thread.currentThread().interrupt();
						return;
					}
					// otherwise, we remove the request from the queue and re-throw the exception
					if (--rdnode.waiters == 0)
						waitingReaders = null;		// queue becomes empty
					throw ie;
				}
				// if shared access was granted then return; otherwise, re-wait
			} while (!rdnode.done);
		} finally {
			mlock.unlock();
		}
	}
		
	// Acquire the lock for write (exclusive) access
	public void lockWrite() throws InterruptedException {
		mlock.lock();
		try {
			// if the lokc isn’t held for read nor for writing and the writers’ wait queue is
			// empty, grant the access immediately
			if (readers == 0 && !writing && waitingWriters.size() == 0) {
				writing = true;
				return;
			}
			// enqueue a request for exclusive access
			WrReqNode wrnode = new WrReqNode();
			waitingWriters.addLast(wrnode);
			// wait until request is granted, or the thread gives up due to interruption
			do {
				try {
					okToWrite.await();
				} catch (InterruptedException ie) {
					// if exclusive access was granted, then we re-assert exception, and return normally
					if (wrnode.done) {
						Thread.currentThread().interrupt();
						return;
					}
					// othwewise, remove the request from the queue, and return throwing the exception.
					waitingWriters.remove(wrnode);
					
					// when a waiting writer gives up, we must grant shared access to all
					// waiting readers that has been blocked by this waiting writer
					if (!writing && waitingWriters.size() == 0 && waitingReaders != null)
						grantAccessToAllWaitingReaders();
					throw ie;
				}
				// if the request was granted return, else re-wait
			} while (!wrnode.done);
		} finally {
			mlock.unlock();
		}
	}
	
	// Release read (shared) lock
	public void unlockRead() {
		mlock.lock();
		try {
			readers--; // decrement the number of readers
			// if this is the last active reader, and there is at least a blocked writer, grant access
			// to the writer that is at front of queue
			if (readers == 0 && waitingWriters.size() > 0)
				grantAccessToOneWaitingWriter();
		} finally {
			mlock.unlock();			
		}
	}

	// Release the write (exclusive) lock
	public void unlockWrite() {
		mlock.lock();
		try  {
			// grant access to all currently waiting readers; if none reader waiting, grant access to
			// a waiting writer, if any 
			if (!grantAccessToAllWaitingReaders())
				grantAccessToOneWaitingWriter();
		} finally {
			mlock.unlock();			
		}
	}
	
	/**
	 * Test code
	 */
	
	public static void main(String... args) {}
}
