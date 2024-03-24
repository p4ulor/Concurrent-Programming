import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration

class MessageQueue_2122v_1<T>() {

    private val lock: Lock = ReentrantLock() //used for synchronization of data
    private val threadsWaiting: Condition = lock.newCondition() //used for synchronization of control

    private val messageList = LinkedList<T>()
    private val dequeueRequestList = LinkedList<Request<T>>() //this list contains threads that want more messages than the quantity that is available

    fun enqueue(message: T) : Unit {
        lock.withLock {
            messageList.addFirst(message)
            dequeueRequestList.forEach {
                if (it.numOfMessagesRequested==messageList.size)
                    it.threadWaiting.signal()
            }
        }
    }

    @Throws(InterruptedException::class)
    fun tryDequeue(nOfMessages: Int, timeout: Duration) : List<T>? {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }
        require(nOfMessages > 0) { "nOfMessages must be greater than zero" }

        lock.withLock {
            var remainingTime = timeout.inWholeNanoseconds
            val currentThreadRequest = Request<T>(nOfMessages, lock.newCondition())
            dequeueRequestList.add(currentThreadRequest)
            while(messageList.size < nOfMessages) { //It's a while because of the thread phenomena called spurious wakeup
                println("I, ${currThread()}, couldn't get my $nOfMessages message(s), I'll wait ${timeout.inWholeSeconds}s for it")
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

    private class Request<T>(
        val numOfMessagesRequested: Int,
        val threadWaiting: Condition, //Pattern of specific notification
    )
}

class Semaphore(private val initialUnits: Int) { //Ex 2
    fun release(): Unit {

    }

    @Throws(InterruptedException::class, RejectedExecutionException::class)
    fun acquire(timeout: Duration): Boolean {
        TODO()
    }

    fun shutdown(): Unit {

    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Duration): Boolean {
        TODO()
    }
}

suspend fun race(f0: suspend () -> Int, f1: suspend () -> Int): Int { //Ex 5
    var result: Int? = null
    val resultV2 = AtomicReference<Int>(null) //V2
    try {
        coroutineScope {
            val scope = this
            launch {
                result = f0()
                resultV2.compareAndSet(null, result) //V2
                scope.cancel()
            }
            launch {
                result = f1()
                resultV2.compareAndSet(null, result) //V2
                scope.cancel()
            }
        }
    } catch (ce: CancellationException){
        if(resultV2.get()==null){
            throw ce
        }
    }

    //only reaches here once all coroutines inside the previous scope have finished
    //return resultV2.get()!!
    return result ?: throw IllegalStateException()
}