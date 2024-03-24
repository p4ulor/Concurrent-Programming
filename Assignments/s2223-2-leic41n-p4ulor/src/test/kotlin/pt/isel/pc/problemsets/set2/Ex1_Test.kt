package pt.isel.pc.problemsets.set2

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class Ex1_Test {

    @Test
    fun `basic test`(){
        val barrier = MyCyclicBarrier(3) { println("Done") }
        var v1 = -1; var v2 = -1; var v3 = -1
        val t1 = Thread({
            v1 = barrier.await()
        }, "t1")

        val t2 = Thread({
            v2 = barrier.await()
        }, "t2")

        val t3 = Thread({
            v3 = barrier.await()
        }, "t3")

        t1.start()
        t2.start()
        t3.start()

        t1.join()
        t2.join()
        t3.join()

        assertTrue(v1==0).also { println(v1) }
        assertTrue(v2==1).also { println(v2) }
        assertTrue(v3==2).also { println(v3) }
    }

    @Test
    fun `reach goal, and do it again`(){
        val barrier = MyCyclicBarrier(3) { println("Done") }

        var v1 = -1; var v2 = -1; var v3 = -1

        val t1 = Thread({
            v1 = barrier.await()
        }, "t1")

        val t2 = Thread({
            v2 = barrier.await()
        }, "t2")

        val t3 = Thread({
            v3 = barrier.await()
        }, "t3")

        t1.start()
        t2.start()
        t3.start()

        t1.join()
        t2.join()
        t3.join()

        println(v1)
        println(v2)
        println(v3)

        assertTrue(v1==0)
        assertTrue(v2==1)
        assertTrue(v3==2)

        //cycle 2

        var v4 = -1; var v5 = -1; var v6 = -1

        val t4 = Thread({
            v4 = barrier.await()
        }, "t4")

        val t5 = Thread({
            v5 = barrier.await()
        }, "t5")

        val t6 = Thread({
            v6 = barrier.await()
        }, "t6")

        t4.start()
        t5.start()
        t6.start()

        t4.join()
        t5.join()
        t6.join()

        println(v4)
        println(v5)
        println(v6)

        assertTrue(v4==0)
        assertTrue(v5==1)
        assertTrue(v6==2)
    }
}