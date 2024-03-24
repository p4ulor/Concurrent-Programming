import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.toKotlinDuration

// Note: I cant put a suspendCoroutine call inside a lock. Otherwise, the error appears: The 'suspendCoroutine' suspension point is inside a critical section
class MessageQueue <T> (private val queueMaxSize: Int) {
    init { require(queueMaxSize>0) }

    private val msgs = mutableListOf<T>()
    private val continuationOfDequeue = mutableListOf<Continuation<T>>()
    private val lock = ReentrantLock()

    suspend fun enqueue(message: T) {
        lock.withLock {
            if(continuationOfDequeue.isNotEmpty()) {
                println("continuationOfDequeue is not empty, will get 1 and resume it - ${currThread()}")
                continuationOfDequeue.removeLast().resume(message)
            } else {
                if(msgs.size==queueMaxSize) throw IllegalStateException("MsgQueue is full - ${currThread()}")
                try {
                    msgs.add(message)
                }catch (e: CancellationException){
                    println("Exception - $e - ${currThread()}")
                    msgs.remove(message)
                    throw e
                }
            }
        }
    }

    suspend fun dequeue(timeout: Duration) : T {
        val isEmpty = lock.withLock { msgs.isEmpty() }
        if(isEmpty) {
            println("Msg queue is empty, will waitForEnqueue() - ${currThread()}")
            return waitForEnqueue(timeout)
        }
        return lock.withLock {
            if (msgs.isEmpty()) throw NoSuchElementException("Message queue is empty - ${currThread()}")
            msgs.removeLast()
        }
    }

    private suspend fun waitForEnqueue(timeout: Duration) : T {
        var _continuation: Continuation<T>? = null
        return withTimeout(timeout.toKotlinDuration()) {
            return@withTimeout try {
                val msg = suspendCancellableCoroutine<T> { continuation -> //must be "suspendCancellableCoroutine", not just "suspendCoroutine" or the withTimeout couldn't throw TimeoutCancellationException when a timeout happens
                    lock.withLock {
                        _continuation = continuation
                        continuationOfDequeue.add(continuation)
                    }
                } //continues after this continuation is called with .resume(msg)
                println("waitForEnqueue got $msg - ${currThread()}")
                return@withTimeout msg

            } catch (e: TimeoutCancellationException){
                println("Exception - $e - ${currThread()}")
                if(_continuation!=null) continuationOfDequeue.remove(_continuation)
                throw e
            } catch (e: CancellationException){
                println("Exception - $e - ${currThread()}")
                if(_continuation!=null) continuationOfDequeue.remove(_continuation)
                throw e
            }
        }
    }

    fun isListEmpty() = lock.withLock {
        msgs.isEmpty()
    }

    fun isContinuationListEmpty() = lock.withLock {
        println("continuationOfDequeue = $continuationOfDequeue")
        continuationOfDequeue.isEmpty()
    }

}