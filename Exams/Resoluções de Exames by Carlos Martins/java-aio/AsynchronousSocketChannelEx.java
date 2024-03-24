import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;
import java.net.SocketAddress;

public class AsynchronousSocketChannelEx implements Closeable {

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

    private final AsynchronousSocketChannel socketChannel;

    public AsynchronousSocketChannelEx(AsynchronousSocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

    public AsynchronousSocketChannel channel() { return socketChannel; }

    public CompletableFuture<Void> connect(SocketAddress remote) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        socketChannel.connect(remote, future, new TapHandler<Void>());
        return future;
    }

    public CompletableFuture<Long> read(ByteBuffer[] dsts, int offset, int length,
                                        long timeout, TimeUnit unit) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        socketChannel.read(dsts, offset, length, timeout, unit, future, new TapHandler<Long>());
        return future;
    }

    public CompletableFuture<Integer> read(ByteBuffer dst) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        socketChannel.read(dst, future, new TapHandler<Integer>());
        return future;
    }

    public CompletableFuture<Integer> read(ByteBuffer dst, long timeout, TimeUnit unit) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        socketChannel.read(dst, timeout, unit, future, new TapHandler<Integer>());
        return future;
    }

    public CompletableFuture<Long> write(ByteBuffer[] srcs, int offset, int length,
                                        long timeout, TimeUnit unit) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        socketChannel.write(srcs, offset, length, timeout, unit, future, new TapHandler<Long>());
        return future;
    }

    public CompletableFuture<Integer> write(ByteBuffer src) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        socketChannel.write(src, future, new TapHandler<Integer>());
        return future;
    }

    public CompletableFuture<Integer> write(ByteBuffer src, long timeout, TimeUnit unit) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        socketChannel.write(src, timeout, unit, future, new TapHandler<Integer>());
        return future;
    }

    public void close() {
        try {
            socketChannel.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
