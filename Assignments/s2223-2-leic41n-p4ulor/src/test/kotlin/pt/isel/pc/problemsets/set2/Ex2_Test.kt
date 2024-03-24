package pt.isel.pc.problemsets.set2

import org.junit.jupiter.api.Test
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.assertTrue

class Ex2_Test {
    @Test
    fun `basic test`(){
        val msgContainer = UnsafeContainer(arrayOf(UnsafeValue("isel", 3), UnsafeValue("pc", 4)))
        val lock = ReentrantLock()
        val isels = mutableListOf<String>()

        val t1 = t("t1", lock) {
            isels.add/*WithLock*/(msgContainer.consume() as String)
        }

        val t2 = t("t2", lock) {
            isels.add(msgContainer.consume() as String)
        }

        val t3 = t("t3", lock) {
            isels.add(msgContainer.consume() as String)
        }

        t1.start(); t2.start(); t3.start()

        t1.join(); t2.join(); t3.join()

        println(isels)

        assertTrue(isels.size==3)
        assertTrue(isels.all {
            it == "isel"
        })

        ///////////////////////////

        val pcs = mutableListOf<String>()

        val t4 = t("t4", lock) {
            pcs.add(msgContainer.consume().toString())
        }

        val t5 = t("t5", lock) {
            pcs.add(msgContainer.consume().toString())
        }

        val t6 = t("t6", lock) {
            pcs.add(msgContainer.consume().toString())
        }

        val t7 = t("t7", lock) {
            pcs.add(msgContainer.consume().toString())
        }

        val t8 = t("t8", lock) {
            pcs.add(msgContainer.consume().toString())
        }

        t4.start(); t5.start(); t6.start(); t7.start(); t8.start()

        t4.join(); t5.join(); t6.join(); t7.join(); t8.join()

        println(pcs)

        assertTrue(pcs.size==5)
        assertTrue(4==pcs.count {
            it=="pc"
        })
        assertTrue(1==pcs.count {
            it=="null"
        })
    }

    fun t(n: String, lock: ReentrantLock, action: () -> Unit) : Thread {
        return Thread({
            lock.withLock {
                action()
            }
        }, n)
    }
}