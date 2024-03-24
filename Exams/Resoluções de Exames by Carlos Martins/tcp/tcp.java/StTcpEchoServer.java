/**
 * ISEL, LEIC, Concurrent Programming
 *
 * A TCP single-threaded echo server.
 *
 * Carlos Martins, November 2018
 *
 **/

import java.util.Random;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * A single threaded TCP echo server
 */

public class StTcpEchoServer {

	//
	// Constants
	//
	
	private static final int SERVER_PORT = 13000;
	private static final int MIN_SERVICE_TIME = 10;
	private static final int MAX_SERVICE_TIME = 500;
	
	//
	// The server socket.
	//
	
	private ServerSocket server;
	
	//
	// Random number generator
	
	private Random random = new Random(System.currentTimeMillis());
		
	/**
	 * Builds a sigle-thread echo server and starts listening
	 */
	
	public StTcpEchoServer() {}
	
	public boolean start() {
		try {
			// create a listen socket bound to the server port.
			server = new ServerSocket(SERVER_PORT);	// socket() + bind() + listen()
			return true;
		} catch (IOException ioe) {
			System.out.printf("***can not create the server socket: %s%n", ioe.getMessage());
			return false;
		}
	}
	/**
	 * Processes the connection represented by the specified TcpClient socket.
	 */
	
	private void processConnection(Socket clientSocket) {
		System.out.printf("-->[#%d]processConnection%n", Thread.currentThread().getId());
		BufferedReader in = null;
		PrintWriter out = null;
		try {
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			String request, response;
			request = in.readLine();
			System.out.printf("-->[%s]%n", request);

			// process the data sent by the client, and take some time before send the response.
			try {
				Thread.sleep(random.nextInt(MAX_SERVICE_TIME) + MIN_SERVICE_TIME);
			} catch (InterruptedException ie) {}
			response = request.toUpperCase();
			out.println(response);
			System.out.printf("<--[%s]%n", response);
		} catch (Exception ex) {
			System.out.printf("***error: %s%n", ex.getClass());
		} finally {
			try {
				if (in != null)
					in.close();
				if (in != null)
					in.close();
				clientSocket.close();
			} catch (IOException ioe) {}
		}
		System.out.printf("<--[#%d]ProcessConnection%n", Thread.currentThread().getId());
	}

	// Utility method that filters IOException
	private static int keysAvailable() {
		try {
			return System.in.available();
		} catch (IOException ioe) {
			return 0;
		}
	}

	// Listen loop
	private void listen() {
		// listening loop.
		System.out.println("--start listening for client's connections");
		//ExecutorService executor = Executors.newCachedThreadPool();
		do {
			try {		
				// accept the next client's connection
				final Socket clientSocket = server.accept();	// accept()
			
				// process the accepted client's connection			
				processConnection(clientSocket);
	
				
				//
				// In order to support multi-threaded we can create a new thread to process the
				// connection.
				//
			
				//new Thread(() -> processConnection(clientSocket)).start();
				//executor.execute(() -> processConnection(clientSocket));		
				
			} catch (IOException ioe) {
				System.out.printf("***IOException: %s%n", ioe.getMessage());
				break;
			} catch (Exception ex) {
				System.out.printf("***%s: {1}{0}", ex.getClass(), ex.getMessage());
				break;
			}
		} while (keysAvailable() == 0);
		System.out.println("--initiate shutdown");
		//executor.shutdownNow();
	}
		
	public static void main(String... args) {
	    StTcpEchoServer echoServer = new StTcpEchoServer();

		// start listening for client requests and process accepted requests.		
		if (echoServer.start())
			echoServer.listen();
	}
}
