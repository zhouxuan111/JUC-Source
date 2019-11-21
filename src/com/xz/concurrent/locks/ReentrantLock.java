package com.xz.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 1.简介：独占锁 可重入锁 分为公平模式和非公平模式
 * 2.非公平锁的效率高 当一个线程请求非公平锁的时候 若发出请求的同时该锁变成可用状态
 * 该线程就会跳过队列中所有的等待线程而获取锁，也仅是在在锁可用状态 要是不可用 还是要进入队列排队
 * * 在恢复一个被挂起的线程与该线程真正运行之前存在着严重的延迟。
 * 3.每次加锁  将锁的state累加1 ，每次释放锁 state减1
 */
public class ReentrantLock implements Lock, java.io.Serializable {

    private static final long serialVersionUID = 7373984872572414699L;

    /**
     * 属性
     */
    private final Sync sync;

    /*----------------------------Sync类-------------------------------*/

    /**
     * 继承AQS 实现独占锁模式 作为基础内部类
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 加锁  需要实现类重现实现
         */
        abstract void lock();

        /**
         * 非公平的尝试获取锁
         * acquires = 1
         * tryLock()的实现  非公平锁和公平锁的实现相同
         */
        final boolean nonfairTryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            //调用父类方法 获取锁的状态
            int c = getState();
            //非公平锁 锁未被占用 直接用CAS尝试获取锁 若成功 直接退出
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            //如果当前线程为锁的拥有者  可重入锁的实现
            else if (current == getExclusiveOwnerThread()) {
                //重入锁 进行+1操作
                int nextc = c + acquires;
                if (nextc < 0) {
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
            //判断当前拥有锁的线程是不是当前线程
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }

            boolean free = false;
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            //更改锁的状态
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

        /**
         * 获取当前锁的Condition对象
         * @return
         */
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        /**
         * 返回拥有锁的线程
         * @return
         */
        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        /**
         * 返回拥有锁的数量
         * @return
         */
        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        /**
         * 判断锁是否被占用
         * @return
         */
        final boolean isLocked() {
            return getState() != 0;
        }

        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0);
        }
    }

    /*------------------------------非公平锁的实现-------------------------------*/

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {

        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 获取锁  state  = 0 锁未被占用 state = 1 锁被占用
         * 对比公平锁 非公平锁尝试直接用CAS获取锁 若失败 才会调用AQS的acquire(1)方法
         */
        @Override
        final void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            }
            else {
                //父类 AQS模板方法 尝试获取
                /**
                 * <pre>{@code
                 *  public void final acquire(int arg){
                 *      if(!tryAcquire(arr) &&
                 *          acquireQueue(addWaiter(Node.EXCLUSIVE),arg)){
                 *          selfInterrupted();
                 *      }
                 *  }
                 * }</>
                 * 只有tryAcquire(int arg)由子类实现 其余均由AQS实现
                 */
                acquire(1);
            }
        }

        /**
         * 获取锁 此时实现的是AQS的tryAcquire(int arg)方法
         * @param acquires
         * @return
         */
        @Override
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /*-----------------------------公平锁的实现-------------------------*/

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {

        private static final long serialVersionUID = -3000897897090466540L;

        /**
         * 获取锁
         */
        @Override
        final void lock() {
            acquire(1);
        }

        /**
         * 获取锁
         * @param acquires
         * @return
         */
        @Override
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            //当前锁没有被占用
            if (c == 0) {
                //没有前驱 保证是队列的次头结点 尝试获取锁 并设置成功
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

    /*--------------------------------构造方法--------------------------------*/

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

    /*---------------------------------功能实现----------------------------------*/


    /*-------------实现Lock接口的方法----------------*/

    /**
     * Lock接口的方法的具体实现均交给Sync类
     */

    /**
     * 获取锁
     */
    @Override
    public void lock() {
        sync.lock();
    }

    /**
     * 响应中断的获取锁  公平锁和非公平锁的实现相同 直接调用AQS的方法
     * <pre>{code
     *   public final void acquireInterruptibly(int arg) throws InterruptedException{
     *       if(Thread.interrupted()){
     *           throw new InterruptedException();
     *       }
     *       if(!tryAcquire(arg)){
     *           // 以自旋的方式获取锁 不断检查前驱是不是头结点
     *           doAcquireInterruptibly(arg);
     *       }
     *   }
     * }</>
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * 尝试获取锁 直接尝试CAS获取锁 若成功返回true 否则返回false
     * 无论是公平锁还是非公平锁 调用改方法 都会直接尝试获取锁 所以
     * 调用非公平锁的nonfairTryAcquire()
     */
    @Override
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 带超时时间的获取锁 想用中断
     */
    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 释放当前线程占用的锁
     * 调用AQS的release()方法  release()方法中的tryRelease()方法由Sync实现
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

    /*-----------------功能方法--------------------*/

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
     *   }
     * }}</pre>
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

    @Override
    public String toString() {
        Thread o = sync.getOwner();
        return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
    }
}
