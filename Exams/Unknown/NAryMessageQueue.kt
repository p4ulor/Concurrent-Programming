package main.exams

import kotlin.time.Duration
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class NAryMessageQueue<T> {
    private val queue = mutableListOf<T>()

    // The monitor's lock and condition
    private val mLock: Lock = ReentrantLock()

    private class Request<T>(val n: Int, var list: MutableList<T>, val condition : Condition)
    private val requests = mutableListOf<Request<T>>()

    fun put(msg: T){
        mLock.withLock {
            queue.add(msg)
            maybeSignalTakers()
        }
    }

    fun get(n: Int, timeout: Duration): List<T>{
        mLock.withLock {
            if (requests.isEmpty()){
                println("requests estao empty")
                if (queue.size >= n){
                    println("vou retornar ja")
                    val toRet = mutableListOf<T>()
                    repeat(n){
                        toRet.add(queue.removeFirst())
                    }
                    println("o que vou retornar $toRet \nqueue after clear: $queue")
                    return toRet
                }
            }

            val req = Request<T>(n, mutableListOf(), mLock.newCondition())
            requests.add(req)
            println("fui para a file de requests")
            var remainingTime = timeout.inWholeNanoseconds
            while (true) {
                try {
                    remainingTime = req.condition.awaitNanos(remainingTime)
                }catch (e: InterruptedException){
                    requests.remove(req)
                    maybeSignalTakers()
                    throw e
                }

                if (req.list.size == req.n){
                    return req.list
                }

                if (remainingTime <= 0) {
                    requests.remove(req)
                    maybeSignalTakers()
                    return emptyList()
                }
            }
        }
    }

    private fun maybeSignalTakers(){
        if (requests.isNotEmpty()) {
            while (requests.first().n < queue.size) {
                val sRequest = requests.removeFirst()
                repeat(sRequest.n){
                    sRequest.list.add(queue.removeFirst())
                }
                sRequest.condition.signal()
            }
        }
    }
}