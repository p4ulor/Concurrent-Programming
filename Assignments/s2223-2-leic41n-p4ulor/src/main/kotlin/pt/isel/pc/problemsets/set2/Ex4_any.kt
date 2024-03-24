package pt.isel.pc.problemsets.set2

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun <T> anyNoLocks(futures: List<CompletableFuture<T>>) : CompletableFuture<T> { //https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
    require(futures.isNotEmpty()) { "futures list can't be empty" }

    val anyFuture = CompletableFuture<T>()
    val exceptions = LockFreeStack<Throwable>() //treiber stack
    val exceptionCount = AtomicInteger(0)

    futures.forEach {
        it.whenCompleteAsync { value, exception ->
            if(exception==null){
                anyFuture.complete(value)
            } else {
                exceptions.push(exception)
                val xceptionCount = exceptionCount.incrementAndGet()

                if(xceptionCount==futures.size){
                    println("All CompletableFutures got an exception")

                    while (true){
                        val xception = exceptions.pop()
                        println("xception = $xception")
                        if(xception==null) break
                        exception.addSuppressed(xception)
                    }

                    anyFuture.completeExceptionally(exception)
                }
            }
        }
    }

    return anyFuture
}

fun <T> anyWithLocks(futures: List<CompletableFuture<T>>): CompletableFuture<T> { //https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html
    require(futures.isNotEmpty()) { "futures list can't be empty" }

    val anyFuture = CompletableFuture<T>()

    val lock = ReentrantLock()

    val exceptions = mutableListOf<Throwable>()
    var exceptionCount = 0

    futures.forEach {
        it.whenCompleteAsync { value, exception ->
            if(exception==null){
                anyFuture.complete(value)
            } else {
                lock.withLock {
                    if(anyFuture.isCompletedExceptionally) return@whenCompleteAsync
                    exceptions.add(exception)
                    ++exceptionCount

                    if(exceptionCount==futures.size){
                        println("All CompletableFutures got an exception")

                        exceptions.forEach {
                            println("Got xception = ${it.message}")
                            exception.addSuppressed(it)
                        }

                        anyFuture.completeExceptionally(exception)
                    }
                }
            }
        }
    }

    return anyFuture
}

class LockFreeStack<T> { //treiber stack

    private class Node<T>(val value: T) {
        var next: Node<T>? = null
    }

    private val head = AtomicReference<Node<T>?>(null)

    fun push(value: T) {
        val node = Node(value)
        while (true) {
            val observedHead: Node<T>? = head.get()
            node.next = observedHead
            if (head.compareAndSet(observedHead, node)) {
                break
            }
            // retry
        }
    }

    fun pop() : T? {
        var observedHead: Node<T>?
        var newHead: Node<T>?
        do {
            observedHead = head.get() ?: null
            if (observedHead == null) return null
            newHead = if(observedHead.next==null) null else observedHead.next // the only "bellow" the head (on top of the stack)
        } while (!head.compareAndSet(observedHead, newHead))
        return observedHead?.value
    }
}
