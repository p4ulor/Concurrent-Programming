import kotlinx.coroutines.*
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageQueueTest {

    @Test //RepeatableTest will not run well here apparently (because I check the order of the de-queuing in this test & I'm running many coroutines at the same time I guess)
    fun `basic test`() {
        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val msg1 = "a"; val msg2 = "b"; val msg3 = "c"

            val c1 = launch { msgQ.enqueue(msg1) }
            val c2 = launch { msgQ.enqueue(msg2) }
            val c3 = launch { msgQ.enqueue(msg3) }

            c1.join(); c2.join(); c3.join()

            val c4 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c5 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c6 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            val obtained1 = c4.await()
            val obtained2 = c5.await()
            val obtained3 = c6.await()

            assertEquals(msg3, obtained1)
            assertEquals(msg2, obtained2)
            assertEquals(msg1, obtained3)
        }
    }

    @RepeatedTest(30)
    fun `repeatable test`() {
        val dispatcher = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val msgs = mutableListOf("a", "b", "c")

            val c1 = launch { msgQ.enqueue(msgs[0]) }
            val c2 = launch { msgQ.enqueue(msgs[1]) }
            val c3 = launch { msgQ.enqueue(msgs[2]) }

            c1.join(); c2.join(); c3.join()

            val c4 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c5 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c6 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            val obtained1 = c4.await()
            val obtained2 = c5.await()
            val obtained3 = c6.await()

            msgs.remove(obtained1)
            msgs.remove(obtained2)
            msgs.remove(obtained3)

            assertEquals(mutableListOf(), msgs, "Msgs not obtained properly: $msgs")
        }
    }

    @RepeatedTest(30)
    fun `make wait for queue`(){
        val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val msgs = mutableListOf("a", "b", "c")

            val c4 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c5 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c6 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            val c1 = launch { msgQ.enqueue(msgs[0]) }
            val c2 = launch { msgQ.enqueue(msgs[1]) }
            val c3 = launch { msgQ.enqueue(msgs[2]) }

            c1.join(); c2.join(); c3.join()

            val obtained1 = c4.await()
            val obtained2 = c5.await()
            val obtained3 = c6.await()

            println()

            msgs.remove(obtained1)
            msgs.remove(obtained2)
            msgs.remove(obtained3)

            assertEquals(mutableListOf(), msgs, "Msgs not obtained properly: $msgs")
        }
    }

    @Test
    fun `timeout occurs`(){
        val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val c4 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c5 = async { msgQ.dequeue(Duration.ofSeconds(3)) }
            val c6 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            assertFailsWith(TimeoutCancellationException::class){
                val obtained1 = c4.await()
            }

            assertFailsWith(TimeoutCancellationException::class){
                val obtained2 = c5.await()
            }

            assertFailsWith(TimeoutCancellationException::class){
                val obtained3 = c6.await()
            }
        }
    }

    @RepeatedTest(5)
    fun `dequeues, delay and then enqueue`(){
        val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val msgs = mutableListOf("a", "b", "c")

            val c1 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            delay(2000)

            val c2 = launch { msgQ.enqueue(msgs[0]) }
            c2.join()

            val obtained1 = c1.await()

            assertEquals(msgs[0], obtained1)
        }
    }

    @RepeatedTest(3)
    fun `cancel a dequeue and check if continuation was removed`(){
        val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()
        runBlocking(dispatcher){
            val msgQ = MessageQueue<String>(3)

            val c1 = async { msgQ.dequeue(Duration.ofSeconds(3)) }

            delay(1000)
            c1.cancel()
            c1.join() //note, must be join(), await() will cause "DeferredCoroutine was cancelled - kotlinx.coroutines.JobCancellationException"
            assertEquals(true, msgQ.isContinuationListEmpty())
        }
    }
}

