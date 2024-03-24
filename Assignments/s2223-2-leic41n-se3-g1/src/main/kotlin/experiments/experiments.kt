package experiments

import kotlinx.coroutines.*

fun main(args: Array<String>) {
    experiments()
}

fun experiments() {
    runBlocking {
        launch {
            delay(1000L)
            println("World!")
        }

        launch {
            delay(1005L)
            println("World2!")
        }

        iCanOnlyBeCalledByAcoroutine()
        anotherScope()

        println("Hello")

        val job = launch { // launch a new coroutine and keep a reference to its Job
            delay(3000L)
        }
        job.join() //like a thread

        val differed_future_value = async {
            "ay"
        }

        //Awaits for completion of this value without blocking a thread and resumes when deferred computation is complete, returning the resulting value
        //An uncaught exception inside the async code is stored inside the resulting Deferred
        val value = differed_future_value.await()
        differed_future_value.join()

        println("Done")
    }

    println("bye")
}

suspend fun iCanOnlyBeCalledByAcoroutine() {
    delay(1)
    println("experiments.iCanOnlyBeCalledByAcoroutine")
}

//coroutineScope can only be used like this:
suspend fun anotherScope() = coroutineScope {
    println("experiments.anotherScope")
}
