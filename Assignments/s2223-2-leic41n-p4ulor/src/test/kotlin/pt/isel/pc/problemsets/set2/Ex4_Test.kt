package pt.isel.pc.problemsets.set2

import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Ex4_Test {

    @Test
    fun `checking out the WhenCompleteAsync`(){
        println("@Test thread = ${Thread.currentThread().name}")

        val x = CompletableFuture<String>()

        x.whenComplete { value, exception ->
            println("Done with ${Thread.currentThread().name}")
        }

        val y = CompletableFuture<String>()

        y.whenCompleteAsync {value, exception ->
            println("Done with ${Thread.currentThread().name}")
        }

        x.complete("")
        y.complete("")

        x.join()
        y.join()
    }

    @RepeatedTest(30)
    fun `basic test`(){
        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()
        val f3 = CompletableFuture<String>()
        val futures = mutableListOf(f1, f2, f3)

        val strings = mutableListOf("f1", "f2", "f3")

        f1.complete(strings[0])
        f2.complete(strings[1])
        f3.complete(strings[2])

        val value = TestAny<String>().any(futures).get()
        println("basic test got = $value")
        assertContains(strings, value)
    }

    @RepeatedTest(30)
    fun `basic test2`(){
        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()
        val f3 = CompletableFuture<String>()
        val futures = mutableListOf(f1, f2, f3)

        val strings = mutableListOf("f1", "f2", "f3")

        val t1 = thread { f1.complete(strings[0]) }
        val t2 = thread { f2.complete(strings[1]) }
        val t3 = thread { f3.complete(strings[2]) }

        val futValue = TestAny<String>().any(futures)

        t1.join(); t2.join(); t3.join()

        val value = futValue.get()
        println("basic test got = $value")
        assertContains(strings, value)
    }

    @RepeatedTest(30)
    fun `test with 1 exception`(){
        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()
        val f3 = CompletableFuture<String>()
        val futures = mutableListOf(f1, f2, f3)

        val strings = mutableListOf("f1", "f2", "f3")

        val t1 = thread { f1.completeAsync { throw Exception() } }
        val t2 = thread { f2.complete(strings[1]) }
        val t3 = thread { f3.complete(strings[2]) }

        val futValue = TestAny<String>().any(futures)

        t1.join(); t2.join(); t3.join()

        val value = futValue.get()
        println("basic test got = $value")
        strings.removeFirst()
        assertContains(strings, value)
    }

    @RepeatedTest(10)
    fun `test all threw exception`(){
        val f1 = CompletableFuture<String>()
        val f2 = CompletableFuture<String>()
        val f3 = CompletableFuture<String>()
        val futures = mutableListOf(f1, f2, f3)

        thread { f1.completeAsync { throw Exception() } }
        thread { f2.completeAsync { throw Exception() } }
        thread { f3.completeAsync { throw Exception() } }

        val future = TestAny<String>().any(futures)
        assertFailsWith(Exception::class){
            future.get()
        }
        assertEquals(true, future.isCompletedExceptionally)
    }
}

class TestAny<T>{
    //fun any(futures: List<CompletableFuture<T>>) = anyNoLocks<T>(futures)
    fun any(futures: List<CompletableFuture<T>>) = anyWithLocks<T>(futures)
}