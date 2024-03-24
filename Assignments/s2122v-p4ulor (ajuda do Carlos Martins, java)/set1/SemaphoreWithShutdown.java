package pt.isel.pc.problemsets.set1;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/*
This semaphore defines 2 acquire operations implemented by the methods acquireSingle and
waitShutdownCompleted.
Since the acquireSingle operation specifies FIFO discipline we will you use the kernal style.
In the waitShutdownComplete operation we will use the monitor style because it is the simplest
implementation.
 */
public class SemaphoreWithShutdown { //FIFO -> estilo kernel, start shutdown -> monitor style
    //acquire operation results
    private static final int COMPLETED = 0, CANCELED = 1;
    //the semaphore state
    private static final int NORMAL = 0, SHUTING_DOWN = 1, SHUTTED_DOWN = 2;

    // i1: create the monitor
    private final Lock monitor = new ReentrantLock();

    // i2: Request object to be used by the acquire operation
    private static class Request {
        // there are no acquire argument (acquires always one permit)
        int result; //the acquire single result: COMPLETED or CANCELED
        public Condition acquireDone;		// the condition variable where the acquirer thread will block
        // there are no acquire result
        public boolean isDone = false;		// true when acquire is done

        public Request(Lock monitor) {
            this.acquireDone = monitor.newCondition();
        }
    }
    // i2: The queue for the acquire single operation
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();

    // i2: the condition variable used with the wait for shutdown operation
    private final Condition shutdownDone = monitor.newCondition();
    // i3: synchronization state for acquire single operation
    private int permits;
    private final int initialPermits;

    // i3: synchronization state for the waitShutdownComplete operation: the semaphore state
    private int semaphoreState;
    // i4: constructor: initialize synchronization state
    public SemaphoreWithShutdown(int initialPermits) {
        if (initialPermits < 0)
            throw new IllegalArgumentException("initialUnits must be >= 0");
        this.permits = this.initialPermits = initialPermits;
        this.semaphoreState = NORMAL;
    }

    // acquireSingle: acquire a permit from the semaphore with timeout support
    public boolean acquireSingle(long timeoutMs) throws InterruptedException {
        // validate argument
        if (timeoutMs < 0)
            throw new IllegalArgumentException("Timeoutms must be greater than zero");
        // a1: test if an interrupt is pending; if so throw InterruptedException
        if (Thread.interrupted())
            throw new InterruptedException();

        // a2: enter the monitor
        monitor.lock();
        try {
            // if the semaphore is shut down or is being shutting down throw CancelationException
            if(semaphoreState != NORMAL)
                throw new CancellationException();
            // a3: if the queue is empty evaluate the acquire's predicate
            if (queue.isEmpty() && permits > 0) {
                // a3: the acquire predicate is true
                permits -= 1;		// update the synchronization state
                return true;		// goto step a14, returning with success
            }

            // a4: check for immediate timeout
            if (timeoutMs == 0)
                return false;		// goto a14, returning timeout

            //a5: create an instance of the Request object and insert it in the wait queue
            NodeLinkedList.Node<Request> myReqNode = queue.enqueue(new Request(monitor));

            // a6: prepare the timeout processing
            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

            do {
                // a7: check if the timeout already expired
                if (nanosTimeout <= 0) {
                    // a7: remove the Request object from the wait queue cancelling the acquire operation
                    queue.remove(myReqNode);
                    // in this synchronizer, no other queued request can be now satisfied
                    return false;		// // goto a14, returning timeout
                }
                try {
                    // a8: block the current thread on its private condition variable
                    nanosTimeout = myReqNode.value.acquireDone.awaitNanos(nanosTimeout);
                } catch (InterruptedException ie) {
                    // a9: check if the aquire was already done
                    if (myReqNode.value.isDone) {
                        // a10: the acquire is already complete, rearm interrupt, and goto a13
                        Thread.currentThread().interrupt();
                        break;	// this break, breaks the do {} while
                    }
                    // a11: the acquire is pending, so remove Request object from the wait queue
                    queue.remove(myReqNode);
                    // in this synchronizer, no other queued request can be now satisfied
                    throw ie;		// goto to a14, returning from this method throwing InterruptException
                }

                // a12: check if the acquire operation was done
            } while (!myReqNode.value.isDone);
            // a13: return success, and goto a14
            if (myReqNode.value.result == COMPLETED)
                return true;
            throw new CancellationException();
        } finally {
            // a14: leave the monitor
            monitor.unlock();
        }
    }

    public void acquireSingle() throws InterruptedException {
        acquireSingle(Long.MAX_VALUE);
    }

    // releaseSingle: release a single permit
    void releaseSingle() {
        // r1: enter the monitor
        monitor.lock();
        try {
            if (semaphoreState == NORMAL) {
                // r2: update synchronization state
                permits += 1;

                // r3: a permit can only satistify a pending request, so
                // r3.1: check if the queue is not empty
                if (queue.isNotEmpty()) {
                    // r3.2: remove the request object that is at the front of the queue
                    Request request = queue.pull().value;
                    // r3.3: adjust synchronization state, mark the request as done, and notify the acquirer thread
                    permits -= 1;                    // discount given permit
                    request.result = COMPLETED;
                    request.isDone = true;            // mark request as done
                    request.acquireDone.signal();    // notify acquirer thread in its private condition variable
                }
            } else {
                // The semaphore is shutting down or already shut down
                if (semaphoreState == SHUTTED_DOWN)
                    return;
                // If the semaphore is shutting down increment permits and test if shutdown is completed
                if (++permits >= initialPermits) {
                    // The shutdown process is completed: update semaphore state and notify all blocked threads
                    // in the waitShutdownComplete operation
                    semaphoreState = SHUTTED_DOWN;
                    shutdownDone.signalAll();
                }
            }
        } finally {
            // r4: leave the monitor
            monitor.unlock();
        }
    }

    // Start shutdown: starts the semaphore shutdown (pre-release for waitShutdownCompleted operation
    // and release (with cancelation) for acquire single operation)
    public void startShutdown() {
        // r1: Enter the monitor
        monitor.lock();
        try {
            // if a shutdown is already in progress, return
            if (semaphoreState != NORMAL)
                return;
            // r2: Update synchronization state of the shutdown process
            semaphoreState = (permits >= initialPermits) ? SHUTTED_DOWN : SHUTING_DOWN;

            // r3.1: while there are pending acquire requests, cancel it
            while (queue.isNotEmpty()) {
                // r3.2: remove the request that is at the front of the queue
                Request request = queue.pull().value;
                // r3.3: set the result as CANCELLED, mark the request as done, and notify the acquire thread
                request.result = CANCELED;
                request.isDone = true;
                request.acquireDone.signal();
            }

        } finally {
            // r4: Leave the monitor
            monitor.unlock();
        }
    }

    // waitShutdownComplete: acquire operation with timeout support for shutdown synchronization
    public boolean waitShutdownComplete(long millisTimeout) throws InterruptedException {
        if (millisTimeout < 0)
            throw new IllegalArgumentException("millisTimout must be greater than zero");

        monitor.lock();
        try {
            // evaluate the acquire predicate
            if (semaphoreState == SHUTTED_DOWN)
                return true;

            if (millisTimeout==0)
                return false;

            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(millisTimeout);
            do {
                if(nanosTimeout <= 0)
                    return false;

                nanosTimeout = shutdownDone.awaitNanos(nanosTimeout);
            } while(semaphoreState != SHUTTED_DOWN);
            return true;
        } finally {
            monitor.unlock();
        }
    }

    public void waitShutdownComplete() throws InterruptedException {
        waitShutdownComplete(Long.MAX_VALUE);
    }

    //se o numero de unidades for iguais ao valor inicial definido na construçao -> encerra o shutdown
    /*
     5
    start shuwdown
    5 ou mais
            entao transita para estado shutdown
            down
    else esperar
    consultar countdown lach, mas eu uso é o estado

    shutdown -> sao definidos 3 etsados de funcionamento : normal, a encerrar , encerrado
    quando chegar a shutting down -> libertar as threads
     */

    /*
     * test the semaphore as a mutual exclusion mechanism
     */

    /**
     * Test the semaphore as a mutual exclusion mechanism
     */
    private static int sharedCounter = 0;

    private static boolean testSemaphore() throws InterruptedException {
        SemaphoreWithShutdown mutex = new SemaphoreWithShutdown(1);			// a mutual exclusion semaphore: 1: lock free
        final int TEST_THREADS = 10, INC_BY_THREAD = 20;
        Thread testThreads[] = new Thread[TEST_THREADS];

        for (int i = 0; i < TEST_THREADS; ++i) {
            int tid = i;
            testThreads[i] = new Thread(() -> {
                System.out.printf("--thread %d started...\n", tid);
                int incs = 0;
                do {
                    // increment the shared counter ensuring mutual exclusive access
                    try {
                        mutex.acquireSingle();		// acquire the mutex
                        // begin critical section
                        int c = sharedCounter;
                        Thread.sleep(1);			// force collision
                        sharedCounter = c + 1;		// increment the shared counter
                        // end critical section
                        mutex.releaseSingle();		// release the mutex
                        incs += 1;
                    } catch (InterruptedException ie) {
                        System.out.printf("**thread %d was interrupted after %d increments\n", tid, incs);
                        break;
                    }
                } while (incs < INC_BY_THREAD);

                System.out.printf("--thread %d exiting, after %d increments...\n", tid, incs);
            });
            testThreads[i].start();
        }

        // wait for a while: in order to allow all threads execute completely
        Thread.sleep(500);

        // synchronize with the termination of the test threads
        for (var tt : testThreads) {
            tt.join();
        }

        System.out.printf("--actual shared counter: %d \n", sharedCounter);
        boolean passed = sharedCounter == TEST_THREADS * INC_BY_THREAD;
        System.out.printf("--passed: %b\n", passed);
        return passed;
    }

    // test the semphore shutdown
    private static void testSemaphoreShutdown() throws InterruptedException {
        SemaphoreWithShutdown semaphore = new SemaphoreWithShutdown(1);
        // get permits from semaphore
        semaphore.acquireSingle();
        // create 2 threads that will synchronize with shutdown
        Thread t1 = new Thread( () -> {
            try {
                semaphore.waitShutdownComplete();
                System.out.println("Thread 1, shutdown completed detected");
                //Thread.sleep(10000);
            } catch (InterruptedException ie){ }
        });
        t1.start();

        Thread t2 = new Thread( () -> {
            try {
                semaphore.waitShutdownComplete();
                System.out.println("Thread 2, shutdown completed detected");
            } catch (InterruptedException ie){ }
        });
        t2.start();

        Thread.sleep(100);

        // initiate the semaphore shutdown
        semaphore.startShutdown();
        try {
            semaphore.acquireSingle();

        } catch (CancellationException ce){
            System.out.println("Acquired was canceled");
        }

        Thread.sleep(5000);
        System.out.println("Release the permit in order to complete shutdown");
        semaphore.releaseSingle();

        t1.join();
        t2.join();
    }

    public static void main(String[] args) throws InterruptedException {
        //testSemaphore();
        testSemaphoreShutdown();
    }
}




