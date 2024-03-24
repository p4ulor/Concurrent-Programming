package pt.isel.pc.problemsets.set2;

import java.util.concurrent.Exchanger;
import java.util.concurrent.locks.*;

// a) Exchanger<V> using monitors with an implementation following the kernel style
public class MyExchanger<V> {
    // i1: the monitor
    private final Lock monitor = new ReentrantLock();

    // i2: the request object
    private static class Request<E>{
        E first; // the offered object, acquired argument
        E second; // the returned object: acquire result
        Condition done;
        boolean isDone = false;
        Request(E first, Lock monitor){
            this.first = first;
            this.done = monitor.newCondition();
        }
    }

    // i2: the queue of pending requests. This queue has only 2 possible states. Either it is empty
    // or it has the request of the first thread of a pair. So, a single reference is enough to implement
    // the queue

    private Request<V> xchgSpot = null;

    // i3: there is no specific synchronization state beyond xchgSpot

    // exchange method with interruption support
    public V exchange(V myData) throws InterruptedException {
        monitor.lock();
        try{
            if (xchgSpot==null) {
                // this is the first thread of a pair
                // create an instance of the Request object and "enqueue" it
                Request<V> request = new Request<>(myData, monitor);
                xchgSpot = request;

                do {
                    try {
                        request.done.await();
                    } catch(InterruptedException ie){
                        if(request.isDone) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        // remove the request and throw InterruptedException
                        xchgSpot = null;
                        throw ie;
                    }
                } while(!request.isDone);
                // return the object offered by the thread that pared with us
                return request.second;
            } else {
                // this is the 2nd thread of a pair, so complete exchange
                Request<V> request = xchgSpot;
                // "remove" the request from the "queue"
                xchgSpot = null;
                // offer myData
                request.second = myData;
                // set the request as done and notify the paired thread
                request.isDone = true;
                request.done.signal();
                //return the data offered by the first thread of the pair
                return request.first;
            }

        } finally {
            monitor.unlock();
        }
    }

}


