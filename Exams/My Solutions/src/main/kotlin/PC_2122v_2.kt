import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration

class ExecutorWithShutdown(private val executor: Executor) { //Ex 1
    private var isShutdown = false
    private var executionsRunning = 0
    private val lock = ReentrantLock()
    private val threadsWaiting = lock.newCondition()

    @Throws(RejectedExecutionException::class)
    fun execute(command: Runnable) : Unit {
        lock.withLock {
            if(isShutdown) throw RejectedExecutionException("Already shutdown")
            ++executionsRunning
        }
        executor.execute {
            try {
                command.run()
            } finally {
                lock.withLock {
                    --executionsRunning
                }
            }
        }
    }
    fun shutdown() : Unit {
        lock.withLock {
            if(isShutdown) return
            isShutdown = true
            threadsWaiting.signalAll()
        }
    }
    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Duration) : Boolean {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }
        if(lock.withLock { isShutdown }) throw RejectedExecutionException("Already shutdown")
        var remainingTime = timeout.inWholeNanoseconds
        while(true){ //Because of the thread phenomena called spurious wakeup, or in the case where a thread was awakened, but the conditions aren't met
            try {
                remainingTime = lock.withLock {
                    threadsWaiting.awaitNanos(remainingTime) //Releases the lock and the current thread "goes" to the threadsWaiting, after the remainingTime reaches the end or the thread is signalled or waken up, it continues to the next line
                }
                println("Thread woke up or was signalled")
            } catch (ie: InterruptedException) {
                println("Thread interrupted")
                throw ie
            }
            val areConditionsMet = lock.withLock {
                isShutdown && executionsRunning==0
            }
            if(areConditionsMet){ //If the timeout has expired, but the condition is done, the thread can no longer "quit"
                return true
            }
            else if (remainingTime <= 0) return false
        }
    }
}

class MessageQueue<T> { // Ex 2
    private val lock = ReentrantLock()
    private val tryDequeueThreadsWaiting = lock.newCondition()

    private val msgContainer = LinkedList<MessageExchanger<T>>()

    @Throws(InterruptedException::class)
    fun tryEnqueue(message: T, timeout: Duration): Thread? {
        var remainingTime = timeout.inWholeNanoseconds
        val currThreadCondition = lock.newCondition()
        val msg = MessageExchanger(message, currThreadCondition)
        try {
            lock.withLock {
                msgContainer.addFirst(msg)
            }

            while (true){
                remainingTime = lock.withLock {
                    tryDequeueThreadsWaiting.signal()
                    currThreadCondition.awaitNanos(remainingTime) //Releases the lock and the current thread "goes" to the threadsWaiting, after the remainingTime reaches the end or the thread is signalled or waken up, it continues to the next line
                }
                println("Thread woke up or was signalled")
                if(msg.message==null) { //someone dequeued our message
                    msgContainer.removeIf {
                        it.hashCode()==msg.hashCode()
                    }
                    return msg.dequeueThread
                }
                if (remainingTime <= 0) return null
            }
        } catch (ie: InterruptedException){
            println("Thread interrupted")
            msgContainer.removeIf {
                it.hashCode()==msg.hashCode()
            }
            throw ie
        }
        return null
    }

    @Throws(InterruptedException::class)
    fun tryDequeue(nOfMessages: Int, timeout: Duration) : List<T> {
        try {
            var remainingTime = timeout.inWholeNanoseconds
            while (true){
                lock.withLock {
                    if(msgContainer.size >= nOfMessages){
                        return dequeueMessages(nOfMessages)
                    }
                    else { //Releases the lock and the current thread "goes" to the threadsWaiting, after the remainingTime reaches the end or the thread is signalled or waken up, it continues to the next line
                        println("List is empty, will wait")
                        remainingTime = tryDequeueThreadsWaiting.awaitNanos(remainingTime)
                        println("Thread woke up or was signalled")
                    }
                    if (remainingTime <= 0) {
                        // "A remoção pode ser realizada parcialmente, apenas caso o tempo de espera seja excedido, ou seja, a função tryDequeue pode retornar uma lista com dimensão inferior a nOfMessages."
                        return dequeueMessages(nOfMessages)
                    }
                }
            }
        } catch (ie: InterruptedException){
            println("Thread interrupted")
            throw ie
        }
    }

    private fun dequeueMessages(nOfMessages: Int) : List<T> { //must be called inside lock
        val msgs = LinkedList<T>()
        val start = if(msgContainer.size - nOfMessages < 0) msgContainer.size - nOfMessages else 0
        for(i in start .. start + nOfMessages){
            val container = try { msgContainer.get(i) } catch (e: IndexOutOfBoundsException) { break }
            if(container!=null){ //idk if this check if this is really required (cuz linked li
                msgs.addFirst(container.message)
                container.message = null
                container.dequeueThread = Thread.currentThread()
                container.enqueueThreadWaiting.signal()
            } else break
        }
        return msgs
    }

    data class MessageExchanger<T>(
        var message: T?, //if is null, it was dequeued
        val enqueueThreadWaiting: Condition, //the enqueue thread
        var dequeueThread: Thread? = null
    )
}

class MessageBox2<T> { //Ex 4, usa-se kernel style

    var oneTimeMessage: T? = null
    private val continuationOfDequeue = mutableListOf<Continuation<T>>()
    private val lock = ReentrantLock()

    suspend fun waitForMessage(): T {
        return coroutineScope {
            val msg = suspendCancellableCoroutine<T> { continuation ->
                lock.withLock {
                    continuationOfDequeue.add(continuation)
                }
            }
            return@coroutineScope msg
        }
    }
    fun sendToAll(message: T): Int {
        lock.withLock {
            val recipients = continuationOfDequeue.size
            continuationOfDequeue.forEach {
                it.resume(message)
            }
            continuationOfDequeue.clear()
            return recipients
        }
    }
}

class MessageBox<T> {
    private val requests = LinkedList<RequestBox<T>>()
    private val mLock: ReentrantLock = ReentrantLock()

    private class RequestBox<T>(var message: T? = null, var continuation: Continuation<T>? = null)

    suspend fun waitForMessage(): T {
        mLock.lock()
        val localNode = requests.addFirst(RequestBox())
        return try {
            suspendCancellableCoroutine { continuation ->
                localNode.value.continuation = continuation
                val observedNodeMessage = localNode.value.message
                if (observedNodeMessage != null) {
                    continuation.resume(observedNodeMessage)
                }
                mLock.unlock()
            }
        } catch (e: CancellationException) {
            requests.remove(localNode)
            mLock.unlock()
            throw CancellationException()
        }
    }


    fun sendToAll(message: T): Int {
        val deliveredMessages: Int
        val oldRequests: NodeLinkedList<RequestBox<T>>

        mLock.withLock {
            oldRequests = requests
            clearRequests()
            deliveredMessages = oldRequests.count
        }

        oldRequests.forEach { request ->
            request.continuation?.resume(message)
        }

        return deliveredMessages
    }

    private fun clearRequests() {
        while (requests.notEmpty) {
            requests.pull()
        }
    }
}

suspend fun <A,B,C> run(f0: suspend () -> A, f1: suspend ()-> B, f2: suspend (A,B) -> C): C { // Ex 5
    //Default will use 1 or more threads
    //withContext(coroutineContext)
    return withContext(Dispatchers.Default) {
        val c1 = async {
            f0()
        }
        val c2 = async {
            f1()
        }

        val a: A = c1.await()
        val b: B = c2.await()

        return@withContext f2(a, b)
    }
}