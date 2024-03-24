/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Parallel aggregation pattern
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Threading;
using System.Threading.Tasks;

public class ParallelAggregationPattern {
	
	private static long Sum(long n, CancellationTokenSource cts) {
		CancellationToken token = cts.Token;
		
		var options = new ParallelOptions { CancellationToken = token };
		long sum = 0;
		object _lock = new object();
		
		var loopResult = Parallel.For(0, n, options,
			// The local initial partial result 
			() => 0L,
			
			// the loop body
			(i, loopState, partialResult) => {
				// ... optionally check if cancellation, break or stop happened, 
				if (loopState.ShouldExitCurrentIteration) {
					//... optonally exit this iteration early
					return partialResult;
				}	
				
				//throw new Exception("Boom!");
				
				if (/* break condition is true */ i == 100) {
					loopState.Break();
					return partialResult;
				}

				if (/* stop condition is true */ i == 100) {
					loopState.Stop();
					return partialResult;
				}
				
				// compute partial result
				partialResult += i + 1;

				return partialResult;
			},
			// the final step called for each local partial result
			(localPartialSum) => {
				// enforce serial access to single, shared result
				//Interlocked.Add(ref sum, localPartialSum);
				lock(_lock)
					sum += localPartialSum;
			}
		);
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
			Console.WriteLine("***sum was canceled!");
		} catch (AggregateException ae) {
			Console.WriteLine($"***{ae.InnerException.GetType()}: {ae.InnerException.Message}");
		}
	}	
}
