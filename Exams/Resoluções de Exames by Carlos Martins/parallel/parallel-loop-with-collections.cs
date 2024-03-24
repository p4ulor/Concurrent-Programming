/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Parallel loop pattern over a collection
 *
 * Carlos Martins, December 2018
 *
 **/

using System;
using System.Diagnostics;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;


public class ParallelLoopPatternOverACollection {
	static int delay = 0;

	static void SpinFor(int count) {
		for (int i = 0; i < count * 10; i++)
			Interlocked.Increment(ref delay);
	}
	
	public static void Main() {
		const int LOWER = 0;
		const int HIGHER = 100000;
		var collection = Enumerable.Range(LOWER, HIGHER);
		
		long sum = 0;
		object _lock = new Object();
		Stopwatch sw = Stopwatch.StartNew(); 
		Parallel.ForEach(collection,
			() => 0L,
			(item, loopState, index, partial) => {
				//Console.WriteLine("v[{0}]: {1}", index, item);
				SpinFor(200);
				return partial + item;
			},
			(partial) => {
				lock(_lock) {
					sum += partial;
				}
			}
		);
		sw.Stop();
		Console.WriteLine("--parallel time with parallel aggregation: {0} ms, result: {1}", sw.ElapsedMilliseconds, sum);
		
		sw.Restart();
		sum = 0;
		Parallel.ForEach(collection, (item) => {
			SpinFor(200);
			lock(_lock) sum += item;
		 });
		sw.Stop();
		Console.WriteLine("--parallel time without parallel aggregagtion: {0} ms, result: {1}", sw.ElapsedMilliseconds, sum);
		
		sum = 0;
		sw.Restart();
		foreach (var i in collection) {
			SpinFor(200);
			sum += i;
		}
		sw.Stop();
		Console.WriteLine("--sequential time: {0} ms, result: {1}", sw.ElapsedMilliseconds, sum);
	}
} 