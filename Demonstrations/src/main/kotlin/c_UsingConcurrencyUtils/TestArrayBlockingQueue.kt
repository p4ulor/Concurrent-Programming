package c_UsingConcurrencyUtils

import java.util.*
import java.util.concurrent.ArrayBlockingQueue

import java.util.concurrent.BlockingQueue




class TestArrayBlockingQueue {
    private var queue: BlockingQueue<Any> = ArrayBlockingQueue(1024)

    fun add(obj: Any){
        queue.add(obj)
    }

    fun put(obj: Any){
        queue.put(obj)
    }


}