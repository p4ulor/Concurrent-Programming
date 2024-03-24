package a_Intro

import org.junit.Test
import java.util.*

// These tests are meant to be ran manually repeatedly to explore and learn
class IncrementAndAddListTests {

    @Test
    fun increment_NotSafe_Counter_(){ //Run multiple times to explore different results
        val nOfThreads = 30
        println("I will try to count till $nOfThreads")
        val inc = IncrementAndAddList_NotSafe_Counter()
        val threads = List(nOfThreads) {
            Thread {
                inc.syncAdd()
            }
        }
        threads.forEach {
            it.start()
        }
        // println("The main-thread wants to get the counter NOW") // Surprisingly, just adding several println() or Thread.sleep() here, makes the main-thread be busy for longer, in order for the threads to perform all of their increments in time before the main threads tries to obtain the value in counter. These minor influential changes only happen because all what our threads do is increment a value
        val counter = inc.counter //notice that if I did 'inc.counter' all the time instead of getting it 1 time and store it in a local variable the results would not be consistent/correct
        println("Counter: $counter")
        if(counter==nOfThreads) println("You were very lucky, the threads counted till $nOfThreads before the main thread gets the counter")
        else println("The main thread got the value too early")

        println("Waiting for the threads to finish...")
        threads.forEach {
            it.join()
        }
        val counter2 = inc.counter
        println("Counter: $counter2")
        if(counter2==nOfThreads) println("The threads didn't read and write the shared value perfectly")
        else println("Ups, the threads reading and writing was all messed up. You were extremely unlucky!") // since there's a very very small space for the threats to mess up up because all the threats do is increment
    }

    @Test
    fun increment_NotSafe_Counter_NoJoin(){
        val nOfThreads = 20
        var fail = 0
        val repeat = 5
        println("I will test $repeat times, counting till $nOfThreads")
        repeat(repeat){
            val inc = IncrementAndAddList_NotSafe_Counter()
            val threads = List(nOfThreads) {
                Thread {
                    inc.syncAdd()
                }
            }
            threads.forEach {
                it.start()
            }
            //Thread.sleep(1) //uncomment to explore different results
            val counter = inc.counter //notice that if I did 'inc.counter' all the time instead of getting it 1 time and storint it in a local variable the results would not be consistent/correct
            println(counter)
            if(counter<nOfThreads) fail++
        }
        println("Failed: $fail times")
    }

    @Test
    fun incrementAndAddList_NotSafe(){
        val nOfThreads = 20
        val repeat = 5
        println("I will test $repeat times, counting till $nOfThreads")
        val counterResults = LinkedList<Int>()
        repeat(repeat){ r ->
            val inc = IncrementAndAddList_NotSafe()
            val threads = List(nOfThreads) {
                Thread {
                    inc.syncAdd(it)
                }
            }
            threads.forEach {
                it.start()
            }

            threads.forEach {
                it.join()
            }
            try {
                val list = inc.list
                println("Repetition $r -> $list")
            } catch (e: Exception){
                println("Repetition $r FAILED, the list is all messed up")
            } finally {
                counterResults.add(inc.counter)
            }
        }
        println("Results: $counterResults")
    }

    // Thread safe implementation tests:
    // Notice that the order of threads can't be controlled, but the access and incrementation of the counter is controlled

    @Test
    fun incrementAndAddList_SafeSync(){
        val inc = IncrementAndAddList_Synch()
        val threads = List(50) { Thread { inc.syncAdd(it) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        inc.list.forEach { println(it) }
    }

    @Test
    fun incrementAndAddList_SafeAnnotation(){
        val inc = IncrementAndAddList_Annotation()
        val threads = List(50) { Thread { inc.syncAdd(it) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        inc.list.forEach { println(it) }
    }

    @Test
    fun incrementAndAddList_SafeLock(){
        val inc = IncrementAndAddList_Lock()
        val threads = List(50) { Thread { inc.syncAdd(it) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        inc.list.forEach { println(it) }
    }
}