package uj.wmii.pwj.exec;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {


    private final BlockingQueue<Runnable> queue;
    private final Thread worker;
    private boolean isShutDown;
    private boolean isTerminated;


    public MyExecService(){
        this.queue = new LinkedBlockingQueue<>();
        this.isShutDown = false;
        this.isTerminated = false;
        this.worker = new Thread(this::process);
        this.worker.start();
    }

    private void process(){
        while (!isShutDown || !queue.isEmpty()){
            try {
                Runnable task = queue.take();
                task.run();
            }
            catch (Exception e){
                continue;
            }
        }
    }


    static MyExecService newInstance() {
        return new MyExecService();
    }

    @Override
    public void shutdown() {
        this.isShutDown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        this.isShutDown = true;
        List<Runnable> remainingTasks = new ArrayList<>();
        queue.drainTo(remainingTasks);
        return remainingTasks;
    }

    @Override
    public boolean isShutdown() {
        return isShutDown;
    }

    @Override
    public boolean isTerminated() {
        return isTerminated;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long millis = unit.toMillis(timeout);


        if (timeout <= 0) {
            return !worker.isAlive();
        }

        worker.join(millis);

        if (!worker.isAlive()) {
            this.isTerminated = true;
            return true;
        }
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (this.isShutDown) {
            throw new RejectedExecutionException();
        }
        if (task == null){
            throw new NullPointerException();
        }
        FutureTask<T> futureTask = new FutureTask<>(task);
        boolean success = queue.offer(futureTask);
        if (!success){
            throw new RejectedExecutionException();
        }

        return futureTask;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (this.isShutDown) {
            throw new RejectedExecutionException();
        }
        if (task == null){
            throw new NullPointerException();
        }
        FutureTask<T> futureTask = new FutureTask<>(task, result);
        boolean success = queue.offer(futureTask);
        if (!success){
            throw new RejectedExecutionException();
        }

        return futureTask;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (this.isShutDown) {
            throw new RejectedExecutionException();
        }
        if (task == null){
            throw new NullPointerException();
        }
        FutureTask<?> futureTask = new FutureTask<>(task, null);
        boolean success = queue.offer(futureTask);
        if (!success){
            throw new RejectedExecutionException();
        }

        return futureTask;

    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        List<Future<T>> promises = new ArrayList<>(tasks.size());

        for (Callable<T> task : tasks) {
            if (task == null) throw new NullPointerException();
            promises.add(submit(task));
        }

        try {
            for (Future<T> future : promises) {
                if (!future.isDone()) {
                    try {
                        future.get();
                    } catch (ExecutionException | CancellationException e) {
                    }
                }
            }
        } catch (Throwable t) {
            for (Future<T> f : promises) {
                f.cancel(true);
            }
            throw t;
        }
        return promises;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + nanos;
        List<Future<T>> promises = new ArrayList<>(tasks.size());

        try {
            for (Callable<T> task : tasks) {
                if (task == null) throw new NullPointerException();
                promises.add(submit(task));
            }

            for (Future<T> future : promises) {
                if (!future.isDone()) {
                    long timeLeft = deadline - System.nanoTime();
                    if (timeLeft <= 0) {
                        return promises;
                    }
                    try {
                        future.get(timeLeft, TimeUnit.NANOSECONDS);
                    } catch (CancellationException | ExecutionException e) {
                    } catch (TimeoutException e) {
                        return promises;
                    }
                }
            }
            return promises;
        } finally {
            for (Future<T> future : promises) {
                if (!future.isDone()) {
                    future.cancel(true);
                }
            }
        }
    }
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        List<Future<T>> promises = new ArrayList<>(tasks.size());
        ExecutionException lastException = null;

        try {
            for (Callable<T> task : tasks) {
                if (task == null) throw new NullPointerException();
                promises.add(submit(task));
            }

            List<Future<T>> activePromises = new ArrayList<>(promises);

            while (!activePromises.isEmpty()) {
                Iterator<Future<T>> it = activePromises.iterator();
                while (it.hasNext()) {
                    Future<T> future = it.next();
                    if (future.isDone()) {
                        try {
                            return future.get();
                        } catch (CancellationException e) {
                            it.remove();
                        } catch (ExecutionException e) {
                            lastException = e;
                            it.remove();
                        }
                    }
                }
                if (!activePromises.isEmpty()) {
                    Thread.yield();
                }
            }

            if (lastException == null) {
                throw new ExecutionException(new RuntimeException("No tasks successfully completed"));
            }
            throw lastException;

        } finally {
            for (Future<T> f : promises) {
                f.cancel(true);
            }
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + nanos;
        List<Future<T>> promises = new ArrayList<>(tasks.size());
        ExecutionException lastException = null;

        try {
            for (Callable<T> task : tasks) {
                if (task == null) throw new NullPointerException();
                promises.add(submit(task));
            }

            List<Future<T>> activePromises = new ArrayList<>(promises);

            while (!activePromises.isEmpty()) {
                Iterator<Future<T>> it = activePromises.iterator();
                while (it.hasNext()) {
                    Future<T> future = it.next();
                    if (future.isDone()) {
                        try {
                            return future.get();
                        } catch (CancellationException e) {
                            it.remove();
                        } catch (ExecutionException e) {
                            lastException = e;
                            it.remove();
                        }
                    }
                }

                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    throw new TimeoutException();
                }

                if (!activePromises.isEmpty()) {
                    Thread.yield();
                }
            }

            if (lastException == null) {
                throw new ExecutionException(new RuntimeException("No tasks successfully completed"));
            }
            throw lastException;

        } finally {
            for (Future<T> f : promises) {
                f.cancel(true);
            }
        }
    }
    @Override
    public void execute(Runnable command) {
        if (command == null){
            throw new NullPointerException();
        }
        if (isShutDown){
            throw new RejectedExecutionException();
        }
        submit(command);

    }
}
