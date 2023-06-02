package sync;

import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * The conditional time stream that comes from calls to the {@link #tick()} method. Can block threads based on this relative time.
 * Conditionally {@link Thread#sleep(long)}, but in relative time, as well as a <i>happens-before</i> guarantee.
 * <p>
 * Guarantees a <i>happens-before</i> relationship for {@code t1} and {@code t2} if {@link #await()} {@code t1} exits earlier by exit ticks in {@code t2}.
 * In other words, if the reserved value in {@code t1} is less than in {@code t2}, calling {@link #await()} on both threads guarantees connectivity.
 */
public class Ticker {
    private volatile long nowTick;
    private final AtomicBoolean isWait;
    private final Object updateMonitor;
    private final ThreadLocal<ThreadReserveTick> reserveTick;
    private final ConcurrentSkipListSet<ThreadReserveTick> reserves;
    private static final ThreadReserveTick MAX_VALUE = new ThreadReserveTick(Long.MAX_VALUE, null, false, -1);

    record ThreadReserveTick(long tick, Thread thread, boolean isAwait, long id) {
        private static final AtomicLong ID = new AtomicLong(0);

        public ThreadReserveTick() {
            this(0);
        }

        public ThreadReserveTick(long tick) {
            this(tick, false);
        }

        public ThreadReserveTick(long tick, boolean isAwait) {
            this(tick, Thread.currentThread(), isAwait);
        }

        public ThreadReserveTick(long tick, Thread thread, boolean isAwait) {
            this(tick, thread, isAwait, ID.incrementAndGet());
        }

        public ThreadReserveTick setTick(long tick) {
            return new ThreadReserveTick(tick, thread, isAwait, id);
        }

        public ThreadReserveTick setAwait(boolean isAwait) {
            return new ThreadReserveTick(tick, thread, isAwait, id);
        }
    }

    public Ticker() {
        nowTick = 0;
        updateMonitor = new Object();
        reserveTick = ThreadLocal.withInitial(ThreadReserveTick::new);
        reserves = new ConcurrentSkipListSet<>(Comparator
                .comparingLong(ThreadReserveTick::tick)
                .thenComparingLong(ThreadReserveTick::id)
                .thenComparing(t -> t.isAwait ? 1 : -1));
        reserves.add(MAX_VALUE);
        isWait = new AtomicBoolean(false);
    }

    /**
     * Blocks the thread if:
     * <p>
     * 1. Someone is already present in tick();
     * <p>
     * 2. If there is no free window in this call, when you can increase {@code tick()};
     * This means that there is not a single thread that has less than one time reserved. In other words, all threads since the last call to {@code tick()}
     * called {@link #reserve(int)} with an argument greater than zero.
     */
    public void tick() throws InterruptedException {
        synchronized (reserves) {//only one thread can wait
            waitUpdate();
            updateNowTick();
            waitUpdate();
        }
    }

    /**
     * Reserves ticks that don't need to block {@link #tick()} calls.
     */
    public void reserve(final int tick) {
        if (tick < 0) throw new IllegalArgumentException("tick < 0");
        final var newVar = reserveTick.get()
                .setTick(Math.max(getNowTick(), reserveTick.get().tick) + tick);
        reserves.add(newVar);
        if (tick != 0) reserves.remove(reserveTick.get());
        reserveTick.set(newVar);
        if (getNowTick() < reserves.first().tick) notifyUpdate();
    }

    /**
     * Blocks the thread until all reserved ticks have expired.
     */
    public void await() throws InterruptedException {
        if (reserveTick.get().tick == 0) reserve(0);
        final var newVar = reserveTick.get().setAwait(true);
        reserves.add(newVar);
        while (getNowTick() < reserveTick.get().tick) {
            LockSupport.park(this);
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        }
        reserves.remove(newVar);
    }

    /**
     * Removes a thread from the accounting queue.
     */
    public void relax() {
        reserves.remove(reserveTick.get());
        notifyUpdate();
    }

    /**
     * Calls {@link #await()} and then {@link #reserve(int)}.
     */
    public void awaitAndReserve(final int tick) throws InterruptedException {
        if (tick < 0) throw new IllegalArgumentException("tick < 0");
        await();
        reserve(tick);
    }

    /**
     * Calls {@link #reserve(int)} and then {@link #await()}.
     */
    public void reserveAndAwait(final int tick) throws InterruptedException {
        if (tick < 0) throw new IllegalArgumentException("tick < 0");
        reserve(tick);
        await();
    }

    /**
     * Someone is definitely waiting for notification and no one has yet gone to notify.
     */
    private void notifyUpdate() {
        if (isWait.compareAndSet(true, false)) {
            synchronized (updateMonitor) {
                updateMonitor.notify();
            }
        }
    }

    private void waitUpdate() throws InterruptedException {
        synchronized (updateMonitor) {
            while (true) {
                for (var t : reserves) {
                    if (t.tick != getNowTick()) break;
                    if (t.isAwait) LockSupport.unpark(t.thread);
                }
                isWait.set(true);
                if (getNowTick() >= reserves.first().tick) updateMonitor.wait();
                else {
                    isWait.set(false);
                    break;
                }
            }
        }
    }

    /**
     * lock-free.
     */
    private long getNowTick() {
        return nowTick;
    }

    private void updateNowTick() {
        synchronized (updateMonitor) {
            nowTick++;
        }
    }
}