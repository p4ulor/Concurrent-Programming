package pt.isel.pc.problemsets.set1

import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Synchronization technique used:
 */
interface MyFuture <T> {

    @Throws(InterruptedException::class)
    fun get() : T

    fun isDone() : Boolean
    fun setDone(item: T)
    fun start(task: () -> Unit)
}

class MyFutureImp <T> : MyFuture<T> {

    private var result: T? = null
    private var isDone: Boolean = false
    private var isRunning: Boolean = false

    private val lock = ReentrantLock()
    private val threadsWaitingForResult = lock.newCondition()

    @Throws(RejectedExecutionException::class)
    override fun start(task: () -> Unit){
        lock.withLock {
            if(! isRunning){
                isRunning = true
                println("start()")
                task()
            }
        }
    }

    @Throws(InterruptedException::class)
    override fun get() : T {
        lock.withLock {
            if(! isRunning) throw Exception("start() must be called first before get()")
            if (isDone) return result ?: throw Exception("Expected result to not be null")
            while (true) {
                try {
                    println("get() will wait")
                    threadsWaitingForResult.await() //Makes the current thread wait (and releases the lock)
                    if (isDone) return result ?: throw Exception("Expected result to not be null")
                } catch (e: InterruptedException){
                    throw e
                }
            }
        }
    }

    override fun isDone() : Boolean {
        lock.withLock {
            return isDone
        }
    }

    override fun setDone(item: T) {
        lock.withLock {
            println("setDone()")
            isDone = true
            result = item
            threadsWaitingForResult.signalAll()
        }
    }
}
