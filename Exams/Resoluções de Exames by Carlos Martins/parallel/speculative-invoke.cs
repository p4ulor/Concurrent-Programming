/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Speculative task invocation.
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;

/**
 * Speculative invocation example.
 */
public static class SpeculativeInvokeExample {

	public static R SpeculativeInvoke<R>(params Func<CancellationToken, R>[] bodies) {
		var cts = new CancellationTokenSource();
		var token = cts.Token;
		Task<R>[] tasks = new Task<R>[bodies.Length];
		
		// Start all tasks in parallel
		for (int i = 0; i < bodies.Length; i++) {
			var body = bodies[i];
			tasks[i] = Task.Run(() => body(token), token);
		}
 
		// Wait for the faster task to complete.
		int first = Task.WaitAny(tasks);
		
		// Cancel all the slower tasks.
		cts.Cancel();
 
		// Wait for cancellation to finish and observe exceptions.
		try {
			Task.WaitAll(tasks);
		} catch (AggregateException ae) {
			// Filter out the exception caused by cancellation itself
			ae.Flatten().Handle(e => e is OperationCanceledException);
		} finally {
			if (cts != null)
				cts.Dispose();
		}
		return tasks[first].Result;
	}

	/**
	 * A cancelable asynchronous processing.
	 */
	private async static Task<int> ComputeAsync(int arg, CancellationToken ctk) {
		await Task.Delay(arg * 1000, ctk);
		return arg;
	}
	
	public static void Main() {
		
		Stopwatch sw = Stopwatch.StartNew();
		try {
			int result = SpeculativeInvoke<int>(
				(ctk) => ComputeAsync(10, ctk).Result,
				(ctk) => ComputeAsync(2, ctk).Result,
				(ctk) => ComputeAsync(3, ctk).Result,
				(ctk) => ComputeAsync(5, ctk).Result
			);
			Console.WriteLine("--success: {0}", result);
		} catch (Exception ex) {
			Console.WriteLine($"***{ex.GetType().Name} : {ex.Message}");
		}
		Console.WriteLine($"--elapsed: {sw.ElapsedMilliseconds} ms");
	}
}

