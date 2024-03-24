package a_Intro
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

val list = LinkedList<String>()

fun main(){

    val threads = List(20) {
        Thread {
            doit(it)
        }
    }
    threads.forEach { it.start() }
    Thread.sleep(1000) //commenting out this, may print correctly, throw concurrent execution exception or theres a chance that it add till 18 and 19
    println(list)
}
val oi = ay()
fun doit(it: Int){
    oi.syncAdd(it)
}


class ay {
    private val lock = ReentrantLock()

   /* @Synchronized */fun syncAdd(n: Int){
        /*synchronized(x){
            list.add(n.toString())
        }*/
       lock.withLock {
           list.add(n.toString())
       }
    }
}