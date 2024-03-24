import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

/* ex3
 * A message queue implementation using the optimized monitor style
 *
 * Implemented following the algorithm explained in the text synchronizers-optimized.md and
 * using the implementation of the Michael Scott queue.
 ~ 
 *
 */
public final class MessageQueueOptimized<E> {
	// i1. the monitor
	private final Lock monitor = new ReentrantLock();

	// i2. the condition variable where the acquirer threads will block
	private final Condition okToReceive = monitor.newCondition();

	// i3. synchronization state: the queue of pending messages
	private final MichaelScottQueue<E> pendingMessages = new MichaelScottQueue<E>();
	
	// the number of declared waiters 
	private volatile int waiters = 0;
	
	// i4. constructor
	public MessageQueueOptimized() { }

	// tryReceive: tries to receive a pending message (uses nonblocking only techniques)
	public E tryReceive() {
		return pendingMessages.tryDequeue();
	}
	
	// doSend: adds the specified message to the pending messages queue (uses only nonbocking techniques).
	// Since that a message sent to queue always enable a reception this method returns true
	private boolean doSend(E message) {
		pendingMessages.enqueue(message);
		return true;
	}

	// receive: receive a message from the queue with timeout support
	// returns: null when it times out
	public E receive(long millisTimeout) throws InterruptedException {
		E receivedMsg;
		
		// a1: try to receive an eventual pending message
		if ((receivedMsg = tryReceive()) != null)
			// a1: there are at least one pending message: receive-fast-path 
			return receivedMsg;
		
		// a2: no pending messages; if an immediate timeout was specified, return failure.
		if (millisTimeout == 0)
			return null;

		// a2: prepare timeout processing
		long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(millisTimeout);
		
		// a3: acquire the monitor's lock
		monitor.lock();
		// a4: the current thread declares itself as a possible waiter
		waiters++;
		
		// a5: define a try ... finally to ensure that the waiters variable is decremented
		// and the monitor's is released no matter how the method terminates
		try {
			do {
				// a6: try to receive a pending message
				if ((receivedMsg = tryReceive()) != null)
					return receivedMsg;
				
				// a7: test whether the specified timeout has expired
				if (nanosTimeout <= 0)
					return null;
				
				// a8: block the acquirer thread until: a notification, timeout expiration, or interruption
				nanosTimeout = okToReceive.awaitNanos(nanosTimeout);
			} while (true);
		} finally {
			// a9: decrement waiters and release the lock, leaving the monitor.
			waiters--;
			monitor.unlock();
		}
	}
			
	// send: send the specified message to the queue
	public void send(E message) {
		// r1: update the synchronization state according to the semantics of the release operation:
		//     that is, add the sent message to the pending message queue (this always returns true)
		doSend(message);
				
		// r2: test waiters to determine if there may be blocked acquirer threads
		if (waiters > 0) {
			
			// r3: acquire the monitor's lock	
			monitor.lock();
			try  {
				// r4: we have to go back to testing waiters to confirm if there are any acquirer threads blocked,
				// in order to avoid unnecessary notifications
				if (waiters > 0) {
					// r5: make the necessary notification: a message enable only one reception
					okToReceive.signal();
				}
			} finally {
				// r6: release the monitor's lock
				monitor.unlock();
			}
		} else
			;	// no waiters (fast-path)
	}
	
	/**
	 * Test the message queue
	 */
	private static boolean testMessageQueueOptimized() throws InterruptedException {
		MessageQueueOptimized<Long> messageQueue = new MessageQueueOptimized<>();	// the message queue
		final int SENDER_THREADS = 10, RECEIVER_THREADS = 5;
		final int MIN_RECV_TIMEOUT = 1, MAX_RECV_TIMEOUT = 50;
		final int MAX_SEND_INTERVAL = 5;
		final int RUN_TIME = 5000, EXIT_TIME = 100;
		
		
		Thread receiverThreads[] = new Thread[RECEIVER_THREADS];
		Thread senderThreads[] = new Thread[SENDER_THREADS];
		int sentMsgs[] = new int[SENDER_THREADS];
		int receivedMsgs[] = new int[RECEIVER_THREADS];
		int receiveTimeouts[] = new int[RECEIVER_THREADS];
		AtomicBoolean endTest = new AtomicBoolean(false);
		
		
		// create and start receiver threads
		for (int i = 0; i < RECEIVER_THREADS; ++i) {
			int rtid = i;
			receiverThreads[i] = new Thread(() -> {
				System.out.printf("->receiver %d started\n", rtid);
				var random = ThreadLocalRandom.current();
				while (!endTest.get()) {
					while (true) {
						Long receivedMsg;
						try {
							receivedMsg = messageQueue.receive(random.nextInt(MAX_RECV_TIMEOUT) + MIN_RECV_TIMEOUT);
							if (receivedMsg != null) {
								receivedMsgs[rtid]++;
								break;
							}
							receiveTimeouts[rtid]++;
							if (endTest.get())
								break;
						} catch (InterruptedException ie) {
							// System.out.printf("**--receiver thread %d was interrupted\n", rtid);
							break;
						}
					}
				}
				System.out.printf("<-receiver %d exiting, after received %d messages with %d timeouts\n", rtid,
						 		  receivedMsgs[rtid], receiveTimeouts[rtid]);
			});
			receiverThreads[i].start();
		}

		// create and start sender threads
		for (int i = 0; i < SENDER_THREADS; ++i) {
			int stid = i;
			senderThreads[i] = new Thread(() -> {
				System.out.printf("->sender %d started\n", stid);
				var random = ThreadLocalRandom.current();
				while (!endTest.get()) {
					messageQueue.send(Thread.currentThread().getId());
					sentMsgs[stid]++;
					try {
						Thread.sleep(random.nextInt(MAX_SEND_INTERVAL));
					} catch (InterruptedException ie) {
						;	// ignore sender thread interruptions
					}
				}
				System.out.printf("<-sender %d exiting, after sent %d messages\n", stid, sentMsgs[stid]);
			});
			senderThreads[i].start();
		}
		
		// wait for a while
		Thread.sleep(RUN_TIME);
		
		// set the end test flag
		endTest.set(true);
		
		// wait for a while
		Thread.sleep(EXIT_TIME);
		
		// synchronize with the termination of the receiver threads
		int totalReceived = 0;
		for (int i = 0; i < RECEIVER_THREADS; i++) {
			if (receiverThreads[i].isAlive()) {
				receiverThreads[i].interrupt();
				receiverThreads[i].join();
			}
			totalReceived += receivedMsgs[i];
		}
	
		// synchronize with the termination of the receiver threads
		int totalSent = 0;
		for (int i = 0; i < SENDER_THREADS; i++) {
			senderThreads[i].join();
			totalSent += sentMsgs[i];
		}
		
		
		System.out.printf("--total sent %s, total received: %d\n", totalSent, totalReceived);
		return totalSent == totalReceived;
	}
	
	public static void main(String...args) throws InterruptedException {
		System.out.println("message queue test: " + (testMessageQueueOptimized() ? "passed" : "failed"));
	}
}
