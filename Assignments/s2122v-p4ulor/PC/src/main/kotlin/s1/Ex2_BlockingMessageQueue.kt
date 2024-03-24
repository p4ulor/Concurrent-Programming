package s1

import java.time.Duration
import java.util.*
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
    FIFO Message queue
    2 Locks are used, one for enqueueing one for dequeue
    The timeout is used in 2 circumstances:
        - In tryEnqueue, if the requestList is full or cannot receive the current enqueue request. In this case, the thread waits until timeout
        -
 */

class BlockingMessageQueue<T>(private val capacity: Int) {

    init {
        require(capacity > 0) {"Capacity must be greater than 0"}
    }

    //private class Request<T>(var isDone: Boolean = false, var message: T? = null) {
    //private class Message<T>(val msg: T, var delivered: Boolean = false)

    private val enqueueLock: Lock = ReentrantLock()
    private val enqueueCondition: Condition = enqueueLock.newCondition()

    private val dequeueLock: Lock = ReentrantLock()
    private val dequeueLockCondition: Condition = dequeueLock.newCondition()

    private val messageList = LinkedList<T>()
    private class Request<T>(var item: T? = null, val myCondition: Condition) //padrao da notificaçao especifica
    private val requestList = LinkedList<Request<T>>() //this list contains

    /**
     Receives list of messages, and all of them are tied to 1 timeout
     If a tryEnqueue() call comes with too many messages, they will all wait.
     I didn't chose to add message by message until (and if) the maximum capacity is reached, to simplify and because
     the timeout is supposed to regard the whole list, not parts of it. If I did it like that, it would be possible
     that certain messages would be enqueued, the max capacity if reached, and then timeout reaches zero, part of the
     messages were not enqueued and plus the method returns Boolean, not the list of messages that didn't go through
     */

    @Throws(InterruptedException::class)
    fun tryEnqueue(messages: List<T>, timeout: Duration): Boolean {
        require(timeout > Duration.ZERO) {"timeout must be greater than zero"}
        enqueueLock.withLock {
            var remainingTime = timeout.toNanos()
            if(messageList.size==capacity || (messages.size+messageList.size)>capacity) {
                try {
                    remainingTime = enqueueCondition.awaitNanos(remainingTime)
                    if (remainingTime <= 0) return false
                } catch (ie: InterruptedException) { throw ie }
            }

            messages.forEach {
                messageList.addFirst(it)
            }
            notifyLatestRequest()
            return true
        }
    }

    @Throws(InterruptedException::class)
    fun tryDequeue(timeout: Duration): T? {
        require(timeout > Duration.ZERO) {"timeout must be greater than zero"}
        dequeueLock.withLock {
            try {
                if(messageList.isEmpty()) {
                    val myRequest = Request<T>(myCondition = dequeueLock.newCondition())
                    requestList.addFirst(myRequest)
                    var remainingTime = timeout.toNanos()
                    remainingTime = myRequest.myCondition.awaitNanos(remainingTime)
                    if(remainingTime<=0) return null
                    //return myRequest.item //meti comment acho q ta mal
                }
                return messageList.pollLast()
            } catch (ie: InterruptedException) {

                throw ie
            } finally {
                enqueueCondition.signal() //signal possible threads waiting to enqueue //WHAT?
            }
        }
    }

    private fun notifyLatestRequest() {
        val longestWaitingRequest = requestList.pollLast() // get request waiting the longest
        longestWaitingRequest.item = messageList.pollLast()
        longestWaitingRequest.myCondition.signal()
    }
}