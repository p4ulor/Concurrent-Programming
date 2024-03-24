package s1

import java.time.Duration
import java.util.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.locks.ReentrantLock

class ThreadPoolExecutor(private val maxThreadPoolSize: Int, private val keepAliveTime: Duration) {

    init {
        require(keepAliveTime > Duration.ZERO) {"keepAliveTime must be greater than zero"}
        require(maxThreadPoolSize > 0) {"maxThreadPoolSize must be greater than zero"}
    }

    private var isShuttingDown = false

    private val workItems = LinkedList<Runnable>()
    private var currentThreadCount: Int = 0
    private var waitingThreadCount: Int = 0

    private val mLock = ReentrantLock()
    private val mCondition = mLock.newCondition()

    @Throws(RejectedExecutionException::class)
    fun execute(runnable: Runnable) : Unit {
        if(isShuttingDown) throw RejectedExecutionException()
        //TODO
    }

    fun shutdown() : Unit {
        isShuttingDown = true
    }

    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Duration) : Boolean {
        return false
    }
}