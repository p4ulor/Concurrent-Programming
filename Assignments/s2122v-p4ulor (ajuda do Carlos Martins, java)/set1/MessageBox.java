package pt.isel.pc.problemsets.set1;

import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageBox<T> { //Kernel style in use

    private final Lock monitor = new ReentrantLock();

    private final NodeLinkedList<Request<T>> queueRequests = new NodeLinkedList<>();
    private final LinkedList<T> pendingMessages = new LinkedList<>();
    private AtomicInteger atomicInt = new AtomicInteger(0);

    private static class Request<V> {
        public V receivedMsg;			// mensage received: result of the acquire operation
        public boolean isDone = false;	// true when the acquire is done
        public Condition receiveDone;	// private condition variable

        public Request(Lock monitor) {
            this.receiveDone = monitor.newCondition();
        }
    }

    public Optional<T> waitForMessage(long timeout) throws InterruptedException { //acquire
        if (Thread.interrupted()) throw new InterruptedException();          //checks if current thread is interrupted
        monitor.lock();
        if (timeout <= 0) return Optional.empty();

        try {
            if (queueRequests.isEmpty() && pendingMessages.size() > 0) {     //if there's no request before this one, and there's a message (waiting to be sent out), then return it immediately
                return Optional.of(pendingMessages.poll());
            }

            NodeLinkedList.Node<Request<T>> thisRequest = queueRequests.enqueue(new Request<T>(monitor));

            do {
                try {
                    if(thisRequest.value.receiveDone.awaitNanos(timeout)<=0) {
                        queueRequests.remove(thisRequest);
                        return Optional.empty();                            //block the current thread until deadline, when reached, it will return value equal or inferior to zero, then return empty
                    }
                } catch (InterruptedException ie) {                         //goes here if interrupted
                    if (thisRequest.value.isDone) {                         //check if, even though the thread got interrupted, the request was full filled
                        Thread.currentThread().interrupt();
                        break;	                                            //exits the do while and returns the message
                    }
                    queueRequests.remove(thisRequest);                      //dont leave the acquire request pending, so remove the Request object from the queue
                    return Optional.empty();
                }
            } while (!thisRequest.value.isDone);                            //while the request HASN'T received it's message
            return Optional.of(thisRequest.value.receivedMsg);              //returned successfully, with the message passed through the Request object
        } finally {
            monitor.unlock();
        }
    }
    public int sendToAll(T message){ //release
        if (message==null) throw new IllegalArgumentException();
        monitor.lock();
        try {
            pendingMessages.addLast(message);

            while (queueRequests.isNotEmpty() && pendingMessages.size() > 0) { //while there are pending requests and available messages
                Request<T> request = queueRequests.pull().value;
                request.receivedMsg = pendingMessages.poll();
                request.isDone = true;
                request.receiveDone.signal();

                atomicInt.getAndIncrement();
            }
        } finally {
            monitor.unlock();
            return atomicInt.get();
        }
    }
}

