package pt.isel.pc.problemsets.set1;

import java.security.Key;
import java.util.LinkedList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Set;
import java.util.HashSet;

public class KeyedThreadPoolExecutor {
    // the type that represents a execute request (same as the message in a message queue)
    private static class WorkItem {
        final Runnable runnable;
        final Object key;
        WorkItem(Runnable runnable, Object key){
            this.runnable = runnable;
            this.key = key;
        }
    }

    // i1. the monitor
    private final Lock monitor = new ReentrantLock();

    // i2. type used as request by the worker threads
    private static class WorkerRequest {
        // there is no acquire arguments
        WorkItem result; //acquire result: work item
        Condition done; // private condition variable where the worker thread is blocked
        Boolean isDone = false;

        WorkerRequest(Lock monitor){
            done = monitor.newCondition();
        }
    }

    // i2: worker threads queue
    private final NodeLinkedList<WorkerRequest> workersQueue = new NodeLinkedList<>();

    // i3: synchronization state: list of pending work items
    private final LinkedList<WorkItem> workItemList = new LinkedList<>();

    // i3: synchronization state: set with the keys that are currently executing
    private final Set<Object> executingKeys = new HashSet<>();

    // i3: synchronization state: actual number of worker threads
    private int actualWorkers;

    /*
     * Declarations related to shutdown synchronization (follows monitor style)
     */

    // i2: condition variable used with shutdown synchronization
    private final Condition shutdownCompleted = monitor.newCondition();

    // i3: synchronization state: the shutting down flag
    private boolean shuttingDown;

    // thread pool executer parameters
    private final int maxPoolSize;
    private final int keepAliveTime;

    // initialize the thread pool executor
    public KeyedThreadPoolExecutor (int maxPoolSize, int keepAliveTime){
        if(maxPoolSize<=0)
            throw new IllegalArgumentException("maxPoolSize must be greater than zero");
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
    }

    // auxiliary method that retrieves the next work item taking the keys into account
    private WorkItem selectNextWorkItem(){
        WorkItem workItem = null;
        for (WorkItem wi : workItemList){
            if (!executingKeys.contains(wi.key)){
                // there is a work item that can be executed, select it
                workItem = wi;
                break;
            }
        }
        return workItem;
    }

    // get the next work item to execute (equivalent to receive in a message queue)
    private WorkItem getWorkItem(WorkItem previousWorkItem){
        monitor.lock();
        try {
            // if there is a previous execute work item, remove its key from the set
            if (previousWorkItem != null)
                executingKeys.remove(previousWorkItem.key);

            // a2: select the next work item
            WorkItem workItem = selectNextWorkItem();
            if (workItem != null){
                //a2: there is an available work item, remove it from the list
                workItemList.remove(workItem);

                //add the key to the executing key set
                executingKeys.add(workItem.key);
                return workItem;
            }

            // the work item list if empty
            if (workItemList.isEmpty() && shuttingDown) {
                // the pool is shutting down
                return null; //the worker thread will terminate
            }

            if (keepAliveTime == 0)
                return null; //the worker thread will terminate

            // add a request object to the workers queue
            NodeLinkedList.Node<WorkerRequest> reqNode = workersQueue.enqueue(new WorkerRequest(monitor)); //FIFO

            //prepare timeout processing
            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(keepAliveTime);

            do {
                if (nanosTimeout <= 0 || shuttingDown){
                    workersQueue.remove(reqNode);
                    return null;
                }

                try {
                    nanosTimeout = reqNode.value.done.awaitNanos(nanosTimeout);
                } catch (InterruptedException ie){
                    // no body may interrupt our worker threads: so, it is safe to ignore interrupts
                }
            } while(!reqNode.value.isDone);
            // add work item to the executing key set
            executingKeys.add(reqNode.value.result.key);
            return reqNode.value.result; //return the next work item to execute
        } finally {
            monitor.unlock();
        }
    }

    // this class implements the worker threads
    private class WorkerThread extends Thread {
        private final WorkItem initialWorkItem;
        WorkerThread(WorkItem initialWorkItem){
            this.initialWorkItem = initialWorkItem;
        }

        // the worker thread body. The work items are executed out of the monitor (without lock)
        public void run(){
            WorkItem workItem = initialWorkItem;
            do {
                try {
                    workItem.runnable.run();
                } catch (Exception e){
                    //ignores exceptions thrown by the submited runnables
                }
            } while((workItem=getWorkItem(workItem)) != null);
            // the worker thread will terminate, due to timeout or shutdown
            // acquire the monitor lock in order to decrement  the actual number of workers
            // and if shutdown is in progress, last worker thread must notify all the threads blocked by the
            // waitTermination method
            monitor.lock();
            try {
                //decrement the number of workers and if this is the last worker thread, notify the shutdown condition
                // if a shutdown was requested
                if (--actualWorkers==0 && shuttingDown)
                    shutdownCompleted.signalAll(); // notify all threads blocked by the waitTermination method
                if (!shuttingDown)
                    System.out.printf("Thread %d exiting due to the timeout\n", Thread.currentThread().getId());
            } finally {
                monitor.unlock();
            }
        }
    }

    //requests the execution of the work item in the thread pool executor
    public void execute(Runnable runnable, Object key) throws RejectedExecutionException {
        monitor.lock();
        try {
            // if the thread pool executor is shutting down, throw the appropriate exception
            if (shuttingDown)
                throw new RejectedExecutionException();

            // create a work item object to represent the request
            WorkItem workItem = new WorkItem(runnable, key);
            //if there is a runnable executing with the same key, we must add the work item to list of pending work items
            if (executingKeys.contains(key)) {
                workItemList.add(workItem);
                return;
            }
            // if there is an idle worker thread, assign this work item to it
            if(workersQueue.isNotEmpty()){
                WorkerRequest request = workersQueue.pull().value;
                request.result = workItem;
                request.isDone = true;
                request.done.signal();
            } else {
                //if there is no idle worker threads, test if we can create one more
                if (actualWorkers < maxPoolSize){
                    // add a new worker thread and give it to work item
                    actualWorkers++;
                    // add the work item key to executing keys set
                    executingKeys.add(workItem.key);
                    new WorkerThread(workItem).start();
                } else {
                    // if we cannot create a new worker thread, add the workItem to the pending list
                    workItemList.addLast(workItem);
                }
            }
        } finally {
            monitor.unlock();
        }
    }

    public void shutdown(){
        monitor.lock();
        try {
            if (!shuttingDown) {
                shuttingDown = true;
                // notify all blocked WorkerThreads so they can test the shutdown
                while(workersQueue.isNotEmpty()){
                    WorkerRequest request = workersQueue.pull().value;
                    request.done.signal();
                }
            }

        } finally {
            monitor.unlock();
        }

    }
    public boolean awaitTermination(int timeout) throws InterruptedException{
        monitor.lock();
        try {
            // wait until all worker threads finished, witch means that all accepted work is done
            if (actualWorkers==0)
                return true;

            if(timeout==0)
                return false;

            long nanosTimeout = TimeUnit.MILLISECONDS.toNanos(timeout);
            do {
                if(nanosTimeout<=0)
                    return false;
                nanosTimeout = shutdownCompleted.awaitNanos(nanosTimeout);
            } while(actualWorkers>0);
            return true;
        } finally {
            monitor.unlock();
        }
    }

    public void awaitTermination() throws InterruptedException{
        monitor.lock();
        try {
            while(actualWorkers>0){
                shutdownCompleted.await();
            }
        } finally {
            monitor.unlock();
        }
    }

    private static void sleep(int millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie){

        }
    }

    private static void testKeyedThreadPoolExecutor() throws InterruptedException{
        final int PARALLEL_SUBMISSIONS = 10;
        final int EXCLUSIVE_SUBMISSIONS = 4;
        final int EXECUTION_TIME = 250;
        final int KEEP_ALIVE_TIME = 100;
        final int INTER_SUBMISSION_TIME = 200;

        // create the thread pool executor with a number of worker threads equal to the number of processors
        // in the system
        final KeyedThreadPoolExecutor executor =
                new KeyedThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), KEEP_ALIVE_TIME);
        System.out.printf("Processors: %d\n", Runtime.getRuntime().availableProcessors());
        // submit runnables that can be executed in parallel
        /*for(int i = 0; i < PARALLEL_SUBMISSIONS; i++){
            int key = i;
            try {
                executor.execute(() -> {
                    System.out.printf("--> [p%d : %d]\n", key, Thread.currentThread().getId());
                    sleep(EXECUTION_TIME);
                    System.out.printf("<-- [p%d : %d]\n", key, Thread.currentThread().getId());
                }, key);
            } catch (RejectedExecutionException ree){
                System.out.println("Rejected exception");
            }
            //sleep(INTER_SUBMISSION_TIME);

        }*/

        //submit runnables that must be executed in exclusive
        Integer uniqueKey = 1;
        for(int i = 0; i < EXCLUSIVE_SUBMISSIONS; i++) {
            int key = i;
            try {
                executor.execute(() -> {
                    System.out.printf("--> [p%d : %d]\n", key, Thread.currentThread().getId());
                    sleep(EXECUTION_TIME);
                    System.out.printf("<-- [p%d : %d]\n",key, Thread.currentThread().getId());
                }, uniqueKey);
            } catch (RejectedExecutionException ree) {
                System.out.println("Rejected exception");
            }
        }

        executor.shutdown();
        executor.awaitTermination();
        System.out.println("Test completed");
    }

    public static void main(String[] args) throws InterruptedException {
        testKeyedThreadPoolExecutor();
    }

}
