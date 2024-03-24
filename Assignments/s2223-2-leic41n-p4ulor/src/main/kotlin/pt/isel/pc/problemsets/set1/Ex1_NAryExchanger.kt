package pt.isel.pc.problemsets.set1

import pt.isel.pc.problemsets.currThread
import pt.isel.pc.problemsets.nanosToSeconds
import java.time.Duration
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.pow

/**
 * Synchronization technique used:
 * @param groupSize indicates the number of threads required to form a group (List<T>)
 * When a group is formed, it's ready to be returned
 */
class NAryExchanger<T>(private val groupSize: Int) {

    init {
        require(groupSize >= 2) { "groupSize must be greater than zero" }
    }

    private val lock: Lock = ReentrantLock() //used for synchronization of data
    private var currentGroup = Group<T>(lock.newCondition())

    @Throws(InterruptedException::class)
    fun exchange(value: T, timeout: Duration) : List<T>? {
        require(timeout > Duration.ZERO) { "timeout must be greater than zero" }

        lock.withLock {
            if(currentGroup.valuesSubmitted.size == groupSize-1){
                currentGroup.valuesSubmitted.add(value)
                return groupFormed()
            } else {
                println("No group yet for ${currThread()}")
                var remainingTime = timeout.toNanos()
                val groupThisThreadJoined = currentGroup //this stores the reference to the group that the current Thread joined, in its stack/context. So that when a new group is replaced, when this thread was waiting, the reference isn't lost
                groupThisThreadJoined.valuesSubmitted.add(value)
                while(true){ //Because of the thread phenomena called spurious wakeup, or if awakened, but the conditions aren't met
                    try {
                        remainingTime = groupThisThreadJoined.threadsWaiting.awaitNanos(remainingTime) //Releases the lock and the current thread "goes" to the threadsWaiting, after the remainingTime reaches the end or the thread is signalled or waken up, it continues to the next line
                    } catch (ie: InterruptedException) {
                        println("${currThread()} was interrupted")
                        groupThisThreadJoined.valuesSubmitted.remove(value)
                        throw ie
                    }
                    if(groupThisThreadJoined.isDone){ //If the timeout has expired, but the group is done, the thread can no longer "quit"
                        return groupThisThreadJoined.valuesSubmitted
                    }
                    else if (remainingTime <= 0) {
                        groupThisThreadJoined.valuesSubmitted.remove(value)
                        println("Timeout exceeded... ${nanosToSeconds(remainingTime)} for ${currThread()}")
                        return null
                    }
                    return null
                }
            }
        }
    }

    private fun groupFormed() : List<T> { //Must be called inside a lock, since it accesses shared state
        println("Group formed with ${currThread()}")
        currentGroup.isDone = true
        currentGroup.threadsWaiting.signalAll()
        val values = currentGroup.valuesSubmitted.toMutableList() //makes a copy
        currentGroup = Group(lock.newCondition()) //creates a new group, references to the old group will exist in the stack/context of other threads w/ the "groupThisThreadJoined"
        return values
    }

    private data class Group<T>( //These fields must be accessed w/ a lock
        val threadsWaiting: Condition, //used for synchronization of control
        val valuesSubmitted: MutableList<T> = mutableListOf(),
        var isDone: Boolean = false
    )
}
