import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.I_Result;
import sync.Ticker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;
import static org.openjdk.jcstress.annotations.Mode.Termination;

public class AwaitTest {

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "No blocked.")
    @Description(value = "Checks if await() blocks when calling reserve(1).")
    @State
    public static class BlockTest1 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(1);
        private final AtomicLong tick = new AtomicLong(0);

        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            try {
                ticker.reserveAndAwait(1);
                tick.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Signal
        public void signal() {
            Thread thread;
            try {
                thread = threads.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (thread.isAlive()) {
                thread.interrupt();
            } else if (tick.get() == 1) try {
                while (true) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "No blocked.")
    @Description(value = "Checks if await() blocks when calling reserve(1).")
    @State
    public static class BlockTest2 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(1);
        private final AtomicLong tick = new AtomicLong(0);

        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            try {
                ticker.reserve(1);
                ticker.await();
                tick.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Signal
        public void signal() {
            Thread thread;
            try {
                thread = threads.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (thread.isAlive()) {
                thread.interrupt();
            } else if (tick.get() == 1) try {
                while (true) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "No blocked.")
    @Description(value = "Checks if await() blocks when calling reserve(1).")
    @State
    public static class BlockTest3 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(2);
        private final AtomicLong tick = new AtomicLong(0);

        @Actor
        public void await() {
            final var ticker = new Ticker();
            final var work = new CountDownLatch(2);
            new Thread(() -> {
                threads.add(Thread.currentThread());
                try {
                    ticker.reserveAndAwait(1);
                    tick.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    work.countDown();
                }
            }).start();
            new Thread(() -> {
                threads.add(Thread.currentThread());
                try {
                    ticker.reserve(1);
                    ticker.await();
                    tick.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    work.countDown();
                }
            }).start();
            try {
                work.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Signal
        public void signal() {
            final Thread[] threads;
            try {
                threads = new Thread[]{this.threads.take(), this.threads.take()};
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (threads[0].isAlive() && threads[1].isAlive()) {
                for (var th : threads) th.interrupt();
            } else if (tick.get() > 0) try {
                while (true) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "No unblocked.")
    @Description(value = "Checks if await() blocks when calling reserve(1) and unblock.")
    @State
    public static class BlockTest4 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(2);
        private final AtomicLong tick = new AtomicLong(0);
        Ticker ticker = new Ticker();

        @Actor
        public void await() {
            final var work = new CountDownLatch(2);
            new Thread(() -> {
                ticker.reserve(0);
                threads.add(Thread.currentThread());
                try {
                    ticker.reserveAndAwait(1);
                    tick.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    ticker.relax();
                    work.countDown();
                }
            }).start();
            new Thread(() -> {
                ticker.reserve(0);
                threads.add(Thread.currentThread());
                try {
                    ticker.reserve(1);
                    ticker.await();
                    tick.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    ticker.relax();
                    work.countDown();
                }
            }).start();
            try {
                work.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Signal
        public void signal() {
            final Thread[] threads;
            try {
                threads = new Thread[]{this.threads.take(), this.threads.take()};
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (threads[0].isAlive() && threads[1].isAlive()) {
                try {
                    ticker.tick();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else if (tick.get() > 0) try {
                while (true) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @JCStressTest()
    @Outcome(id = "2", expect = ACCEPTABLE, desc = "OK")
    @Description(value = "Checking the visibility guarantee of the await() that happened before.")
    @State
    public static class HappensBeforeTest {
        private final CountDownLatch latch = new CountDownLatch(2);
        private final Ticker ticker = new Ticker();

        @Actor
        public void th1(I_Result r) {
            ticker.reserve(1);
            latch.countDown();
            try {
                ticker.await();
                r.r1++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ticker.relax();
            }
        }

        @Actor
        public void th2(I_Result r) {
            ticker.reserve(2);
            latch.countDown();
            try {
                ticker.await();
                r.r1++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ticker.relax();
            }
        }

        @Actor
        public void ticker() {
            try {
                latch.await();
                ticker.tick();
                ticker.tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
