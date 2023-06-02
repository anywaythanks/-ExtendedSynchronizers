import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.I_Result;
import sync.Ticker;

import java.util.concurrent.CountDownLatch;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public class CountTest {
    @JCStressTest()
    @Outcome(id = "0", expect = ACCEPTABLE, desc = "OK")
    @Description(value = "Switch the task between N threads every tick in order {@code mod N}, M times.")
    @State
    public static class SwitchTest1 {
        private final int N = 10, M = 5;

        @Actor
        public void ticker(I_Result state) {
            CountDownLatch reserved = new CountDownLatch(N);
            Ticker ticker = new Ticker();
            for (int i = 0; i < N; ++i) {
                final var c = i;
                new Thread(() -> {
                    ticker.reserve(c);
                    reserved.countDown();
                    try {
                        for (int j = 0; j < M; ++j) {
                            ticker.await();
                            state.r1++;
                            ticker.reserve(N);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        ticker.relax();
                    }
                }).start();
            }
            try {
                reserved.await();
                for (int i = 0; i < N * M; ++i) {
                    ticker.tick();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Arbiter
        public void check(I_Result state) {
            state.r1 %= N * M;
        }
    }
}
