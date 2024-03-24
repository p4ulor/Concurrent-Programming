package pt.isel.pc.problemsets.set1

import pt.isel.pc.problemsets.currThread
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Synchronization technique used:
 */
class ThreadPoolExecutor(private val maxThreadPoolSize: Int, private val keepAliveTime: Duration) {

    init {
        require(maxThreadPoolSize > 0) { "maxThreadPoolSize must be greater than zero" }
        require(keepAliveTime > Duration.ZERO) { "keepAliveTime must be greater than zero" } //keepAliveTime is the time a Thread can wait for a Runnable
    }

    private var isShutdown = false

    private val thingsToRun = mutableListOf<Runnable>() //AKA workQueue
    private val thingsToCall = mutableListOf<Callable<Any>>()
    private var currentRunnablesRan: Int = 0
    private var currentCallablesRan: Int = 0

    private var currentThreadCount: Int = 0 //AKA currentWorkThreads
    private var currentThreadsWaiting = 0

    private val lock = ReentrantLock()
    private val threadsWaitingForThingsToRun = lock.newCondition()
    private val threadsWaitingForThingsToCall = lock.newCondition()
    private val threadsWaitingForTermination = lock.newCondition() //these threads aren't created by this class

    @Throws(RejectedExecutionException::class)
    fun execute(runnable: Runnable) {
        lock.withLock {
            if(isShutdown) throw RejectedExecutionException()
            val isThereAThreadAvailable = addRunnable(runnable)
            if (!isThereAThreadAvailable && currentThreadCount < maxThreadPoolSize) {
                ++currentThreadCount
                println("currentThreadCount = $currentThreadCount")
                Thread { //Note that the Thread's code is run outside the lock
                    while (true) { //A thread can run one or more things to run (or none, if there's no things to run)
                        val maybeRun = getThingToRun() ?: break
                        maybeRun.run().also {
                            lock.withLock {
                                ++currentRunnablesRan
                                if(isShutdown) return@Thread
                            }
                        }
                    }
                    lock.withLock {
                        --currentThreadCount
                        println("currentThreadCount = $currentThreadCount")
                        if (currentThreadCount == 0) threadsWaitingForTermination.signalAll()
                    }
                }.start()
            }
        }
    }

    /**
     * @return Either there's a thread that can handle the runnable or not
     */
    private fun addRunnable(runnable: Runnable) : Boolean { //Must be called inside a lock
        thingsToRun.add(runnable) //adds to the end
        if(currentThreadsWaiting>0) {
            threadsWaitingForThingsToRun.signal()
            return true
        }
        return false
    }

    private fun getThingToRun() : Runnable? {
        lock.withLock {
            if (thingsToRun.isNotEmpty()) return thingsToRun.removeLast()

            var remainingTime = keepAliveTime.toNanos()

            while (true) { //Because of the thread phenomena called spurious wakeup, or if awakened, but the conditions aren't met
                try {
                    ++currentThreadsWaiting
                    remainingTime = threadsWaitingForThingsToRun.awaitNanos(remainingTime)
                } catch (e: InterruptedException) {
                    return null
                }
                if (thingsToRun.isNotEmpty()) {
                    --currentThreadsWaiting
                    return thingsToRun.removeFirst()
                }
                if (remainingTime <= 0) return null
                if(isShutdown) return null
            }
        }
    }

    fun shutdown() {
        lock.withLock {
            isShutdown = true
        }
    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Duration) : Boolean { //Returns true to the calling thread, if there are no threads running in the pool
        lock.withLock {
            return try {
                if (currentThreadCount == 0) return true

                var remainingTime = timeout.toNanos()
                do {
                    if (remainingTime <= 0) return false
                    remainingTime = threadsWaitingForTermination.awaitNanos(timeout.toNanos())
                } while (currentThreadCount > 0)
                return true
            } catch (e: InterruptedException){
                throw e
            }
        }
    }

    @Throws(RejectedExecutionException::class)
    fun <T> execute(callable: Callable<T>) : MyFuture<T> { //Ex4, kinda scuffed?
        val future = MyFutureImp<T>()
        future.start {
            lock.withLock {
                if(isShutdown) throw RejectedExecutionException()
                val isThereAThreadAvailable = addCallable(callable as Callable<Any>)
                if (!isThereAThreadAvailable && currentThreadCount < maxThreadPoolSize) {
                    ++currentThreadCount
                    Thread { //Note that the Thread's code is run outside the lock
                        println("${currThread()} running")
                        while (true) {
                            val maybeCallable = getThingToCall() ?: break
                            maybeCallable.call().also {
                                //println("called .also {")
                                future.setDone(it!! as T) //this is why I make the variable first. I added "!!" because .also is providing T! IDK why
                                lock.withLock {
                                    ++currentCallablesRan
                                    if(isShutdown) return@Thread
                                }
                            }
                        }
                        lock.withLock {
                            --currentThreadCount
                            println("currentThreadCount = $currentThreadCount")
                            if (currentThreadCount == 0) threadsWaitingForTermination.signalAll()
                        }
                    }.start()
                }
            }
        }
        return future
    }

    /**
     * @return Either there's a thread that can handle the callable or not
     */
    private fun addCallable(callable: Callable<Any>) : Boolean { //Must be called inside a lock
        println("Added callable")
        thingsToCall.add(callable) //adds to the end
        if(currentThreadsWaiting>0) {
            threadsWaitingForThingsToCall.signal()
            return true
        }
        return false
    }

    private fun getThingToCall() : Callable<Any>? {
        lock.withLock {
            if (thingsToCall.isNotEmpty()) {
                println("Thread got thingToCall")
                return thingsToCall.removeLast()
            }

            var remainingTime = keepAliveTime.toNanos()

            while (true) { //It's a while true because of the thread phenomena called spurious wakeup
                try {
                    println("${currThread()} will wait")
                    ++currentThreadsWaiting
                    remainingTime = threadsWaitingForThingsToCall.awaitNanos(remainingTime)
                } catch (e: InterruptedException) {
                    return null
                }
                if (thingsToCall.isNotEmpty()) {
                    println("Thread got thingToCall")
                    --currentThreadsWaiting
                    return thingsToCall.removeFirst()
                }
                if (remainingTime <= 0) return null
                if(isShutdown) return null
            }
        }
    }

    fun getCurrentThreadCount() : Int = lock.withLock { return currentThreadCount }
    fun getSizeThingsToRun() : Int = lock.withLock { return thingsToRun.size }
    fun getSizeThingsToCall() : Int = lock.withLock { return thingsToCall.size }
    fun getCurrentRunnablesRan() : Int = lock.withLock { return currentRunnablesRan }
    fun getCurrentCallablesRan() : Int = lock.withLock { return currentCallablesRan }
}
