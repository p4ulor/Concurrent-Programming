/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Parallel Invoke
 *
 * Carlos Martins, January 2017
 *
 **/

using System;
using System.Threading;
using System.Threading.Tasks;

public class ParallelInvoke {
	
	// Execute explicity the task in parallel and wait until they terminate
	internal static void DirectParallelInvoke(CancellationToken ctk, params Action[] actions) {
		Task[] parallelTasks = new Task[actions.Length];
		
		for (int i = 0; i < actions.Length; i++)
			parallelTasks[i] = Task.Run(actions[i], ctk);
		
		try {
			Task.WaitAll(parallelTasks);
		} catch (AggregateException ae) {
			// filter task cancelled exceptions
			bool showCancellation = true;
			ae.Handle((ex) => {
				if (ex is TaskCanceledException) {
					if (showCancellation) {
						showCancellation = false;
						Console.WriteLine("***error: at least a task was cancelled");
					}
					return true;
				}
				return false;
			});				
		}
	}
	
	public static void Main() {
		
		CancellationTokenSource cts = new CancellationTokenSource();
		
		if ((new Random(Environment.TickCount)).Next(100) < 25)
			cts.Cancel();
	
		try {
			
			DirectParallelInvoke(
				cts.Token,
				//() => { throw new Exception("boom!"); },
				() => { Console.WriteLine("--one"); },
				() => Console.WriteLine("--two"),
				() => Console.WriteLine("--three"),
				() => Console.WriteLine("--four"),
				() => Console.WriteLine("--five"),
				() => Console.WriteLine("--six"),
				() => Console.WriteLine("--seven"),
				() => Console.WriteLine("--eight"),
				() => Console.WriteLine("--nine"),
				() => Console.WriteLine("--ten")
			);
			
			Console.WriteLine("---");
			Parallel.Invoke(
				new ParallelOptions() { CancellationToken = cts.Token,
						 			  MaxDegreeOfParallelism = Environment.ProcessorCount },
				//() => { throw new Exception("boom!"); },
				() => { Console.WriteLine("++one"); },
				() => Console.WriteLine("++two"),
				() => Console.WriteLine("++three"),
				() => Console.WriteLine("++four"),
				() => Console.WriteLine("++five"),
				() => Console.WriteLine("++six"),
				() => Console.WriteLine("++seven"),
				() => Console.WriteLine("++eight"),
				() => Console.WriteLine("++nine"),
				() => Console.WriteLine("++ten")
			);
					
		} catch (AggregateException ae){
			Console.WriteLine($"***{ae.InnerException.GetType().Name}: {ae.InnerException.Message}");
		} catch (OperationCanceledException oce) {
			Console.WriteLine($"***{oce.GetType().Name}: {oce.Message}");				
		}
	}
}
