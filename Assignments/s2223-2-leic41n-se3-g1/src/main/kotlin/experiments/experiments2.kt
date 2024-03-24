package experiments
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.*
import kotlin.system.measureTimeMillis

//https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html

fun main() {
    runBlocking {
        atomicRef()
    }

    runBlocking {
        singleThreadContext()
    }

    runBlocking {
        mutex()
    }
}

suspend fun atomicRef(){
    println("experiments.atomicRef:")
    val counter = AtomicInteger()
    withContext(Dispatchers.Default) {
        massiveRun {
            counter.incrementAndGet()
        }
    }
    println("Counter = $counter")
}

suspend fun singleThreadContext(){ // confine everything to a single-threaded context
    println("experiments.singleThreadContext:")
    val counterContext = newSingleThreadContext("CounterContext")
    var counter = 0
    withContext(counterContext) {
        massiveRun {
            counter++
        }
    }
    println("Counter = $counter")
}

suspend fun mutex(){
    println("experiments.mutex:")
    val mutex = Mutex()
    var counter = 0
    withContext(Dispatchers.Default) {
        massiveRun {
            // protect each increment with lock
            mutex.withLock {
                counter++
            }
        }
    }
    println("Counter = $counter")
}

suspend fun massiveRun(action: suspend () -> Unit) {
    val n = 100  // number of coroutines to launch
    val k = 1000 // times an action is repeated by each coroutine
    val time = measureTimeMillis {
        coroutineScope { // scope for coroutines
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed ${n * k} actions in $time ms")
}
