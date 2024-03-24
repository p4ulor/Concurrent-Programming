package pt.isel.pc.problemsets.set1

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.test.assertTrue

//Note: asserts() should only be called inside the @Test functions, otherwise the green checkmark icon can be displayed, when in fact an assert inside the Utils class could have failed...
class Ex2_Test {

    @Test
    fun `Basic test, enqueue 3 msgs and get them`(){
        val u = Ex2Utils(BlockingMessageQueue<String>(3))
        val expected = mutableListOf("dude", "bro", "bruh")
        var checkT1 = false; var checkT2 = false; var checkT3 = false; var checkT4 = false

        val t1 = u.enqueue("t1", "dude", 3) { checkT1 = it }
        val t2 = u.enqueue("t2", "bro", 3) { checkT2 = it }
        val t3 = u.enqueue("t3", "bruh", 3) { checkT3 = it }
        val t4 = u.dequeue("t4", 3, 3) {
            checkT4 = it?.containsAll(expected) == true
        }

        t1.start(); t2.start(); t3.start(); t4.start()
        t1.join(); t2.join(); t3.join();t4.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
        assertTrue(checkT3)
        assertTrue(checkT4)
    }

    @Test
    fun `Max capacity reached`(){
        val u = Ex2Utils(BlockingMessageQueue<String>(3))
        var checkT1 = false; var checkT2 = false; var checkT3 = false; var checkT4 = false

        val t1 = u.enqueue("t1", "dude", 3) { checkT1 = it }
        val t2 = u.enqueue("t2", "bro", 3) { checkT2 = it }
        val t3 = u.enqueue("t3", "bruh", 3) { checkT3 = it }
        val t4 = u.enqueue("t4", "bruh2", 5) { checkT4 = !it }

        t1.start(); t2.start()
        t3.start()
        Thread.sleep(1000) //tries to make sure the previous Threads are the ones that get in the list, cuz there's the possibility that t4 enqueues the list, and then t3 tries to enqueue (and the max capacity is reached), so our tests fail, but it's just cuz an expected action occurred in another thread
        t4.start()

        t1.join(); t2.join(); t3.join(); t4.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
        assertTrue(checkT3)
        assertTrue(checkT4)
    }

    @Test
    fun `Request msgs when there are none`(){
        val u = Ex2Utils(BlockingMessageQueue<String>(3))
        var checkT1 = false
        val t1 = u.dequeue("t1", 3, 3) {
            checkT1 = it==null
        }
        t1.start()
        t1.join()
        assertTrue(checkT1)
    }

    @Test
    fun `Request a msg when there's none and then put a message and the req gets it`(){
        val u = Ex2Utils(BlockingMessageQueue<String>(3))
        val msg = "ok"
        var checkT1 = false; var checkT2 = false

        val t1 = u.dequeue("t1", 1, 3) {
            checkT1 = it?.equals(mutableListOf(msg)) == true
        }
        val t2 = u.enqueue("t2", msg, 3) {
            checkT2 = it
        }

        t1.start(); t2.start()
        t1.join(); t2.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
    }

    @Test
    fun `Request a msg and quit`(){
        val u = Ex2Utils(BlockingMessageQueue<String>(3))
        var checkT1 = false

        val t1 = u.dequeueCheckException<InterruptedException>(1, 3) {
            checkT1 = it
        }

        t1.start()
        t1.interrupt()
        t1.join()
        assertTrue(checkT1)
    }

    private class Ex2Utils <T> (val msgQueue: BlockingMessageQueue<T>){
        fun enqueue(tName: String, value: T, seconds: Long, onResultObtained: (boolean: Boolean) -> Unit) : Thread {
            return thread(name = tName, start = false, block = {
                val res = msgQueue.tryEnqueue(value, Duration.ofSeconds(seconds))
                println("$tName returned $res")
                onResultObtained(res)
            })
        }

        fun dequeue(tName: String, nOfMessages: Int, seconds: Long, onResultObtained: (msgs: List<T>?) -> Unit) : Thread {
            return thread(name = tName, start = false, block = {
                val res = msgQueue.tryDequeue(nOfMessages, Duration.ofSeconds(seconds))
                println("$tName returned $res")
                onResultObtained(res)
            })
        }

        fun <V> dequeueCheckException(nOfMessages: Int, seconds: Long, onResultObtained: (isItTheExpectedException: Boolean) -> Unit) : Thread {
            return thread(start = false, block = {
                try {
                    msgQueue.tryDequeue(nOfMessages, Duration.ofSeconds(seconds))
                    onResultObtained(false)
                } catch (ie: Exception){
                    val x = ie as V?
                    onResultObtained(x !=null)
                }
            })
        }
    }
}