package com.xz.concurrent.test;

import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;

import com.xz.concurrent.locks.Lock;

/**
 * 独占锁 可重入锁
 * @author xuanzhou
 * @date 2019/11/21 14:07
 */
public class ReentrantLock implements Lock, Serializable {

    /*------------------属性--------------------------*/

    private final Sync sync;

    /*----------------------构造方法--------------------------*/

    public ReentrantLock() {
        sync = new NonFairSync();
    }

    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonFairSync();
    }

    /*-----------------------Sync类------------------------------*/
    abstract static class Sync extends AbstractQueuedSynchronizer {

        /*--------------获取锁、释放锁---------------*/
        abstract void lock();

        final boolean nonFairTryAcquire(int arg) {
            final Thread thread = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, arg)) {
                    setExclusiveOwnerThread(thread);
                    return true;
                }
            }
            else if (thread == getExclusiveOwnerThread()) {
                int next = c + arg;
                if (next < 0) {
                    throw new Error();
                }
                setState(next);
                return true;
            }
            return false;
        }

        @Override
        protected boolean tryRelease(int arg) {
            int c = getState() - arg;
            if (Thread.currentThread() == getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }
            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        /*功能方法*/

        @Override
        protected boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLock() {
            return getState() != 0;
        }
    }

    /*--------------------------------非公平锁的实现------------------------------*/

    static final class NonFairSync extends Sync {

        @Override
        void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            }
            else {
                acquire(1);
            }
        }

        @Override
        protected boolean tryAcquire(int arg) {
            return nonFairTryAcquire(arg);
        }
    }

    static final class FairSync extends Sync {

        @Override
        void lock() {
            acquire(1);
        }

        @Override
        protected boolean tryAcquire(int arg) {
            final Thread thread = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() && compareAndSetState(0, arg)) {
                    setExclusiveOwnerThread(thread);
                    return true;
                }
            }
            else if (thread == getExclusiveOwnerThread()) {
                int next = c + arg;
                if (next < 0) {
                    throw new Error();
                }
                setState(next);
                return true;
            }
            return false;
        }
    }

    @Override
    public void lock() {
        sync.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    @Override
    public boolean tryLock() {
        return sync.nonFairTryAcquire(1);
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(time));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }

    /*-------------------------ReentrantLock功能方法----------------------------*/
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public boolean isLocked() {
        return sync.isLock();
    }

    public boolean isFair() {
        return sync instanceof FairSync;
    }

    public Thread getOwner() {
        return sync.getOwner();
    }

    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    public final boolean hasQueueThread(Thread thread) {
        return sync.isQueued(thread);
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueueThreads() {
        return sync.getQueuedThreads();
    }

    public boolean hasWaiters(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not onwer");
        }
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    public int getWaitQueueLength(Condition condition) {
        if (null == condition) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (null == condition) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }
}
