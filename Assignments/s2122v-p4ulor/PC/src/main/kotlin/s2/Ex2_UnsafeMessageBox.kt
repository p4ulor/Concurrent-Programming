package s2

import java.util.concurrent.atomic.AtomicReference

class UnsafeMessageBox<M> {

    private class Holder<M>(val msg: M, var lives: Int) { init { assert(lives>0) }}

    private val msgHolder = AtomicReference<Holder<M>?>()

    fun publish(msg: M, lives: Int) {
        msgHolder.set(Holder(msg, lives))
    }

    fun tryConsume() : M? {
        while(true) {
            val observedMsgHolder = msgHolder.get() // get a copy of the mutable shared state

            // depending on the copy of the mutable shared state, decide what to do next
            if (observedMsgHolder == null || observedMsgHolder.lives == 0) {
                return null // the operation is not possible so, return null
            }

            // compute the to-be-altered value for the mutable shared state
            val updatedMsgHolder: Holder<M>? =
                if (observedMsgHolder.lives > 1) Holder<M>(observedMsgHolder.msg, observedMsgHolder.lives - 1)
                else null

            // call CAS in order to try to change the mutable shared state from observedMsgHolder to updated updatedMsgHolder
            if (msgHolder.compareAndSet(observedMsgHolder, updatedMsgHolder)) {
                return observedMsgHolder.msg // the CAS succeeded, return the msg.
            }

            //the CAS failed due to collision, try again
        }
    }
}
