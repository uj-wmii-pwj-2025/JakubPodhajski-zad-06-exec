package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ExecServiceTest {

    @Test
    void testSubmitCallableNull(){
        MyExecService s = MyExecService.newInstance();
        assertThrows(NullPointerException.class, () -> {
            s.submit((StringCallable) null);
        });

    }

    @Test
    void testSubmitRunnableNull(){
        MyExecService s = MyExecService.newInstance();
        assertThrows(NullPointerException.class, () -> {
            s.submit((Runnable) null);
        });
        assertThrows(NullPointerException.class, () -> {
            s.submit((Runnable) null, "123");
        });
    }

    @Test
    void testSubmitCallable() throws ExecutionException, InterruptedException {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("123", 10);
        Future<String> f = s.submit(c);

        doSleep(100);
        assertTrue(f.isDone());
        assertEquals("123", f.get());
    }
    @Test
    void testSubmitRunnableWithResult() throws ExecutionException, InterruptedException {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Future<String> f = s.submit(r, "abc");
        doSleep(100);
        assertTrue(f.isDone());
        assertEquals("abc", f.get());
    }
    @Test
    void testSubmitRunnableWithoutResult() throws ExecutionException, InterruptedException {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Future<String> f = s.submit(r, null);
        doSleep(100);
        assertTrue(f.isDone());
        assertNull(f.get());
    }

    @Test
    void testInvokeAll() throws ExecutionException, InterruptedException {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> list = List.of(
                new StringCallable("asd", 12),
                new StringCallable("zzz", 34),
                new StringCallable("bar", 67),
                new StringCallable("foo", 89)
        );

        List<Future<String>> ans = s.invokeAll(list);
        assertEquals(4, ans.size());
        for (Future<String> a : ans) {
            assertTrue(a.isDone());
            assertNotNull(a.get());
        }
        assertEquals("asd", ans.get(0).get());
        assertEquals("zzz", ans.get(1).get());
        assertEquals("bar", ans.get(2).get());
        assertEquals("foo", ans.get(3).get());
    }
    @Test
    void testInvokeAny() throws ExecutionException, InterruptedException {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> list = List.of(
                new StringCallable("fast", 10),
                new StringCallable("slow", 500),
                new StringCallable("slow2", 500)
        );

        String result = s.invokeAny(list);

        assertEquals("fast", result);
    }

    @Test
    void testInvokeAnyNull() {
        MyExecService s = MyExecService.newInstance();
        assertThrows(NullPointerException.class, () -> {
            s.invokeAny(null);
        });
    }
    @Test
    void testInvokeAnyWithNullTask() {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> list = new ArrayList<>();
        list.add(new StringCallable("ok", 100));
        list.add(null); // To powinno wywołać NPE wewnątrz pętli submitującej

        assertThrows(NullPointerException.class, () -> {
            s.invokeAny(list);
        });
    }

    @Test
    void testInvokeAnyEmptyCollection() {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> list = new ArrayList<>();

        // Twoja implementacja rzuca ExecutionException(RuntimeException) gdy lista jest pusta
        // (pętla się nie wykonuje, lastException jest null)
        assertThrows(ExecutionException.class, () -> {
            s.invokeAny(list);
        });
    }

    @Test
    void testInvokeAnyAllTasksFail() {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> list = List.of(
                () -> { throw new RuntimeException("Error 1"); },
                () -> { throw new IOException("Error 2"); },
                () -> { throw new IllegalStateException("Error 3"); }
        );

        assertThrows(ExecutionException.class, () -> {
            s.invokeAny(list);
        });
    }

    @Test
    void testInvokeAnyWithTimeoutSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> list = List.of(
                new StringCallable("fast", 10),
                new StringCallable("slow", 500)
        );

        String result = s.invokeAny(list, 200, TimeUnit.MILLISECONDS);
        assertEquals("fast", result);
    }


    @Test
    void testInvokeAnyWithTimeoutExceeded() {
        MyExecService s = MyExecService.newInstance();
        List<StringCallable> list = List.of(
                new StringCallable("slow", 200),
                new StringCallable("slower", 300)
        );

        assertThrows(TimeoutException.class, () -> {
            s.invokeAny(list, 50, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    void testInvokeAnyWithTimeoutAndNullTasks() {
        MyExecService s = MyExecService.newInstance();
        assertThrows(NullPointerException.class, () -> {
            s.invokeAny(null, 1, TimeUnit.SECONDS);
        });
    }
    @Test
    void testShutdownWithPendingTasks() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r1 = new TestRunnable();
        TestRunnable r2 = new TestRunnable();
        s.submit(() -> doSleep(200));
        s.submit(r1);
        s.submit(r2);
        s.shutdown();

        doSleep(999);

        assertTrue(r1.wasRun, "After the shutdown");
        assertTrue(r2.wasRun, "After the shutdown");
    }
    @Test
    void testAwaitTermination() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();

        s.submit(() -> doSleep(100));

        s.shutdown();
        boolean terminated = s.awaitTermination(500, TimeUnit.MILLISECONDS);

        assertTrue(terminated, "Should return true because task finished within timeout");
        assertTrue(s.isTerminated(), "Executor should be in terminated state");
    }
    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.execute(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnable() {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        s.submit(r);
        doSleep(10);
        assertTrue(r.wasRun);
    }

    @Test
    void testScheduleRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnable r = new TestRunnable();
        Object expected = new Object();
        Future<Object> f = s.submit(r, expected);
        doSleep(10);
        assertTrue(r.wasRun);
        assertTrue(f.isDone());
        assertEquals(expected, f.get());
    }

    @Test
    void testScheduleCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        StringCallable c = new StringCallable("X", 10);
        Future<String> f = s.submit(c);
        doSleep(20);
        assertTrue(f.isDone());
        assertEquals("X", f.get());
    }

    @Test
    void testShutdown() {
        ExecutorService s = MyExecService.newInstance();
        s.execute(new TestRunnable());
        doSleep(10);
        s.shutdown();
        assertThrows(
            RejectedExecutionException.class,
            () -> s.submit(new TestRunnable()));
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }



}

class StringCallable implements Callable<String> {

    private final String result;
    private final int milis;

    StringCallable(String result, int milis) {
        this.result = result;
        this.milis = milis;
    }

    @Override
    public String call() throws Exception {
        ExecServiceTest.doSleep(milis);
        return result;
    }
}
class TestRunnable implements Runnable {

    boolean wasRun;
    @Override
    public void run() {
        wasRun = true;
    }
}
