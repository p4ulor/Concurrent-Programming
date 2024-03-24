/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Pipelines pattern
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Collections.Concurrent;
using System.Threading;
using System.Threading.Tasks;
using System.Diagnostics;

public class Pipeline {
	
	// type to hold pipeline data
	internal class PipeData {
		internal Stopwatch timestamp;
		internal string data;
	}

	internal const int BUFFER_SIZE = 16;
	
	// stage delays
	internal const int FIRST_STAGE_DELAY = 200;
	internal const int SECOND_STAGE_DELAY = 50;
	internal const int THIRD_STAGE_DELAY = 80;
	
	internal const int PRODUCTIONS = BUFFER_SIZE;
	internal const int MIN_PRODUCTION_INTERVAL = 10;
	internal const int MAX_PRODUCTION_INTERVAL = 100;
	
	// producer/consumer queues
	internal static BlockingCollection<PipeData> input = 
						new BlockingCollection<PipeData>(BUFFER_SIZE);
	internal static BlockingCollection<PipeData> out1stIn2nd = 
						new BlockingCollection<PipeData>(BUFFER_SIZE);
	internal static BlockingCollection<PipeData> out2ndIn3rd = 
						new BlockingCollection<PipeData>(BUFFER_SIZE);
	internal static BlockingCollection<PipeData> output = 
						new BlockingCollection<PipeData>(BUFFER_SIZE);
	
	/**
	 * Simulate stage processing 1 - using a Consuming Enumerable.
	 */
	internal static void StageProcessing(String stageId, int delay,
										 BlockingCollection<PipeData> inc,
										 BlockingCollection<PipeData> outc,	
										 CancellationTokenSource cts) {
		Console.WriteLine($"-->stage: {stageId}");
		CancellationToken ctk = cts.Token;
		try {
			foreach (var d in inc.GetConsumingEnumerable()) {
				if (ctk.IsCancellationRequested) {
					break;		// exit pipeline state task
				}
				
				if (stageId.Equals("1") && new Random(Environment.TickCount).Next(100) == 99) {
					throw new Exception($"Boom! at stage {stageId}");
				}
				
				if (d.timestamp == default(Stopwatch)) {
					d.timestamp = Stopwatch.StartNew();
				}
				Console.WriteLine($"--{stageId}: '{d.data}'");
				Thread.Sleep(delay);
				d.data += "+" + stageId;
				outc.Add(d, ctk);
			}
		} catch (Exception ex) {
			// If an exception occurs, notify all other pipeline stages
			cts.Cancel();
			if (!(ex is OperationCanceledException))
				throw;
		} finally {
			Console.WriteLine($"<--stage: {stageId}");
			outc.CompleteAdding();
		}
	}

	/**
	 * Simulate stage processing 2 - without using the Consuming Enumerable.
	 */
	internal static void StageProcessing2(String stageId, int delay,
										  BlockingCollection<PipeData> inc,
		 								  BlockingCollection<PipeData> outc,
										  CancellationTokenSource cts) {
		Console.WriteLine($"-->stage: {stageId}");
		CancellationToken ctk = cts.Token;
		try {
			do {
				PipeData d = null;
				try {
					d = inc.Take();
				} catch (InvalidOperationException) {
					// the collection is empty and the method CompleaAdding() was called.
					// so, complete adding to the output collection and exit the stage task
					break;
				}
				if (ctk.IsCancellationRequested)
					break;		// exit pipeline state task
				if (d.timestamp == default(Stopwatch)) {
					d.timestamp = Stopwatch.StartNew();
				}
				Console.WriteLine($"--{stageId}: '{d.data}'");
				Thread.Sleep(delay);
				d.data += "+" + stageId;
				outc.Add(d, ctk);
			} while (true);
		} catch (Exception ex) {
			// If an exception ocuurs, notify all other pipeline stages
			cts.Cancel();
			if (!(ex is OperationCanceledException))
				throw;
		} finally {
			outc.CompleteAdding();
			Console.WriteLine($"<--stage: {stageId}");
		}		
	}

	public static void Main() {
		Random random = new Random(Environment.TickCount);
		
		// Pipeline tasks are LONG RUNNING tasks
		TaskFactory factory = new TaskFactory(TaskCreationOptions.LongRunning,
											  TaskContinuationOptions.None);
		CancellationTokenSource cts = new CancellationTokenSource();
		CancellationToken ctk = cts.Token;
		 
		var firstStage = factory.StartNew(() => {
			StageProcessing("1", FIRST_STAGE_DELAY, input, out1stIn2nd, cts);
		}, ctk);
  
		var secondStage = factory.StartNew(() => {
			StageProcessing2("2", SECOND_STAGE_DELAY, out1stIn2nd, out2ndIn3rd, cts);
		}, ctk);
        
		var thirdStage = factory.StartNew(() => {
			StageProcessing("3", THIRD_STAGE_DELAY, out2ndIn3rd, output, cts);
		}, ctk);
		
		var consumer = Task.Run(() => {
			int consumptions = 0;
			Stopwatch sw = Stopwatch.StartNew();
			foreach (var d in output.GetConsumingEnumerable()) {
				consumptions++;
				Console.WriteLine($"-->[consume delay: {sw.ElapsedMilliseconds} ms]; " +
								  $"total: {d.timestamp.ElapsedMilliseconds} ms; outdata: '{d.data}'");
				sw.Restart();
			}
			Console.WriteLine($"--consumer exits after consume {consumptions} data items");
		});

		// Start productions
		int productions = 0;
		do {
			Thread.Sleep(random.Next(MIN_PRODUCTION_INTERVAL, MAX_PRODUCTION_INTERVAL));
			if (ctk.IsCancellationRequested)
				break;
			if (Console.KeyAvailable) {
				if (Console.ReadKey(true).Key == ConsoleKey.Q) {
					cts.Cancel();
				}
				break;
			}
			input.Add(new PipeData { data = "indata" });
			productions++;		
		} while (true);
		input.CompleteAdding();
		Console.WriteLine($"--produced {productions} data items");
		
		try {
			Task.WaitAll(new Task[] { firstStage, secondStage, thirdStage, consumer });
		} catch (AggregateException ae) {
			Console.WriteLine($"***{ae.InnerException.GetType().Name}: {ae.InnerExceptions[0].Message}");
		}
		Console.WriteLine("--done");
	}
}