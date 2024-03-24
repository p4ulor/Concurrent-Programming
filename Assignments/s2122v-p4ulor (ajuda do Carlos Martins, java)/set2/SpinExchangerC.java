import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
 * ex1 c) A spin exchanger whit support for interruption
 *
 * RACIONAL (ESTE TEXTO DEVE SER APAGADO NO FICHEIRO ENTREGUE PARA AVALIAÇÃO)
 *
 * O principal problema na implementação do exchanger com cancelamento, neste caso por interrupção, está
 * relacionado com o facto da primeiro thread de um par expor um objecto request às potenciais segundas
 * threads. Neste caso, o cancelamento efectivo apenas é possível se a primiera thread conseguir remover
 * o seu objecto request antes de uma potencial segunda thread assumir a troca.
 * Assim, quando é detectada uma interrupção (com o método Thread.interrupted), a thread interrompida,
 * testa se o ponto de troca ainda refere o seu objecto request. Em caso afirmativo, usa CAS para alterar
 * a referência do ponto de troca do seu objecto request para null. Se o CAS tiver sucesso, o cancelamento
 * é concluído com sucesso. No caso contrário, uma segunda thread já iniciou a troca, pelo que a primeira
 * thread terá que aguardar pela conclusão da mesma e deverá rearmar a interrupção para que a mesma não se
 * perca
 */

class SpinExchangerC<V> {

	// the request object: works as an holder object that aggregates all needed information
	private class Request<E> {
		E first;						// the offered message
		E second;						// the message with which the exchange was performed
		volatile boolean done = false;	// true when done; volatile because we will spin on it
		
		Request(E first) {
			this.first = first;
		}
	}
	
	// the exchange spot. This can be thought of as a "queue" that is either empty
	// or contains an element.
	private final AtomicReference<Request<V>> xchgSpot = new AtomicReference<>();

	// exchange method whit support for interruption
	public V exchange(V myData) throws InterruptedException {
		// if the thread was already interrupted, throw InterruptedException
		if (Thread.interrupted())
			throw new InterruptedException();
		
		Request<V> request = null;
		do {
			
			// s1: get a copy of the mutable shared state
			Request<V> observedXchgSpot = xchgSpot.get();
			
			// s2: decide what to do next
			if (observedXchgSpot == null) {
				// s2.i: we are the first thread of a pair, offer my object exposing a request object
				// if we do not have a request yet, create one
				if (request == null)
					request = new Request<V>(myData);
				
				// s3: use CAS to change the mutable sahred state.	
				if (xchgSpot.compareAndSet(null, request)) {
					// s3.i: CAS succedded, so the current thread is the first thread of the pair
					// - this thread will spinning until the request object indicates that the
					//   second thread of the pair already finished exchanging objects.
					// - this loop breaks, if the thread is interrupted while spinning
					do {
						if (request.done)
							return request.second;	
						if (Thread.interrupted())
							break;
						Thread.yield();		// yield the processor
					} while (true);
					
					// we get here when there is a cancellation due to interruption
					
					// in order to cancel the operation, we must remove our request object; however,
					// this is only possivel if the no other thread already paired with us. We know that
					// no other thread paired with us if the xchgSopt still points to our request object 
					
					// s1: get a copy of the nutable shared state
					observedXchgSpot = xchgSpot.get();
					
					// s2: decide what to do next
					if (observedXchgSpot == request) {
						// the exchange with another thread has not yet started: try to remove our request
						// object using CAS
						if (xchgSpot.compareAndSet(request, null)) {
							// we removed our request object: return the cancellation result
							throw new InterruptedException();
						} else
							;	// if CAS fails that means the exchange has already started, we
								// wait until the exchange is done
					}
					// the exchange has already started, wait until it is completed
					while (!request.done)
						Thread.yield();
					// we know that the current thread was interrupted, reactivate the interrupt
					Thread.currentThread().interrupt();
					// return normally
					return request.second;
				}
			} else {
				// s3: current thread is the second thread of a pair: use CAS to try to reset
				// the exchange spot, getting the request object exposed by the first thread of
				// the exchange pair
				if (xchgSpot.compareAndSet(observedXchgSpot, null)) {
					// s3.i: the CAS succeeded, give my data and get the data of the first thread
					observedXchgSpot.second = myData;
					observedXchgSpot.done = true;		// volatile write: mark the exchange as completed 
					return observedXchgSpot.first;
				}
			}
		} while (true);
	}	

	/*
	 * Test code
	 */

	// test the exchanger	
	private static boolean testExchangerWithInterrupts() throws InterruptedException {

		final int MAX_RETRY_EXCHANGE_TIME = 25;
		final int MAX_INTERRUPT_TIME = 50;
		final int RUN_TIME = 5 * 1000;
		final int EXIT_TIME = 100;
		final int JOIN_TIMEOUT = 50;
		final int THREADS = 20;

		Thread[] exchangers = new Thread[THREADS];
		int[] privateCounters = new int[THREADS];
		int[] interrupts = new int[THREADS];
		final AtomicInteger sharedCounter = new AtomicInteger();
		final AtomicBoolean running = new AtomicBoolean(true);
		SpinExchangerC<Long> exchanger = new SpinExchangerC<>();
		
		System.out.printf("%n--> Start test of exchanger...%n%n");
				
		//
		// Create and start exchanger threads
		//
		
		for (int i = 0; i < THREADS; i++) {
			final int tid = i;
			exchangers[i] = new Thread(() -> {
				System.out.printf("-> exchanger thread #%02d starting...%n", tid);
				var random = ThreadLocalRandom.current();	
				outerloop: do {
					Long mine = Thread.currentThread().getId();
					Long yours;
					do {
						try {
							yours = exchanger.exchange(mine);
							break;
						} catch (InterruptedException ie) {
							//System.out.println("\nexchange.interrupt");
							interrupts[tid]++;
						}
						if (!running.get())
							break outerloop;
 					} while (true);
					if (mine == yours)
						System.out.printf("%n*** error: wrong exchange: %d/%d%n%n", mine, yours);
					else {
						sharedCounter.incrementAndGet();
						if (++privateCounters[tid] % 100 == 0) {
							System.out.printf("[#%d]", tid);
						}
						try {
							Thread.sleep(random.nextInt(MAX_RETRY_EXCHANGE_TIME));
						} catch (InterruptedException ie) {
							//System.out.println("\nsleep.interrupt");
							interrupts[tid]++;
						}
					}
				} while (running.get());
				if (Thread.interrupted())
					interrupts[tid]++;
				System.out.printf("<- exchanger thread #%02d exiting...%n", tid);
			});
			exchangers[i].start();
		}

		// run the test threads for a while, interrupting random thread
		long deadline = System.currentTimeMillis() + RUN_TIME;
		var random = ThreadLocalRandom.current(); 
		int interruptsSent = 0;
		do {
			Thread.sleep(random.nextInt(MAX_INTERRUPT_TIME));
			Thread victim = exchangers[random.nextInt(THREADS)];			
			if (!victim.isInterrupted()) {
				victim.interrupt();
				interruptsSent++;
			}
		} while (System.currentTimeMillis() < deadline);
		running.set(false);
		Thread.sleep(EXIT_TIME);
		
		// wait until each exchanger threads terminate
		for (int i = 0; i < THREADS; i++) {
			exchangers[i].join(JOIN_TIMEOUT);
			if (exchangers[i].isAlive()) {
				exchangers[i].interrupt();
				interruptsSent++;
				exchangers[i].join();
			}
		}
		
		// All thread finished - compute results
		
		System.out.printf("Private exchange/interrupt counters:");
		int totalExchanges = 0, totalInterrupts = 0;
		for (int i = 0; i < THREADS; i++) {
			totalExchanges += privateCounters[i];
			totalInterrupts += interrupts[i];
			if ((i % 4) == 0)
				System.out.println();
			else
				System.out.print(' ');
			System.out.printf("[#%02d: %4d/%3d]", i, privateCounters[i], interrupts[i]);
		}
		System.out.printf("%n%n--shared exchanges: %d, private exchanges: %d%n", sharedCounter.get(), totalExchanges);
		System.out.printf("%n--sent interrupts: %d, received interrupts: %d%n", interruptsSent, totalInterrupts);
		return totalExchanges == sharedCounter.get();
	}
		
	public static void main(String... args) throws InterruptedException {
		System.out.println("test monitor based exchanger with interrupts: " +
				 		   (testExchangerWithInterrupts() ? "passed" : "failed"));	
	}
}
