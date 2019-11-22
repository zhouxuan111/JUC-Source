package com.xz.concurrent.locks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 1.简介：读写锁(一个读锁 一个写锁) 在读操作使用读锁 在写操作使用写锁 当写锁被获取到 后续的读写操作都会被阻塞
 * 2.特点：支持公平锁和非公平锁 可重入锁 锁降价(写锁降低为读锁)
 * 3.锁数量：读写采用32位二进制保存锁的数量 高位保存读锁  地位保存写锁 锁的数量最大为65535
 * 4.类结构
 * AbstractQueuedSynchronizer                         Lock
 * |                                        /|
 * Sync                               ReadLock  WriteLock
 * /\
 * NonFairSync   FairSync
 * 5.写锁：支持重新进入的排他锁(独占锁)
 * 6.读锁：支持重新进入的共享锁，在没有其他写线程访问时（写状态为0），读锁总是被成功的获取
 * 7.读写锁的状态获取和设置
 * *读锁状态的获取：S >> 16
 * *读锁状态的增加：S + (1 << 16)
 * *写锁状态的获取：S & 0x0000FFFF
 * *写锁状态的增加：S + 1
 */
public class ReentrantReadWriteLock implements ReadWriteLock, java.io.Serializable {

    /*------------------------属性---------------------------*/
    private static final long serialVersionUID = -6992448646407690164L;

    /**
     * 内部类对象 读锁
     */
    private final ReentrantReadWriteLock.ReadLock readerLock;

    /**
     * 内部类对象 写锁
     */
    private final ReentrantReadWriteLock.WriteLock writerLock;

    /**
     * AQS的实现类
     */
    final ReentrantReadWriteLock.Sync sync;

    /*-------------------------构造方法-------------------------------*/

    public ReentrantReadWriteLock() {
        this(false);
    }

    public ReentrantReadWriteLock(boolean fair) {
        sync = fair ? new ReentrantReadWriteLock.FairSync() : new ReentrantReadWriteLock.NonfairSync();
        readerLock = new ReentrantReadWriteLock.ReadLock(this);
        writerLock = new ReentrantReadWriteLock.WriteLock(this);
    }

    /*------------------------ReadWriteLock的实现方法------------------------*/
    @Override
    public ReentrantReadWriteLock.WriteLock writeLock() {
        return writerLock;
    }

    @Override
    public ReentrantReadWriteLock.ReadLock readLock() {
        return readerLock;
    }

    /*-----------------------------Sync 同步器类-----------------------------------*/

    abstract static class Sync extends AbstractQueuedSynchronizer {

        /*----------------属性------------------*/
        private static final long serialVersionUID = 6317671515068378041L;

        /**
         * 读锁状态偏移位 偏移16位 高16位为读锁 低16位为写锁
         */
        static final int SHARED_SHIFT = 16;

        /**
         * 读锁（增加）操作的基本单元 读锁状态+1 则状态变量值+SHARED_UNIT
         */
        static final int SHARED_UNIT = (1 << SHARED_SHIFT);

        /**
         * 读锁的最大数量(可重入锁的最大数量)
         */
        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;

        /**
         * 写锁的最大数量
         */
        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

        /**
         * 本地线程计数器
         */
        private transient ReentrantReadWriteLock.Sync.ThreadLocalHoldCounter readHolds;

        /**
         * 缓存计数器 读锁相关
         */
        private transient ReentrantReadWriteLock.Sync.HoldCounter cachedHoldCounter;

        /**
         * 第一个读线程
         */
        private transient Thread firstReader = null;

        /**
         * 第一个读线程的计数
         */
        private transient int firstReaderHoldCount;

        /*------------构造函数------------*/
        Sync() {
            //本地线程计数器
            readHolds = new ReentrantReadWriteLock.Sync.ThreadLocalHoldCounter();
            //设置AQS的状态
            setState(getState());
        }

        /*---------------内部类HoldCounter ThreadLocalHoldCounter------------*/

        /**
         * Sync内部类 计数器：HoldCounter 主要与读锁配合使用
         * 保存每个线程持有读锁的数量 每次获取锁 +1 释放锁-1
         */
        static final class HoldCounter {

            //计数器：表示读线程重入的次数
            int count = 0;

            /**
             * 获取当前线程的TID属性 tid用来唯一标识一个线程
             */
            final long tid = getThreadId(Thread.currentThread());
        }

        /**
         * Sync内部类：本地线程计数器
         */
        static final class ThreadLocalHoldCounter extends ThreadLocal<ReentrantReadWriteLock.Sync.HoldCounter> {

            /**
             * 重写初始化方法 在没有进行set的情况下 获取的都是该HoldCounter的值
             * ThreadLocal可以将对象与线程相关联
             */
            @Override
            public ReentrantReadWriteLock.Sync.HoldCounter initialValue() {
                return new ReentrantReadWriteLock.Sync.HoldCounter();
            }
        }

        /*--------------工具方法--------------*/

        /**
         * c = 是所有线程获取读锁的总数量
         * 获取读状态(占有读锁的线程数量) c = getState()
         * 直接将同步状态无符号右移16位即读锁的数量
         */
        static int sharedCount(int c) {
            return c >>> SHARED_SHIFT;
        }

        /**
         * 获取写状态(占有写锁的线程数量) c = getState()  c&2^16-1
         * 当写锁数量 == 0 时 代表当前读锁已经被获取
         */
        static int exclusiveCount(int c) {
            return c & EXCLUSIVE_MASK;
        }

        /*-----------子类需重写方法-------------*/

        /**
         * 读锁是否应该阻塞
         */
        abstract boolean readerShouldBlock();

        /**
         * 写锁是否应该阻塞
         */
        abstract boolean writerShouldBlock();

        /*-------------Sync重写AQS方法----------*/

        /**
         * 写锁的释放 与 ReentrantLock的释放锁过程相似
         */
        @Override
        protected final boolean tryRelease(int releases) {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            int nextc = getState() - releases;
            //写状态 == 0 代表被释放
            boolean free = exclusiveCount(nextc) == 0;
            if (free) {
                setExclusiveOwnerThread(null);
            }
            setState(nextc);
            return free;
        }

        /**
         * 获取写锁
         */
        @Override
        protected final boolean tryAcquire(int acquires) {

            Thread current = Thread.currentThread();
            //获取同步状态(判断锁是否被占用)
            int c = getState();
            //获取写状态
            int w = exclusiveCount(c);
            if (c != 0) { //写锁或读锁被占用  进行重入锁状态
                //写状态 == 0 代表读锁被获取 (读状态=0 代表写锁被获取)
                if (w == 0 || current != getExclusiveOwnerThread()) {
                    return false;
                }
                //判断写锁的获取次数是否找出限制
                if (w + exclusiveCount(acquires) > MAX_COUNT) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c + acquires);
                return true;
            }
            //锁未被占用 获取锁
            //写线程是否应该被阻塞
            if (writerShouldBlock() || !compareAndSetState(c, c + acquires)) {
                return false;
            }
            //不需要阻塞且同步状态修改成功
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 释放读锁
         */
        @Override
        protected final boolean tryReleaseShared(int unused) {
            Thread current = Thread.currentThread();
            //当前线程为第一个获取读锁的线程 释放两个属性
            if (firstReader == current) {
                if (firstReaderHoldCount == 1) {
                    firstReader = null;
                }
                else {
                    firstReaderHoldCount--;
                }
            }
            //不是第一个获取读锁的线程
            else {
                ReentrantReadWriteLock.Sync.HoldCounter rh = cachedHoldCounter;
                if (rh == null || rh.tid != getThreadId(current)) {
                    rh = readHolds.get();
                }
                int count = rh.count;
                if (count <= 1) {
                    readHolds.remove();
                    if (count <= 0) {
                        throw unmatchedUnlockException();
                    }
                }
                --rh.count;
            }
            //CAS更新状态
            for (; ; ) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc)) {
                    return nextc == 0;
                }
            }
        }

        private IllegalMonitorStateException unmatchedUnlockException() {
            return new IllegalMonitorStateException("attempt to unlock read lock, not locked by current thread");
        }

        /**
         * 获取读锁
         */
        @Override
        protected final int tryAcquireShared(int unused) {

            Thread current = Thread.currentThread();
            int c = getState();
            //写锁被占用 并且当前线程不占有独占锁(不能进行锁降级) 直接返回 获取失败
            if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current) {
                return -1;
            }
            //获取读状态(所有线程获取读锁状态的总和)
            int r = sharedCount(c);
            //获取成功
            if (!readerShouldBlock() && r < MAX_COUNT && compareAndSetState(c, c + SHARED_UNIT)) {
                //代表写锁被线程第一次获取
                if (r == 0) {
                    //设置第一个读线程
                    firstReader = current;
                    //读线程占用的资源数 = 1
                    firstReaderHoldCount = 1;
                }
                //当前线程为第一个读线程 重进入
                else if (firstReader == current) {
                    firstReaderHoldCount++;
                }
                //非首次获取 也不是一个获取该锁的线程 对HoldCounter进行更新
                else {
                    //获取计数器
                    HoldCounter rh = cachedHoldCounter;
                    //当前线程计数器不存在或者tid不为当前运行线程的
                    if (rh == null || rh.tid != getThreadId(current)) {
                        //缓存没有命中 从ThreadLocal进行获取
                        cachedHoldCounter = rh = readHolds.get();
                    }
                    //计数器为0 设置
                    else if (rh.count == 0) {
                        readHolds.set(rh);
                    }
                    //计数++
                    rh.count++;
                }
                return 1;
            }
            //获取读锁失败 方法fullTryAcquireShared()方法进行自旋重试
            return fullTryAcquireShared(current);
        }

        /**
         * 自旋获取读锁
         */
        final int fullTryAcquireShared(Thread current) {

            ReentrantReadWriteLock.Sync.HoldCounter rh = null;
            //自旋
            for (; ; ) {
                int c = getState();
                //读锁被占
                if (exclusiveCount(c) != 0) {
                    //不为当前线程  返回
                    if (getExclusiveOwnerThread() != current) {
                        return -1;
                    }
                }
                //该线程应该被阻塞
                else if (readerShouldBlock()) {
                    //当前线程为第一个读线程
                    if (firstReader == current) {
                        // assert firstReaderHoldCount > 0;
                    }
                    //当前线程不是第一个读线程
                    else {
                        //计数器为空
                        if (rh == null) {
                            //从缓存中获取当前线程计数器
                            rh = cachedHoldCounter;
                            //缓存未命中 或者 tid不是当前线程
                            if (rh == null || rh.tid != getThreadId(current)) {
                                //从ThreadLocal中获取
                                rh = readHolds.get();
                                if (rh.count == 0) {
                                    readHolds.remove();
                                }
                            }
                        }
                        //计数器不为空 count == 0
                        if (rh.count == 0) {
                            return -1;
                        }
                    }
                }
                //读锁组大数量
                if (sharedCount(c) == MAX_COUNT) {
                    throw new Error("Maximum lock count exceeded");
                }
                //获取成功
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    //第一个获取读锁的线程
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    }
                    //第一个获取读锁的线程重新进入
                    else if (firstReader == current) {
                        firstReaderHoldCount++;
                    }
                    //不是第一个获取
                    else {
                        //从缓存中获取
                        if (rh == null) {
                            rh = cachedHoldCounter;
                        }
                        //计数器为空或者tid非当前线程
                        if (rh == null || rh.tid != getThreadId(current)) {
                            rh = readHolds.get();
                        }
                        //设置
                        else if (rh.count == 0) {
                            readHolds.set(rh);
                        }
                        //计数++
                        rh.count++;
                        //设置缓存 因为之前缓存(因为阻塞)删除过
                        cachedHoldCounter = rh;
                    }
                    return 1;
                }
            }
        }

        /**
         * 尝试获取写锁
         */
        final boolean tryWriteLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            //读锁或者写锁被占用
            if (c != 0) {
                int w = exclusiveCount(c);
                //写状态为0  代表读锁被占用 || 或者当前不是占有锁的线程
                if (w == 0 || current != getExclusiveOwnerThread()) {
                    return false;
                }
                //写锁获取数量达到最大
                if (w == MAX_COUNT) {
                    throw new Error("Maximum lock count exceeded");
                }
            }
            //锁未被占用 且获取失败
            if (!compareAndSetState(c, c + 1)) {
                return false;
            }
            //获取成功
            setExclusiveOwnerThread(current);
            return true;
        }

        /**
         * 尝试获取读锁(以自旋的方式获取锁)
         */
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();
            for (; ; ) {
                int c = getState();
                //写锁被占用
                if (exclusiveCount(c) != 0 && getExclusiveOwnerThread() != current) {
                    return false;
                }
                //判断读锁获取数量是否达到最大
                int r = sharedCount(c);
                if (r == MAX_COUNT) {
                    throw new Error("Maximum lock count exceeded");
                }
                //获取成功
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    }
                    else if (firstReader == current) {
                        firstReaderHoldCount++;
                    }
                    else {
                        ReentrantReadWriteLock.Sync.HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current)) {
                            cachedHoldCounter = rh = readHolds.get();
                        }
                        else if (rh.count == 0) {
                            readHolds.set(rh);
                        }
                        rh.count++;
                    }
                    return true;
                }
            }
        }

        /**
         * 拥有写锁的线程是否是当前线程
         */
        @Override
        protected final boolean isHeldExclusively() {

            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        /**
         * 获取锁的Condition对象
         */
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        /**
         * 获取拥有写锁的线程
         * @return
         */
        final Thread getOwner() {
            return ((exclusiveCount(getState()) == 0) ? null : getExclusiveOwnerThread());
        }

        /**
         * 获取当前线程获取读锁的次数
         */
        final int getReadLockCount() {
            return sharedCount(getState());
        }

        /**
         * 写锁是否被占用
         */
        final boolean isWriteLocked() {
            return exclusiveCount(getState()) != 0;
        }

        /**
         * 获取当前线程获取写锁的次数
         */
        final int getWriteHoldCount() {
            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
        }

        /**
         * 当前线程获取读锁的次数
         * @return
         */
        final int getReadHoldCount() {
            if (getReadLockCount() == 0) {
                return 0;
            }

            Thread current = Thread.currentThread();
            if (firstReader == current) {
                return firstReaderHoldCount;
            }

            ReentrantReadWriteLock.Sync.HoldCounter rh = cachedHoldCounter;
            if (rh != null && rh.tid == getThreadId(current)) {
                return rh.count;
            }

            int count = readHolds.get().count;
            if (count == 0) {
                readHolds.remove();
            }
            return count;
        }

        private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            readHolds = new ReentrantReadWriteLock.Sync.ThreadLocalHoldCounter();
            setState(0);
        }

        /**
         * 获取同步状态
         */
        final int getCount() {
            return getState();
        }
    }

    /*----------------------------非公平锁------------------------------*/
    static final class NonfairSync extends ReentrantReadWriteLock.Sync {

        private static final long serialVersionUID = -8159625535654395037L;

        /**
         * 非公平锁的写锁是否应该被阻塞
         * 非公平锁 要是获取写锁(独占锁) 直接获取
         */
        @Override
        final boolean writerShouldBlock() {
            return false;
        }

        /**
         * 非公平锁的读锁是否应该被阻塞
         * 看他的前驱是否是获取读锁的 如果是 不阻塞 要是写锁 阻塞
         */
        @Override
        final boolean readerShouldBlock() {

            return apparentlyFirstQueuedIsExclusive();
        }
    }

    /*----------------------------公平锁----------------------------------*/

    /**
     * 公平锁：必须进入等待队列
     */
    static final class FairSync extends ReentrantReadWriteLock.Sync {

        private static final long serialVersionUID = -2274990926593161451L;

        /**
         * 无论是读锁还是写锁 公平锁必须进入等待队列 只需要判断是否有前驱
         */
        @Override
        final boolean writerShouldBlock() {
            return hasQueuedPredecessors();
        }

        @Override
        final boolean readerShouldBlock() {
            return hasQueuedPredecessors();
        }
    }

    /*---------------------------读锁--------------------------------*/
    public static class ReadLock implements Lock, java.io.Serializable {

        private static final long serialVersionUID = -5992448646407690164L;

        private final ReentrantReadWriteLock.Sync sync;

        /**
         * 获取读锁
         */
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * 获取共享锁 不响应中断
         */
        @Override
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * 响应中断获取共享锁
         */
        @Override
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 尝试获取共享锁
         */
        @Override
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * 尝试超时获取共享锁
         */
        @Override
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 释放共享锁
         */
        @Override
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         *
         */
        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         *
         */
        @Override
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() + "[Read locks = " + r + "]";
        }
    }

    /*----------------------------写锁-------------------------------*/
    public static class WriteLock implements Lock, java.io.Serializable {

        private static final long serialVersionUID = -4992448646407690164L;

        private final ReentrantReadWriteLock.Sync sync;

        /**
         *
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        @Override
        public void lock() {
            sync.acquire(1);
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        @Override
        public boolean tryLock() {
            return false;
        }

        @Override
        public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         *
         */
        @Override
        public void unlock() {
            sync.release(1);
        }

        /**
         *
         */
        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            return sync.newCondition();
        }

        /**
         *
         */
        @Override
        public String toString() {
            Thread o = sync.getOwner();
            return super.toString() + ((o == null) ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
        }

        /**
         *
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         *
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }

    /*----------------------ReenreantReadWriteLock功能方法--------------------*/

    /**
     * 判断是否是公平锁
     */
    public final boolean isFair() {
        return sync instanceof ReentrantReadWriteLock.FairSync;
    }

    /**
     * 获取当前拥有锁的线程
     */
    protected Thread getOwner() {
        return sync.getOwner();
    }

    /**
     * 获取当前线程获取读锁的次数
     */
    public int getReadLockCount() {
        return sync.getReadLockCount();
    }

    /**
     * 判断写锁是否被占用
     */
    public boolean isWriteLocked() {
        return sync.isWriteLocked();
    }

    /**
     * 判断当前线程是否占有写锁
     */
    public boolean isWriteLockedByCurrentThread() {
        return sync.isHeldExclusively();
    }

    /**
     * 获取当前线程获取写锁的次数
     */
    public int getWriteHoldCount() {
        return sync.getWriteHoldCount();
    }

    /**
     * 获取当前线程获取读锁的数量
     */
    public int getReadHoldCount() {
        return sync.getReadHoldCount();
    }

    /**
     * 返回获取写锁的线程
     */
    protected Collection<Thread> getQueuedWriterThreads() {
        return sync.getExclusiveQueuedThreads();
    }

    /**
     * 返回获取读锁的数量
     */
    protected Collection<Thread> getQueuedReaderThreads() {
        return sync.getSharedQueuedThreads();
    }

    /**
     * 判断同步队列是否有在等待的线程
     */
    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * 判断当前线程是否在同步队列
     */
    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    /**
     * 获取同步队列的长度
     */
    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    /**
     * 返回同步队列中的所有节点的线程
     */
    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    /**
     * 判断Condition中是否有条件队列
     */
    public boolean hasWaiters(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 获取等待队列的长度
     */
    public int getWaitQueueLength(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    /**
     * 返回等待队列中是所有节点对应的线程
     */
    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null) {
            throw new NullPointerException();
        }
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject)) {
            throw new IllegalArgumentException("not owner");
        }
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    @Override
    public String toString() {
        int c = sync.getCount();
        int w = ReentrantReadWriteLock.Sync.exclusiveCount(c);
        int r = ReentrantReadWriteLock.Sync.sharedCount(c);

        return super.toString() + "[Write locks = " + w + ", Read locks = " + r + "]";
    }

    /**
     * 获取线程的TID属性
     */
    static final long getThreadId(Thread thread) {
        //Unsafe 的根据属性偏移量和对象获取属性值
        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
    }

    /*------------------CAS操作------------------------------------*/
    private static final sun.misc.Unsafe UNSAFE;

    private static final long TID_OFFSET;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            TID_OFFSET = UNSAFE.objectFieldOffset(tk.getDeclaredField("tid"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}