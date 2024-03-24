/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Client of echo server using TAP interfaces, TPL and asynchronous methods
 *
 * Carlos Martins, June 2017
 *
 **/

/**
 * Comment the next line in order to use explicit task creation;
 * otherwise a parallel for loop is used.
 */
#define USE_PARALLEL_FOR

using System;
using System.Threading;
using System.Threading.Tasks;
using System.Text;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;

/**
 * Tcp client for a echo server.
 */

class TcpEchoClientAsync {
	private const int SERVER_PORT = 13000;
	private const int BUFFER_SIZE = 1024;
	
	private static int requestCount = 0;

	/**
	 * Send a server request and display the response.
	 */
	
	static async Task SendRequestAndReceiveResponseAsync(string server, string requestMessage) {
		NetworkStream stream = null;
		TcpClient connection = null;
		
		try  {
			// Star a stop watch timer
			Stopwatch sw = Stopwatch.StartNew();
			
			/**
			 * Create a TcpClient socket connected to the server and
			 * get the associated stream.
			 */
						
			connection = new TcpClient();
			await connection.ConnectAsync(server, SERVER_PORT);
			stream = connection.GetStream();
			
			/**
			 * Translate the message to a byte stream and send it to the server.
			 */
			
			byte[] requestBuffer = Encoding.ASCII.GetBytes(requestMessage);         			
			await stream.WriteAsync(requestBuffer, 0, requestBuffer.Length);
			
			Console.WriteLine("-->[{0}]", requestMessage);         

			/**
			 * Receive the server's response and display it.
			 */

			byte[] responseBuffer = new byte[BUFFER_SIZE];
			int bytesRead;
			while ((bytesRead = await stream.ReadAsync(responseBuffer, 0, responseBuffer.Length)) > 0) {
				Console.WriteLine("<--[{0} ({1}) ms]", Encoding.ASCII.GetString(responseBuffer, 0, bytesRead),
							      sw.ElapsedMilliseconds);
			}
			sw.Stop();
			Interlocked.Increment(ref requestCount);
		} catch (Exception ex) {
			Console.WriteLine("***error:[{0}] {1}", requestMessage, ex.Message);		
		} finally {
		
			/**
			 * Close everything.
			 */
			
			if (stream != null)
				stream.Close();
			if (connection != null)
				connection.Close();
		}
	}
	
	/**
	 * Send continuously batches of requests until a key is pressed.
	 */

	private const int MAX_DEGREE_OF_PARALLELISM = 20;
	private const int REQ_BATCH_COUNT = 50;
	
#if USE_PARALLEL_LOOP
	/**
	 * Using parallel for loop
	 */
	public static void Main(string[] args) {
		bool executeOnce = false;
		
		// set the minimum thread pool's worker threads to theh MAX_DEGREE_OF_PARALLELISM
		int minworker, miniocp;
		ThreadPool.GetMinThreads(out minworker, out miniocp);
		ThreadPool.SetMinThreads(minworker < MAX_DEGREE_OF_PARALLELISM ? MAX_DEGREE_OF_PARALLELISM : minworker,
				 				 miniocp);
		
		string request = (args.Length > 0) ? args[0] : "--default request message to be echoed--";
		Stopwatch sw = Stopwatch.StartNew();
		do {
			ParallelOptions options = new ParallelOptions { MaxDegreeOfParallelism = MAX_DEGREE_OF_PARALLELISM };
			Parallel.For(0, REQ_BATCH_COUNT, options, i => {
				SendRequestAndReceiveResponseAsync("localhost", String.Format("#{0}: {1}", i, request)).Wait();
			});
		} while (!executeOnce && !Console.KeyAvailable);
		Console.WriteLine("--completed requests: {0} / {1} ms",
							Volatile.Read(ref requestCount), sw.ElapsedMilliseconds);
	}

#else
	// use explicitly created tasks
	public static void Main(string[] args) {
		bool executeOnce = false;
		
		// set the minimum thread pool's worker threads to theh MAX_DEGREE_OF_PARALLELISM
		int worker, iocp;
		ThreadPool.GetMinThreads(out worker, out iocp);
		ThreadPool.SetMinThreads(worker < MAX_DEGREE_OF_PARALLELISM ? MAX_DEGREE_OF_PARALLELISM : worker, iocp);

		string request = (args.Length > 0) ? args[0] : "--default request message to be echoed--";
		
		Task[] tasks = new Task[REQ_BATCH_COUNT];
		Stopwatch sw = Stopwatch.StartNew();
		do {
			for(int i = 0; i < REQ_BATCH_COUNT; i++) {
				tasks[i] = SendRequestAndReceiveResponseAsync("localhost", String.Format("#{0}: {1}", i, request));
			}
			Task.WaitAll(tasks);
		} while (!(executeOnce ||Console.KeyAvailable));
		Console.WriteLine("--completed requests: {0} / {1} ms",
							Volatile.Read(ref requestCount), sw.ElapsedMilliseconds);
	}
#endif
}
