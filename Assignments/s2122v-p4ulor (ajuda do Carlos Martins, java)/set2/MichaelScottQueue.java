/** ex3
 *
 * Michael & Scott Non-blocking Queue Algorithm (Michael and Scott, 1996)
 *
 * This implementation follows the algorithm describe in the section 15.4.2 of the
 * book Java Concurrency in practice and described in the file nonblocking-synchronization.md
 * section "Michael and Scott Queue (1996)"
 
		
 *
 **/

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.IOException;

public class MichaelScottQueue<E> {

	// the queue node
	private static class Node<V> {
		final V data;
		final AtomicReference<Node<V>> next;
		
		Node(V d) {
			data = d;
			next = new AtomicReference<Node<V>>(null);
		}
	}
	
	// the head and tail references
	private final AtomicReference<Node<E>> head;
	private final AtomicReference<Node<E>> tail;
	
	public MichaelScottQueue() {
		Node<E> sentinel = new Node<E>(null);
		head = new AtomicReference<Node<E>>(sentinel);
		tail = new AtomicReference<Node<E>>(sentinel);
	}
	
	// enqueue: add a datum to the queue
	public void enqueue(E data) {
		Node<E> newNode = new Node<E>(data);

		do {
			// s1: get the current state od the queue
			Node<E> observedTail = tail.get();
			Node<E> observedTailNext = observedTail.next.get();
			// s1.i: confirm that we have a good tail, in order to prevent CAS failures
			if (observedTail == tail.get()) {
				// s2: test what to do next
				if (observedTailNext != null) {
					//s2.ii: queue in intermediate state, so advance tail helping some other thread
					tail.compareAndSet(observedTail, observedTailNext);
					// retry
				} else {
					// s2.i:  queue in quiescent state, try inserting new node
					// s3: use CAS in order to insert the new node
					if (observedTail.next.compareAndSet(null, newNode)) {
						// s3: advance the tail: this CAS is not checked, because any thread could help us
						tail.compareAndSet(observedTail, newNode);
						return;		// enqueue completed
					}
					// retry
				}
			}
		} while (true);		
	}
	
	// tryDequeue: tries to dequeue a datum
	public E tryDequeue() {
		Node<E> observedHead, observedHeadNext, observedTail;
		do {
			// s1: get a coherent copy of the mutable shared state
			observedHead = head.get();
			observedTail = tail.get();
			observedHeadNext = observedHead.next.get();
			
			// s2: decide what to do next
			// test if the queue is empty
			if (observedHead == observedTail) {
				// the queue seems empty, test if this is true
				if (observedHeadNext == null)
					return null;		// the queue is really empty
				// the queue is in the intermediate state, advance the tail to observedHeadNext
				tail.compareAndSet(observedTail, observedHeadNext);
				// retry after help
			 } else {
				 // the queue is not empty, remove the first node
				 // first do a sanity test
				 if (observedHead == head.get()) {
					 if (head.compareAndSet(observedHead, observedHeadNext)) {
						 return observedHeadNext.data;	// the first node of the queue is a sentinel
					 }
				 }
			 }
		 } while (true);
	 }
}



