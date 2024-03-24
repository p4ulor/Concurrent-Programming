package pt.isel.pc.problemsets.set2;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class SpinExchangeB <V> {
    // the request object: works as holder object that aggregates the mutable shared state
    private static class Request<E>{
        E first; // the offered object, acquired argument
        E second; // the returned object: acquire result
        volatile boolean isDone = false; // true when done; volatile because we will spin on it
        Request(E first){
            this.first = first;
        }
    }

    // the exchange spot
    private final AtomicReference<Request<V>> xchgSpot = new AtomicReference<>();

    // exchange method without support for interruption
    public V exchange(V myData){
        Request<V> request = null;
        do {
            // s1:
            var observedXchgSpot = xchgSpot.get();
            // s2:
            if (observedXchgSpot==null){
                // this is the first thread of the pair
                if(request==null)
                    request = new Request<>(myData);

                // s3:
                if(xchgSpot.compareAndSet(null, request)){
                    // s3.i
                    while(!request.isDone) {
                        Thread.yield();
                    }
                    return request.second;
                }
                // s3.ii: CAS failed retry
            } else {
                // this is the second thread of a pair
                // s3: CAS to null in order to remove the request
                if(xchgSpot.compareAndSet(observedXchgSpot, null)){
                    // do the exchange
                    observedXchgSpot.second = myData;
                    observedXchgSpot.isDone = true;
                    return observedXchgSpot.first;
                }
                //cache failed retry
            }
        } while(true);
    }

}
