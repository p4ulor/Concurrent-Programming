package pt.isel.pc.problemsets.set2

import pt.isel.pc.problemsets.currThread
import java.time.Duration
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

//https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/CyclicBarrier.html
//https://www.baeldung.com/java-cyclic-barrier
class MyCyclicBarrier (private val goalNumberOfWaitingThreads: Int, private val uponLastThreadReachesBarrier: () -> Unit) {

    init {
        require(goalNumberOfWaitingThreads >= 1) { "goalNumberOfWaitingThreads must be greater than zero" }
    }

    private var currentThreadsWaiting = 0
    private var isBroken = false
    private var goalReached = false

    private val lock: Lock = ReentrantLock()
    private var threadsWaiting = lock.newCondition()

    fun await(timeout: Duration) : Int {
        lock.withLock {
            try {
                var remainingTime = timeout.toNanos()

                while(goalReached){ //if a thread tries to await() when the goal was reached, it will have to wait
                    remainingTime = threadsWaiting.awaitNanos(remainingTime)
                }

                if(isBroken){
                    throw BrokenBarrierException()
                }

                val arrivalIndex = currentThreadsWaiting
                ++currentThreadsWaiting
                while(true){

                    if(currentThreadsWaiting==goalNumberOfWaitingThreads){
                        println("With ${currThread()}, the goal has been reached")
                        uponLastThreadReachesBarrier()
                        --currentThreadsWaiting
                        goalReached = true
                        threadsWaiting.signalAll()
                        return arrivalIndex
                    }

                    remainingTime = threadsWaiting.awaitNanos(remainingTime)
                    if(goalReached) {
                        --currentThreadsWaiting
                        if(currentThreadsWaiting==0) _reset()
                        return arrivalIndex
                    }

                    if(isBroken){
                        throw BrokenBarrierException()
                    }

                    if (remainingTime <= 0) {
                        isBroken = true
                        return -1
                    }
                }
            } catch (ie: InterruptedException){
                isBroken = true
                return -1
            }
        }
    }

    fun await() : Int {
        return await(Duration.ofDays(365))
    }

    fun getNumberOfThreadsWaitingForOthers() : Int { // == getNumberWaiting()
        lock.withLock {
            return currentThreadsWaiting
        }
    }

    fun getGoalNumberOfWaitingThreads() : Int { // == getParties()
        lock.withLock {
            return goalNumberOfWaitingThreads
        }
    }

    private fun _reset(){
        currentThreadsWaiting = 0
        isBroken = false
        goalReached = false
        threadsWaiting.signalAll()
    }

    fun reset(){
        lock.withLock {
            _reset()
        }
    }

    fun isBroken() : Boolean {
        lock.withLock {
            return isBroken
        }
    }
}