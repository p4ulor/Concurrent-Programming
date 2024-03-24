/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Exam PC_1415i_1: resolution of exercise 4
 *
 * Build executable with the command: csc PC_1415i_1_R[4].cs generic-async-result.cs
 *
 * Carlos Martins, June 2017
 *
 **/

using System;
using System.Threading;
using System.Threading.Tasks;

/**
Teste 1ª Época Inverno 2014/2015

4.
A classe Reviews fornece um serviço assíncrono de consulta remota de registos de opinião.
Por outro lado, a classe Translations dá acesso a um serviço remoto de tradução de texto.
Tirando partido destes serviços, pretende-se criar um novo serviço, também assíncrono, que
permite obter conjuntos de opiniões traduzidas para uma língua-alvo. Internamente, por cada
pedido de ObtainAllInfo são realizados pedidos paralelos para todos os reviews, cujos
resultados são traduzidos (inLang=”en”) ainda em paralelo, ficando a operação concluida quando
todas as traduções estiverem concluídas. Todos os serviços envolvidos disponibilizam interfaces
assincronas no estilo Asynchronous Programming Model (APM) e Task-based Asynchronous Pattern (TAP). 

a) Implemente os métodos BeginObtainAllInfo e EndObtainAllInfo.
b) Tirando partido da Task Parallel Library (TPL), implemente o método ObtainAllInfoAsync.
**/


public class Reviews {	/*already implemented */
	
	public IAsyncResult BeginObtainReview(int ReviewId, AsyncCallback callback, object state) {
		GenericAsyncResult<String> ar = new GenericAsyncResult<String>(callback, state, false);		
		new Timer((_) => ar.SetResult("blá blá"), null, 1000, Timeout.Infinite);
		return ar;
	}
	
	public String EndObtainReview(IAsyncResult operation) {
		return ((GenericAsyncResult<String>)operation).Result;
	}
	
	public Task<String> ObtainReviewAsync(int ReviewId) {
		var tcs = new TaskCompletionSource<String>();
		new Timer((_) => tcs.SetResult("blá blá"), null, 1000, Timeout.Infinite);
		return tcs.Task;
	}
}

public class Translations{ /*already implemented */
	public IAsyncResult BeginTranslate(String text, String inLang, String outLang,
									   AsyncCallback callback, object state) {
		GenericAsyncResult<String> ar = new GenericAsyncResult<String>(callback, state, false);
		new Timer((_) => ar.SetResult(text.ToUpper()), null, 250, Timeout.Infinite);
		return ar;
	}
	
	public String EndTranslate(IAsyncResult operation) {
		return ((GenericAsyncResult<String>)operation).Result;
	}
		
	public Task<String> TranslateAsync(String text, String inLang, String outLang) {
	var tcs = new TaskCompletionSource<String>();
		new Timer((_) => tcs.SetResult(text.ToUpper()), null, 1000, Timeout.Infinite);
		return tcs.Task;
	}
}

public class AllInfo { /* to implement using TPL and TAP */

	// Auxiliary objects
	private Reviews r = new Reviews();
	private Translations t = new Translations();

	public IAsyncResult BeginObtainAllInfo(int[] ReviewIds, String outLang,
									       AsyncCallback callback, object state){
		var gar = new GenericAsyncResult<String[]>(callback, state, false);
		int count = ReviewIds.Length;
		String[] translated = new String[count];
		
		AsyncCallback onTranslation = (_ar) => {
			int id = (int)_ar.AsyncState;
			translated[id] = t.EndTranslate(_ar);
			if (Interlocked.Decrement(ref count) == 0)
				gar.SetResult(translated);
		};

		AsyncCallback onObtainReview = (_ar) => {
			int id = (int)_ar.AsyncState;
			t.BeginTranslate(r.EndObtainReview(_ar), "en", outLang, onTranslation, id);
		};
					
		for (int i = 0; i < ReviewIds.Length; ++i) {
			r.BeginObtainReview(ReviewIds[i], onObtainReview, i);
		}
		return gar;
	}
	
	public String[] EndObtainAllInfo(IAsyncResult operation) {
		return ((GenericAsyncResult<String[]>)operation).Result;
	}

	public Task<String[]> ObtainAllInfoAsync(int[] ReviewIds, String outLang) {
		int count = ReviewIds.Length;
		Task<String>[] tasks = new Task<String>[count];
		for (int i = 0; i < count; i++) {
			int li = i;
			tasks[i] = r.ObtainReviewAsync(ReviewIds[li]).
					   ContinueWith((a) => t.TranslateAsync(a.Result, "us", outLang)).Unwrap();
					   
		}
		return Task<String[]>.Factory.ContinueWhenAll(tasks, (_) => {
			String[] result = new String[count];
			for (int i = 0; i < count; i++) {
				result[i] = tasks[i].Result;
			}
			return result;
		});
		/*
			return Task.WhenAll(tasks);
		 */
	}

	public async Task<String[]> ObtainAllInfoAsyncAsync(int[] ReviewIds, String outLang) {
		int count = ReviewIds.Length;
		Task<String>[] tasks = new Task<String>[count];
		for (int i = 0; i < count; i++) {
			int li = i;
			tasks[i] = r.ObtainReviewAsync(ReviewIds[li]).
					   ContinueWith((a) => t.TranslateAsync(a.Result, "us", outLang)).Unwrap();		   
		}
		return await Task.WhenAll(tasks);
	}
	
	// test programa
	public static void Main() {
		AllInfo t = new AllInfo();
		int[] reviewIds = new int[] {1, 20, 33, 4, 54, 63};
		string[] result;
		
		// Using APM
		IAsyncResult ar = t.BeginObtainAllInfo(reviewIds, "us", null, null);
		Console.Write("--the BeginObtainAllInfo method returned, so wait until the results are available...");
		result = t.EndObtainAllInfo(ar);
		Console.WriteLine("\n--no we already have the results, display them.");
		for (int i = 0; i < result.Length; i++)
			Console.WriteLine("result[{0}]: {1}", i, result[i]);
		
		// Using TPL
		var r = t.ObtainAllInfoAsync(reviewIds, "us");
		Console.Write("--the ObtainAllInfoAsync method returned, so wait until the results are available...");
		result = r.Result;
		Console.WriteLine("\n--no we already have the results, display them.");
		for (int i = 0; i < result.Length; i++)
			Console.WriteLine("result[{0}]: {1}", i, result[i]);
		// Using Async methods
		r = t.ObtainAllInfoAsyncAsync(reviewIds, "us");
		Console.Write("--the ObtainAllInfoAsyncAsync method returned, so wait until the results are available...");
		result = r.Result;
		Console.WriteLine("\n--no we already have the results, display them.");
		for (int i = 0; i < result.Length; i++)
			Console.WriteLine("result[{0}]: {1}", i, result[i]);
	}
}
