package s2

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class CounterModulo(private val maxValueExcluive: Int) {
    init { assert(maxValueExcluive>0) }
    private val _value = AtomicInteger(0)
    val value: Int
        get() = _value.get()

    fun increment() : Int {
        while(true) {
            val observedValue = _value.get()
            var incrementedValue = observedValue+1
            if(incrementedValue==maxValueExcluive) incrementedValue = 0
            if (_value.compareAndSet(observedValue, incrementedValue))
                return incrementedValue
            // else, try again
        }
    }

    fun decrement() : Int {
        while(true) {
            val observedValue = _value.get()
            var decrementedValue = observedValue-1
            if(decrementedValue==-1) decrementedValue = maxValueExcluive-1
            if (_value.compareAndSet(observedValue, decrementedValue))
                return decrementedValue
            // else, try again
        }
    }
}
