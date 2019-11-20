package com.xz.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 简介：独占锁 可重入锁 分为公平模式和非公平模式
 * <pre> {@code
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
 *   // ...
 *   public void m() {
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }}</pre>
 * @author Doug Lea
 * @since 1.5
 */
public class ReentrantLock implements Lock, java.io.Serializable {

    private static final long serialVersionUID = 7373984872572414699L;

    /**
     * Synchronizer providing all implementation mechanics
     */
    private final Sync sync;

    /**
     * 继承AQS 实现独占锁模式 作为基础内部类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 加锁
         */
        abstract void lock();

        /**
         * 非公平的尝试获取锁
         * acquires = 1
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            //调用父类方法 获取锁的状态
            int c = getState();
            if (c == 0) { //锁未被占用 若所以释放 再次获取锁
                //直接将状态设置为1  若成功 把owner设置为当前线程 退出
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //如果当前线程为锁的拥有者
            else if (current == getExclusiveOwnerThread()) {
                //重入锁 进行+1操作
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                {
                    throw new Error("Maximum lock count exceeded");
                }
                //状态设置为累加值
                setState(nextc);
                return true;
            }
            return false;
        }

        /**
         * 释放锁
         * @param releases
         * @return
         */
        @Override
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
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

        /**
         * 判断是否是当前线程用有锁
         * @return
         */
        @Override
        protected final boolean isHeldExclusively() {
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

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {

        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 获取锁
         */
        @Override
        final void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            }
            else {
                //父类 AQS模板方法 尝试获取
                acquire(1);
            }
        }

        @Override
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {

        private static final long serialVersionUID = -3000897897090466540L;

        @Override
        final void lock() {
            acquire(1);
        }

        /**
         * Fair version of tryAcquire.  Don't grant access unless
         * recursive call or no waiters or is first.
         */
        @Override
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            //当前锁没有被占用
            if (c == 0) {
                //有前驱 并设置成功
                if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //重入锁
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    /**
     * 无参构造 默认为非公平锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * 有参构造 指定公平锁 还是非公平锁
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 获取锁
     */
    @Override
    public void lock() {
        sync.lock();
    }

    /**
     * 响应中断的获取锁
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * 非公平获取锁 尝试 成功 返回
     */
    @Override
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 尝试在超时获取锁
     */
    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        sync.release(1);
    }

    /**
     * 获取锁对应的Condition对象
     */
    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * 当前线程获取锁的次数
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *   public void m() {
     *       assert lock.isHeldByCurrentThread();
     *       // ... method body
     *   }
     * }}</pre>
     * <p>It can also be used to ensure that a reentrant lock is used
     * in a non-reentrant manner, for example:
     * <pre> {@code
     * class X {
     *   ReentrantLock lock = new ReentrantLock();
     *   // ...
     *   public void m() {
     *       assert !lock.isHeldByCurrentThread();
     *       lock.lock();
     *       try {
     *           // ... method body
     *       } finally {
     *           lock.unlock();
     *       }
     *   }
     * }}</pre>
     * 判断是否是当前线程持有锁
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 获取是否占着锁 不为0：表示上锁  0：在等待 不上锁
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 判断是否为公平锁
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }

    /**
     * 获取锁的拥有者的线程
     * 状态 = 0 返回null 说明未上锁
     * 不为0 返回当前线程
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 判断是否有等待线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     *
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 判断等待线程的长度 同步队列的长度
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 获取等待线程列表
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 判断是否有等待队列
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.hasWaiters((java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 获取等待队列的长度
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync
                .getWaitQueueLength((java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 获取条件队列的线程集合
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof java.util.concurrent.locks.AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     *
     */
    @Override
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
