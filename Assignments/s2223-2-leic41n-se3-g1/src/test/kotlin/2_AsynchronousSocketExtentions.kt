import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.Executors
import kotlin.test.assertTrue

class AsynchronousSocketExtentions {
    val hostAddress = "localhost"
    val port = 5000

    @Test
    fun `write and read chat`(){
        val server = createServerSocketChannel(hostAddress, port, Executors.newSingleThreadExecutor())

        var clientSays = "?"
        var serverSays = "?"

        var didServerGotWhatClientSaid = false
        var didClientGotWhatServerSaid = false

        val dispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        runBlocking(dispatcher){

            launch {
                val sessionAccepted = server.suspendingAccept()
                val id = sessionAccepted.hashCode()
                sessionAccepted.use {
                    println("Server - Accepted session $id")
                    println("Server - I will say hi to client")
                    serverSays = "Hello client! $id"
                    sessionAccepted.suspendingWriteLine(serverSays)

                    delay(2000L)

                    val clientSaid = sessionAccepted.suspendingReadLine(3000L)
                    if(clientSaid==clientSays) didServerGotWhatClientSaid = true
                    println("Server - Client says: $clientSaid")
                }
            }

            val client = AsynchronousSocketChannel.open()
            val inetSocketAddress = InetSocketAddress(hostAddress, port)
            val future = client.connect(inetSocketAddress)
            future.get()

            val serverSaid = try {
                client.suspendingReadLine(3000L)
            } catch (e: Exception){
                println("Exception - $e")
                ""
            }
            if(serverSaid==serverSays) didClientGotWhatServerSaid = true
            println("Client - Server says: $serverSaid")
            println("Client - I will reply to him")
            clientSays = "Hello server!"
            val byteWritten = client.suspendingWriteLine(clientSays)
            println("Client - bytes written: $byteWritten")
        }
        server.close()

        assertTrue(didServerGotWhatClientSaid)
        assertTrue(didClientGotWhatServerSaid)
    }
}