package pt.isel.pc.problemsets.set1;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;


public class BlockingMessageQueue<T> {
    // States of a dequeue request
    private static final int QUEUED = 0, COMPLETED = 1, CANCELLED = 2;

    // i1: create the monitor
    private final Lock monitor = new ReentrantLock();

    // i2: object Request object used by the enqueue operation
    private static class EnqueueRequest<V> {
        public V message;			// the acquire argument
        // there is no acquire result
        public Condition enqueueDone;	// private condition variable
        public boolean isDone = false;	// true when the acquire is done

        public EnqueueRequest(V message, Lock monitor) {
            this.message = message;
            this.enqueueDone = monitor.newCondition();
        }
    }
    // i2: Queue used by the enqueue operation
    private final NodeLinkedList<EnqueueRequest<T>> enqQueue = new NodeLinkedList<>();

    // i2: request object used by the dequeue operation
    private static class DequeueRequest<V> {
        // there is no acquire argument
        public V dequeuedMsg; //the dequeue result: the message
        public int state; // the dequeue state: QUEUED, COMPLETED or CANCELLED
        public Condition dequeueDone;

        public DequeueRequest(Lock monitor) {
            this.state = QUEUED;
            this.dequeueDone = monitor.newCondition();
        }
    }

    // i2: queue used by the dequeue operation
    private final NodeLinkedList<DequeueRequest<T>> deqQueue = new NodeLinkedList<>();

    // i3: synchronization state: dequeue capacity and the list of pending messages
    private final int capacity;
    private final LinkedList<T> pendingMessages = new LinkedList<>();

    // i4: constructor: no initializations needed
    public BlockingMessageQueue(int capacity) {
        if (capacity <= 0)
            throw new IllegalArgumentException("Capacity must be greater than zero");
        this.capacity = capacity;
    }

    /**
     * enqueue: enqueue a message
     *
     * this method implements the 2 synchronization semantics.
     * if there are pending dequeue operations, satisfy 1 of them and return (release semantics)
     * otherwise, it is a pure acquire operation: is there is room in the list of pending messages,
     * it adds the message to the list and returns; otherwise, it creates a request object adds it to the queue
     * and blocks the current thread until the acquire operation is complete.
     */
    public boolean enqueue(T message, long millisTimeout) throws InterruptedException {
        if (millisTimeout < 0)
            throw new IllegalArgumentException("Millistimeout must be greater or equal than zero");
        // a1: test if an interrupt is pending; if so throw InterruptedException
        if (Thread.interrupted())
            throw new InterruptedException();

        // a2: enter the monitor
        monitor.lock();
        try {
            /*
             * Release semantics: if there are pending dequeue requests complete, complete
             * what is the head of queue
             */
            if (deqQueue.isNotEmpty()) {
                // dequeue the first request, complete it with our message and return success
                DequeueRequest<T> deqRequest = deqQueue.pull().value;
                deqRequest.dequeuedMsg = message;
                deqRequest.state = COMPLETED;
                deqRequest.dequeueDone.signalAll(); // needed by the asynchronous dequeue
                return true;
            }

            /*
             * Acquire semantics
             */

            // a3: if there are available room, add the message to the pending message list
            if (pendingMessages.size() < capacity) {
                // a3: add the message to the list and return success
                pendingMessages.addLast(message);
                return true;
            }

            // a4: check for immediate timeout
            if (millisTimeout == 0)
                return false;	// goto step a14, returning timeout

            //a5: create an instance of the Request object and insert it in the wait queue
            NodeLinkedList.Node<EnqueueRequest<T>> myReqNode = enqQueue.enqueue(new EnqueueRequest<T>(message, monitor));

            // a6: prepare the timeout processing
            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(millisTimeout);

            do {
                // a7: check if the timeout already expired
                if (nanosTimeout <= 0) {
                    // a7: remove the Request object from the wait queue, cancelling the acquire operation
                    // in this synchronizer, now other queued requests can be now satisfied
                    enqQueue.remove(myReqNode);
                    return false;	// goto step a14, returning timeout
                }
                try {
                    // a8: block the current thread on its private condition variable
                    nanosTimeout = myReqNode.value.enqueueDone.awaitNanos(nanosTimeout);
                } catch (InterruptedException ie) {
                    // a9: check if the acquire request was already done
                    if (myReqNode.value.isDone) {
                        // a10: the acquire is already complete, rearm interrupt and goto a13
                        Thread.currentThread().interrupt();
                        break;	// this break, breaks the do {} while
                    }
                    // a11: the acquire request is still pending, so remove the Request object from the queue
                    enqQueue.remove(myReqNode);
                    throw ie;	// goto a14, returning from this method throwing InterruptedException
                }

                // a12: check if the acquire operation was done
            } while (!myReqNode.value.isDone);
            // a13: return success, with the message passed through the Request object
            return true;
        } finally {
            // a14: leave the monitor
            monitor.unlock();
        }
    }



    /*
     * Dequeue synchronous: demo method
     *
     * This method also implements the 2 synchronization semantics.
     * it is an acquire operation to obtain the message. If there is atleast 1 message available in list
     * it returns immediately; Otherwise it creates a request object, inserts into the queue and waits for
     * the acquire operation to complete.
     * On the other hand, when this method removes a message directly from the queue, it works as a release
     * operation of the dequeue operation
     */
    public T dequeueSynchronous() throws InterruptedException {
        // a1: test if an interrupt is pending; if so throw InterruptedException
        if (Thread.interrupted())
            throw new InterruptedException();

        // a2: enter the monitor
        monitor.lock();
        try {
            // a3
            if (pendingMessages.size() > 0) {
                T message = pendingMessages.poll();
                /*
                 * Release semantics
                 */
                // we make room for a message, so satisfy a possible pending enqueue request
                if (enqQueue.isNotEmpty()) {
                    EnqueueRequest<T> request = enqQueue.pull().value;
                    pendingMessages.addLast(request.message);
                    request.isDone = true;
                    request.enqueueDone.signal();
                }
                return message;
            }

            //a4: create an instance of the Request object and insert it in the wait queue
            NodeLinkedList.Node<DequeueRequest<T>> myReqNode = deqQueue.enqueue(new DequeueRequest<T>(monitor));

            do {
                try {
                    // a5: block the current thread on its private condition variable
                    myReqNode.value.dequeueDone.await();
                } catch (InterruptedException ie) {
                    // a6: check if the acquire request was already done
                    if (myReqNode.value.state != QUEUED) {
                        // a7: the acquire is already complete, rearm interrupt and goto a13
                        Thread.currentThread().interrupt();
                        break;	// this break, breaks the do {} while
                    }
                    // a8: the acquire request is still pending, so remove the Request object from the queue
                    deqQueue.remove(myReqNode);
                    throw ie;	// goto a14, returning from this method throwing InterruptedException
                }

                // a9: check if the acquire operation was done
            } while (myReqNode.value.state == QUEUED);
            // a10: return success, with the message passed through the Request object
            if (myReqNode.value.state == COMPLETED)
                return myReqNode.value.dequeuedMsg;
            throw new CancellationException();
        } finally {
            // a11: leave the monitor
            monitor.unlock();
        }
    }

    public Future<T> dequeue() { //asynchronous
        // aa1: enter the monitor
        monitor.lock();
        try {
            // aa2: Test if there are pending messages
            if (pendingMessages.size() > 0) {
                //aa2: get next message from the pending message list
                T message = pendingMessages.poll();
                /*
                 * Release semantics
                 */
                // we make room for a message, so satisfy a possible pending enqueue request
                if (enqQueue.isNotEmpty()) {
                    EnqueueRequest<T> request = enqQueue.pull().value;
                    pendingMessages.addLast(request.message);
                    request.isDone = true;
                    request.enqueueDone.signal();
                }
                // aa3: return a completed future with the dequeued message
                return new CompletedFuture(message);
            }

            //aa4: create an instance of the Request object and insert it in the wait queue
            NodeLinkedList.Node<DequeueRequest<T>> myReqNode = deqQueue.enqueue(new DequeueRequest<T>(monitor));

            //aa5: return a Future<T> associated to the dequeue request
            return new NodeBasedFuture(myReqNode);
        } finally {
            // a11: leave the monitor
            monitor.unlock();
        }
    }

    /**
     * Future<> implementations: implementation presented in course classes
     */

    /**
     * A Future that is already completed when it is created.
     */
    private class CompletedFuture implements Future<T> {
        private final T value;

        CompletedFuture(T value) { this.value = value; }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) { return false; }

        @Override
        public boolean isCancelled() { return false; }

        @Override
        public boolean isDone() { return true; }

        @Override
        public T get() { return value; }

        @Override
        public T get(long timeout, TimeUnit unit) { return value;}
    }

    /**
     * A Future that refers to a node in the queue.
     * This is an inner class, so that it can access the fields of the creating object,
     * namely the monitor.
     * All methods acquire the lock of the associated message queue object.
     */
    private class NodeBasedFuture implements Future<T> {
        // The node in the acquireRequests queue
        private final NodeLinkedList.Node<DequeueRequest<T>> reqNode;

        NodeBasedFuture(NodeLinkedList.Node<DequeueRequest<T>> reqNode) {
            this.reqNode = reqNode;
        }

        @Override
        public boolean isCancelled() {
            monitor.lock();
            try {
                return reqNode.value.state == CANCELLED;
            } finally {
                monitor.unlock();
            }
        }

        @Override
        public boolean isDone() {
            monitor.lock();
            try {
                return reqNode.value.state == COMPLETED;
            } finally {
                monitor.unlock();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            monitor.lock();
            try {
                if (reqNode.value.state == COMPLETED)
                    return false;
                if (reqNode.value.state == CANCELLED)
                    return true;
                // request node is in the queue
                deqQueue.remove(reqNode);
                reqNode.value.state = CANCELLED;
                reqNode.value.dequeueDone.signalAll();	// notify threads blocked by Future<>.get methods
                return true;
            } finally {
                monitor.unlock();
            }
        }

        // implementation following the "monitor style"
        @Override
        public T get() throws InterruptedException {
            monitor.lock();
            try {
                while (reqNode.value.state == QUEUED) {
                    reqNode.value.dequeueDone.await();
                    // no catch because there are no actions needed on withdrawal due to InterruptedException
                }
                if (reqNode.value.state == COMPLETED)
                    return reqNode.value.dequeuedMsg;
                throw new CancellationException();
            } finally {
                monitor.unlock();
            }
        }

        // implementation following the "monitor style"
        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            monitor.lock();
            try {
                if (reqNode.value.state != QUEUED) {
                    if (reqNode.value.state == COMPLETED)
                        return reqNode.value.dequeuedMsg;
                    throw new CancellationException();
                }
                if (timeout == 0)
                    throw new TimeoutException();

                long nanosTimeout = unit.toNanos(timeout);
                do {
                    if (nanosTimeout <= 0)
                        throw new TimeoutException();
                    nanosTimeout = reqNode.value.dequeueDone.awaitNanos(nanosTimeout);
                } while (reqNode.value.state == QUEUED);
                if (reqNode.value.state == COMPLETED)
                    return reqNode.value.dequeuedMsg;
                throw new CancellationException();
            } finally {
                monitor.unlock();
            }
        }
    }

    /*
     * Test code
     */
    private static void testBlockingMessageQueue() throws InterruptedException{
        BlockingMessageQueue<Integer> msgQueue = new BlockingMessageQueue<>(1);
        final int SENDERS = 10;
        Thread[] senderThreads = new Thread[SENDERS];
        /**

        Future<Integer> future = msgQueue.dequeue();
        System.out.println("dequeue returned");
        try {
            int count = 0;
            while (!future.isDone()) {
                Thread.sleep(250);
                System.out.print('.');
                if (++count==10) {
                    msgQueue.enqueue(42, Long.MAX_VALUE);
                }
            }
            int message = future.get();
            System.out.printf("\nAfter future.get(): %d\n", message);
        } catch (ExecutionException ee){}
         */

        for (int i = 0; i < SENDERS; i++) {
            int sid = i;
            senderThreads[i] = new Thread( () -> {
                try {
                    boolean success = msgQueue.enqueue(sid, Long.MAX_VALUE);
                    //boolean success = msgQueue.enqueue(sid, 10);
                    if (success)
                        System.out.printf("Enqueue %d\n", sid);
                    else
                        System.out.printf("Enqueue %d timeout\n", sid);
                } catch (InterruptedException ie) {
                    System.out.printf("Sender %d was interrupted\n", sid);
                }
            });
            senderThreads[i].start();
        }
        // wait for all sender threads start
        Thread.sleep(100);
        //senderThreads[8].interrupt();
        /*
        // synchronous dequeue
        for (int i = 0; i < SENDERS; i++) {
            int message = msgQueue.dequeueSynchronous();
            System.out.printf("Dequeue: %d\n", message);
        }
        */

        // asynchronous receive
        for (int i = 0; i < SENDERS; i++) {
            Future<Integer> future = msgQueue.dequeue();
            try {
                int message;
                try {
                    System.out.printf("isDone: %b\n", future.isDone());
                    message = future.get(100, TimeUnit.MILLISECONDS);
                    System.out.printf("Dequeued: %d\n", message);
                } catch (TimeoutException te) {
                    System.out.println("Dequeue timeout");
                }
            } catch (ExecutionException ee) { }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        testBlockingMessageQueue();
    }
}

