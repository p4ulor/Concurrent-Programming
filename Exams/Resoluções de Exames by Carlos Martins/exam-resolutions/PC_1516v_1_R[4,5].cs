/**
 *
 * ISEL, LEIC, Concurrent Programming.
 *
 * Exam PC_1516v_1: resolution of exercises 4 and 5.
 *
 * Build executable with the command: csc PC_1516v_1_R[4,5].cs generic-async-result.cs
 *
 * Carlos Martins, June 2017
 *
 **/


using System;
using System.Linq;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using System.Net;

/**
 4.
A interface Services define os serviços síncronos disponibilizados por uma organização
que oferece a execução de diversos serviços em servidores localizados em diferentes áreas
geográficas (SaaS). O método PingServer responde a um pedido de ping, devolvendo o Uri do
respectivo servidor. O método ExecService executa, no servidor especificado através do
parâmetro server, o serviço especificado pelos tipos genéricos S (serviço) e R (resposta).
O método ExecOnNearServer usa as operações de Services para executar de forma síncrona o
serviço especificado, no servidor que primeiro responder ao serviço PingServer, isto é,
aquele que se considera ser o servidor mais próximo.

public class Exec{
	public interface Services<S,R> {
		Uri PingServer(Uri server);
		R ExecService(Uri server,S service);
	}

	public R ExecOnNearServer<S,R>(Services<S,R> svc, Uri[] servers, S service);
}

a.
A classe APMExec será a variante assíncrona de Exec ao estilo Asynchronous Programming Model (APM).
Implemente os métodos BeginExecOnNearServer e EndExecOnNearServerque usam a interface APMServices
(variante APM de Services que não tem de apresentar).

NOTA: não pode usar a TPL e só se admitem esperas de controlo dentro das operações End,
estritamente onde o APM o exige.

b.
A classe TAPExec será a variante assíncrona de Exec, ao estilo Task based Asynchronous Pattern (TAP).
Usando a funcionalidade oferecida pela Task Parallel Library (T P L) ou pelos métodos async do C#,
implemente o método ExecOnNearServerAsync, que usa a interface TAPServices (variante TAP de Services
que não tem de apresentar).
NOTA: na implementação não se admite a utilização de operações com bloqueios de controlo.
**/

public class Exec {
	public interface Services<S, R> {
		Uri PingServer(Uri server);
		R ExecService(Uri server, S service);
	}
	
	public R ExecOnNearServer<S, R>(Services<S, R> svc, Uri[] servers, S service) {
		return default(R);
	}
}

// 4.a

public class APMExec {
	public interface APMServices<S, R> {
		IAsyncResult BeginPingServer(Uri server, AsyncCallback ucb, object ust);
		Uri EndPingServer(IAsyncResult ar);
		IAsyncResult BeginExecService(Uri server, S service, AsyncCallback ucb, object ust);
		R EndExecService(IAsyncResult ar);
	}

	public IAsyncResult BeginExecOnNearServer<S, R>(APMServices<S, R> svc, Uri[] servers, S service,
													AsyncCallback ucb, object ust) {
		GenericAsyncResult<R> gar =  new GenericAsyncResult<R>(ucb, ust, false);
		int got = 0, failures = 0;
		
		AsyncCallback onPingServer = (ar) => {
			try {
				Uri uri = svc.EndPingServer(ar);		// get result and cleanup resources!
				if (Interlocked.Exchange(ref got, 1) == 0) {
					// this is the near server, so execute the service on it 
					svc.BeginExecService(uri, service, (ar2) => {
						try {
							gar.SetResult(svc.EndExecService(ar2));
						} catch (Exception ex) {
							gar.SetException(ex);
						}
					}, null);
				} else {
					;		// ignore response
				}
			} catch (Exception ex) {
				if (Interlocked.Increment(ref failures) == servers.Length) {
					// all servers failed to respond. So, finish global operation with
					// the last exception
					gar.SetException(ex); 
				}	
			}
		};
		// start ping on all servers
		for (int i = 0; i < servers.Length; i++) {
			svc.BeginPingServer(servers[i], onPingServer, null);
		}
		// return the global async result
		return gar;
	}

	public R EndExecOnNearServer<R>(IAsyncResult ar) {
		return ((GenericAsyncResult<R>)ar).Result;
	}
}

// 4.b

public class TAPExec {
	public interface TAPServices<S, R> {
		Task<Uri> PingServerAsync(Uri server);
		Task<R> ExecServiceAsync(Uri server, S service);
	}
	
	//---
	// Using bare tasks and continuations
	//---
	
	
	// chaining whitout considering exceptions that can occur in ping server
	public Task<R> ExecOnNearServerAsync<S, R>(TAPServices<S, R> svc, Uri[] servers, S service) {
		Task<Uri>[] pingTasks = new Task<Uri>[servers.Length];
		for (int i = 0; i < servers.Length; i++) {
			pingTasks[i] = svc.PingServerAsync(servers[i]);
		}
		return Task.Factory.ContinueWhenAny(pingTasks, (antecedent) => {
			// remove terminated task from the pingTasks array		
			pingTasks = pingTasks.Where(t => t != antecedent).ToArray();
			// observe and ignore any exceptions thrown by the abandoned tasks
			Task.Factory.ContinueWhenAll(pingTasks, (antecedents) => {
				try {
					Task.WaitAll(antecedents);
				} catch { /* at least log exception exceptins throw by ping */ }
			});
			return svc.ExecServiceAsync(antecedent.Result, service);
		}).Unwrap();
	}

	/**
	 *  Using TaskCompletionSource<TResult>
	 */

	// same struture as APM using simple continutaions and TaskCompletionSource<R>
	public Task<R> ExecOnNearServerAsync_2<S, R>(TAPServices<S, R> svc, Uri[] servers, S service) {
		// the task that represents the result
		TaskCompletionSource<R> tcs = new TaskCompletionSource<R>();
		int got = 0, failures = 0;
		
		// continuation set for all ping tasks
		Action<Task<Uri>> pingContinuation = (antecedent) => {
			try {
				Uri uri = antecedent.Result;		// get result from ping task
				if (Interlocked.Exchange(ref got, 1) == 0) {
					// this is the near server, so execute the service on it 
					svc.ExecServiceAsync(uri, service).ContinueWith((antecedent2) => {
						try {
							tcs.SetResult(antecedent2.Result);
						} catch (AggregateException ae) {
							tcs.SetException(ae.InnerException);
						}
					});
				} else {
					;		// ignore response
				}
			} catch (AggregateException ae) {
				/*** log thrown exception ***/
				
				// if we got an exception and there are still ping tasks to teminate,
				// we wait for the next one; otherwise complete the asynchronous operation 
				// with exception
				if (Interlocked.Increment(ref failures) == servers.Length)
					// all pings failed, complete asynchronous operation with exception
					tcs.SetException(ae.InnerException);
			}
		};

		for (int i = 0; i < servers.Length; i++)
			svc.PingServerAsync(servers[i]).ContinueWith(pingContinuation);
	
		// return theh Task<R> associated with the instance of TaskCompletionSource<R>,
		// that reºresents the ongoing asynchronous operation
		return tcs.Task;
	}

	// using TaskCompletionSource<R> and TaskFactory.ContinueWhenAny method
	public Task<R> ExecOnNearServerAsync_3<S, R>(TAPServices<S, R> svc, Uri[] servers, S service) {
		// the task that represents the result
		TaskCompletionSource<R> tcs = new TaskCompletionSource<R>();
		Task<Uri>[] pingTasks = new Task<Uri>[servers.Length];

		for (int i = 0; i < servers.Length; i++)
			pingTasks[i] = svc.PingServerAsync(servers[i]);
			
		Action<Task<Uri>> pingContinuation = null;
		pingContinuation = (antecedent) => {
			// remove terminated task from the pingTasks array		
			pingTasks = pingTasks.Where(t => t != antecedent).ToArray();
			try {
				Uri uri = antecedent.Result;
				// ping succeeded...
				if (pingTasks.Length > 0) {
					// ensure that exceptions thrown by the abandoned tasks are observed
					Task.Factory.ContinueWhenAll(pingTasks, (antecedents) => {
						try {
							Task.WaitAll(antecedents);
						} catch { /* at least log exception thrown by the remaining ping tasks */ }
					});
				}
				// execute service and setup a continuation to set result task
				svc.ExecServiceAsync(uri, service).ContinueWith((antecedent2) => {
					try {
						tcs.SetResult(antecedent2.Result);
					} catch (AggregateException ae) {
						tcs.SetException(ae.InnerException);
					}
				});
			} catch (AggregateException ae) {
				// if we got an exception and there are still ping tasks to teminate,
				// re-assert ContinuewhenAny; other fail global asynchronous operation
				if (pingTasks.Length > 0)
					// try to get URI from another server
					Task.Factory.ContinueWhenAny(pingTasks, pingContinuation);
				else
					tcs.SetException(ae.InnerException);
			}
		};
		// when the first ping task completes, execute ping continuation
		Task.Factory.ContinueWhenAny(pingTasks, pingContinuation);
	
		// return theh Task<R> associated with the instance of TaskCompletionSource<R>,
		// that reºresents the ongoing asynchronous operation
		return tcs.Task;
	}
	
	/**
	 * Using C# async methods
	 */
	
	// chaining whitout considering exceptions that can occur in ping server
	public async Task<R> ExecOnNearServerAsync_4<S, R>(TAPServices<S, R> svc, Uri[] servers, S service) {
		Task<Uri>[] pingTasks = new Task<Uri>[servers.Length];
		for (int i = 0; i < servers.Length; i++)
			pingTasks[i] = svc.PingServerAsync(servers[i]);
/*
Warning CS4014: Because this call is not awaited, execution of the current method continues
before the call is completed. Consider applying the 'await' operator to the result of the call.
*/
#pragma warning disable 4014
		// this ensures that the result of all ping tasks are observed.
		Task.Factory.ContinueWhenAll(pingTasks, (antecedents) => {
			try { Task.WaitAll(antecedents); } catch { /*** log any thrown exceptions ***/ }
		});
#pragma warning restore 4014
		
		// return service result
		return await svc.ExecServiceAsync(await Task.WhenAny(pingTasks).Unwrap(), service);
	}
	
	// now, considering the exceptions that can occur when we ping the servers
	
	public async Task<R> ExecOnNearServerAsync_5<S, R>(TAPServices<S, R> svc, Uri[] servers, S service) {
		Task<Uri>[] pingTasks = new Task<Uri>[servers.Length];
		for (int i = 0; i < servers.Length; i++)
			pingTasks[i] = svc.PingServerAsync(servers[i]);
		
		// await until we got a ping task that terminates with success 
		do {
			var pingTask = await Task.WhenAny(pingTasks);
			// remove completed ping task from the array
			pingTasks = pingTasks.Where(t => t != pingTask).ToArray();
			try {
				var uri = pingTask.Result;
				// here we have the URI of one of the servers
#pragma warning disable 4014	
				// this ensures that the result of all ping tasks are observed.
				Task.Factory.ContinueWhenAll(pingTasks, (antecedents) => {
					try { Task.WaitAll(antecedents); } catch { /*** log any thrown exception ***/ }
				});
#pragma warning restore 4014
				
				// return the result of the service execution or exception
				return await svc.ExecServiceAsync(pingTask.Result, service);
			} catch (AggregateException ae) {
				if (pingTasks.Length == 0)
					throw ae.InnerException;
				// try get another server
			}
		} while (true);
	}	
}

/***
5.
No método MapAggregate, apresentado a seguir, as invocações a Map podem decorrer em paralelo, o que
seria vantajoso já que é nessa operação se concentra a maior componente de processamento. O método
Aggregate implementa a operação, comutativa e associativa, que agrega os resultados, sendo o respectivo
elemento neutro produzindo pela expressão new Result(). A operação de agregação pode retornar overflow,
situação em que o método MapAggregatedeverá retornar rapidamente essa indicação. Tirando partido da
Task Parallel Library, apresente uma versão do método MapAggregate que faça invocações paralelas ao método
Map de modo a tirar partido de todos os cores de processamento disponíveis.
NOTA: considere que o método  Aggregate retorna  Result.OVERFLOW quando um dos seus argumentos já for
overflow.
 
public static Result MapAggregate(IEnumerable<Data> data) {
	Result result = newResult();
	foreach (var datum in data) {
		result = Aggregate(result, Map(datum));
		if (result.Equals(Result.OVERFLOW))
			break;
	 }
	return result;
}

***/

class ParallelMapAggregate_ {
	
	internal class Result {
		internal int value;
		internal static Result OVERFLOW = new Result(-1);
		internal Result(int v) {
			value = v;
		}
		internal Result() : this(0) {}
	}

	internal class Data {
		internal int value;
		
		internal Data(int v) { value = v; }
	}
	
	internal static Result Map(Data datum) {
		Thread.SpinWait(100000);
		return new Result(datum.value);
	}
	
	internal static Result Aggregate(Result r, Result r2) {
		if (r.value > 50000)
			return Result.OVERFLOW;
		return new Result(r.value + r2.value);
	}
	
	public static Result MapAggregate(IEnumerable<Data> data) {
	  Result result = new Result();
	  foreach (var datum in data) {
	    result = Aggregate(result, Map(datum));
	    if (result.Equals(Result.OVERFLOW))
			break;
	  }
	  return result;
	}
	
	public static Result ParallelMapAggregate(IEnumerable<Data> data) {
		Result total = new Result();
		bool stop = false;
		object _lock = new object();
		Parallel.ForEach( data,
				() => new Result(),
				(datum, loopState, partial) => {
					if (Volatile.Read(ref stop))
						return partial;
					partial = Aggregate(partial, Map(datum));
					if (partial.Equals(Result.OVERFLOW)) {
						Volatile.Write(ref stop, true);
						loopState.Stop();
					}
					return partial;
				},
				(partial) => {
					lock (_lock) {
						total = Aggregate(total, partial);
						if (total.Equals(Result.OVERFLOW)) {
							Volatile.Write(ref stop, true);
						}
					}
				});
		return total;
	}		
	
	public static void Test() {
		const int COUNT = 1000;
		Random random = new Random(Environment.TickCount);
		
		Data[] data = new Data[COUNT];
		
		for (int i = 0; i < COUNT; i++)
			data[i] = new Data(i + 1);
		Result r = ParallelMapAggregate(data);
		Console.WriteLine("result: {0}", r.value);
		
	}
}

public class PC_1516iv {
	public static void Main() {
	}
}