/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Futures pattern
 *
 * Carlos Martins, December 2019
 *
 **/

using System;
using System.Diagnostics;
using System.Threading;
using System.Threading.Tasks;

/**
 * Input: a
 * F1(a) -> b
 * F2(a) -> c
 * F3(a) -> d
 * F4(b, c) -> e 
 * F5(d, e) -> f (final result)
 */


public class FuturePattern {
 
    public static void SpinFor(int count) {
        SpinWait spinner = new SpinWait();
        for (int i = 0; i < 2000; i++)
            spinner.SpinOnce();
    }

    public static int func1(int arg) {
        SpinFor(2000);
        Console.WriteLine($"--func1({arg}): {arg + 1}");
        return arg + 1;
    }

    public static int func2(int arg) {
        SpinFor(2000);
        Console.WriteLine($"--func2({arg}): {arg + 2}");
        return arg + 2;
    }

    public static int func3(int arg) {
        SpinFor(2000);
        Console.WriteLine($"--func3({arg}): {arg + 3}");
        return arg + 3;
    }
    public static int func4(int arg1, int arg2) {
        SpinFor(2000);
        Console.WriteLine($"--func4({arg1}, {arg2}): {arg1 + arg2}");
        return arg1 + arg2;
    }

    public static int func5(int arg1, int arg2) {
        SpinFor(2000);
        Console.WriteLine($"--func5({arg1}, {arg2}): {arg1 * arg2}");
        return arg1 * arg2;
    }

    public static int ComputeSequential(int a, Func<int, int> f1, Func<int, int> f2,
                       Func<int, int> f3, Func<int, int, int> f4, Func<int, int, int> f5) {
        int b = f1(a);
        int c = f2(a);
        int d = f3(a);
        int e = f4(b, c);
        return f5(d, e);
    }
    
    public static int ComputeParallel(int a, Func<int, int> f1, Func<int, int> f2,
                         Func<int, int> f3, Func<int, int, int> f4, Func<int, int, int> f5) {
        Task<int> cTask = Task.Run(() => f2(a));
        Task<int> dTask = Task.Run(() => f3(a));
        int b = f1(a);
        int e = f4(b, cTask.Result);
        return f5(e, dTask.Result);
    }

    public static void Main() {
        Stopwatch sw = Stopwatch.StartNew();
        int r = ComputeSequential(1, func1, func2, func3, func4, func5);
        sw.Stop();
        Console.WriteLine($"--compute sequential: result: {r}, after {sw.ElapsedMilliseconds} ms");
        sw.Restart();
        r = ComputeParallel(1, func1, func2, func3, func4, func5);
        sw.Stop();
        Console.WriteLine($"--compute parallel: result: {r}, after {sw.ElapsedMilliseconds} ms");
    }
}
