/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Fila copy using the Java nio2 Asynchronous I/O
 *
 * To generate the executable execute: javac FileCopyAsync.java
 *	
 * Carlos Martins, November 2019
 *
 **/

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import java.io.IOException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class FileCopyAsync {

	static final int BUFFER_SIZE = 4 * 1024;

	/**
	 * File copy using synchronous read and write operations, but asynchronous
	 * channels and providing an asynchronous interface.
	 * 
	 * This code uses the rendezvoud with completion by "polling - wait to completion"
	 * based on Future<>.
	 */
	public static <A> void copySync(String srcFile, String dstFile, A attachment,
									CompletionHandler<Long, A> handler)
											throws IOException, InterruptedException {		
		// open files for asynchronous access
		Path srcPath = Paths.get(srcFile);
		Path dstPath = Paths.get(dstFile);
		AsynchronousFileChannel src = null, dst = null;
		long filePosition = 0;
		try {
			// create the synchronous channels
			src = AsynchronousFileChannel.open(srcPath, READ);
			dst = AsynchronousFileChannel.open(dstPath, CREATE, WRITE, TRUNCATE_EXISTING);
		} catch (Throwable ex) {
			if (src != null)
				src.close();
			throw ex;		// throw synchronous exception
		}
		try {
			// allocate a buffer
			ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
			do {
				Future<Integer> readFuture = src.read(buffer, filePosition);
				/**
				 * Here we can advance work that do not depends from
				 * read data until the async read completes
				 * ...
				 */
				 // get the read result using blocking-wait polling
				int bytesRead = readFuture.get();
				if (bytesRead < 0)
					break;
				buffer.flip();
				Future<Integer> writeFuture = dst.write(buffer, filePosition);
				/**
				 * Here we can advance work that do not depends from
				 * write result until the async write completes
				 * ...
				*/
				// wait until async write completes using blocking-wait pooling
				writeFuture.get(); // wait until async write completes using pooling
				filePosition += bytesRead;
				buffer.clear();
			} while (true);
			// complete file copy successfully
			handler.completed(filePosition, attachment);
		} catch (Throwable ex) {
			// complete file copy exceptionally
			handler.failed(ex, attachment);		// throw an asynchronous exception
			return;
		}
	}

	/** 
	 * File copy asynchronous reads and asynchronous write operations, but only with two
	 * outstanding asynchronous operations.
	 * 
	 * Note: Java's asynchrounous channels allow only one pending read or write operation.
	 *
	 */

	/*
	 * Auxiliar class to encapsulate copy state a process asynchronous read and
	 * write completions
	 */
	private static class CopyState<A> {
		final Object lock = new Object();
		final A attachment;
		final CompletionHandler<Long, A> handler;
		final AsynchronousFileChannel src;
		final AsynchronousFileChannel dst;
		ByteBuffer readBuffer;
		ByteBuffer writeBuffer;
		long readPosition;
		long writePosition;
		Throwable exception; 
		static final int COPYING = 0, COMPLETING = 1, CANCELING = 2, DONE = 3;		// file copy states
		int state;
		int pending;
	
		/**
		 * Initialize copy state
		 */
		CopyState(A attachment, CompletionHandler<Long, A> handler, AsynchronousFileChannel src,
				  AsynchronousFileChannel dst) {
			this.attachment = attachment;
			this.handler = handler;
			this.src = src;
			this.dst = dst;
			this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			this.writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
			state = COPYING;
			this.pending = 1;		// taking into account the first read operation
		}

		/**
		 * Swap read and write buffers
		 */
		private void swapBuffers() {
			ByteBuffer tmp = readBuffer;
			readBuffer = writeBuffer;
			writeBuffer = tmp;
			readBuffer.clear();		// prepare the buffer for the next read 
			writeBuffer.flip();		// prepare the buffer for the next write
		}

		/**
		 * Called when the reading completes successfully
		 */
		boolean readCompleted(int result) {
			synchronized(lock) {
				pending--;					// a pending read was completed
				if (state == CANCELING) {
					if (pending == 0)
						state = DONE;
				} else if (result > 0) {
					readPosition += result;
					if (pending == 0) {
						pending = 2;
						swapBuffers();
						return true;		// the next read and write must start
					}
					// the next step occurs when the write finishes
					return false;
				} else {
					// end-of-file
					state = COMPLETING;
					if (pending == 0)
						state = DONE;
				}
			}
			if (state == DONE)
				completeCopy();
			// after end-of-file or cancellation we do not start more
			// read and write operations
			return false;
		}

		/**
		 * Called when the reading fails
		 */
		void readFailed(Throwable ex) {
			synchronized (lock) {
				if (state == COPYING)
					exception = ex;			// register the first exception
				if (--pending == 0)
					state = DONE;
				else
					state = CANCELING;		// we must wait until pending write completes
			}
			if (state == DONE)
				completeCopy();
		}

		/**
		 * Called when the writing completes successfully
		 */
		boolean writeCompleted(int result) {
			synchronized(lock) {
				writePosition += result;
				if (--pending == 0) {
					if (state == CANCELING) {
						state = DONE;
					} else if (state == COPYING) {
						pending = 2;
						swapBuffers();
						return true;		// start a new write and read operation
					} else {
						state = DONE;		// EOF: copy is completed
					}
				}
			}
			if (state == DONE)
				completeCopy();	// complete copy operation successfully
			return false;
		}

		/**
		 * Called when the writing fails
		 */
		void writeFailed(Throwable ex) {
			synchronized (lock) {
				if (state == COPYING)
					exception = ex;			// register the first exception
				if (--pending == 0)
					state = DONE;
				else
					state = CANCELING;		// we must wait until the read completes	
			}
			if (state == DONE)
				completeCopy();			// complete copy operation with failure
		}

		/**
		 * Called when copy is completed successfully or exceptionally
		 */
		private void completeCopy() {
			// close the asynchronous channels
			try {
				src.close();
				dst.close();
			} catch (IOException ioe) { }
			if (exception != null)
				handler.failed(exception, attachment);			// complete exceptionally
			else
				handler.completed(writePosition, attachment);	// complete successfully
		}
	}

	/**
	 * CompletionHandlerHolder: auxiliary class to work around the "final" issue with closures
	 */
	static class CompletionHandlerHolder<V, A> {
		CompletionHandler<V, A> value;
	 }

	/**
	 * The copy method itself
	 */
	@SuppressWarnings("unchecked")
	public static <A> void copyAsync(String srcFile, String dstFile, A attachment,
						 CompletionHandler<Long, A> handler) throws IOException {
		AsynchronousFileChannel src = null, dst = null;
		
		// open files for asynchronous access - exception must be thrown synchronously
		try {
			Path srcPath = Paths.get(srcFile);
			Path dstPath = Paths.get(dstFile);
			src = AsynchronousFileChannel.open(srcPath, READ);
			dst = AsynchronousFileChannel.open(dstPath, CREATE, WRITE, TRUNCATE_EXISTING);
		} catch (Throwable ex) {
			if (src != null)
				src.close();
			// throw a synchronous exception
			throw ex;
		}
		final CopyState<A> copyState = new CopyState<A>(attachment, handler, src, dst);
		final CompletionHandlerHolder<Integer, Void> onWriteCompletedHolder = new CompletionHandlerHolder<>();
		final CompletionHandler<Integer, Void> onReadCompleted = new CompletionHandler<>() {
			@Override
			public void completed(Integer result, Void ignore) {
				if (copyState.readCompleted(result)) {
					try {
						copyState.dst.write(copyState.writeBuffer, copyState.writePosition, null, onWriteCompletedHolder.value);
					} catch (Throwable ex) {
						copyState.writeFailed(ex); 	// throws an asynchronous exception
					}
					try {
						copyState.src.read(copyState.readBuffer, copyState.readPosition, null, this);
					} catch (Throwable ex) {
						copyState.readFailed(ex);	// throws an asynchronous exception
					}
				}
			}

			@Override
			public void failed(Throwable ex, Void ignore) {
				// throws an asynchronous exception
				copyState.readFailed(ex);
			}
		};

		onWriteCompletedHolder.value = new CompletionHandler<>() {
			@Override
			public void completed(Integer result, Void ignore) {
				if (copyState.writeCompleted(result)) {
					try {
						copyState.dst.write(copyState.writeBuffer, copyState.writePosition, null, this);
					} catch (Throwable ex) {
						copyState.writeFailed(ex);	// throws an asynchronous exception
					}
					try {
						copyState.src.read(copyState.readBuffer, copyState.readPosition, null, onReadCompleted);
					} catch (Throwable ex) {
						copyState.readFailed(ex);	// throws an asynchronous exception
					}
				}
			}

			@Override
			public void failed(Throwable ex, Void ignore) {
				copyState.writeFailed(ex);		// throws an asynchronous exception
			}
		};
		try {
			// start the first asynchronous read to start the copy process 
			copyState.src.read(copyState.readBuffer, 0L, null, onReadCompleted);
		} catch (Throwable ex) {
			copyState.readFailed(ex);		// throws an asynchronous exception
		}
	}

	public static void main(String... args) {
		final CountDownLatch done = new CountDownLatch(1);
		try {
			CompletionHandler<Long, CountDownLatch> onCopyCompleted = new CompletionHandler<>() {
				@Override
				public void completed(Long result, CountDownLatch done) {
					System.out.printf("--copy completed: %d bytes copied%n", result);
					done.countDown();
				}
				@Override
				public void failed(Throwable ex, CountDownLatch done) {
					System.out.printf("***asynchronous exception message: %s%n", ex.getClass());
					done.countDown();
				}
			};
			System.out.println("--start file copy");
			long startTime = System.currentTimeMillis();
			/*
			 * Test under Windows
			 */
//			copySync("c:\\Windows\\System32\\ntoskrnl.exe", "./kernel-copy", done, onCopyCompleted);
//			copyAsync("c:\\Windows\\System32\\ntoskrnl.exe", "./kernel-copy", done, onCopyCompleted);
			/**
			 * Test under Mac OS
			 */
			copySync("/System/Library/Kernels/kernel", "./kernel-copy", done, onCopyCompleted);
//			copyAsync("/System/Library/Kernels/kernel", "./kernel-copy", done, onCopyCompleted);
			long elapsedUntilReturn = System.currentTimeMillis() - startTime;
			done.await();
			long elapsedUntilDone = System.currentTimeMillis() - startTime;
			System.out.printf("--time elapsed until return: %d ms; time elapsed until completed: %d ms%n",
			 	 			  elapsedUntilReturn, elapsedUntilDone);
		} catch (Throwable ex) {
			System.out.printf("***synchrounous exception message: %s%n", ex.getClass());
		}
	}
}

