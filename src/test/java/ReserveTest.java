import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.ZZZ_Result;
import sync.Ticker;

import java.util.concurrent.CountDownLatch;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;
import static org.openjdk.jcstress.annotations.Mode.Termination;

public class ReserveTest {
    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "tick() blocks")
    @Description(value = "Checks if tick() blocks on reserve(2).")
    @State
    public static class TwoReserveTest1 {
        @Actor
        public void tick() {
            final var ticker = new Ticker();
            try {
                ticker.reserve(2);
                ticker.tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ticker.relax();
            }
        }

        @Signal
        public void signal() {
            //ignore
        }
    }

    @JCStressTest(Termination)
    @Outcome(id = "TERMINATED", expect = ACCEPTABLE, desc = "OK")
    @Outcome(id = "STALE", expect = FORBIDDEN, desc = "tick() blocks")
    @Description(value = "Checks if tick() blocks on reserve(2).")
    @State
    public static class TwoReserveTest2 {
        @Actor
        public void tick() {
            final var ticker = new Ticker();
            final CountDownLatch started = new CountDownLatch(1), work = new CountDownLatch(1);
            try {
                new Thread(() -> {
                    try {
                        ticker.reserve(2);
                        started.countDown();
                        work.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        ticker.relax();
                    }
                }).start();
                started.await();
                ticker.tick();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                work.countDown();
            }
        }

        @Signal
        public void signal() {
            //ignore
        }
    }

    @JCStressTest()
    @Outcome(id = "false, false, false", expect = ACCEPTABLE, desc = "OK")
    @Description(value = "Checks if invalid values can be passed.")
    @State
    public static class IllegalArgumentTest {
        private final Ticker ticker = new Ticker();

        @Actor
        public void res(ZZZ_Result z) {
            try {
                ticker.reserve(-1);
            } catch (IllegalArgumentException ex) {
                z.r1 = false;
                return;
            } finally {
                ticker.relax();
            }
            z.r1 = true;
        }

        @Actor
        public void resAw(ZZZ_Result z) {
            try {
                ticker.reserveAndAwait(-1);
            } catch (IllegalArgumentException ex) {
                z.r2 = false;
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ticker.relax();
            }
            z.r2 = true;
        }

        @Actor
        public void awRes(ZZZ_Result z) {
            try {
                ticker.awaitAndReserve(-1);
            } catch (IllegalArgumentException ex) {
                z.r3 = false;
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ticker.relax();
            }
            z.r3 = true;
        }
    }
}
