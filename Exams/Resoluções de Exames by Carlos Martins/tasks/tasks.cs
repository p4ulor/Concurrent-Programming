/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Using TaskFactory.StartNew() and Task.Run() with asynchronous lambdas
 *
 * Carlos Martins, November 2019
 *
 */

using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;

class Program {

	// using non-async lambda expressions
	public static void Main() {
		/**
		 * Hello, world task, using TaskFactory.StartNew and Task.Run factory methods.
		 */
		
		Task t = Task.Factory.StartNew((arg) => {
			Console.WriteLine($"Hello, world with TaskFactory.StartNew with \"{arg}\" argument!");
			Thread.Sleep(2000);
		}, "the-argument");
		Console.WriteLine("-- first task was created, wait until it completes");
		t.Wait();
		
		t = Task.Run(() => Console.WriteLine("Hello, world with Task.Run!"));
		Console.WriteLine("-- second task was created, wait until it completes");
		while (!t.IsCompleted)
			Thread.Sleep(10);
		
	}
	
	// using async lambda expressions
	public static void _Main() {
		
		/**
		 * differences between TaskFactory.StartNew() asnd Task.Run() when the task
		 * body is specified with an asynchronous delegate/lamdba
		 */
		
		// when using TaskFactory.StartNew() we must call Unwrap() explicity to get the inner task
		var t = Task.Factory.StartNew(async (arg) => {
			await Task.Delay(1000);
			Console.WriteLine($"TaskFactory.StartNew() created task with \"{arg}\" argument");
		}, "the-argument");
		t.Unwrap().Wait();
		
		// using Task.Run() we get already an unwraped task
		var tr = Task.Run(async () => {
			await Task.Delay(2000);
			Console.WriteLine("Task.Run() created task");
			return 42;
		}, CancellationToken.None);
		Console.WriteLine("task result: {0}", tr.Result);
		Console.WriteLine("main exiting...");
	}
}
