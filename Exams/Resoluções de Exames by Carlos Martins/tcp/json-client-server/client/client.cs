/**
 * ISEL, LEIC, Concurrent Programming
 *
 * Client of the JSON echo server.
 *
 * Nota: We are using the Nuget package Newtonsoft.Json.
 *       To add a NuGet pack to a Visual Studio project consulte:
 *          https://docs.microsoft.com/en-us/nuget/quickstart/install-and-use-a-package-in-visual-studio
 *
 * Carlos Martins, November 2019
 **/

using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using System.Text;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Diagnostics;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

/**
 * Represents a JSON request
 */
public class Request {
    public String Method { get; set; }
    public Dictionary<String, String> Headers { get; set; }
    public JObject Payload { get; set; }

    public override String ToString() {
        return $"Method: {Method}, Headers: {Headers}, Payload: {Payload}";
    }
}

/**
 * Represents a JSON response
 */
public class Response {
    public int Status { get; set; }
    public Dictionary<String, String> Headers { get; set; }
    public JObject Payload { get; set; }

	public override String ToString() {
        return $"Status: {Status}, Headers: {Headers}, Payload: {Payload}";
	}
}    

/**
 * Represents the payload of the request message
 */
public class RequestPayload {

	public int Number { get; set; }
	public String Text { get; set; }

	public override String ToString() {
		return $"[ Number: {Number}, Text: {Text} ]";
	}
}

/**
 * Client for a JSON echo server.
 */
class JsonEchoClient {
	private const int SERVER_PORT = 13000;	
	private static int requestCount = 0;

	private static JsonSerializer serializer = new JsonSerializer();
	
	/**
	 * Send a server request and display the response.
	 */
	static async Task SendRequestAndReceiveResponseAsync(string server, RequestPayload payload) {
		/**
		 * Create a TcpClient socket in order to connectto the echo server.
		 */
		using (TcpClient connection = new TcpClient()) {
			try  {
				// Start a stop watch timer
				Stopwatch sw = Stopwatch.StartNew();
			
				// connect socket to the echo server.
						
				await connection.ConnectAsync(server, SERVER_PORT);

				// Create and fill the Request with "payload" as Payload
				Request request = new Request {
					Method = $"echo-{payload.Number}",
					Headers = new Dictionary<String, String>(),
					Payload = (JObject)JToken.FromObject(payload),
				};

				// Add some headers for test purposes 
				request.Headers.Add("agent", "json-client");
				request.Headers.Add("timeout", "10000");

				/**
			 	 * Translate the message to JSON and send it to the echo server.
			 	 */
			
				JsonTextWriter writer = new JsonTextWriter(new StreamWriter(connection.GetStream()));
				serializer.Serialize(writer, request);
				Console.WriteLine($"-->{payload.ToString()}");         
				await writer.FlushAsync();
			
				/**
			 	 * Receive the server's response and display it.
			 	 */
            	JsonTextReader reader = new JsonTextReader(new StreamReader(connection.GetStream())) {
                	// To support reading multiple top-level objects
                	SupportMultipleContent = true
            	};
				try {
					// to consume any bytes until start of object ('{')
					do {
						await reader.ReadAsync();
					} while (reader.TokenType != JsonToken.StartObject &&
							 reader.TokenType != JsonToken.None);
					if (reader.TokenType == JsonToken.None) {
                		Console.WriteLine("***error: reached end of input stream, ending.");
                    	return;
                	}
					/**
				 	 * Read response JSON object
					 */
					JObject jresponse = await JObject.LoadAsync(reader);
					sw.Stop();
				
					/**
				 	 * Back to the .NET world
				 	 */
                	Response response = jresponse.ToObject<Response>();
					request = response.Payload.ToObject<Request>();
					RequestPayload recoveredPayload = request.Payload.ToObject<RequestPayload>();

					Console.WriteLine($"<--{recoveredPayload.ToString()}, elapsed: {sw.ElapsedMilliseconds} ms");

				} catch (JsonReaderException jre) {
					Console.WriteLine($"***error: error reading JSON: {jre.Message}");
				} catch (Exception e) {
					Console.WriteLine($"-***error: exception: {e.Message}");
				}
				sw.Stop();
				Interlocked.Increment(ref requestCount);
			} catch (Exception ex) {
				Console.WriteLine($"--***error:[{payload}] {ex.Message}");		
			}
		}
	}
	
	/**
	 * Send continuously batches of requests until a key is pressed.
	 */
	private const int MAX_DEGREE_OF_PARALLELISM = 1;
	private const int REQ_BATCH_COUNT = 10;
	
	// use explicitly created tasks
	public static void Main(string[] args) {
		bool executeOnce = false;
		
		// set the minimum thread pool's worker threads to theh MAX_DEGREE_OF_PARALLELISM
		int worker, iocp;
		ThreadPool.GetMinThreads(out worker, out iocp);
		ThreadPool.SetMinThreads(worker < MAX_DEGREE_OF_PARALLELISM ? MAX_DEGREE_OF_PARALLELISM : worker, iocp);

		string text = (args.Length > 0) ? args[0] : "--default text--";
		
		Task[] tasks = new Task[REQ_BATCH_COUNT];
		Stopwatch sw = Stopwatch.StartNew();
		do {
			for(int i = 0; i < REQ_BATCH_COUNT; i++) {
				tasks[i] = SendRequestAndReceiveResponseAsync("localhost",
					new RequestPayload { Number = i, Text = text });
			}
			Task.WaitAll(tasks);
		} while (!(executeOnce || Console.KeyAvailable));
		Console.WriteLine("--completed requests: {0} / {1} ms",
							Volatile.Read(ref requestCount), sw.ElapsedMilliseconds);
	}
}
