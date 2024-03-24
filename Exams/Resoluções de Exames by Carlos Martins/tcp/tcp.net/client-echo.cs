/*
 * ISEL, LEIC, Concurrent Programming
 *
 * A TCP client for the echo server.
 *
 * Carlos Martins, May 2019
 *
 **/

using System;
using System.Threading;
using System.Text;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;

/*
 * If IPv6 is enabled and the TcpClient(String, Int32) method is called to connect
 * to a host that resolves to both IPv6 and IPv4 addresses, the connection to the
 * IPv6 address will be attempted first before the IPv4 address.
 * This may have the effect of delaying the time to establish the connection if the host
 * is not listening on the IPv6 address.
 */

//
// TCP client for a echo server.
//

class TcpEchoClient {
	private const int SERVER_PORT = 13000;
	private const int BUFFER_SIZE = 1024;
	
	//
	// Send a server request and display the response.
	//
	
	static void SendRequestAndReceiveResponse(IPEndPoint serverEP, string requestMessage) {
		/**
		* Create a TcpClient socket connected to the server and get the associated stream.
		*/
		using (TcpClient connection = new TcpClient()) {			
			try  {

				/**
				 * Connect to the server and get the associated stream.
			 	 */
			
				connection.Connect(serverEP);
				NetworkStream stream = connection.GetStream();
			
				Stopwatch sw = new Stopwatch();
				sw.Start();
			
				// sned the message to the server as a byte stream		
				byte[] requestBuffer = Encoding.ASCII.GetBytes(requestMessage);         			
				stream.Write(requestBuffer, 0, requestBuffer.Length);
				Console.WriteLine("-->[{0}]", requestMessage);
			
				// loop to receive all data sent by the server and display it.
				byte[] responseBuffer = new byte[BUFFER_SIZE];
				int bytesRead;
				while ((bytesRead = stream.Read(responseBuffer, 0, responseBuffer.Length)) > 0) {      
					Console.WriteLine("<--[{0} ({1} ms)]",
							   	  	  Encoding.ASCII.GetString(responseBuffer, 0, bytesRead),
							   	      sw.ElapsedMilliseconds);
				}
				sw.Stop();
			} catch (Exception ex) {
				Console.WriteLine("***error:[{0}] {1}", requestMessage, ex.Message);		
			}
		}
	}
	
	/**
	 * Send continuously batch of requests until a key is pressed.
	 */

	private const int THREAD_COUNT = 10;
	private const int REQS_PER_THREAD = 20;

	public static void Main(string[] args) {
		bool executeOnce = false;
		String message = args.Length > 0 ? args[0] : "--default request message to be echoed--";
		
		int minworker, miniocp;
		ThreadPool.GetMinThreads(out minworker, out miniocp);
		if (minworker < THREAD_COUNT)
			ThreadPool.SetMinThreads(THREAD_COUNT, miniocp);

		// build the server's network end point.
		IPEndPoint serverIPEndPoint = new IPEndPoint(IPAddress.Loopback, SERVER_PORT);
		do {
			CountdownEvent done = new CountdownEvent(THREAD_COUNT);
			for (int i = 0; i < THREAD_COUNT; i++) {
				int li = i;
				ThreadPool.QueueUserWorkItem((_) => {
					for (int j = 0; j < REQS_PER_THREAD; j++) {
						string msg =String.Format("-{0}: {1}", li * REQS_PER_THREAD + j, message);
						SendRequestAndReceiveResponse(serverIPEndPoint, msg);
						if (Console.KeyAvailable) {
							break;
						}
					}
					done.Signal();
				});
			}
			done.Wait();
		} while (!executeOnce && !Console.KeyAvailable);
	}
}
