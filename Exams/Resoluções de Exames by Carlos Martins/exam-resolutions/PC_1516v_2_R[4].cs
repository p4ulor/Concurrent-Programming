/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Resolution of exercise 4 and 5, PC_1516v_2
 *
 * Build executable with the command: csc PC_1516v_2_R[4].cs generic-async-result.cs
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
4. [7] A interface Services define o conjunto de serviços síncronos disponibilizados por uma organização que oferece
   a execução de serviços nos seus servidores. O método Login estabelece uma sessão de utilização em proveito do
   utilizador especifcado, lançando a excepção IllegalUserException, quando as credenciais não são válidas.
   O método Execute executa, num servidor remoto, no âmbito da sessão especificada por session,  o serviço service
   e devolve a respectiva resposta através do seu valor de retorno. O método LogUsage, que pode ser executado em
   paralelo com o método Execute, regista, num servidor de logs, o pedido de execução de um serviço. Por fim, o
   método Logout encerra a sessão iniciada com o método Login.
   O método estático ExecuteService executa sincronamente o serviço especificado através do parâmetro service em
   proveito do utilizador especificado com o parâmetro uid, usando as operações disponibilizadas por uma implementaçao
   da interface Services.
 
public class Execute {
	public interface Services {
		Session Login(UserID uid);
		void LogUsage(Session session, Requestservice);
		Response Execute(Session session, Request service);
		void Logout(Session session);
	}

	public static Response ExecuteService(Services svc, UserID uid, Request service) {
		Session session = svc.Login(uid);
		svc.LogUsage(session, service);
		Response response = svc.Execute(session, service);
		svc.Logout(session);
		return response;
	}
}
a) [3,5] A classe APMExecute será a variante assíncrona de Execute ao estilo Asynchronous Programming Model (APM).
   Implemente os métodos BeginExecuteService e EndExecuteService que usam a interface APMServices (variante APM de
   Services que não tem de apresentar).
   NOTA: não pode usar a TPL e só se admitem esperas de controlo dentro das operações End, estritamente onde o 
         APM o exige.

b) [3,5] A classe TAPExecute será a variante assíncrona de Execute, ao estilo Task ­based Asynchronous Pattern (TAP).
   Usando a funcionalidade oferecida pela Task Parallel Library (TPL) ou pelos métodos async do C#, implemente o
   método ExecuteServiceAsync, que usa a interface TAPServices (variante TAP de Services que não tem de apresentar).
   NOTA: na implementação não se admite a utilização de operações com bloqueios de controlo.  
	

**/

public class Session {}
public class UserID {}
public class Request {}
public class Response {}

public class Execute {
	public interface Services {
		Session Login(UserID uid);
		void LogUsage(Session session, Request service);
		Response Execute(Session session, Request service);
		void Logout(Session session);
	}

	public static Response ExecuteService(Services svc, UserID uid, Request service) {
		Session session = svc.Login(uid);
		svc.LogUsage(session, service);
		Response response = svc.Execute(session, service);
		svc.Logout(session);
		return response;
	}
	
	public interface APMServices {
		IAsyncResult BeginLogin(UserID uid, AsyncCallback ucb, object ust);
		Session EndLogin(IAsyncResult ar);
		
		IAsyncResult LogUsage(Session session, Request service, AsyncCallback ucb, object ust);
		void EndLogUsage(IAsyncResult ar);

		IAsyncResult BeginExecute(Session session, Request service, AsyncCallback ucb, object ust);
		Response EndExecute(IAsyncResult ar);

		IAsyncResult BeginLogout(Session session, AsyncCallback ucb, object ust);
		void EndLogout(IAsyncResult ar);
	}

	public interface TAPServices {
		Task<Session> LoginAsync(UserID uid);
		Task LogUsageAsync(Session session, Request service);
		Task<Response> ExecuteAsync(Session session, Request service);
		Task LogoutAsync(Session session);
	}


	public static Task<Response> ExecuteServiceAsync(TAPServices svc, UserID uid, Request service) {
		Task<Session> loginTask = svc.LoginAsync(uid);
		// fork two tasks
		Task logUsageTask = loginTask.ContinueWith(_ => svc.LogUsageAsync(loginTask.Result, service)).Unwrap();
		Task<Response> executeTask = loginTask.ContinueWith(_ => svc.ExecuteAsync(loginTask.Result, service)).Unwrap();
		// join the previous two tasks
		Task logoutTask = Task.Factory.ContinueWhenAll(new Task[] { logUsageTask, executeTask },
				 				_ => svc.LogoutAsync(loginTask.Result)).Unwrap();
		// this continuation returns the result of ExecuteAsync
		return logoutTask.ContinueWith(_ => executeTask.Result);
	}
	
}
public class Program {
	
	// teste program
	public static void Main() {
	}
	
}
