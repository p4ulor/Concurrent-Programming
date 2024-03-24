package s1

import org.junit.Test
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread
import kotlin.system.measureTimeMillis

internal class ExchangerTest{

    /*
    With these tests we can conclude that the time elapsed of the Thread that performs the switch (the 2nd one) is always slightly higher than T1's.
    Program to understand these unit times a little better
    https://play.kotlinlang.org/#eyJ2ZXJzaW9uIjoiMS42LjIxIiwicGxhdGZvcm0iOiJqYXZhIiwiYXJncyI6IiIsImpzQ29kZSI6IiIsIm5vbmVNYXJrZXJzIjp0cnVlLCJ0aGVtZSI6ImlkZWEiLCJjb2RlIjoiZnVuIG1haW4oKSB7XG5cdHZhbCBzdGFydCA9IFN5c3RlbS5uYW5vVGltZSgpXG4gICAgcHJpbnRsbihzdGFydClcbiAgICBUaHJlYWQuc2xlZXAoMTAwMClcblx0dmFsIGZpbmlzaCA9IFN5c3RlbS5uYW5vVGltZSgpXG4gICAgcHJpbnRsbihmaW5pc2gpXG4gICAgcHJpbnRsbihmaW5pc2ggLSBzdGFydClcbiAgICBcbiAgICB2YWwgc3RhcnQyID0gU3lzdGVtLmN1cnJlbnRUaW1lTWlsbGlzKClcbiAgICBwcmludGxuKHN0YXJ0MilcbiAgICBUaHJlYWQuc2xlZXAoMTAwMClcblx0dmFsIGZpbmlzaDIgPSBTeXN0ZW0uY3VycmVudFRpbWVNaWxsaXMoKVxuICAgIHByaW50bG4oZmluaXNoMilcbiAgICBwcmludGxuKGZpbmlzaDIgLSBzdGFydDIpXG5cblxufSJ9
     */

    private val exchanger = Exchanger<String>()

    @Test
    fun test1(){
        val t1 = thread (start = false) { //using kotlin lib
            val start = System.nanoTime()
            val startMilli = System.currentTimeMillis()
            val exchanged = exchanger.exchange("t1", Duration.ofSeconds(1))
            assert(exchanged=="t2")
            val timeElapsed = System.nanoTime() - start
            val timeElapsedMilli = System.currentTimeMillis() - startMilli -0f //usar long do java e Long do kotlin parece dar meio mal e arrendoda, por isso é q tem o "-0f"
            println("T1 obtained: $exchanged, Nano: $timeElapsed, seconds: ${timeElapsed/1_000_000_000f}, milli: $timeElapsedMilli")
        }

        val t2 = Thread { //using java lib
            val start = System.nanoTime()
            val exchanged = exchanger.exchange("t2", Duration.ofSeconds(1))
            assert(exchanged=="t1")
            val timeElapsed = System.nanoTime() - start
            println("T2 obtained: $exchanged, Nano: $timeElapsed, seconds: ${timeElapsed/1_000_000_000f}, milli: ${timeElapsed/1_000_000f}")
        }
        t1.start()
        t2.start()
        Thread.currentThread().join(1000) //wait for the 2 threads to do its thing
    }

    @Test
    fun test2(){
        val threads = LinkedList<Thread>()
        repeat(10){
            threads.add(thread (start = false, name = it.toString()) {
                val exchanged = exchanger.exchange((it).toString(), Duration.ofSeconds(1))
                println("I, thread $it, recived $exchanged")
            })

        }
        threads.forEach {
            it.start()
        }
        Thread.currentThread().join(1000)
    }
}