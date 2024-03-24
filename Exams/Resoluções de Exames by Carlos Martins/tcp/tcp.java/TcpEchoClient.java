/*
 * ISEL, LEIC, Concurrent Programming
 *
 * A TCP client for the echo server.
 *
 * Carlos Martins, November 2019
 *
 **/

import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * If IPv6 is enabled and the TcpClient(String, Int32) method is called to connect
 * to a host that resolves to both IPv6 and IPv4 addresses, the connection to the
 * IPv6 address will be attempted first before the IPv4 address.
 * This may have the effect of delaying the time to establish the connection if the host
 * is not listening on the IPv6 address.
 */

//
// TCP client for a echo server.
//

public class TcpEchoClient {
	private static final int SERVER_PORT = 13000;
	
	/**
	 * Send a server request and display the response.
	 */
	static void sendRequestAndReceiveResponse(String hostName, int portNumber, String requestMessage) {
		Socket clientSocket = null;
		PrintWriter out = null;
		BufferedReader in = null;

		try  {
			
			// Create a client socket socket connected to the server.
			clientSocket = new Socket(hostName, portNumber);

			// Associate a PrintWriter and a BuffersRead to out streams and in streams, respectively
    		out = new PrintWriter(clientSocket.getOutputStream(), true);
    		in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

			long startMillis  = System.currentTimeMillis();
			
			// Send server's request
			System.out.printf("-->[%s]%n", requestMessage);
			out.println(requestMessage);
			
			// Loop to receive all lines of data sent by the server and display it.
			String response;
			while ((response = in.readLine()) != null) {
				long elapsedMillis = System.currentTimeMillis() - startMillis;   
				System.out.printf("<--[%s (%d ms)]%n", response, elapsedMillis);
			}
		} catch (IOException ioe) {
			System.out.printf("***error:[%s] %s%n", requestMessage, ioe.getMessage());		
		} finally {
			try {
				if (out != null)
					out.close();
				if (in != null)
					in.close();
				if (clientSocket != null)
					clientSocket.close();
			} catch (IOException ioe) {}
		}
	}
	
	// Utility method that filters IOException
	private static int keysAvailable() {
		try {
			return System.in.available();
		} catch (IOException ioe) {
			return 0;
		}
	}

	/**
	 * Send continuously batch of requests until a key is pressed.
	 */

	private static final int THREAD_COUNT = 5;
	private static final int REQS_PER_THREAD = 20;

	public static void main(String... args) throws InterruptedException, IOException {
		boolean executeOnce = false;
		String message = args.length > 0 ? args[0] : "--default request message to be echoed--";
		
		// Create an executor service in order to send requests in parallel
		ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
		do {
			CountDownLatch done = new CountDownLatch(THREAD_COUNT);
			for (int i = 0; i < THREAD_COUNT; i++) {
				final int li = i;
				executor.execute(() -> {
					for (int j = 0; j < REQS_PER_THREAD; j++) {
						String request = String.format("#%02d: %s", li * REQS_PER_THREAD + j, message);
						sendRequestAndReceiveResponse("localhost", SERVER_PORT, request);
						if (keysAvailable() > 0)
							break;
					}
					done.countDown();;
				});
			}
			done.await();
		} while (!executeOnce && keysAvailable() == 0);
		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
	}
}
