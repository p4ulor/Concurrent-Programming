/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Resolution of exercise 4 and 5, PC_1516i_1
 *
 * Build executable with the command: csc PC_1516i_1_R[4,5].cs generic-async-result.cs
 *
 * Carlos Martins, June 2017
 *
 **/

using System;
using System.Linq;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using System.Diagnostics;

/**
4.
A interface Users.Service disponibiliza serviços base de um sistema de gestão de utilizadores,
apenas em versões assíncronas, quer no modelo Asynchronous Programming Model (APM), quer no
modelo Task-based Asynchronous Pattern (TAP). A classe Users disponibiliza ainda a operação
composta GetUserAvatarAsync, também assíncrona, que invoca dois serviços assíncronos em
sequência. No entanto, verifica-se que o uso desta operação resulta num consumo considerável
de recursos nos servidores do sistema, principalmente nos momentos em que há mais pedidos em
curso simultaneamente. 

	public class Users {
		public interface Service {
			IAsyncResult BeginFindId(String name, String bdate, AsyncCallback cb, Object stt);
			int EndFindId(IAsyncResult asyncRes);
			Task<int> FindIdAsync(String name, String birthdate);
			IAsyncResult BeginObtainAvatarUri(int userId, AsyncCallback cb, Object stt);
    		Uri EndObtainAvatarUri(IAsyncResult asyncRes);
    		Task<Uri> ObtainAvatarUriAsync(int userId);
		}
  
  		public static Task<Uri> GetUserAvatarAsync(Service svc, String name, String bdate) {
    		return Task.Run(() => {
      				int userId = service.FindIdAsync(name, bdate).Result;
      				return service.ObtainAvatarUriAsync(userId).Result;
    		});
  		}
	}

a.
Sabendo que as operações de Users.Service consistem essencialmente em I/O, qual é o defeito
grave de implementação de GetUserAvatarAsync e porque razão este impõe um elevado consumo de
recursos quando é intensivamente utilizado em vários pedidos em simultâneo?

b.
Apresente uma implementação corrigida de GetUserAvatarAsync, utilizando devidamente a
Task Parallel Library (TPL) e/ou os métodos async de C#.

c.
Acrescente à classe Users as operações BeginGetUserAvatar e EndGetUserAvatar, para que a
operação GetUserAvatar também possa ser utilizada de acordo com o modelo APM.
NOTA: não pode usar a TPL e só se admitem esperas de controlo dentro da operação End,
estritamente onde o APM o exige.

**/

public class Users {
	public interface Service {
		IAsyncResult BeginFindId(String name, String bdate, AsyncCallback cb, Object stt);
		int EndFindId(IAsyncResult asyncRes);
		
		Task<int> FindIdAsync(String name, String birthdate);
		IAsyncResult BeginObtainAvatarUri(int userId, AsyncCallback cb, Object stt);

		Uri EndObtainAvatarUri(IAsyncResult asyncRes);
		Task<Uri> ObtainAvatarUriAsync(int userId);
	}

    public class ServiceImpl : Service {
		IAsyncResult Service.BeginFindId(String name, String bdate, AsyncCallback cb, Object st) {
			GenericAsyncResult<int> ar = new GenericAsyncResult<int>(cb, st, false);
			new Timer(
				(_) => ar.SetResult(42), null,
				new Random(Environment.TickCount).Next(1000),
			    Timeout.Infinite
			);
			return ar;
		}
		int Service.EndFindId(IAsyncResult asyncRes) {
			return ((GenericAsyncResult<int>)asyncRes).Result;
		}
		
		Task<int> Service.FindIdAsync(String name, String birthdate) {
			Thread.Sleep(new Random(Environment.TickCount).Next(1000));
			return Task<int>.FromResult(42);
		}
		
		IAsyncResult Service.BeginObtainAvatarUri(int userId, AsyncCallback cb, Object st) {
			GenericAsyncResult<Uri> ar = new GenericAsyncResult<Uri>(cb, st, false);
			new Timer(
				 (_) => ar.SetResult(new Uri("http://google.com")),
			     null,
				 new Random(Environment.TickCount).Next(1000),
			     Timeout.Infinite
			);
			return ar;
		}

		Uri Service.EndObtainAvatarUri(IAsyncResult asyncRes) {
			return ((GenericAsyncResult<Uri>)asyncRes).Result;
		}
		
		Task<Uri> Service.ObtainAvatarUriAsync(int userId) {
			Thread.Sleep((new Random(Environment.TickCount)).Next(2000));
			return Task<Uri>.FromResult(new Uri("http://google.com"));
		}
    }
	
	public static Task<Uri> GetUserAvatarAsync(Service svc, String name, String bdate) {
		return Task.Run(() => {
  				int userId = svc.FindIdAsync(name, bdate).Result;
  				return svc.ObtainAvatarUriAsync(userId).Result;
		});
	}

	public static Task<Uri> GetUserAvatarAsync_b(Service svc, String name, String bdate) {
		return svc.FindIdAsync(name, bdate)
			      .ContinueWith<Task<Uri>>(
					  (antecedent) => svc.ObtainAvatarUriAsync(antecedent.Result)).Unwrap();
	}	

	public static Task<Uri> GetUserAvatarAsync_b2(Service svc, String name, String bdate) {
		return svc.FindIdAsync(name, bdate)
			      .ContinueWith<Task<Uri>>(
					  (antecedent) => svc.ObtainAvatarUriAsync(antecedent.Result))
						  .ContinueWith<Uri>((antecedent) => antecedent.Result.Result);
	}	
	public static async Task<Uri> GetUserAvatarAsync_bb(Service svc, String name, String bdate) {
		return await svc.ObtainAvatarUriAsync(await svc.FindIdAsync(name, bdate));
	}	
	
	public static void TestTAP() {
		Stopwatch sw = Stopwatch.StartNew();
		var avatarUriTask = GetUserAvatarAsync_b2(new ServiceImpl(), "name", "date");
		avatarUriTask.Wait();
		Console.WriteLine("--TAP: elapsed {0} ms, got: \"{1}\"",
			 			  sw.ElapsedMilliseconds, avatarUriTask.Result);
	}
	
	public static IAsyncResult BeginGetUserAvatar(Service svc, String name, String bdate,
		 										AsyncCallback ucb, object ust) {
		GenericAsyncResult<Uri> gar = new GenericAsyncResult<Uri>(ucb, ust, false);
		
		svc.BeginFindId(name, bdate, (ar) => {
			try {
				var id = svc.EndFindId(ar);
				svc.BeginObtainAvatarUri(id, (ar2) => {
					try {
						var uri = svc.EndObtainAvatarUri(ar2);
						gar.SetResult(uri);
					} catch (Exception ex) {
						gar.SetException(ex);							
					}
				}, null);	
			} catch (Exception ex) {
				gar.SetException(ex);
			}
		}, null);
		return gar;	
	}
	public static Uri EndGetUserAvatar(IAsyncResult ar) {
		return ((GenericAsyncResult<Uri>)ar).Result;
	}						

	public static void TestAPM() {
		Stopwatch sw = Stopwatch.StartNew();
		var ar = BeginGetUserAvatar(new ServiceImpl(), "name", "date", null, null);
		var avatarUri = EndGetUserAvatar(ar);
		Console.WriteLine("--APM: elapsed {0} ms, got: \"{1}\"",
			 			  sw.ElapsedMilliseconds, avatarUri);
	}
}
		
/*
5.
No método ProcessItems, apresentado a seguir, as invocações a ExtractInfo podem decorrer em
paralelo, o que seria vantajoso já que nessa operação se concentra a maior parte do tempo total
de execução. O método MergeInfo implementa uma operação comutativa e associativa e new Info()
produz o seu elemento neutro. A função Info ExtractInfo(...) só realiza operações de leitura
sobre a instância de Data que recebe como argumento. Tirando partido da Task Parallel Library,
apresente uma versão de ProcessItems que use invocações paralelas a ExtractInfo para tirar
partido de todos os cores de processamento disponíveis.

	static Info ProcessItems(Data[]items,Session session) {
		Info info = new Info();
		for(int i = 0; i < items.Length; ++i)
			info = MergeInfo(info, ExtractInfo(items[i], session));
	 	return info;
	}
*/

public class DoParallel {
	class Info { public int count; }
	class Data { public int value; }
	class Session {}
	
	static Info ExtractInfo(Data d, Session s) {
		return new Info { count = d.value };
	}
	
	static Info MergeInfo(Info i, Info i2) {
		return new Info { count = i.count + i2.count};
	}
	
	static Info ProcessItems(Data[] items, Session session) {
		Info info = new Info();
		for(int i = 0; i < items.Length; ++i)
			info = MergeInfo(info, ExtractInfo(items[i], session));
	 	return info;
	}
	
	// the best!
	static Info ProcessItemsParallel(Data[] items, Session session) {
		Info total = new Info();
		object _lock = new object();
		Parallel.ForEach<Data, Info>( items,
			() => new Info(),
			(item, _, partial) => {
				return MergeInfo(partial, ExtractInfo(item, session));
			},
			(partial) => {
				lock (_lock) {
					total = MergeInfo(total, partial);
				}
			});
		return total;
	}
	
	static Info ProcessItemsParallel_2(Data[] items, Session session) {
		var workers = new List<Task<Info>>();
		for (int i = 0; i < items.Length; i++) {
			int li = i;
			workers.Add(Task<Info>.Factory.StartNew(() => ExtractInfo(items[li], session)));
		}
		Task<Info>.WaitAll(workers.ToArray());
		// merge results
		Info total = new Info();
		foreach (var t in workers) {
			total = MergeInfo(total, t.Result);
		}
		return total;
	}
	
	static Info ProcessItemsParallel_3(Data[] items, Session session) {
		return Task.WhenAll(
			items.Select(item => Task.Run(() => ExtractInfo(item, session)))).Result.
				Aggregate(MergeInfo);
	}

	static async Task<Info> ProcessItemsParallel_4(Data[] items, Session session) {
		var workers = new List<Task<Info>>();
		for (int i = 0; i < items.Length; i++) {
			int li = i;
			workers.Add(Task<Info>.Factory.StartNew(() => ExtractInfo(items[li], session)));
		}
		var results = await Task.WhenAll(workers.ToArray());
		// merge results
		Info total = new Info();
		for (int i = 0; i < results.Length; i++) {
			total = MergeInfo(total, results[i]);
		}
		return total;
	}


	
	public static void Test() {
		Data[] items = new Data[32];
		for (int i = 0; i < 32; i++)
			items[i] = new Data { value = i + 1};
		var result = ProcessItemsParallel_3(items, new Session());
		Console.WriteLine("result: {0}", result.count);
	}
}

public class Program {
	
	// teste program
	public static void Main() {
		Users.TestTAP();
		Users.TestAPM();
		DoParallel.Test();	
	}
	
}
