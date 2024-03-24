/**
 *
 * ISEL, LEIC, Concurrent Programming.
 *
 * PC_1415i_2: resolution of exercise 4
 *
 * Build executable with the command: csc PC_1415i_2_R[4].cs generic-async-result.cs
 *
 * Carlos Martins, June 2017
 *
 **/


using System;
using System.Threading;
using System.Threading.Tasks;

/**
Teste 2ª Época Inverno 2014/2015

4.
​A interface ​Services representa serviços síncronos de um sistema de gestão de equipamentos.
O método síncrono ​CheckDeviceVersion usa operações de ​Services para verificar se a versão de
um equipamento coincide com a registada anteriormente. Todas as operações de ​Services envolvem
comunicações por redes de dados com os equipamentos ou com servidores de bases de dados, sendo
vantajosa a disponibilização de variantes assíncronas dessas operações, bem como de
​CheckDeviceVersion.​Para além disso, em CheckDeviceVersion,​a operação ​GetStoredVersion poderia
decorrer em paralelo com a sequência GetDeviceAddress→ GetVersionFromDevice.
​
	public class SyncOps {
		public interface Services{
			String GetDeviceAddress(int devId);
			int GetVersionFromDevice(String addr);
			int  GetStoredVersion(intdev Id);
		}

		public bool CheckDeviceVersion(Services svc,int devId){
			String addr = svc.GetDeviceAddress(devId);
			int devVer = svc.GetVersionFromDevice(addr);
			int stoVer =s vc.GetStoredVersion(devId);
			return devVer==stoVer;
		}
	}
	
a. A classe ​APMOps será a variante assíncrona de ​SyncOps,​seguindo o estilo A​synchronous
   Programming Model (APM). Apresente os métodos ​BeginCheckDeviceVersione ​EndCheckDeviceVersion,​
   que usam a interface ​APM Services​(variante APM de ​Services​que não tem de apresentar).
   NOTA: não pode usar a TPL e só se admitem esperas de controlo dentro das operações ​End,​
         estritamente onde o APM o exige.
b. ​A classe ​TAPOps será a variante assíncrona de ​SyncOps,​seguindo o estilo T​ask­based Asynchronous
   Pattern (TAP). Tirando partido da T​ask Parallel Library (TPL) ou dos métodos ​async do C#,
   implemente o método CheckDeviceVersionAsync,​que usa a interface ​TAPServices (variante TAP de
   ​Servicesque não tem de apresentar).
   NOTA: na implementação não se admite a utilização de operações com bloqueios de controlo.

**/

public class APMAndTAPOps {
	public interface Services{
		
		// APM interface
		IAsyncResult BeginGetDeviceAddress(int devId, AsyncCallback callback, object state);
		String EndGetDeviceAddress(IAsyncResult ar);
		IAsyncResult BeginGetVersionFromDevice(String addr, AsyncCallback callback, object state);
		int EndGetVersionFromDevice(IAsyncResult ar);
		IAsyncResult BeginGetStoredVersion(int devId, AsyncCallback calbback, object state);
		int EndGetStoredVersion(IAsyncResult ar);

		// TAP interface
		Task<String>GetDeviceAddressAsync(int devId);
		Task<int> GetVersionFromDeviceAsync(String addr);
		Task<int> GetStoredVersionAsync(int devId);
	}
	
	public class APMAndTAPServicesImpl : Services {
		
		// Fake implementation of the Services interface methods
		
		public IAsyncResult BeginGetDeviceAddress(int devId, AsyncCallback callback, object state) {
			GenericAsyncResult<String> ar = new GenericAsyncResult<String>(callback, state, false);		
			new Timer((_) => ar.SetResult("device42"), null, 1000, Timeout.Infinite);
			return ar;
		}

		public String EndGetDeviceAddress(IAsyncResult ar) {
			return ((GenericAsyncResult<String>)ar).Result;
		}
		
		public IAsyncResult BeginGetVersionFromDevice(String addr, AsyncCallback callback, object state) {
			GenericAsyncResult<int> ar = new GenericAsyncResult<int>(callback, state, false);		
			new Timer((_) => ar.SetResult(42), null, 100, Timeout.Infinite);
			return ar;
		}
		
		public int EndGetVersionFromDevice(IAsyncResult ar) {
			return ((GenericAsyncResult<int>)ar).Result;	
		}
		
		public IAsyncResult BeginGetStoredVersion(int devId, AsyncCallback callback, object state) {
			GenericAsyncResult<int> ar = new GenericAsyncResult<int>(callback, state, false);		
			new Timer((_) => ar.SetResult(42), null, 200, Timeout.Infinite);
			return ar;
		}
		
		public int EndGetStoredVersion(IAsyncResult ar) {
			return ((GenericAsyncResult<int>)ar).Result;
		}
		
		public Task<String>GetDeviceAddressAsync(int devId) {
			var tcs = new TaskCompletionSource<String>();
			new Timer((_) => tcs.SetResult("device42"), null, 500, Timeout.Infinite);
			return tcs.Task;
		}
		
		public Task<int> GetVersionFromDeviceAsync(String addr) {
			var tcs = new TaskCompletionSource<int>();
			new Timer((_) => tcs.SetResult(42), null, 250, Timeout.Infinite);
			return tcs.Task;	
		}
		
		public Task<int> GetStoredVersionAsync(int devId) {
			var tcs = new TaskCompletionSource<int>();
			new Timer((_) => tcs.SetResult(42), null, 150, Timeout.Infinite);
			return tcs.Task;
		}
	}

	// APM implementation of BeginCheckDeviceVersion

	public IAsyncResult BeginCheckDeviceVersion(Services svc, int devId, AsyncCallback cb, object state){
		
		AsyncCallback onGetDeviceAddress = null, onGetVersionFromDevice = null, onGetStoredVersion = null;
		var gar = new GenericAsyncResult<bool>(cb, state, false);
		int stateCount = 2;		/* to control completion */
		int devVer = -1, stoVer = -1;
		
		onGetDeviceAddress = (_ar) => {
			string devAddr = svc.EndGetDeviceAddress(_ar);
			svc.BeginGetVersionFromDevice(devAddr, onGetVersionFromDevice, null);
		};
		onGetVersionFromDevice = (_ar) => {
			devVer = svc.EndGetVersionFromDevice(_ar);
			if (Interlocked.Decrement(ref stateCount) == 0)
				gar.SetResult(devVer == stoVer);			
		};
		onGetStoredVersion = (_ar) => {
			stoVer = svc.EndGetStoredVersion(_ar);
			if (Interlocked.Decrement(ref stateCount) == 0)
				gar.SetResult(devVer == stoVer);
		};
		
		svc.BeginGetDeviceAddress(devId, onGetDeviceAddress, null);
		svc.BeginGetStoredVersion(devId, onGetStoredVersion, null);
		return gar;
	}

	public bool EndCheckDeviceVersion(IAsyncResult ar) {
		return ((GenericAsyncResult<bool>)ar).Result;
	}

	// TAP implementation
	public Task<bool> CheckDeviceVersionAsync(Services svc,int devId){
		Task<int> devVert = svc.GetDeviceAddressAsync(devId).ContinueWith(
						(antecedent) => svc.GetVersionFromDeviceAsync(antecedent.Result)).Unwrap();
		Task<int> stoVert = svc.GetStoredVersionAsync(devId);
		return Task<bool>.Factory.ContinueWhenAll(new Task[]{stoVert, devVert}, (_) =>
			stoVert.Result == devVert.Result);
	}
	
	// Using asynchronous methods
	public async Task<bool> CheckDeviceVersionAsyncAsync(Services svc,int devId){
		Task<String> addrt = svc.GetDeviceAddressAsync(devId);
		Task<int> stoVert = svc.GetStoredVersionAsync(devId);
		Task<int> devVert = svc.GetVersionFromDeviceAsync(await addrt);
		
		return await stoVert == await devVert; 
		/**
		 * or,
		 * await Task.WhenAll(new Task[]{stoVert, devVert});
		 * return stoVert.Result == devVert.Result;
		 */
	}		

	// test program
	public static void Main() {
		APMAndTAPOps ops = new APMAndTAPOps();
		Services svc = new APMAndTAPServicesImpl();
		
		// APM test
		IAsyncResult ar = ops.BeginCheckDeviceVersion(svc, 42, null, null);
		Console.Write("-- return from BeginXxx method, wait for result...");
		Console.WriteLine("\n-- the result is: {0}", ops.EndCheckDeviceVersion(ar));

		// TAP test
		Task<bool> tb = ops.CheckDeviceVersionAsync(svc, 42);
		Console.Write("-- return from XxxAsync method, wait for result...");
		Console.WriteLine("\n-- the result is: {0}", tb.Result);

		// Async methods test
		Task<bool> ta = ops.CheckDeviceVersionAsyncAsync(svc, 42);
		Console.Write("-- return from XxxAsyncAsync method, wait for result...");
		Console.WriteLine("\n-- the result is: {0}", ta.Result);
	}
}
