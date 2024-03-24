import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val encoder: CharsetEncoder = Charsets.UTF_8.newEncoder()
private val decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
const val maxReadBytes = 50

fun createServerSocketChannel(hostname: String, port: Int, executor: ExecutorService): AsynchronousServerSocketChannel {
    val group = AsynchronousChannelGroup.withThreadPool(executor)
    val serverSocket = AsynchronousServerSocketChannel.open(group)
    serverSocket.bind(InetSocketAddress(hostname, port))
    println("Created server")
    return serverSocket
}

suspend fun AsynchronousServerSocketChannel.suspendingAccept(): AsynchronousSocketChannel {
    return suspendCoroutine { continuation ->
        this.accept(null, object : CompletionHandler<AsynchronousSocketChannel, Any?> {
            override fun completed(sessionSocket: AsynchronousSocketChannel, attachment: Any?) {
                println("Accept succeeded")
                continuation.resume(sessionSocket)
            }

            override fun failed(error: Throwable, attachment: Any?) {
                println("Failed to accept $error")
                continuation.resumeWithException(error)
            }
        })
    }
}

/**
 * @return number of bytes written
 */
suspend fun AsynchronousSocketChannel.suspendingWriteLine(text: String): Int {
    return suspendCoroutine { continuation ->
        val toSend = CharBuffer.wrap(text + "\n")
        write(encoder.encode(toSend), toSend, object : CompletionHandler<Int, Any?> {
            override fun completed(result: Int, attachment: Any?) {
                //println("attachment: ${attachment!!::class.java.name}")
                println("Write succeeded")
                continuation.resume(result)
            }

            override fun failed(error: Throwable, attachment: Any?) {
                println("Write failed")
                continuation.resumeWithException(error)
            }
        })
    }
}

suspend fun AsynchronousSocketChannel.suspendingReadLine(timeoutMs: Long = 1000): String {
    return suspendCancellableCoroutine { continuation ->
        val buf = ByteBuffer.allocate(1000)
        read(buf, timeoutMs, TimeUnit.MILLISECONDS, null, object : CompletionHandler<Int, Any?> {
            override fun completed(result: Int, attachment: Any?) {
                if (continuation.isCancelled)
                    continuation.resumeWithException(CancellationException("Read was canceled"))
                println("Read succeeded.")
                val received = decoder.decode(buf.flip()).toString().trim()
                continuation.resume(received)
            }

            override fun failed(error: Throwable, attachment: Any?) {
                println("Read failed.")
                continuation.resumeWithException(error)
            }
        })
    }
}