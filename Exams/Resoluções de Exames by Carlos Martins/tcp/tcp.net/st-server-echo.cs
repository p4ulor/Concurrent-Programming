/**
 * ISEL, LEIC, Concurrent Programming
 *
 * A TCP single-threaded echo server.
 *
 * Carlos Martins, May 2019
 *
 **/

/**
 * Select how connections from clientes are processed
 */
//#define SINGLE_THREADED
//#define NEW_THREAD_FOR_EACH_CONNECTION
#define USE_THREAD_POOL

using System;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;

/**
 * Definitions needed in ordem to use a CTRL^C handler
 */

internal class Win32API {

    // A delegate that matches the Win32 HandleRoutine
    public delegate bool HandlerRoutine(uint ctrlType);

    // the managed prototype of Win32 SetConsoleCtrlHandler() API 
    [DllImport("kernel32.dll")]
    public static extern bool SetConsoleCtrlHandler(HandlerRoutine hcb, bool add);
}

/**
 * A single threaded TCP echo server
 */

public class SingleThreadedEchoServer {

	//
	// Constants
	//
	
	private const int SERVER_PORT = 13000;
	private const int BUFFER_SIZE = 1024;
	private const int MIN_SERVICE_TIME = 50;
	private const int MAX_SERVICE_TIME = 500;

	//
	// The server socket.
	//
	
	private TcpListener server;
	
	//
	// Random number generator
	
	private Random random = new Random(Environment.TickCount);
	
	// the CRTL C handler's delegate
    Win32API.HandlerRoutine ctrl_c_handler;
	
	/**
     * Handler for CTRL-C
     * -- closes the server socket.
	 */
	
    private bool CtrlCHandler(uint ctrlType) {
        Console.Write("\n--hit <enter> to exit the server...");
        Console.ReadLine();
        // closes the server socket
        server.Stop();
        // return true to not terminate the process.
        return true;
    }
	
	/**
	 * Builds a sigle-thread echo server and starts listening
	 */
	
	public SingleThreadedEchoServer() {		
		// create a listen socket bound to the server port.
		server = new TcpListener(IPAddress.Loopback, SERVER_PORT);	// socket() + bind()

		// set the CTRL C handler
		ctrl_c_handler = new Win32API.HandlerRoutine(CtrlCHandler);
		Win32API.SetConsoleCtrlHandler(ctrl_c_handler, true);	
		
		// start listening for client's connection requests.
		server.Start(); 	//listen()
	}
	
	/**
	 * Processes the connection represented by the specified TcpClient socket.
	 */
	
	private void ProcessConnection(TcpClient connection) {
		Console.WriteLine("-->[#{0}]ProcessRequest", Thread.CurrentThread.ManagedThreadId);
		using (connection)
		using (NetworkStream stream = connection.GetStream()) {
			try {
				var buffer = new byte[BUFFER_SIZE];
	
				// receive request message from the client
				int bytesRead = stream.Read(buffer, 0, buffer.Length);
		
				// translate data bytes to a ASCII string.
				string request = System.Text.Encoding.ASCII.GetString(buffer, 0, bytesRead);
				Console.WriteLine("-->[{0}]", request);

				// process the data sent by the client, and take some time before send the response.
				Thread.Sleep(random.Next(MIN_SERVICE_TIME, MAX_SERVICE_TIME));
				request = request.ToUpper();
				byte[] response = System.Text.Encoding.ASCII.GetBytes(request);
				stream.Write(response, 0, response.Length);
				Console.WriteLine("<--[{0}]", request);            
			} catch (Exception ex){
				Console.WriteLine("***--error: {0}", ex.Message);
			}
		}
		Console.WriteLine("<--[#{0}]ProcessRequest", Thread.CurrentThread.ManagedThreadId);
	}

	// Listen loop
	private void Listen() {

		// listening loop.
		Console.WriteLine("--start listening for client's connections");				
		do {
			try {		
				// accept the next client's connection
				TcpClient connection = server.AcceptTcpClient();	// accept()
			
				// process the accepted client's connection
#if SINGLE_THREADED		
				ProcessConnection(connection);
#elif NEW_THREAD_FOR_EACH_CONNECTION
			
				//
				// In order to support multi-threaded we can create a new thread to process the
				// connection or alternatively use the worker thread pool.
				//
			
				new Thread((_conn) => ProcessConnection((TcpClient)_conn)).Start(connection);

#else			
				ThreadPool.QueueUserWorkItem((_conn) => ProcessConnection((TcpClient)_conn), connection);
#endif
			
			} catch (SocketException) {
				break;
			} catch (InvalidOperationException) {
				/* this exception can occur when the socked is stopped and then we call AcceptTcpClient */
				break;
			} catch (Exception ex) {
				Console.WriteLine("***error: {1}{0}", ex.Message, ex);
				break;
			}
		} while (true);
	}
		
	public static void Main() {
	    SingleThreadedEchoServer echoServer = new SingleThreadedEchoServer();
		// listening for client requests and process accepted requests.
		
		echoServer.Listen();
		
		// ensure that the ctrl_c_handler delegate is not collected!??
		GC.KeepAlive(echoServer);
	}
}
