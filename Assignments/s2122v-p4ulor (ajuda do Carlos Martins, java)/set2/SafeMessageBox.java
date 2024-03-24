package pt.isel.pc.problemsets.set2;

import java.util.concurrent.atomic.AtomicReference;

public class SafeMessageBox <M> {
    // Immutable object to hold the mutable shared state
    private static class MsgHolder<E> {
        final E msg;
        final int lives;
        MsgHolder(E msg, int lives){
            this.msg = msg;
            this.lives = lives;
        }
    }

    // mutable shared state: an atomic reference to an instance of MsgHolder
    private final AtomicReference<MsgHolder<M>> msgHolder = new AtomicReference<>(); //initialized with null

    // publish a new message: this is an unconditional operation
    public void publish(M msg, int lvs){
        // unconditional assignment
        msgHolder.set(new MsgHolder<M>(msg, lvs));
    }

    // tryConsume: this method follows the pattern given for this kind of algorithms
    // and the technic of using a holder object to use only one shared variable (
    // demonstrated in CasNumberRange example)
    public M tryConsume(){
        do {
            // s1: get a copy of the mutable shared state
            MsgHolder<M> observedMsgHolder = msgHolder.get();

            // s2: depending on the copy of the mutable shared state, decide what to do next
            if (observedMsgHolder==null || observedMsgHolder.lives==0) {
                // s2.ii: the operation is not possible so, return null
                return null;
            }

            // s2.i: compute the updating value for the mutable shared state
            MsgHolder <M> updatedMsgHolder = (observedMsgHolder.lives > 1) ?
                    new MsgHolder<>(observedMsgHolder.msg, observedMsgHolder.lives-1) : null;

            // s3: invoke CAS in order to try to change the mutable shared state from observedMsgHolder
            // to updated updatedMsgHolder
            if (msgHolder.compareAndSet(observedMsgHolder, updatedMsgHolder)) {
                // s3.i: the CAS succeeded, so return the msg.
                return observedMsgHolder.msg;
            }

            // s3.ii: the CAS failed due to collision, try again
        } while (true);
    }
}
