/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Echo server with JSON request/responses.
 * 
 * Nota: We are using the Nuget package Newtonsoft.Json.
 *       To add a NuGet pack to a Visual Studio project consulte:
 *          https://docs.microsoft.com/en-us/nuget/quickstart/install-and-use-a-package-in-visual-studio
 * 
 * Carlos Martins, November 2019
 *
 */

using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

/**
 * Represent a JSON request
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
 * Represent a JSON response
 */
public class Response {
    public int Status { get; set; }
    public Dictionary<String, String> Headers { get; set; }
    public JObject Payload { get; set; }
}    

/**
 * The echo server
 */
class JsonEchoServer {
    private const int SERVER_PORT = 13000;

    private const int MIN_SERVICE_TIME = 10;
    private const int MAX_SERVICE_TIME = 500;
    
    // the JSON deserializer object
    private static readonly JsonSerializer serializer = new JsonSerializer();
        
    // Process a connection
    private static async Task ProcessConnectionAsync(int id, TcpClient connection, int service_time) {
        using (connection) {
            var stream = connection.GetStream();
            var reader = new JsonTextReader(new StreamReader(stream)) {
                // In order to support reading multiple top-level objects
                SupportMultipleContent = true
            };
            var writer = new JsonTextWriter(new StreamWriter(stream));
            try {
                // Consume any bytes until start of object character ('{')
                do {
                    await reader.ReadAsync();
                    //Console.WriteLine($"advanced to {reader.TokenType}");
                } while (reader.TokenType != JsonToken.StartObject &&
                         reader.TokenType != JsonToken.None);

                if (reader.TokenType == JsonToken.None) {
                    Console.WriteLine($"[{id}] reached end of input stream, ending.");
                    return;
                }
                
                // Load root JSON object
                JObject json = await JObject.LoadAsync(reader);
                
                // Retrive the Request object, and show its Method and Headers fields
                Request request = json.ToObject<Request>();
                Console.WriteLine($"Request {{\n  Method: {request.Method}");
                Console.Write("  Headers: { ");
                if (request.Headers != null)
                {
                    int i = 0;
                    foreach (KeyValuePair<String, String> entry in request.Headers) {
                        Console.Write($"{entry.Key}: {entry.Value}");
                        if (i < request.Headers.Count - 1)
                            Console.Write(", ");
                        i++;
                    }
                }
                Console.WriteLine(" }\n}");

                // Simulate some service time
                await Task.Delay(service_time);

                // Build response and send it to the client
                var response = new Response {
                    Status = 200,
                    Payload = json,
                };
                serializer.Serialize(writer, response);
                await writer.FlushAsync();
            } catch (JsonReaderException e) {
                Console.WriteLine($"[{id}] Error reading JSON: {e.Message}, continuing");
                var response = new Response { Status = 400, };
                serializer.Serialize(writer, response);
                await writer.FlushAsync();
                // close the connection because an error may not be recoverable by the reader
                return;
            } catch (Exception e) {
                Console.WriteLine($"[{id}] Unexpected exception, closing connection {e.Message}");
                return;
            }
        }
    }

    /**
     * Listen for connections, but without parallelizing multiple-connection processing
     */
    public static async Task ListenAsync(TcpListener listener) {
        int ids = 0;
        Random random = new Random(Environment.TickCount);
        listener.Start();
        Console.WriteLine($"Listening on port {SERVER_PORT}");
        do {
            try {
                TcpClient connection = await listener.AcceptTcpClientAsync();
                ids += 1;
                Console.WriteLine($"connection accepted with id '{ids}'");
                await ProcessConnectionAsync(ids, connection, random.Next(MIN_SERVICE_TIME, MAX_SERVICE_TIME));
            } catch (ObjectDisposedException) {
                // exit the method normally, the listen socket was closed
                return;                
            }
        } while (true);
    }

    /**
     * Execute server and wait for <enter> to shutdown.
     */
    public static async Task Main() {
        var listener = new TcpListener(IPAddress.Loopback, SERVER_PORT);
        var listenTask = ListenAsync(listener);
        Console.Write("---hit <enter> to shutdown the server...");
        Console.ReadLine();
        listener.Stop();
        await listenTask;
    }
}
