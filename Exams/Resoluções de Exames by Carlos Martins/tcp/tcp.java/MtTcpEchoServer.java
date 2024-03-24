/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * A TCP multithreading server based on Java Asynchronous Channels.
 * Limits maximum number of simultaneous connections and shuts down graciously
 * 
 * Carlos Martins, November 2019
 *
 **/

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.*;

/**
 * A TCP multi-threaded echo server.
 */

public class MtTcpEchoServer {

	// the listening port
	private static final int SERVER_PORT = 13000;
	
	// minimum and maximum service time
	private static final int MIN_SERVICE_TIME = 50;
	private static final int MAX_SERVICE_TIME = 250;
	
	// the server's listetner socket
	private AsynchronousServerSocketChannel server;
	
	// Number of active connections and the maximum allowed.
	private final AtomicInteger activeConnections = new AtomicInteger();
	private static final int MAX_ACTIVE_CONNECTIONS = 10;

	// Set to true when the shut down of the server is in progress.
	private volatile boolean shutdownInProgress;
	
	/**
	 * Buils an echo TCP multithread server
	 */

	public MtTcpEchoServer() {}

	/**
	 * Create the setver socket and starts listen for client connections
	 */

	public boolean start() {
		try {
			// Create a server socket bound to the server port.
			server = AsynchronousServerSocketChannel.open();
			server.bind(new InetSocketAddress(SERVER_PORT));
			return true;
		} catch (IOException ioe) {
			return false;
		}
	}

	/**
	 * Starts Begins the processing the connection represented by the specifiedsocket.
	 */
	private CompletableFuture<Void> processConnectionAsync(AsynchronousSocketChannel socket) {
		final int BUFFER_SIZE = 4096;
		final CompletableFuture<Void> future = new CompletableFuture<>();
		try {
			
			 // Read asynchronously the client request that we know that is smaller than
			 // BUFFER_SIZE bytes.
			 // For the sake of simplicity we use a synchronous write when
			 // responding to the client.

			final ByteBuffer requestBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			CompletionHandler<Integer, Void> onReadComplete = new CompletionHandler<>() {

				// Called when the read operation succeeds
				@Override
				public void completed(Integer result, Void attachment) {
					Charset charSet = Charset.forName("UTF-8");
					String request = new String(requestBuffer.array(), 0, result - 1, charSet);
					System.out.printf("-->[%s]%n", request);
					
					// Simulate a random service delay and send the response
					try {
						Thread.sleep(ThreadLocalRandom.current().nextInt(MAX_SERVICE_TIME) + MIN_SERVICE_TIME);
						String response = request.toUpperCase();
						ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(charSet));
						socket.write(responseBuffer).get();
						System.out.printf("<--[%s]%n", response);
						future.complete(null);
					} catch (InterruptedException ie) { /* never happens */
					} catch (Exception ex) {
						// something wents wrong! fail process request
						future.completeExceptionally(ex);
					} finally {
						try {
							socket.close();
						} catch (IOException ignore) { /* just ignore */ }
					}
				}

				// Called when the read operation fails
				@Override
				public void failed(Throwable ex, Void attachment) {
					// something wents wrong! fail process request
					future.completeExceptionally(ex);
				}
			};
			socket.read(requestBuffer, null, onReadComplete);
		} catch (Exception ex) {
			try {
				socket.close();
			} catch (IOException ignore) { /* just ignore */ }
			future.completeExceptionally(ex);
		}
		return future;
	}
	
	/**
	 * Future that marks the end of service
	 */
	CompletableFuture<Void> listenFuture;

	/**
	 * CompletionHandler for asynchronous ServerSocket.accept() call
	 */
	CompletionHandler<AsynchronousSocketChannel, Void> onAcceptCompleted = new CompletionHandler<>() {

		// Called when the accept operation succeeds
		@Override
		public void completed(AsynchronousSocketChannel socket, Void attachment) {
			try {
				/**
			 	* Increment the number of active connections and, if the we are below of
			 	* maximum allowed, start accepting a new connection.
			 	*/
			
				int c = activeConnections.incrementAndGet();
				if (!shutdownInProgress && c < MAX_ACTIVE_CONNECTIONS)
					server.accept(null, this);

				/**
			 	* Start the processing the previously accepted connection.
			 	* Here, we alse use and asynchronous completion handler interface,
			 	* but the outcome is ignored.
			 	* However, we ignore exceptions thrown when processing a client connection.
			 	*/

				processConnectionAsync(socket).thenRun(() -> {
				
   					/**
				 	* When the processing of the connection is completed, decrement the number of
			 	 	* active connections. If a shut down isn't in progress and if the number of
			 	 	* active connections was equal to the maximum allowed, we must accept a new
			 	 	* connection. Otherwise, if the number of active connections drops to zero and
			 	 	* the shut down was initiated, set the server idle event.
   				 	*/
				
   					int c2 = activeConnections.decrementAndGet();
					if (!shutdownInProgress && c2 == MAX_ACTIVE_CONNECTIONS - 1)
						server.accept(null, this);
					else if (shutdownInProgress && c2 == 0)
						listenFuture.complete(null);
				});
			} catch (Exception ex) {
				System.out.printf("***exception: %s%n",ex.getClass());
			}
		}
		
		// Called when the accept operation fails
		@Override
		public void failed(Throwable exc, Void attachment) {
			// We get here when the server socket is closed	
		}
	};
	 	
	/**
	 * Start listening for client requests.
	 */
	public CompletableFuture<Void> listenAsync() {
		/**
		 * Accept the first conenction request and return a Future to represent the
		 * asynchronous server operation.
		 */
		server.accept(null, onAcceptCompleted);
		return new CompletableFuture<Void>();	
	}
		
	/**
	 * Shutdown the server and await for termination.
	 */
	private void shutdownAndWaitTermination(CompletableFuture<Void> listenFuture) {

		 // Initiate the server shutdown
		shutdownInProgress = true;

		// Stop listening connection requests.
		try {
			server.close();
		} catch (IOException ignore) {}

		// If there is no connection still active, the shutdown is completed.
		if (activeConnections.get() == 0)
			listenFuture.complete(null);
		
		// Wait until all accpted connections has been processed.
		try {
			listenFuture.get();
		} catch (Throwable ignore) {}
	}
	
	public static void main(String... args) {
		MtTcpEchoServer echoServer = new MtTcpEchoServer();
		if (!echoServer.start()) {
			System.out.println("--something wrong when start server");
			return;
		}

		// Start listening for client requests and process them asynchronously
			
		echoServer.listenFuture = echoServer.listenAsync();
			
		/**
		 * Wait a <enter> from the console to terminate the server. 
		 */
			
		System.out.println("Hit <enter> to exit the server...");
		try {
			new BufferedReader(new InputStreamReader((System.in))).readLine();
		} catch (IOException ignore) {}
		System.out.println("--initiate the shutdown");
		// Initiate server shutdown
		echoServer.shutdownAndWaitTermination(echoServer.listenFuture);
		System.out.println("--shutdown completed");

	}
}
