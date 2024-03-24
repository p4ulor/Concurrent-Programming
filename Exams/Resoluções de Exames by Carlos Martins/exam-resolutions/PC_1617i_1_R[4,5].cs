using System;
using System.Linq;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using System.Net;
using System.Diagnostics;

// 4.
public class A { public int Value; }
public class B { public int Value; }
public class C { public int Value; }
public class D { public int Value; }

public class Execute {
  public interface Services {
    A Oper1();
    B Oper2(A a);
    C Oper3(A a);
    D Oper4(B b, C c);
  }
 
  public static D Run(Services svc) {
    var a = svc.Oper1();
    return svc.Oper4(svc.Oper2(a), svc.Oper3(a)); 
  }
}

public class APMExecute {
	public interface APMServices {
		
		IAsyncResult BeginOper1(AsyncCallback ucb, object ust);
		A EndOper1(IAsyncResult ar);
		
		IAsyncResult BeginOper2(A a, AsyncCallback ucb, object ust);
		B EndOper2(IAsyncResult ar);

		IAsyncResult BeginOper3(A a, AsyncCallback ucb, object ust);
		C EndOper3(IAsyncResult ar);

		IAsyncResult BeginOper4(B b, C c, AsyncCallback ucb, object ust);
		D EndOper4(IAsyncResult ar);
	}
  
  	// Tap servives implementation
	public class APMServicesImpl : APMServices {
		
		IAsyncResult APMServices.BeginOper1(AsyncCallback ucb, object ust) {
			var gar = new GenericAsyncResult<A>(ucb, ust, false);
			Task.Delay(500).ContinueWith((_) => {
				gar.SetResult(new A(){ Value = 1 });
			});
			return gar;
		}
		
		A APMServices.EndOper1(IAsyncResult ar) {
			return ((GenericAsyncResult<A>)ar).Result;
		}
		
		IAsyncResult APMServices.BeginOper2(A a, AsyncCallback ucb, object ust) {
			var gar = new GenericAsyncResult<B>(ucb, ust, false);
			Task.Delay(500).ContinueWith((_) => {
				gar.SetResult(new B(){ Value = a.Value + 1 });
			});
			return gar;
		}

		B APMServices.EndOper2(IAsyncResult ar) {
			return ((GenericAsyncResult<B>)ar).Result;
		}
		
		IAsyncResult APMServices.BeginOper3(A a, AsyncCallback ucb, object ust) {
			var gar = new GenericAsyncResult<C>(ucb, ust, false);
			Task.Delay(500).ContinueWith((_) => {
				gar.SetResult(new C(){ Value = a.Value + 2 });
			});
			return gar;
		}
		
		C APMServices.EndOper3(IAsyncResult ar) {
			return ((GenericAsyncResult<C>)ar).Result;
		}
		
		IAsyncResult APMServices.BeginOper4(B b, C c, AsyncCallback ucb, object ust) {
			var gar = new GenericAsyncResult<D>(ucb, ust, false);
			Task.Delay(500).ContinueWith((_) => {
				gar.SetResult(new D(){ Value = b.Value + c.Value });
			});
			return gar;
		}
		
		D APMServices.EndOper4(IAsyncResult ar) {
			return ((GenericAsyncResult<D>)ar).Result;
		}
	}
	
	public static IAsyncResult BeginRun(APMServices svc, AsyncCallback ucb, object ust) {
		var gar = new GenericAsyncResult<D>(ucb, ust, false);
		int count = 2;
		AsyncCallback onOper4Completed = ar => {
			try {
				gar.SetResult(svc.EndOper4(ar));
			} catch (Exception ex) {
				gar.SetException(ex);
			}
		};
		B b = null;
		C c = null;
		AsyncCallback onOper1Completed = (ar) => {
			try {
				var a = svc.EndOper1(ar);
				svc.BeginOper2(a, (ar2) => {
					try {
						Volatile.Write(ref b, svc.EndOper2(ar2));
						if (Interlocked.Decrement(ref count) == 0) {
							svc.BeginOper4(b, Volatile.Read(ref c), onOper4Completed, null);
						}
					} catch (Exception ex) {
						gar.TrySetException(ex);
					}
				}, null);
				svc.BeginOper3(a, (ar2) => {
					try {
						Volatile.Write(ref c, svc.EndOper3(ar2));
						if (Interlocked.Decrement(ref count) == 0) {
							svc.BeginOper4(Volatile.Read(ref b), c, onOper4Completed, null);
						}
					} catch (Exception ex) {
						gar.TrySetException(ex);
					}
				}, null);
			} catch (Exception ex) {
				gar.SetException(ex);
			}
		};
		svc.BeginOper1(onOper1Completed, null);
		return gar;
	}
	
	public static D EndRun(IAsyncResult ar) {
		return ((GenericAsyncResult<D>)ar).Result;
	}
	
	
	public static void Test() {
		Stopwatch sw = Stopwatch.StartNew();
		var ar= BeginRun(new APMServicesImpl(), null, null);
		Console.WriteLine("--elapsed until return: {0}", sw.ElapsedMilliseconds);
		ar.AsyncWaitHandle.WaitOne();
		Console.WriteLine("--elapsed until completed: {0}, result: {1}", sw.ElapsedMilliseconds, EndRun(ar).Value);
	}
}

public class TAPExecute {
	public interface TAPServices {
		Task<A> Oper1Async();
		Task<B> Oper2Async(A a);
		Task<C> Oper3Async(A a);
		Task<D> Oper4Async(B b, C c);
	}
  
  	// Tap servives implementation
	public class TAPServicesImpl : TAPServices {
		
		Task<A> TAPServices.Oper1Async() {
			var tcs = new TaskCompletionSource<A>();
			Task.Delay(500).ContinueWith((_) => {
				tcs.SetResult(new A(){ Value = 1 });
			});
			return tcs.Task;
		}
		
		Task<B> TAPServices.Oper2Async(A a) {
			var tcs = new TaskCompletionSource<B>();
			Task.Delay(500).ContinueWith((_) => {
				tcs.SetResult(new B(){ Value = a.Value + 1 });
			});
			return tcs.Task;
		}
		
		Task<C> TAPServices.Oper3Async(A a) {
			var tcs = new TaskCompletionSource<C>();
			Task.Delay(500).ContinueWith((_) => {
				tcs.SetResult(new C(){ Value = a.Value + 2 });
			});
			return tcs.Task;
		}
		
		Task<D> TAPServices.Oper4Async(B b, C c) {
			var tcs = new TaskCompletionSource<D>();
			Task.Delay(500).ContinueWith((_) => {
				tcs.SetResult(new D(){ Value = b.Value + c.Value });
			});
			return tcs.Task;
		}
	}
	
	public static Task<D> RunAsync(TAPServices svc) {
		var t2_t3 = svc.Oper1Async().ContinueWith((t1) => {
			var t2 = svc.Oper2Async(t1.Result);
			var t3 = svc.Oper3Async(t1.Result);
			return Task.Factory.ContinueWhenAll(new Task[] { t2, t3 }, (__) => {
						return new Tuple<B, C>(t2.Result, t3.Result);
				});
		}).Unwrap();
		return t2_t3.ContinueWith((_) => {
			return svc.Oper4Async(t2_t3.Result.Item1, t2_t3.Result.Item2);
		}).Unwrap();
	}

	public static Task<D> RunAsync1(TAPServices svc) {
		return svc.Oper1Async().ContinueWith((t1) => {
			var t2 = svc.Oper2Async(t1.Result);
			var t3 = svc.Oper3Async(t1.Result);
			return Task.Factory.ContinueWhenAll(new Task[] { t2, t3 }, (_) => {
						return new Tuple<B, C>(t2.Result, t3.Result);
			});
			}).Unwrap().ContinueWith((t2_t3) => {
				return svc.Oper4Async(t2_t3.Result.Item1, t2_t3.Result.Item2);
			}).Unwrap();
	}

	public static Task<D> RunAsync2(TAPServices svc) {
		var tcs = new TaskCompletionSource<D>();
		int count = 2;
		Task<B> t2 = null;
		Task<C> t3 = null;
		svc.Oper1Async().ContinueWith( t1 => {
			(t2 = svc.Oper2Async(t1.Result)).ContinueWith( _ => {
				if (Interlocked.Decrement(ref count) == 0) {
					svc.Oper4Async(t2.Result, t3.Result).ContinueWith( t4 => tcs.SetResult(t4.Result));
				}
			});
			(t3 = svc.Oper3Async(t1.Result)).ContinueWith( _ => {
				if (Interlocked.Decrement(ref count) == 0) {
					svc.Oper4Async(t2.Result, t3.Result).ContinueWith( t4 => tcs.SetResult(t4.Result));
				}				
			});
		});
		return tcs.Task;
	}

	public static async Task<D> RunAsync3(TAPServices svc) {
		var a = await svc.Oper1Async();
		var t2 = svc.Oper2Async(a);
		var t3 = svc.Oper3Async(a);		
		return await svc.Oper4Async( await t2, await t3);
	}

	
	public static void Test() {
		Stopwatch sw = Stopwatch.StartNew();
		var t = RunAsync3(new TAPServicesImpl());
		Console.WriteLine("--elapsed until return: {0}", sw.ElapsedMilliseconds);
		t.Wait();
		Console.WriteLine("--elapsed until completed: {0}, result: {1}", sw.ElapsedMilliseconds, t.Result.Value);
	}
}

// 4.a
	
// 5.

public static class Question5 {

	public static List<O> MapSelectedItems<I,O>(IEnumerable<I> items, Predicate<I> selector,
	                                     Func<I,O> mapper, CancellationToken ctoken) {
	  var result = new List<O>();
	  foreach(I item in items) {
	    ctoken.ThrowIfCancellationRequested();
	    if (selector(item)) result.Add(mapper(item));
	  }
	  return result;
	}


	public static List<O> ParallelMapSelectedItems<I,O>(IEnumerable<I> items, Predicate<I> selector,
		 											    Func<I, O> mapper, CancellationToken ctk) {
		var result = new List<O>();
		Parallel.ForEach(items, new ParallelOptions { CancellationToken = ctk },
			// the local initial partial result
			() => new List<O>(),
			// the loop body
			(item, loopState, partial) => {
				if (ctk.IsCancellationRequested) { /* loopState.ShoulExitIteration */
					// exit this iteration
					return partial; 
				}
				Thread.SpinWait(10000);
				if (selector(item)) {
					partial.Add(mapper(item));
				}
				return partial; 
			},
			(partial) => {
				lock(result) {
					result.AddRange(partial);
				}
			}
		);
		return result;
	}
}

// Main

public class PC_1617i_1 {
	public static void Main() {
//		TAPExecute.Test();
//		APMExecute.Test();
		CancellationTokenSource cts = new CancellationTokenSource(20000);
		try {
			Stopwatch sw = Stopwatch.StartNew();
			var result = Question5.MapSelectedItems<int,int>(Enumerable.Range(0, 200000), i => i >= 50000, i => i - 500,
														     cts.Token);
			/*
			var result = Question5.ParallelMapSelectedItems<int,int>(Enumerable.Range(0, 200000), i => i >= 50000, i => i - 500,
														             cts.Token);
			*/
			Console.WriteLine("count: {0}, elapsed: {1} ms", result.Count, sw.ElapsedMilliseconds);
		} catch (OperationCanceledException) {
			Console.WriteLine("***Operation was canceled!");
		}
	}
}