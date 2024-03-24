/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Parallel loop pattern
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Threading;
using System.Threading.Tasks;

public class ParallelLoopPattern {
	static	int delay = 0;
	static void SpinFor(int count) {
		for (int i = 0; i < count * 1000; i++)
			Interlocked.Increment(ref delay);
	}
	
	internal static long Sum(long n, CancellationTokenSource cts) {
		CancellationToken token = cts.Token;
		
		ParallelOptions options = new ParallelOptions { CancellationToken = token };
		/* other properties of ParallelOptions: MaxDegreeOfParallelim, TaskScheduler */
		
		long sum = 0;
		ParallelLoopResult loopResult = Parallel.For(0, n, options, (i, loopState) => {

			Console.WriteLine($"-[#{Thread.CurrentThread.ManagedThreadId}]: {i}");

			// return if the for loop was cancelled, breaked or stopped
			if (loopState.ShouldExitCurrentIteration)
				return;
			
			SpinFor(10000);
			//throw new Exception("Opps!");
				
			if (/* break condition is true */ i == 100) {
				loopState.Break();
				return;
			}

			if (/* stop condition is true */ i == 100) {
				loopState.Stop();
				return;
			}
				
			// do something with shared data (needs synchronization)
			Interlocked.Add(ref sum, i + 1);
		});
		if (!loopResult.IsCompleted) {
			if (loopResult.LowestBreakIteration.HasValue) {
				Console.WriteLine("--loop encountered a break at {0}",
								   loopResult.LowestBreakIteration.Value);
			} else {
				Console.WriteLine("--loop was stopped");
			}
		}
		return sum;
	}
	
	public static void Main() {
		const int SUM_UP_TO = 100;
		CancellationTokenSource cts = new CancellationTokenSource();
		
		if ((new Random(Environment.TickCount)).Next(100) < 25)
			cts.Cancel();
		
		try { 
			Console.WriteLine($"--sum({SUM_UP_TO}): {Sum(SUM_UP_TO, cts)}");
		} catch (OperationCanceledException) {
			Console.WriteLine("***the operation was cancelled");
		} catch (AggregateException ae) {
			Console.WriteLine($"***Exception {ae.InnerException.GetType().Name}: {ae.InnerException.Message}");
		}
	}	
}