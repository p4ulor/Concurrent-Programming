package pt.isel.pc.problemsets.set1

import pt.isel.pc.problemsets.currThread
import java.time.Duration
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Synchronization technique used:
 */
class BlockingMessageQueue<T>(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be greater than 0" }
    }

    private val lock: Lock = ReentrantLock() //used for synchronization of data
    private val threadsWaiting: Condition = lock.newCondition() //used for synchronization of control

    private val messageList = LinkedList<T>()
    private val dequeueRequestList = LinkedList<Request<T>>() //this list contains threads that want more messages than the quantity that is available

    @Throws(InterruptedException::class)
    fun tryEnqueue(message: T, timeout: Duration) : Boolean {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }
        lock.withLock {
            var remainingTime = timeout.toNanos()
            while(messageList.size==capacity) { //Because of the thread phenomena called spurious wakeup, or if awakened, but the conditions aren't met
                println("Max capacity reached for ${currThread()}")
                try {
                    remainingTime = threadsWaiting.awaitNanos(remainingTime)
                    if (remainingTime <= 0) return false
                } catch (ie: InterruptedException) {
                    throw ie
                }
            }
            messageList.addFirst(message)
            dequeueRequestList.forEach {
                if (it.numOfMessagesRequested==messageList.size)
                    it.threadWaiting.signal()
            }
            return true
        }
    }

    @Throws(InterruptedException::class)
    fun tryDequeue(nOfMessages: Int, timeout: Duration) : List<T>? {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }
        require(nOfMessages > 0) { "nOfMessages must be greater than zero" }
        lock.withLock {
            var remainingTime = timeout.toNanos()
            val currentThreadRequest = Request<T>(nOfMessages, lock.newCondition())
            dequeueRequestList.add(currentThreadRequest)
            while(messageList.size < nOfMessages) { //It's a while because of the thread phenomena called spurious wakeup
                println("I, ${currThread()}, couldn't get my $nOfMessages message(s), I'll wait ${timeout.seconds}s for it")
                try {
                    remainingTime = currentThreadRequest.threadWaiting.awaitNanos(remainingTime)
                    println("I, ${currThread()}, woke up")
                    if(remainingTime<=0) return null
                } catch (ie: InterruptedException) {
                    throw ie
                } finally {
                    dequeueRequestList.remove(currentThreadRequest)
                }
            }
            return messageList.takeLast(nOfMessages)
        }
    }

    private class Request<T>( //Pattern of specific notification
        val numOfMessagesRequested: Int,
        val threadWaiting: Condition,
    )

}
