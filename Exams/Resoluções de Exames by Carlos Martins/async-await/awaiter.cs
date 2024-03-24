/**
 * ISEL, LEIC, Concurrent Programming
 *
 * Asynchronous methods : Custom Awaiter
 *
 * Carlos Martins, November 2019
 *
 **/

using System;
using System.Threading;
using System.Threading.Tasks;
using System.Runtime.CompilerServices;


/**
 * Show messages with current thread ID
 */
static class Console_ {
	public static void WriteLine(String msg) {
		Console.WriteLine($"[#{Thread.CurrentThread.ManagedThreadId}]: {msg}");
	}
}

/**
 * A custom awaiter that resumes the async method 3 seconds after
 * the call to the OnCompleted() method and produce a result of 42.
 */
class WaitForAWhileAwaiter : INotifyCompletion {
	private Task delay;
	public bool IsCompleted {
		get {
			Console_.WriteLine("--IsCompleted.get() called");
//			return true;
			return delay != null ? delay.IsCompleted : false;
		}
	}

	// INotifyCompletion
	public void OnCompleted(Action continuation) {
		int start = System.Environment.TickCount;
		Console_.WriteLine("--OnCompleted() called, async method suspended for 3 seconds");
		delay = Task.Delay(3000).ContinueWith((ascendent) => {
			Console_.WriteLine("--async method continues, after " +
							  $"{System.Environment.TickCount - start} ms");
			continuation();
		});
	}

	public int GetResult() {
		Console_.WriteLine("--GetResult() called");
		return 42;
	}
}

/**
 * A custom awaiter source that will be used as "awaiter expression".
 */
class WaitForAWhileAwaiterSource {
	public WaitForAWhileAwaiter GetAwaiter() {
		Console_.WriteLine("--GetWaiter() called");
		return new WaitForAWhileAwaiter();
	}
}

public class CustomAwaiter {

	/**
	 * Asynchronous method that uses the custom awaiter.
	 */
	private static async Task<int> WaitForAWhileAsync() {
		Console_.WriteLine("--async method called");
		int result = await new WaitForAWhileAwaiterSource();
		return result;
	}
	public static void Main() {
		var asyncTask = WaitForAWhileAsync();
		Console_.WriteLine("--async method return");
		asyncTask.Wait();
		Console_.WriteLine($"--async method result: {asyncTask.Result}");
	}
}
