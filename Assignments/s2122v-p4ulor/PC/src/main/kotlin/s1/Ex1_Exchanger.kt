package s1

import java.time.Duration
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Exchanger<T> {

    private val mLock: Lock = ReentrantLock()
    private val mCondition: Condition = mLock.newCondition()

    private var request: Request<T>? = null
    private class Request<T> (var value: T, var isExchanged: Boolean = false)

    @Throws(InterruptedException::class)
    fun exchange(value: T, timeout: Duration): T? {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }
        mLock.withLock {
            val currentRequest = request
            if(currentRequest!=null){ //if true, this is the 2nd thread
                val myValue = currentRequest.value
                currentRequest.value = value
                currentRequest.isExchanged = true
                request = null
                mCondition.signal()
                return myValue
            }
            val myRequest = Request(value) //private variable in T1's stack
            request = myRequest
            var remainingTime = timeout.toNanos()
            while(true){
                try {
                    remainingTime = mCondition.awaitNanos(remainingTime)
                } catch (ie: InterruptedException) {
                    request = null
                    throw ie
                }

                if (remainingTime <= 0) return null
                if(myRequest.isExchanged) {
                    return myRequest.value
                }
            }
        }
    }
}
