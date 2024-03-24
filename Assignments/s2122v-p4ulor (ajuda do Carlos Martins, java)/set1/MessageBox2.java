package pt.isel.pc.problemsets.set1;
import java.util.Optional;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MessageBox2 <T> {
    // i1: create the monitor
    private final Lock monitor = new ReentrantLock();

    // i2: object Request usado pela operação acquire - wait for message
    private static class Request<V> {
        //there is no acquire argument
        public V receivedMsg;			// message received: result of the acquire operation
        public Condition receiveDone;	// private condition variable
        public boolean isDone = false;	// true when the acquire is done

        public Request(Lock monitor) {
            this.receiveDone = monitor.newCondition();
        }
    }
    // i2: The wait queue associated to the acquire operation
    private final NodeLinkedList<Request<T>> queue = new NodeLinkedList<>();

    // i3: synchronization state: not applicable (the sent message are never memorized)

    // i4: constructor: no initializations needed
    public MessageBox2() { }

    // waitForMessage: receives the next message sent to the message box with timeout support from the message queue without timeout support (acquire operation)

    public Optional<T> waitForMessage(long timeoutMs) throws InterruptedException {
        // validate argument
        if(timeoutMs<=0)
            throw new IllegalArgumentException("timeoutMs must be greater than zero");
        // a1: test if an interrupt is pending; if so throw InterruptedException
        if (Thread.interrupted())
            throw new InterruptedException();

        // a2: enter the monitor
        monitor.lock();
        try {
            // a3: not applicable (this method always block the current thread)

            // a4: not applicable (immediate timeout does not make sense)

            //a5: create an instance of the Request object and insert it in the wait queue
            NodeLinkedList.Node<Request<T>> myReqNode = queue.enqueue(new Request<T>(monitor));

            // a6: prepare the timeout processing
            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

            do {
                // a7: check if the timeout already expired
                if (nanosTimeout <= 0) {
                    // a7: remove the Request object from the wait queue, cancelling the acquire operation
                    queue.remove(myReqNode);
                    // in this synchronizer, now other queued requests can be now satisfied
                    return Optional.empty();	// goto step a14, returning timeout
                }
                try {
                    // a8: block the current thread on its private condition variable
                    nanosTimeout = myReqNode.value.receiveDone.awaitNanos(nanosTimeout);
                } catch (InterruptedException ie) {
                    // a9: check if the acquire request was already done
                    if (myReqNode.value.isDone) {
                        // a10: the acquire is already complete, rearm interrupt and goto a13
                        Thread.currentThread().interrupt();
                        break;	// this break, breaks the do {} while
                    }
                    // a11: the acquire request is still pending, so remove the Request object from the queue
                    queue.remove(myReqNode);
                    // in this synchronizer, now other queued requests can be now satisfied
                    throw ie;	// goto a14, returning from this method throwing InterruptedException
                }

                // a12: check if the acquire operation was done
            } while (!myReqNode.value.isDone);
            // a13: return success, with the message passed through the Request object
            return Optional.of(myReqNode.value.receivedMsg);
        } finally {
            // a14: leave the monitor
            monitor.unlock();
        }
    }

    // sendToAll: send a message to all blocked receiver threads (release many)
    public int sendToAll(T sentMsg) {

        // r1: enter the monitor
        monitor.lock();
        try {
            // r2: update synchronization state: not applicable (synchronization state is empty)
            int receivers = queue.getCount(); //number of receiver threads

            // r3.1: while there are pending requests give the sent message to all requests
            while (queue.isNotEmpty()) {
                // r3.2: remove the request that is at the front of queue
                Request<T> request = queue.pull().value;
                // r3.3: adust the synchronization state: not applicable
                request.receivedMsg = sentMsg;	// pass the message to the receiver thread through the request object
                request.isDone = true;
                request.receiveDone.signal();
            }
            return receivers;
        } finally {
            // r4: leave the monitor
            monitor.unlock();
        }
    }

    /*
    Test code
     */

    private static void _sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie){

        }
    }

    private static void test1() throws InterruptedException {
        MessageBox2<String> msgBox = new MessageBox2<>();
        final int RECEIVERS = 10;
        Thread[] rcvThrs = new Thread[RECEIVERS];
        //create and start receiver threads
        for (int i = 0; i < RECEIVERS; i++){
            int rid = i;
            rcvThrs[i] = new Thread( () -> {
                try {
                    Optional<String> receivedMessage;
                    if (rid % 2 == 0)
                        receivedMessage = msgBox.waitForMessage(1000);
                    else
                        receivedMessage = msgBox.waitForMessage(100);

                    if (receivedMessage.isPresent())
                        System.out.printf("Receiver %d, message: %s\n", rid, receivedMessage.get());
                    else {
                        System.out.printf("Receiver %d, timed out\n", rid);
                    }
                } catch (InterruptedException ie){
                    System.out.printf("Receiver %d, interrupted\n", rid);
                }
            });
            rcvThrs[i].start();
        }
        Thread.sleep(500);
        int receivers = msgBox.sendToAll("this is the message");
        System.out.printf("The message was sent to %d receivers\n", receivers);
        for (Thread rcv: rcvThrs) {
            rcv.join();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("MessageBox");
        test1();
    }

}

