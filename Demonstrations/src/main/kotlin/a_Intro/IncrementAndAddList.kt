package a_Intro

import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class IncrementAndAddList_NotSafe_Counter {
    var counter = 0
    fun syncAdd() {
        ++counter
    }
}

//accessing the linked list in a non thread safe way, most of the times causes the error Null Pointer Exception at java.util.LinkedList$ListItr.next(LinkedList.java:893)
// Using multiple of threads on existing not safe libraries, can mess up their state and variables, causing these errors.
// Note: Most libraries, including those of java (but excluding the tools that come from the java.concurrency libraries of course) are not thread safe, so it must be the user of the library that must use them properly
class IncrementAndAddList_NotSafe {
    val list = LinkedList<String>()
    var counter = 0
    fun syncAdd(n: Int) {
        ++counter
        list.add("T$n, summed counter to to: $counter")
    }
}

class IncrementAndAddList_Synch {
    val list = LinkedList<String>()
    var counter = 0

    companion object { //a companion object is equivalent to a static field. Why use ' = String()' and not '= ""' ? -> 1. https://stackoverflow.com/questions/3052442/what-is-the-difference-between-text-and-new-stringtext    2. https://www.baeldung.com/java-synchronization-bad-practices
        val x = String()
    }

    fun syncAdd(n: Int) {
        synchronized(x){
            ++counter
            list.add("T$n, summed counter to to: $counter")
        }
    }
}

class IncrementAndAddList_Annotation {
    val list = LinkedList<String>()
    var counter = 0

    @Synchronized fun syncAdd(n: Int) {
        ++counter
        list.add("T$n, summed counter to to: $counter")
    }
}

class IncrementAndAddList_Lock {
    private val lock = ReentrantLock() //It's called ReentrantLock, because different threads can enter the same lock, but only 1 at a time
    val list = LinkedList<String>()
    var counter = 0
    fun syncAdd(n: Int) {
        lock.withLock {
            counter++
            list.add("T$n, summed counter to to: $counter")
        }
    }
}