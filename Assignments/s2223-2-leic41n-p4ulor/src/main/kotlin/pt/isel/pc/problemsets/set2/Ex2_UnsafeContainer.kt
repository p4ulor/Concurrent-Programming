package pt.isel.pc.problemsets.set2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class UnsafeValue<T>(val value: T, val lives: Int){
    init {
        require(lives >= 0)
    }
}

class UnsafeContainer<T>(private val values: Array<UnsafeValue<T>>){

    private val _values = mutableListOf<AtomicReference<UnsafeValue<T>?>>()
    private val isEmpty = AtomicBoolean(false)
    private var index = AtomicInteger(0) //current index, being used to obtain values and use "lives", just to optimize searches

    init {
        values.forEach {
            _values.add(AtomicReference(it))
        }
    }

    fun consume(): T? {
        if(isEmpty.get()) return null
        var curIndex = index.get()
        while(curIndex < values.size) {
            val observedValue = _values[curIndex].get()
            if(observedValue!=null && observedValue.lives!=0){
                val updatedValue = UnsafeValue(observedValue.value, observedValue.lives-1) //creates new reference
                if(_values[curIndex].compareAndSet(observedValue, updatedValue)){ //note: this compares references first
                    return observedValue.value
                }
            } else {
                while(true){
                    val observedIndex = index.get()
                    if(index.compareAndSet(observedIndex, observedIndex+1)){
                        break
                    }
                }
            }
            curIndex = index.get()
        }

        isEmpty.set(true) //no need for a while and compareAndSet because it's never set to false
        _values.clear()
        return null
    }
}
