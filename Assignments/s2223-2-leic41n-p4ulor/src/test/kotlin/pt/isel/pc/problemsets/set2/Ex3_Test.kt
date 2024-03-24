package pt.isel.pc.problemsets.set2

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.io.Closeable
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Ex3_Test {

    class RandomCloseable : Closeable {
        override fun close() {
            println("closed")
        }
    }

    @RepeatedTest(30)
    fun `basic test - check increments`(){
        val usageCounterHolder = UnsafeUsageCountedHolder(RandomCloseable())
        val t1 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t1")

        val t2 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t2")

        val t3 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t3")

        t1.start(); t2.start(); t3.start()

        t1.join(); t2.join(); t3.join()

        assertEquals(usageCounterHolder.useCounter.get(), 4)
    }

    @RepeatedTest(30)
    fun `basic test - increments and then decrements`(){
        val usageCounterHolder = UnsafeUsageCountedHolder(RandomCloseable())

        val t1 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t1")

        val t2 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t2")

        val t3 = Thread({
            usageCounterHolder.tryStartUse()
        }, "t3")

        val t4 = Thread({
            usageCounterHolder.endUse()
        }, "t4")

        val t5 = Thread({
            usageCounterHolder.endUse()
        }, "t5")

        val t6 = Thread({
            usageCounterHolder.endUse()
        }, "t6")

        t1.start(); t2.start(); t3.start()
        t1.join(); t2.join(); t3.join()

        println("Counter = ${usageCounterHolder.useCounter}")

        t4.start(); t5.start(); t6.start()
        t4.join(); t5.join(); t6.join()

        assertEquals(usageCounterHolder.useCounter.get(), 1)
    }

    @Test
    fun `fails with IllegalStateException`(){
        val usageCounterHolder = UnsafeUsageCountedHolder(RandomCloseable())
        thread {
            usageCounterHolder.endUse()
        }

        assertFailsWith(IllegalStateException::class) {
            try {
                usageCounterHolder.endUse()
            } catch (e: IllegalStateException){
                println("Failed with IllegalStateException: $e")
                throw IllegalStateException(e)
            }
        }
    }
}