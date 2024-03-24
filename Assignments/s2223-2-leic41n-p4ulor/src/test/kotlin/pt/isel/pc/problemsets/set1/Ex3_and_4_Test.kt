package pt.isel.pc.problemsets.set1

import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

//Note: asserts() should only be called inside the @Test functions, otherwise the green checkmark icon can be displayed, when in fact an assert inside the Utils class could have failed...
class Ex3_and_4_Test {
    @Test
    fun `Ex3 - Call awaitTermination with no threads`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        var didTestPass = false
        val t1 = p.awaitTermination("t1", 3) {
            didTestPass = it
        }
        t1.start()
        t1.join()
        assertTrue(didTestPass)
    }

    @Test
    fun `Ex3 - a) Run a runnable that takes longer than awaitTermination`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        p.execute {
            Thread.sleep(3000)
        }
        var didTestPass = false
        val t1 = p.awaitTermination("t1", 1) {
            didTestPass = !it
        }
        t1.start()
        t1.join()
        assertTrue(didTestPass)
    }

    @Test
    fun `Ex3 - b) Submit runnable but the pool is shutdown`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        p.shutdown()
        assertFailsWith(RejectedExecutionException::class) {
            p.execute {
                Thread.sleep(3)
            }
        }
    }

    @Test
    fun `Ex3 - c) Check that threads finish when there are no longer runnables to run`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        assertTrue(p.getCurrentThreadCount()==0)
        p.execute {
            Thread.sleep(3000)
        }
        Thread.sleep(1000) //filler, extra 1s to make up for other intermediary operations, just to make sure, the next test goes well
        assertTrue(p.getCurrentThreadCount()==1)
        Thread.sleep(2000) //Another 2000 millis to make up the p.execute { Thread.sleep(3000) }
        Thread.sleep(4000) //4 seconds to make up for the 3s keep alive time
        assertTrue(p.getCurrentThreadCount()==0)
    }

    @Test
    fun `Ex3 - d) Check that a thread executes more than 1 runnable`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        p.execute { Thread.sleep(3000) }
        p.execute { Thread.sleep(3000) }
        p.execute { Thread.sleep(3000) }
        p.execute { Thread.sleep(3000) }
        Thread.sleep(1000) //filler, extra 1s to make up for other intermediary operations, just to make sure, the next test goes well
        assertTrue(p.getSizeThingsToRun()==1) //1 because all the other 3 threads (very probably) already called getThingToRun(), thus removing the other 3 Runnable from the mutableListOf<Runnable>(
        assertTrue(p.getCurrentThreadCount()==3)
        Thread.sleep(6000) //3s get executed concurrently by 3 threads -> 3s total + 1 of the 3 threads runs the 4th Callable -> 6 seconds
        assertTrue(p.getCurrentRunnablesRan()==4)
        Thread.sleep(3000) //keepAliveTime
        assertTrue(p.getCurrentThreadCount()==0)
    }

    // Ex4

    @Test
    fun `Ex4 - Execute and get`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))

        val callable = Callable {
            println("Callable running")
            Thread.sleep(3000)
            println("Callable finished running")
            return@Callable "ok"
        }

        var future: MyFuture<String> = p.execute<String>(callable)

        Thread.sleep(1000) //filler, extra 1s to make up for other intermediary operations, just to make sure, the next test goes well
        assertTrue(! future.isDone())
        Thread.sleep(3000) //the Callable's Thread.sleep(3000)
        assertTrue(future.isDone())
        assertTrue(p.getCurrentThreadCount()==1)
        println("future = ${future.get()}")
        assertTrue(future.get()=="ok")
        Thread.sleep(3000) // keepAliveTime (3s)
        assertTrue(p.getCurrentThreadCount()==0)
    }

    @Test
    fun `Ex4 - b) Submit callable but the pool is shutdown`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))

        p.shutdown()

        val callable = Callable {
            println("Callable running")
            return@Callable "ok"
        }

        assertFailsWith(RejectedExecutionException::class) {
            p.execute<String>(callable) //It must be the framework's thread running this function to get the exception, not the one I create in the Utils.... that's why I don't use the other function
        }
    }

    @Test
    fun `Ex4 - c) Check that threads finish when there are no callables to run`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        assertTrue(p.getCurrentThreadCount()==0)

        val callable = Callable {
            println("Callable running")
            Thread.sleep(3000)
            println("Callable finished running")
            return@Callable "ok"
        }

        p.execute<String>(callable)

        Thread.sleep(1000) // a filler, just to make sure, the next test goes well
        assertTrue(p.getCurrentThreadCount()==1)
        Thread.sleep(3000) //Another 3000 millis to make up the p.execute { Thread.sleep(3000) }
        Thread.sleep(3000) //3 seconds to make up for the 3s keep alive time
        assertTrue(p.getCurrentThreadCount()==0)
    }

    @Test
    fun `Ex4 - d) Check that a thread executes more than 1 callable`(){
        val p = Ex3_4_Utils(ThreadPoolExecutor(3, Duration.ofSeconds(3)))
        p.execute<String> { Thread.sleep(3000); return@execute "ok" }
        p.execute<String> { Thread.sleep(3000); return@execute "ok" }
        p.execute<String> { Thread.sleep(3000); return@execute "ok" }
        p.execute<String> { Thread.sleep(3000); return@execute "ok" }
        Thread.sleep(1000) //filler, extra 1s to make up for other intermediary operations, just to make sure, the next test goes well
        assertTrue(p.getSizeThingsToCall()==1) //1 because all the other 3 threads (very probably) already called getThingToRun(), thus removing the other 3 Runnable from the mutableListOf<Runnable>(
        assertTrue(p.getCurrentThreadCount()==3)
        Thread.sleep(6000) //3s get executed concurrently by 3 threads -> 3s total + 1 of the 3 threads runs the 4th Callable -> 6 seconds
        assertTrue(p.getCurrentCallablesRan()==4)
        Thread.sleep(3000) //keepAliveTime
        assertTrue(p.getCurrentThreadCount()==0)
    }

    private class Ex3_4_Utils (val pool: ThreadPoolExecutor) {
        fun execute(runnable: Runnable) = pool.execute(runnable)
        fun shutdown() = pool.shutdown()

        fun awaitTermination(tName: String, seconds: Long, onResultObtained: (boolean: Boolean) -> Unit) : Thread {
            return thread(name = tName, start = false, block = {
                val res = pool.awaitTermination(Duration.ofSeconds(seconds))
                println("$tName returned $res")
                onResultObtained(res)
            })
        }

        fun <T> execute(tName: String, callable: Callable<T>, onResultObtained: (future: MyFuture<T>) -> Unit) : Thread {
            return thread(name = tName, start = false, block = {
                val res = pool.execute(callable)
                println("$tName returned $res")
                onResultObtained(res)
            })
        }

        fun <T> execute(callable: Callable<T>) = pool.execute(callable)

        fun getCurrentThreadCount() = pool.getCurrentThreadCount()
        fun getSizeThingsToRun() = pool.getSizeThingsToRun()
        fun getSizeThingsToCall() = pool.getSizeThingsToCall()
        fun getCurrentRunnablesRan() = pool.getCurrentRunnablesRan()
        fun getCurrentCallablesRan() = pool.getCurrentCallablesRan()
    }
}