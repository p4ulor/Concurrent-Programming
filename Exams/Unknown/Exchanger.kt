import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Exchanger<T> {
    private val lock = ReentrantLock()

    private inner class Group(val continuation: Continuation<T>, val value: T)

    private var group: Group? = null

    suspend fun exchange(value: T): T {
        lock.lock()
        val myGroup: Group? = group
        return if (myGroup != null) {
            group = null
            lock.unlock()
            myGroup.continuation.resume(value)
            myGroup.value
        } else {
            suspendCoroutine {
                group = Group(it, value)
                lock.unlock()
            }
        }
    }
}