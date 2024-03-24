package pt.isel.pc.problemsets.set1

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertTrue


class Ex1_Test {

    @Test
    fun `Basic test, create 1 group and exchange`() {
        val u = Ex1Utils(NAryExchanger<String>(3))
        val expected = mutableListOf("dude", "bruh", "bro")
        var checkT1 = false; var checkT2 = false; var checkT3 = false

        val t1 = u.exchangerThread("t1", "bruh", 4) {
            checkT1 = it?.containsAll(expected) == true
        }
        val t2 = u.exchangerThread("t2", "dude", 5) {
            checkT2 = it?.containsAll(expected) == true
        }
        val t3 = u.exchangerThread("t3", "bro", 6) {
            checkT3 = it?.containsAll(expected) == true
        }

        t1.start(); t2.start(); t3.start()
        t1.join(); t2.join(); t3.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
        assertTrue(checkT3)
    }

    @Test
    fun `Make 1 group with 3, and then another thread joins alone and fails`() {
        val u = Ex1Utils(NAryExchanger<String>(3))
        val expected = mutableListOf("dude", "bruh", "bro")
        var checkT1 = false; var checkT2 = false; var checkT3 = false; var checkT4 = false

        val t1 = u.exchangerThread("t1", "bruh", 4) {
            checkT1 = it?.containsAll(expected) == true
        }
        val t2 = u.exchangerThread("t2", "dude", 5) {
            checkT2 = it?.containsAll(expected) == true
        }
        val t3 = u.exchangerThread("t3", "bro", 6) {
            checkT3 = it?.containsAll(expected) == true
        }
        val t4 = u.exchangerThread("t4", "brooooo", 6) {
            checkT4 = it==null
        }

        t1.start(); t2.start()
        t3.start()
        Thread.sleep(1000) //tries to make sure the previous Threads are the ones that get in the list, cuz there's the possibility that t4 joins the list, and t3 tries to enqueue, so our tests fail, but it's just cuz an expected action occurred in another thread
        t4.start()

        t1.join(); t2.join(); t3.join(); t4.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
        assertTrue(checkT3)
        assertTrue(checkT4)
    }

    @Test
    fun `2 Items enter, 1 quits, another 2 enter and makes group`(){
        val u = Ex1Utils(NAryExchanger<String>(3))
        val expected = mutableListOf("bro", "bro2", "bro3")
        var checkT1 = false; var checkT2 = false; var checkT3 = false; var checkT4 = false

        val t1 = u.exchangerThread("t1", "bro", 4) {
            checkT1 = it?.containsAll(expected) == true
        }

        val t2 = u.exchangerThreadCheckException<InterruptedException>("t2", "interrupted", 5){
            checkT2 = it
        }

        val t3 = u.exchangerThread("t3", "bro2", 6) {
            checkT3 = it?.containsAll(expected) == true
        }

        val t4 = u.exchangerThread("t4", "bro3", 6) {
            checkT4 = it?.containsAll(expected) == true
        }

        t1.start()

        t2.start()
        t2.interrupt()

        t3.start()
        t4.start()

        t1.join(); t3.join(); t4.join()

        assertTrue(checkT1)
        assertTrue(checkT2)
        assertTrue(checkT3)
        assertTrue(checkT4)
    }

    //Because it's recommended to have use separate instances in the tests, or things can get messed up (sometimes, randomly). And this makes things more structured
    private class Ex1Utils <T> (val exchanger: NAryExchanger<T>){
        fun exchangerThread(tName: String, value: T, seconds: Long, onResultObtained: (values: List<T>?) -> Unit) : Thread {
            val t = Thread {
                val res = exchanger.exchange(value, Duration.ofSeconds(seconds))
                println("$tName returned $res")
                onResultObtained(res)
            }
            t.name = tName
            return t
        }

        fun <V> exchangerThreadCheckException(tName: String, value: T, seconds: Long, onResultObtained: (isItTheExpectedException: Boolean) -> Unit) : Thread {
            val t = Thread {
                try {
                    exchanger.exchange(value, Duration.ofSeconds(seconds))
                    onResultObtained(false)
                } catch (ie: Exception){
                    val x = ie as V?
                    onResultObtained(x !=null)
                }
            }
            t.name = tName
            return t
        }

        fun exchange(value: T, seconds: Long) = exchanger.exchange(value, Duration.ofSeconds(seconds))
    }
}