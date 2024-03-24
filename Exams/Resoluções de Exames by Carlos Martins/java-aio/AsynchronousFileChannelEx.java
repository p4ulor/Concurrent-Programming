import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.concurrent.*;

public class AsynchronousFileChannelEx implements Closeable {

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

    private final AsynchronousFileChannel fileChannel;

    public AsynchronousFileChannelEx(AsynchronousFileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    public AsynchronousFileChannel channel() { return fileChannel; }

    public CompletableFuture<FileLock> lock() {
        CompletableFuture<FileLock> future = new CompletableFuture<>();
        fileChannel.lock(future, new TapHandler<FileLock>());
        return future;
    }

    public CompletableFuture<FileLock> lock(long position, long size, boolean shared) {
        CompletableFuture<FileLock> future = new CompletableFuture<>();
        fileChannel.lock(position, size, shared, future, new TapHandler<FileLock>());
        return future;
    }

    public CompletableFuture<Integer> read(ByteBuffer dst, long position) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        fileChannel.read(dst, position, future, new TapHandler<Integer>());
        return future;
    }

    public CompletableFuture<Integer> write(ByteBuffer src, long position) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        fileChannel.write(src, position, future, new TapHandler<Integer>());
        return future;
    }

    public void close() {
        try {
            fileChannel.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
