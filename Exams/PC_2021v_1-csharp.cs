/* *********** PC_2021v_1 *********** */

/*
3) a. implement; b. Add Cancellation Token;

public class BroadcastBox<T>
{
	public Task<T> WaitForMessageAsync();
	public int SentToAll(T message);
}

*/

// a.
public class BroadcastBox<T>
{
	private class Box : TaskCompletionSource<T> {
		public Box() 
		: base(TaskCompletionSource.RunContinuationsAsynchronously)
		{
			
		}
	}
	
	private int waiters = 0;
	private readonly object _lock = new object();
	private Box _currBox = new Box();
	
	public Task<T> WaitForMessageAsync() {
		lock(_lock) {
			++waiters;
			return _currBox.Task;
		}
	}
	
	public int SentToAll(T message) {
		lock(_lock) {
			_currBox.SetResult(message);
			_currBox = new Box();
			int ret = waiters;
			waiters = 0;
			return ret;
		}
	}
}

// b.
public class BroadcastBox<T>
{
	private class Box : TaskCompletionSource<T> {
		public bool Done;
		public CancellationTokenRegistration? CancellationTokenRegistration;
		public Box() 
		: base(TaskCompletionSource.RunContinuationsAsynchronously)
		{
			
		}
	}
	
	private readonly Action<object?> _cancellationCallback = 
		node => {
            TryCancel((LinkedListNode<Request>) node!);
        };
		
	private readonly object _lock = new object();
	private readonly LinkedList<Box> _boxes = new LinkedList<Box>();
	
	public Task<T> WaitForMessageAsync(CancellationToken ct) {
		lock(_lock) {
			var req = new Box();
			var reqNode = _boxes.AddLast(req);
			if(ct.CanBeCanceled)
			{
				reqNode.CancellationTokenRegistration = ct.Register(_cancellationCallback, reqNode);
			}
			return req.Task;
		}
	}
	
	public int SentToAll(T message) {
		LinkedList<Box?> boxesToComplete;
		lock(_lock) 
		{
			boxesToComplete = ReleaseAllBoxes();
		}
		CompleteAll(boxesToComplete, message);
	}
	
	private LinkedList<Box>? ReleaseAllBoxes() 
	{
		Debug.Assert(Monitor.IsEntered(_lock), "Lock MUST be held");
		LinkedList<Box>? boxes = null;
		var firstNode = _boxes.First;
		while (firstNode != null)
		{
			_boxes.RemoveFirst();
			firstNode.Value.Done = true;
			
			queue ??= new LinkedList<Box>();
			queue.AddLast(firstNode);
			
			firstNode = _boxes.First;
		}
		return boxes;
	}
	
	private void CompleteAll(LinkedList<Request>? boxesToRelease, T message)
	{
		if (boxesToRelease != null)
		{
			Debug.Assert(!Monitor.IsEntered(_lock), "Lock must NOT be held");
			foreach (var box in boxesToRelease)
			{
				box.CancellationTokenRegistration?.Dispose();
				request.SetResult(message);
			}
		}
	}
	
	private void TryCancel(LinkedListNode<Request> node)
	{
		lock (_lock)
		{
			if (node.Value.Done)
			{
				return;
			}
			node.Value.Done = true;
			_boxes.Remove(node);
		}

		node.Value.SetCanceled();
	}
}

/*
4) do method Async

public T Oper<T>(T[] xs, T initial)
	{
	var acc = initial;
	for (var i = 0; i < xs.Length; ++i)
	{
		var ai = A(xs[i]);
		acc = E(D(B(ai), C(ai)), acc);
	}
	return acc;
}
*/

public async Task<T> OperAsync<T>(T[] xs, T initial) 
{
	Task<T> tds = new Task<T>[xs.length];
	for (var i = 0 ; i < xs.length ; ++i) {
		tds[i] = Aux(xs[i]);
	}
	var acc = inicial;
	for (var i = 0 ; i < tds.length ; ++i) {
		acc = await EAsync(await tds[i], acc);
	}
	return acc;
}

async Task<T> Aux(T ai) 
{
	var ai = await AAsync(ai);
	var tb = BAsync(ai);
	var tc = CAsync(ai);
	return await DAsync(await tb, await tc);
}

/* *********** PC_2021v_2 *********** */

/*
3) a. implement; b. Add Cancellation Token;

public class ManualResetEvent
{
	public Task WaitAsync();
	public void Set();
	public void Clear();
}

*/

// a.
public class ManualResetEvent
{
	private readonly object _lock = new object();
	private bool _state = false;
	private TaskCompletionSource ts = new TaskCompletionSource(-.RunContinuationsAsynchronously);
	
	public Task WaitAsync() {
		lock(_lock) {
			if(_state) {
				return Task.CompletedTask;
			}
			if(ts == null) {
				ts = new TaskCompletionSource(-.RunContinuationsAsynchronously);
			}
			return ts.Task;
		}
	}
	
	public void Set() {
		lock(_lock) {
			_state = true;
			ts.SetResult();
			ts = null;
		}
	}
	
	public void Clear() {
		lock(_lock) {
			_state = false;
		}
	}
}

// b.

public class ManualResetEvent
{
	private class Waiters : TaskCompletionSource {
		public bool Done;
		public CancellationTokenRegistration? CancellationTokenRegistration;
		public Waiters() 
		: base(-.RunContinuationsAsynchronously)
		{
			
		}
	}
	
	private readonly Action<object?> _cancellationCallback = 
		node => {
            TryCancel((LinkedListNode<Request>) node!);
        };
	
	private readonly object _lock = new object();
	private bool _state = false;
	private LinkedList<Waiters> _waiters = new LinkedList();
	
	public Task WaitAsync(CancellationToken ct) {
		lock(_lock) {
			if(_state) {
				return Task.CompletedTask;
			}
			var waiter = new Waiters();
			var waiterNode = _waiters.AddLast(waiter);
			if(ct.CanBeCanceled)
			{
				waiterNode.CancellationTokenRegistration = ct.Register(_cancellationCallback, waiterNode);
			}
			return waiter.Task;
		}
	}
	
	public void Set() {
		LinkedList<Waiters?> ToComplete;
		lock(_lock) 
		{
			_state = true;
			ToComplete = ReleaseAll();
		}
		CompleteAll(ToComplete);
	}
	
	public void Clear() {
		lock(_lock) {
			_state = false;
		}
	}
	
	private LinkedList<Waiters>? ReleaseAll() 
	{
		Debug.Assert(Monitor.IsEntered(_lock), "Lock MUST be held");
		LinkedList<Waiters>? ret = null;
		var firstNode = _waiters.First;
		while (firstNode != null)
		{
			_waiters.RemoveFirst();
			firstNode.Value.Done = true;
			
			ret ??= new LinkedList<Waiters>();
			ret.AddLast(firstNode);
			
			firstNode = _waiters.First;
		}
		return ret;
	}
	
	private void CompleteAll(LinkedList<Waiters>? toRelease)
	{
		if (toRelease != null)
		{
			Debug.Assert(!Monitor.IsEntered(_lock), "Lock must NOT be held");
			foreach (var waiter in toRelease)
			{
				waiter.CancellationTokenRegistration?.Dispose();
				waiter.SetResult();
			}
		}
	}
	
	private void TryCancel(LinkedListNode<Waiters> node)
	{
		lock (_lock)
		{
			if (node.Value.Done)
			{
				return;
			}
			node.Value.Done = true;
			_waiters.Remove(node);
		}

		node.Value.SetCanceled();
	}
}

/*
4) do method Async

public static T[] Oper<T>(T[] xs, T[] ys)
{
	if(xs.Length != ys.Length) throw new ArgumentException("lengths must be equal");
	var res = new T[xs.Length];
	for (var i = 0; i < xs.Length; ++i)
	{
		res[i] = B(A(xs[i]), A(ys[i]));
	}
	return res;
}
*/

public async Task<T>[] OperAsync<T>(T[] xs, T[] ys) 
{
	if(xs.Length != ys.Length) throw new ArgumentException("lengths must be equal");
	Task<T> tds = new Task<T>[xs.length];
	for (var i = 0 ; i < xs.length ; ++i) {
		tds[i] = Aux(xs[i], ys[i]);
	}
	return await tds;
}

async Task<T> Aux(T xi, T yi) 
{
	var axi = AAsync(xi);
	var ayi = AAsync(yi);
	return await BAsync(await axi, await ayi);
}

