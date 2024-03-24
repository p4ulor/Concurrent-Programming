import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.net.SocketAddress;

public class AsynchronousServerSocketChannelEx implements Closeable {

    private static class TapHandler<R> implements CompletionHandler<R, CompletableFuture<R>> {
        @Override
        public void completed(R result, CompletableFuture<R> future) {
            future.complete(result);
        }

        @Override
        public void failed(Throwable ioex, CompletableFuture<R> future) {
            future.completeExceptionally(ioex);
        }
    }

    private final AsynchronousServerSocketChannel serverSocketChannel;

    public AsynchronousServerSocketChannelEx(AsynchronousServerSocketChannel serverSocketChannel) {
        this.serverSocketChannel = serverSocketChannel;
    }

    public AsynchronousServerSocketChannel channel() { return serverSocketChannel; }

    public CompletableFuture<AsynchronousSocketChannel> accept() {
        CompletableFuture<AsynchronousSocketChannel> future = new CompletableFuture<>();
        serverSocketChannel.accept(future, new TapHandler<AsynchronousSocketChannel>());
        return future;
    }

    public void close() {
        try {
            serverSocketChannel.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
