package pt.isel.pc.problemsets.set2

import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

class UnsafeUsageCountedHolder<T : Closeable>(value: T) {
    @Volatile
    var value: T? = value //since this value is set at the beginning, and can only be written to null as a final state (and the decision to do so does not depend on its current read value), I guess it's safe to say that it can be a volatile var
    val useCounter = AtomicInteger(1) // the instance creation counts as one usage

    fun tryStartUse(): T? {
        var observedValue: Int
        var updatedValue: Int

        while(true) {
            if (value == null) return null
            observedValue = useCounter.get()
            updatedValue = observedValue + 1
            println("${Thread.currentThread().name} -> endUse, observed = $observedValue, updatedValue = $updatedValue")

            if (useCounter.compareAndSet(observedValue, updatedValue)) {
                println("${Thread.currentThread().name} -> Success")
                break
            }
            else println("${Thread.currentThread().name} -> Failed")
        }

        return value
    }

    fun endUse() {
        var observedValue: Int
        var updatedValue: Int

        while(true){
            if (value == null) throw IllegalStateException("Already closed")

            observedValue = useCounter.get()
            updatedValue = observedValue - 1

            if(observedValue == 0) throw IllegalStateException("Already closed")

            println("${Thread.currentThread().name} -> endUse, observed = $observedValue, updatedValue = $updatedValue")

            if (useCounter.compareAndSet(observedValue, updatedValue)) {
                println("${Thread.currentThread().name} -> Success")
                if(updatedValue==0){
                    println("${Thread.currentThread().name} -> Will close")
                    value?.close()
                    value = null
                }
                break
            }
            else println("${Thread.currentThread().name} -> Failed")
        }
    }
}

