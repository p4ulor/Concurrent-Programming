/* *********** PC_2021v_1 *********** */

/* 
1) implement below

	public class MessageBox<T> {
		public Optional<T> waitForMessage(long timeout) throws InterruptedException;
		public int sendToAll(T message);
	}
*/

public class MessageBox<T> {
	
	private class Box<T> {
		public boolean isDone = false;
		public T message = null;
	}
	
	private final NodeLinkedList<Box<T>> threads = new NodeLinkedList();
	private final Object lock = new Object();
	private Box<T> currentBox = new Box<>();
	
	public Optional<T> waitForMessage(long timeout) throws InterruptedException {
		synchronized (lock) {
			long deadline = Timeouts.deadlineFor(timeout);
            long remaining = Timeouts.remainingUntil(deadline);
			var node = threads.enqueue(currentBox);
			while(true) {
				try {
					lock.wait(remaining);
				} catch(InterruptedException e) {
					if(node.value.isDone) {
						Thread.currentThread().interrupt();
						return Optional.of(node.value.message);
					}
					threads.remove(node);
					throw e;
				}
				
				if(node.value.isDone) {
					return Optional.of(node.value.message);
				}
				
				remaining = Timeouts.remainingUntil(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    threads.remove(node);
                    return Optional.empty();
                }
			}
		}
	}
	
	public int sendToAll(T message) {
		synchronized (lock) {
			int count = threads.getCount();
			currentBox.message = message;
			while(threads.isNotEmpty()) {
				threads.pull();
			}
			lock.notifyAll();
			currentBox = new Box();
			return count;
		}
	}
}

/*
2) implement

	public class NArySemaphore {
		public NArySemaphore(int initial);
		public boolean acquire(int n, long timeout) throws InterruptedException;
		public boolean lowPriorityAcquireAll(long timeout) throws InterruptedException;
		public void release(int n);
	}
*/

public class NArySemaphore {
	
	public static class Request {
		public final int requestedUnits;
		public final Condition condition;
		public boolean isDone = false;
		public Request(int requestedUnits, Lock monitor) {
			this.requestedUnits = requestedUnits;
			condition = monitor.newCondition();
		}
	}
	
	private final Lock monitor = new ReentrantLock();
	private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
	private final NodeLinkedList<Request> all = new NodeLinkedList<>();
	
	private final int initial;
	private int units;
	
	public NArySemaphore(int initial) {
		units = initial = initialUnits;
	}
	
	public boolean acquire(int n, long timeout) throws InterruptedException {
		monitor.lock();
		try {
			// fast path
			if (timeout < 0) {
				throw new IllegalArgumentException("timeout must be >=0");
			}
			
			if (queue.isEmpty() && units >= n) {
				units -= n;
				return true;
			}
			
			if (Timeouts.noWait(timeout)) {
				return false;
			}
			// wait path
			long deadline = Timeouts.deadlineFor(timeoutInMs);
            long remaining = Timeouts.remainingUntil(deadline);
			var node = queue.enqueue(new Request(n, monitor));
			while(true) {
				try {
					node.value.condition.await(remaining, TimeUnit.MILISECONDS);
				} catch(InterruptedException e) {
					if (node.value.isDone) {
						Thread.currentThread().interrupt();
						return true;
					}
					completeAllRequestsThatCanBeCompleted();
					throw e;
				}
				
				if (node.value.isDone) {
					return true;
				}
				
				remaining = Timeouts.remainingUntil(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
					completeAllRequestsThatCanBeCompleted();
                    return false;
                }
			}
		} finally {
			monitor.unlock();
		}
	}
	
	public boolean lowPriorityAcquireAll(long timeout) throws InterruptedException {
		monitor.lock();
		try {
			if (timeout < 0) {
				throw new IllegalArgumentException("timeout must be >=0");
			}
			
			if (queue.isEmpty() && all.isEmpty() && units >= initial) {
				units = 0;
				return true;
			}
			
			if (Timeouts.noWait(timeout)) {
				return false;
			}
			long deadline = Timeouts.deadlineFor(timeoutInMs);
            long remaining = Timeouts.remainingUntil(deadline);
			var node = queue.enqueue(new Request(0, monitor));
			while(true) {
				try {
					node.value.condition.await(remaining, TimeUnit.MILISECONDS);
				} catch(InterruptedException e) {
					if (node.value.isDone) {
						Thread.currentThread().interrupt();
						return true;
					}
					all.remove(node);
					throw e;
				}
				
				if (node.value.isDone) {
					return true;
				}
				
				remaining = Timeouts.remainingUntil(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    all.remove(node);
					completeAllRequestsThatCanBeCompleted();
                    return false;
                }
			}
		} finally {
			monitor.unlock();
		}
	}
	
	public void release(int n) {
		monitor.lock();
        try {
            units += releasedUnits;
            completeAllRequestsThatCanBeCompleted();
        } finally {
            monitor.unlock();
        }
	}
	
	private void completeAllRequestsThatCanBeCompleted() {
		if(queue.isNotEmpty()) {
			while (queue.isNotEmpty() && units >= queue.getHeadValue().requestedUnits) {
				Request headRequest = queue.pull().value;
				headRequest.isDone = true;
				units -= headRequest.requestedUnits;
				headRequest.condition.signal();
			}
		} else {
			if(all.isNotEmpty() && units >= initial) {
				var req = all.pull().value;
				units = 0;
				req.isDone = true;
				req.condition.signal();
			}
		}
    }
}

/* *********** PC_2021v_2 *********** */

/*
1) implement

public class BatchExchanger<T> {
	public BatchExchanger(int batchSize);
	public Optional<List<T>> deliverAndWait(T msg, long timeout) throws InterruptedException;
}
*/

public class BatchExchanger<T> {
	
	private static class BatchBox<T> {
		public boolean isDone = false;
		public LinkedList<T> messages = new LinkedList<>();
	}
	
	private final NodeLinkedList<BatchBox<T>> threads = new NodeLinkedList<>();
	private final int batchSize;
	private BatchBox<T> currBox = new BatchBox();
	private final Object lock = new Object();
	
	public BatchExchanger(int batchSize) {
		this.batchSize = batchSize;
	}
	
	public Optional<List<T>> deliverAndWait(T msg, long timeout) throws InterruptedException {
		synchronized(lock) {
			//fast path
			if (timeout < 0) {
				throw new IllegalArgumentException("timeout must be >=0");
			}
			
			if (currBox.messages.size()+1 == batchSize) {
				currBox.messages.push(msg);
				currBox.isDone = true;
				var ret = currBox.messages;
				currBox = new Box();
				lock.notifyAll();
				return Optional.of(ret);
			}
			
			if (Timeouts.noWait(timeout)) {
				return Optional.empty();
			}
			//wait path
			long deadline = Timeouts.deadlineFor(timeoutInMs);
            long remaining = Timeouts.remainingUntil(deadline);
			var node = threads.enqueue(currBox);
			while(true) {
				try {
					lock.wait(remaining);
				} catch(InterruptedException e) {
					if(node.value.isDone) {
						Thread.currentThread().interrupt();
						return Optional.of(node.value.messages);
					}
					node.value.messages.remove(msg);
					threads.remove(node);
					throw e;
				}
				
				if(node.value.isDone) {
					return Optional.of(node.value.messages);
				}
				
				remaining = Timeouts.remainingUntil(deadline);
                if (Timeouts.isTimeout(remaining)) {
					node.value.messages.remove(msg);
                    threads.remove(node);
                    return Optional.empty();
                }
			}
		}
	}
}


/*
2) implement

public class NAryMessageQueue<T> {
	public NAryMessageQueue<T>()
	public void put(T msg);
	public Optional<List<T>> get(int n, long timeout) throws InterruptedException;
}
*/

public class NAryMessageQueue<T> {
	
	private static class Request<T> {
        public final int requestedMessages;
        public final Condition condition;
        public boolean isDone = false;
		public final List<T> msgs = new List<>();

        public Request(int requestedMessages, Lock monitor) {
            this.requestedMessages = requestedMessages;
            this.condition = monitor.newCondition();
        }
    }

    private final Lock monitor = new ReentrantLock();
    private final NodeLinkedList<Request> queue = new NodeLinkedList<>();
    private final NodeLinkedList<T> messages = new NodeLinkedList<>();
	
	public NAryMessageQueue<T>() {}
	
	public void put(T msg) {
		monitor.lock();
        try {
            messages.enqueue(msg);
            completeAllRequestsThatCanBeCompleted();
        } finally {
            monitor.unlock();
        }
	}
	
	public Optional<List<T>> get(int n, long timeout) throws InterruptedException {
		monitor.lock();
        try {

            if (timeoutInMs < 0) {
                throw new IllegalArgumentException("timeoutInMs must be >=0");
            }

            // fast-path (non wait-path)
            if (queue.isEmpty() && messages.getCount() >= n) {
                return Optional.of(getNMsgs(n));
            }

            if (Timeouts.noWait(timeoutInMs)) {
                return Optional.empty();
            }

            // wait-path
            long deadline = Timeouts.deadlineFor(timeoutInMs);
            long remaining = Timeouts.remainingUntil(deadline);
            NodeLinkedList.Node<Request> myNode = queue.enqueue(new Request(n, monitor));
            while (true) {
                try {
                    myNode.value.condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (myNode.value.isDone) {
                        Thread.currentThread().interrupt();
                        return Optional.of(myNode.value.msgs);
                    }
                    queue.remove(myNode);
                    completeAllRequestsThatCanBeCompleted();
                    throw e;
                }

                if (myNode.value.isDone) {
                    return Optional.of(myNode.value.msgs);
                }

                remaining = Timeouts.remainingUntil(deadline);
                if (Timeouts.isTimeout(remaining)) {
                    queue.remove(myNode);
                    completeAllRequestsThatCanBeCompleted();
                    return false;
                }
            }
        } finally {
            monitor.unlock();
        }
	}
	
	private void completeAllRequestsThatCanBeCompleted() {
        while (queue.isNotEmpty() && messages.getCount() >= queue.getHeadValue().requestedMessages) {
            Request headRequest = queue.pull().value;
            headRequest.isDone = true;
            headRequest.msgs = getNMsgs(headRequest.requestedMessages);
            headRequest.condition.signal();
        }
    }
	
	private List<T> getNMsgs(int n) {
		List<T> ret = new List<>();
		while(n > 0) {
			ret.add(messages.pull());
			n--;
		}
		return ret;
	}		
}
