class RunnableImpl implements Runnable {
    public void run() {
        System.out.printf("--other2(%d): I started \n", Thread.currentThread().getId());
        try {
            Thread.sleep(2500);
        } catch (InterruptedException ie) {}
        System.out.println("--other2: I finished");
    }
}

class MyThread extends Thread {
    public void run() { //thread body
        System.out.printf("--mythread (daemon) (%d): starts and loops indefinitely\n", Thread.currentThread().getId());
        while (true);
    }
}

public class Main {
    public static void main(String... args) throws InterruptedException {
        System.out.printf("--Main thread(%d): starts\n", Thread.currentThread().getId());

        Thread other = new Thread(() -> {
            System.out.printf("--other(%d): I started\n", Thread.currentThread().getId());
            try {
                Thread.sleep(5000); //since this thread will sleep, the primary will continue its execution
            } catch (InterruptedException ie) {
                println("--other: I was interrupted!!!");
            }
            println("--other: I finished");
        });

        println("--Main thread: starts other thread\n");
        other.setPriority(Thread.MAX_PRIORITY);
        other.start();

        println("--Main thread: starts other2 thread\n");
		Thread other2 = new Thread(new RunnableImpl());
		other2.start();

		Thread mythread = new MyThread();

		//Daemon threads are terminated by the JVM when there are no longer any user threads running and only daemon threads running
        //runs as a low priority thread
		//including the main thread of execution
		//It's like a background thread. It will not block the other threads from running

		mythread.setDaemon(true);
		mythread.start();

        println("Waiting for the 2 other threads to finish");
        Thread.sleep(1000L); //reduce this sleep to something significantly less than 5000 to interrupt the thread
        other.interrupt();

        println("--primary: waiting for other thread to terminate"); //which would be 3s after this
        try {
            other.join();		// blocks current thread until the "other" thread terminates
        } catch (InterruptedException ie) {}
        //mythread.stop();		// abrupt termination, shouldnt be used
        println("--primary: finished");
    }

    static void println(String s){ System.out.println(s); }
}
