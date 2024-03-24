/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Dynamic task parallelism pattern
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Linq;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Diagnostics;

static class Sort {

	/**
	 * Sort an integer vector using the insertion sort algorithm.
	 */
	public static void InsertionSort(int[] v, int from, int to) {
		for (int i = from; i < to; i++) {
			// save v[i] to make a hole at index ihole
			int item = v[i];
			int ihole = i;
			// keep moving the hole to the next smaller index until
			// v[ihole - 1] is <= item
			while (ihole > from && v[ihole - 1] > item) {
				// move the hole to the next snaller index
				v[ihole] = v[ihole - 1];
				ihole--;
				//Thread.SpinWait(20);
			}
			// put the item in the hole
			v[ihole] = item;
		}
	}
	
	/**
	 * Swap the contents of the specified two array positions.
	*/
	private static void Swap(int[] v, int x, int y) {
		int tmp = v[x];
		v[x] = v[y];
		v[y] = tmp;
	}

	/**
	 * Partition a vector using first vector element as pivot.
	 */
	private static int Partition(int[] v, int from, int to) {
		int pivot = v[from];
		int i = from + 1, j = to - 1;
		while (i <= j) {
			if (v[i] <= pivot) { 
				i++;
			} else if (pivot < v[j]) {
				j--;
			} else {
				Swap(v, i, j);
				i++;
				j--;
			}
			Thread.SpinWait(10);		// simulate some computation time
		}
		v[from] = v[j];
		v[j] = pivot;
		return j;
	}
	
	/**
	 * Partition a vector using middle vector element as pivot.
	 */
	private static int Partition2(int[] v, int from, int to) {
		Swap(v, from, (from + to) / 2);	// move partition element to v[left];
		int pivot = from;
		for (int i = from + 1; i < to; i++) {	// partition
			if (v[i] < v[from])
				Swap(v, ++pivot, i);
			Thread.SpinWait(10);		// simulate some computation time
		}
		Swap(v, from, pivot);
		return pivot;
	}
			 
	
	/**
	 * Sort a vector using the quicksort algorithm without a threshold.
	 */
	private static void QuickSort(int[] v, int from, int to) {
		if (from < to - 1) {
			int pivot = Partition(v, from, to);
			QuickSort(v, from, pivot);
			QuickSort(v, pivot + 1, to); 
		}
	}
	
	/**
	 * Sort a vector using the quicksort algorithm using a threshold.
	 */	
	const int THRESHOLD = 20;
	private static void SequentialQuickSort(int[] v, int from, int to) {
		if (to - from <= THRESHOLD) {
			InsertionSort(v, from, to);
		} else {
			int pivot = Partition(v, from, to);
			SequentialQuickSort(v, from, pivot);
			SequentialQuickSort(v, pivot + 1, to); 
		}
	}
	
	/**
	 * Quicksort creating at most 2 ^ depthRemaining tasks
	 */
	private static void ParallelQuickSort(int[] v, int from,  int to, int depthRemaining) {
		if (to - from <= THRESHOLD) {
			InsertionSort(v, from, to);
		} else {
			int pivot = Partition(v, from, to);
			if (depthRemaining > 0) {
				/* 
				Parallel.Invoke(
						() => ParallelQuickSort(v, from, pivot, depthRemaining - 1),
						() => ParallelQuickSort(v, pivot + 1, to, depthRemaining - 1));
				
			
				*/
				Task lower = Task.Run(() => ParallelQuickSort(v, from, pivot, depthRemaining - 1));
                ParallelQuickSort(v, pivot + 1, to, depthRemaining - 1);
				lower.Wait();
			} else {
				ParallelQuickSort(v, from, pivot, 0);
				ParallelQuickSort(v, pivot + 1, to, 0);
			}
		}
	}

	private static void ParallelQuickSort(int[] v) {
		// quicksort creates at most the number of logical processors * 16 tasks
		ParallelQuickSort(v, 0, v.Length, Environment.ProcessorCount * 16);
	}
	
	/**
	 * Fills an int vector with random values.
	 */
	private static void FillRandom(int[] v, Random random) {
		for (int i = 0; i < v.Length; i++)
			v[i] = random.Next(Int32.MinValue, Int32.MaxValue);	
	}
	
	static void Main() {
		const int VSIZE = 10000000;
		int[] v = new int[VSIZE];
		Random random = new Random(Environment.TickCount);
		Stopwatch sw = new Stopwatch();
		
		/*
		FillRandom(v, random);
		sw.Restart();
		Console.Write("--start sorting using a sequential insertion sort algorithm...");
		InsertionSort(v, 0, v.Length);
		Console.WriteLine($"\b\b\b, time: {sw.ElapsedMilliseconds} ms");
		*/		

		/*
		FillRandom(v, random);
		sw.Restart();
		Console.Write("--start sorting using a sequential quick sort algorithm...");
		SequentialQuickSort(v, 0, v.Length);
		Console.WriteLine($"\b\b\b, time: {sw.ElapsedMilliseconds} ms");		
		*/
		
		FillRandom(v, random);
		sw.Restart();
		Console.Write("--start sorting using a parallel quick sort algorithm...");
		ParallelQuickSort(v);
		Console.WriteLine($"\b\b\b, time: {sw.ElapsedMilliseconds} ms");
		
		/*
		// display sorted vector
		foreach (int iv in v)
			Console.WriteLine("{0} ", iv);
		*/
	}
}
