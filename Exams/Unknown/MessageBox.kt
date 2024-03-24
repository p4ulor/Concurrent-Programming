package main.exams

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.nanoseconds

class MessageBox<T> {
    private val mLock = ReentrantLock()

    private class Request<T>(var msg: T?, val condition: Condition)
    private val requests = mutableListOf<Request<T>>()

    fun waitForMessage(timeout: Long, unit: TimeUnit): T?{
        mLock.withLock {
            println("entrei no wait")
            val req = Request<T>(null, mLock.newCondition())
            requests.add(req)

            var dueTime = unit.toNanos(timeout)
            while (true){
                try {
                    dueTime = req.condition.awaitNanos(dueTime)
                }catch (e: InterruptedException){
                    requests.remove(req)
                    throw e
                }

                if (dueTime <= 0){
                    return null
                }

                println("estou aqui")
                if (req.msg != null){
                    val toRet = req.msg
                    println("Before $toRet")
                    requests.remove(req)
                    println("After $toRet")
                    return toRet
                }
            }
        }
    }

    fun sendToAll(message: T): Int{
        mLock.withLock {
            println("entrei no send")
            if (requests.isNotEmpty()){
                val number = requests.size
                requests.forEach {
                    it.msg = message
                }
                requests.forEach {
                    it.condition.signal()
                }
                return number
            }
            return 0
        }
    }
}