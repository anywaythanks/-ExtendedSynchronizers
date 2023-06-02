import org.openjdk.jcstress.annotations.*;
import sync.Ticker;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;
import static org.openjdk.jcstress.annotations.Mode.Termination;


public class TickTest {
    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "tick() blocks")
    @Description(value = "Checks if tick() blocks if no one has timed out.")
    @State
    public static class OneTickTest {
        @Actor
        public void tick() {
            final var ticker = new Ticker();
            try {
                ticker.tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Signal
        public void signal() {
            //ignore
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "Incorrect")
    @Description(value = "Checks if the interrupt implementation is correct.")
    @State
    public static class InterruptTest {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(1);

        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            try {
                ticker.reserve(0);
                ticker.tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }


        @Signal
        public void signal() {
            try {
                threads.take().interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "No blocked.")
    @Description(value = "Checks if tick() blocks when calling reserve().")
    @State
    public static class BlockTest1 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(1);
        private final AtomicLong tick = new AtomicLong(0);

        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            try {
                ticker.reserve(0);
                ticker.tick();
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
    @Description(value = "Checks if tick() blocks when calling reserve().")
    @State
    public static class BlockTest2 {
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(1);
        private final AtomicLong tick = new AtomicLong(0);

        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            final CountDownLatch started = new CountDownLatch(1), work = new CountDownLatch(1);
            var th = new Thread(() -> {
                try {
                    ticker.reserve(0);
                    started.countDown();
                    work.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            th.start();
            try {
                started.await();
                ticker.tick();
                tick.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                work.countDown();
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
    @Description(value = "Checks if N tick() blocks before reserve(0).")
    @State
    public static class NTickBlockTest {
        private static final int N = 10;
        BlockingQueue<Thread> threads = new ArrayBlockingQueue<>(N + 1);
        private final AtomicLong tick = new AtomicLong(0);


        @Actor
        public void tick() {
            threads.add(Thread.currentThread());
            final var ticker = new Ticker();
            final CountDownLatch reserved = new CountDownLatch(1),
                    worked = new CountDownLatch(N + 1);
            var resTh = new Thread(() -> {
                try {
                    ticker.reserve(0);
                    reserved.countDown();
                    worked.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            resTh.start();
            try {
                reserved.await();
                for (int i = 0; i < N; ++i) {
                    new Thread(() -> {
                        try {
                            threads.add(Thread.currentThread());
                            ticker.tick();
                            tick.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            worked.countDown();
                        }
                    }).start();
                }
                ticker.tick();
                tick.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                worked.countDown();
            }
        }

        @Signal
        public void signal() {
            Thread[] threads = Stream.generate(() -> {
                try {
                    return this.threads.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }).limit(N + 1).toArray(Thread[]::new);
            boolean isWait = false;
            for (var th : threads) {
                if (th.isAlive()) {
                    th.interrupt();
                } else {
                    isWait = true;
                }
            }
            if (isWait && tick.get() > 0) try {
                while (true) wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}