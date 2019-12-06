package com.xz.concurrent.locks;

import java.util.concurrent.TimeUnit;

/**
 * 1.since JDK 1.8
 * 2.更加高效的读写锁的实现，不基于AQS实现，但还是利用CLH队列对线程进行管理
 * 3.内部具有三种模式：写模式 读模式 乐观读模式（一个写锁 一个悲观读锁 一个乐观读锁）
 * 4.乐观读：在读数据时假定没有现成修改数据 读完后再检查版本号是否发生变化 没有变化成功，
 * 5.读锁状态和写锁状态：写锁被占用的标志第8位为1 读锁使用0-7位
 * 6.StampedLock特点：1.内部悲观读锁、写锁 乐观读锁 2.不支持Condition 3.不可重入锁
 */
public class StampedLock implements java.io.Serializable {

    private static final long serialVersionUID = -6001602636862214147L;

    /*-----------------------------------------StampedLock属性------------------------------------------*/

    /**
     * 正在运行的进程数
     */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * 线程入队前的自旋次数
     */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 0;

    /**
     * 队列头结点自旋锁获取锁最大失败次数后再次进入队列
     */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 0;

    /**
     * 重新阻塞前最大自旋次数
     */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 0;

    /**
     * The period for yielding when waiting for overflow spinlock
     */
    private static final int OVERFLOW_YIELD_RATE = 7;

    /**
     * 读线程的个数占低7位
     */
    private static final int LG_READERS = 7;

    /**
     * 读线程每次增加的单位
     */
    private static final long RUNIT = 1L;

    /**
     * 写锁标识位（写锁被占用的标志是第8位为1 ）  128
     */
    private static final long WBIT = 1L << LG_READERS;

    /**
     * 读状态标识 128-1
     */
    private static final long RBITS = WBIT - 1L;

    /**
     * 最大读线程个数 126
     */
    private static final long RFULL = RBITS - 1L;

    /**
     * 用于获取写状态
     */
    private static final long ABITS = RBITS | WBIT;

    /**
     * 读线程个数的反数 高25位全部为1
     */
    private static final long SBITS = ~RBITS;

    /**
     * state的初始值
     */
    private static final long ORIGIN = WBIT << 1;

    private static final long INTERRUPTED = 1L;

    /**
     * 等待队列头结点
     */
    private transient volatile StampedLock.WNode whead;

    /**
     * 等待队列尾结点
     */
    private transient volatile StampedLock.WNode wtail;

    /**
     * 锁的状态 当前的版本号
     */
    private transient volatile long state;

    /**
     * 正常情况下读锁的数量为 1-126 超过126 使用readerOverFlow保存超出数量
     */
    private transient int readerOverflow;

    /*---------------------三种视图 对StampedLock方法的封装-----------------------*/

    /**
     * 实现Lock接口 相当于ReentrantReadWriteLock.readLock(),writeLock(),
     */

    transient StampedLock.ReadLockView readLockView;

    transient StampedLock.WriteLockView writeLockView;

    transient StampedLock.ReadWriteLockView readWriteLockView;

    /*-----------数据结构类WNODE属性--------------*/

    /**
     * 等待中
     */
    private static final int WAITING = -1;

    /**
     * 取消
     */
    private static final int CANCELLED = 1;

    /**
     * 读模式
     */
    private static final int RMODE = 0;

    /**
     * 写模式
     */
    private static final int WMODE = 1;

    /*------------------数据结构-----------------------*/
    static final class WNode {

        /**
         * 前驱
         */
        volatile StampedLock.WNode prev;

        /**
         * 后继
         */
        volatile StampedLock.WNode next;

        /**
         * 读线程使用的链表（保存读线程）
         */
        volatile StampedLock.WNode cowait;

        /**
         * 阻塞的线程
         */
        volatile Thread thread;

        /**
         * 节点的状态 0 WAITING CANCELLED
         */
        volatile int status;

        /**
         * 锁模式 写锁 读锁
         */
        final int mode;

        WNode(int m, StampedLock.WNode p) {
            mode = m;
            prev = p;
        }
    }

    /**
     * 构造 初始化状态
     */
    public StampedLock() {
        state = ORIGIN;
    }
    /*--------------------------获取锁------------------------------*/

    /**
     * 获取写锁 独占锁（不可重入锁）
     */
    public long writeLock() {

        long s, next;
        //((s = state) & ABITS) == 0L 表示读锁和写锁均未被获取 CAS将第8位设置为1
        return ((((s = state) & ABITS) == 0L && U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ? next :
                //获取失败进入等待队列
                acquireWrite(false, 0L));
    }

    /**
     * 获取读锁
     */
    public long readLock() {
        long s = state, next;
        //(whead == wtail && (s & ABITS) < RFULL 表示写锁未被占用 并且读数量没有超过最大值
        return ((whead == wtail && (s & ABITS) < RFULL && U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) ?
                next :
                //获取失败 添加到等到队列
                acquireRead(false, 0L));
    }

    /**
     * 构建写节点添加到等待队列
     * @param interruptible
     * @param deadline
     * @return
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        //node 新增节点 p 尾结点(称为node的前置节点)
        StampedLock.WNode node = null, p;
        //第一次自旋入队
        for (int spins = -1; ; ) {
            long m, s, ns;
            if ((m = (s = state) & ABITS) == 0L) {
                if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT)) {
                    return ns;
                }
            }
            //自旋次数小于0 重新计算自旋次数
            else if (spins < 0) {
                //若当前有写锁 并且五队列元素 自旋次数=SPINS 否则为0
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            }

            else if (spins > 0) {
                if (LockSupport.nextSecondarySeed() >= 0) {
                    --spins;
                }
            }
            //队列未初始化
            else if ((p = wtail) == null) {
                StampedLock.WNode hd = new StampedLock.WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd)) {
                    wtail = hd;
                }
            }
            //新增节点未初始化
            else if (node == null) {
                node = new StampedLock.WNode(WMODE, p);
            }
            //尾结点有变化 更新尾结点的前置节点未新的尾结点
            else if (node.prev != p) {
                node.prev = p;
            }
            //尝试更新新的尾结点成功 退出
            else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                p.next = node;
                break;
            }
        }

        //第二次自旋阻塞并等待唤醒
        for (int spins = -1; ; ) {
            //h= 头结点 np=新增节点的前置节点 pp:前前置节点
            StampedLock.WNode h, np, pp;
            //前置节点的状态
            int ps;
            if ((h = whead) == p) {
                //初始化自旋次数
                if (spins < 0) {
                    spins = HEAD_SPINS;
                }
                //新增自旋次数
                else if (spins < MAX_HEAD_SPINS) {
                    spins <<= 1;
                }
                //第三次自旋 不断尝试获取锁
                for (int k = spins; ; ) {
                    long s, ns;
                    //获取锁成功 将node设置为新头结点并清除前置节点
                    if (((s = state) & ABITS) == 0L) {
                        if (U.compareAndSwapLong(this, STATE, s, ns = s + WBIT)) {
                            whead = node;
                            //利于GC
                            node.prev = null;
                            return ns;
                        }
                    }
                    else if (LockSupport.nextSecondarySeed() >= 0 && --k <= 0) {
                        break;
                    }
                }
            }

            else if (h != null) {
                StampedLock.WNode c;
                Thread w;

                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null) {
                        U.unpark(w);
                    }
                }
            }
            //头节点没有变化
            if (whead == h) {
                //尾结点有变化 更新
                if ((np = node.prev) != p) {
                    if (np != null) {
                        (p = np).next = node;
                    }
                }
                else if ((ps = p.status) == 0) {
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                }
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                //处理超时时间
                else {
                    long time;
                    if (deadline == 0L) {
                        time = 0L;
                    }
                    else if ((time = deadline - System.nanoTime()) <= 0L) {
                        return cancelWaiter(node, node, false);
                    }
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) && whead == h && node.prev == p) {
                        U.park(false, time);  // emulate LockSupport.park
                    }
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted()) {
                        return cancelWaiter(node, node, true);
                    }
                }
            }
        }
    }

    /**
     * 尝试自旋的获取锁 获取不到 加入等待队列 并阻塞线程
     * interruptible = true 表示检测中断 deadline 非0 表示限时获取
     */
    private long acquireRead(boolean interruptible, long deadline) {
        //p 指向尾结点
        StampedLock.WNode node = null, p;
        for (int spins = -1; ; ) {
            StampedLock.WNode h;
            //只有头结点 直接尝试获取
            if ((h = whead) == (p = wtail)) {
                //自旋尝试获取锁
                for (long m, s, ns; ; ) {
                    if ((m = (s = state) & ABITS) < RFULL ?
                            //写锁是否被占用
                            U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) : //写锁未被占用 且读锁数量未超限 则更新同步状态
                            (m < WBIT && (ns = tryIncReaderOverflow(s))
                                    != 0L)) { //写锁未被占用 但度所以数量超出限制 超出部分存放在readerOverFlow中
                        //获取成功 直接返回
                        return ns;
                    }
                    else if (m >= WBIT) { //写锁被占用 以随机方式探测是否要自旋
                        if (spins > 0) {
                            if (LockSupport.nextSecondarySeed() >= 0) {
                                --spins;
                            }
                        }
                        else {
                            if (spins == 0) {
                                StampedLock.WNode nh = whead, np = wtail;
                                if ((nh == h && np == p) || (h = nh) != (p = np)) {
                                    break;
                                }
                            }
                            spins = SPINS;
                        }
                    }
                }
            }
            //队列为空
            if (p == null) { //初始化队列 构造头节点
                StampedLock.WNode hd = new StampedLock.WNode(WMODE, null);
                if (U.compareAndSwapObject(this, WHEAD, null, hd)) {
                    wtail = hd;
                }
            }
            //当前线程包装为读节点
            else if (node == null) {
                node = new StampedLock.WNode(RMODE, p);
            }
            //只有头结点 或者尾结点不是写节点
            else if (h == p || p.mode != RMODE) {
                //前驱不是尾 直接将节点的前驱设置为之前的尾结点
                if (node.prev != p) {
                    node.prev = p;
                }
                //CAS
                else if (U.compareAndSwapObject(this, WTAIL, p, node)) {
                    p.next = node;
                    break;
                }
            }
            //使用头插法插入节点
            else if (!U.compareAndSwapObject(p, WCOWAIT, node.cowait = p.cowait, node)) {
                node.cowait = null;
            }
            else {
                for (; ; ) {
                    StampedLock.WNode pp, c;
                    Thread w;
                    if ((h = whead) != null && (c = h.cowait) != null && U.compareAndSwapObject(h, WCOWAIT, c, c.cowait)
                            && (w = c.thread) != null) // help release
                    {
                        U.unpark(w);
                    }
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        do {
                            if ((m = (s = state) & ABITS) < RFULL ?
                                    U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                                    (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                                return ns;
                            }
                        }
                        while (m < WBIT);
                    }
                    if (whead == h && p.prev == pp) {
                        long time;
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        if (deadline == 0L) {
                            time = 0L;
                        }
                        else if ((time = deadline - System.nanoTime()) <= 0L) {
                            return cancelWaiter(node, p, false);
                        }
                        Thread wt = Thread.currentThread();
                        U.putObject(wt, PARKBLOCKER, this);
                        node.thread = wt;
                        if ((h != pp || (state & ABITS) == WBIT) && whead == h && p.prev == pp) {
                            U.park(false, time);
                        }
                        node.thread = null;
                        U.putObject(wt, PARKBLOCKER, null);
                        if (interruptible && Thread.interrupted()) {
                            return cancelWaiter(node, p, true);
                        }
                    }
                }
            }
        }

        for (int spins = -1; ; ) {
            StampedLock.WNode h, np, pp;
            int ps;
            if ((h = whead) == p) {
                if (spins < 0) {
                    spins = HEAD_SPINS;
                }
                else if (spins < MAX_HEAD_SPINS) {
                    spins <<= 1;
                }
                for (int k = spins; ; ) { // spin at head
                    long m, s, ns;
                    if ((m = (s = state) & ABITS) < RFULL ?
                            U.compareAndSwapLong(this, STATE, s, ns = s + RUNIT) :
                            (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        StampedLock.WNode c;
                        Thread w;
                        whead = node;
                        node.prev = null;
                        while ((c = node.cowait) != null) {
                            if (U.compareAndSwapObject(node, WCOWAIT, c, c.cowait) && (w = c.thread) != null) {
                                U.unpark(w);
                            }
                        }
                        return ns;
                    }
                    else if (m >= WBIT && LockSupport.nextSecondarySeed() >= 0 && --k <= 0) {
                        break;
                    }
                }
            }
            else if (h != null) {
                StampedLock.WNode c;
                Thread w;
                while ((c = h.cowait) != null) {
                    if (U.compareAndSwapObject(h, WCOWAIT, c, c.cowait) && (w = c.thread) != null) {
                        U.unpark(w);
                    }
                }
            }
            if (whead == h) {
                if ((np = node.prev) != p) {
                    if (np != null) {
                        (p = np).next = node;   // stale
                    }
                }
                else if ((ps = p.status) == 0) {
                    U.compareAndSwapInt(p, WSTATUS, 0, WAITING);
                }
                else if (ps == CANCELLED) {
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time;
                    if (deadline == 0L) {
                        time = 0L;
                    }
                    else if ((time = deadline - System.nanoTime()) <= 0L) {
                        return cancelWaiter(node, node, false);
                    }
                    Thread wt = Thread.currentThread();
                    U.putObject(wt, PARKBLOCKER, this);
                    node.thread = wt;
                    if (p.status < 0 && (p != h || (state & ABITS) == WBIT) && whead == h && node.prev == p) {
                        U.park(false, time);
                    }
                    node.thread = null;
                    U.putObject(wt, PARKBLOCKER, null);
                    if (interruptible && Thread.interrupted()) {
                        return cancelWaiter(node, node, true);
                    }
                }
            }
        }
    }

    /*----------------------------try系列方法------------------------------*/
    /**
     * try方法：尝试 成功返回非0的stamp值 失败 返回stamp = 0
     */
    /**
     * 尝试获取写锁 返回非0表示获取成功
     */
    public long tryWriteLock() {
        long s, next;
        return ((((s = state) & ABITS) == 0L && U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) ? next : 0L);
    }

    /**
     * 尝试获取写锁
     */
    public long tryWriteLock(long time, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            long next, deadline;
            if ((next = tryWriteLock()) != 0L) {
                return next;
            }
            if (nanos <= 0L) {
                return 0L;
            }
            if ((deadline = System.nanoTime() + nanos) == 0L) {
                deadline = 1L;
            }
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED) {
                return next;
            }
        }
        throw new InterruptedException();
    }

    /**
     * 尝试中断的获取写锁
     */
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() && (next = acquireWrite(true, 0L)) != INTERRUPTED) {
            return next;
        }
        throw new InterruptedException();
    }

    /**
     * 尝试获取读锁
     */
    public long tryReadLock() {
        for (; ; ) {
            long s, m, next;
            if ((m = (s = state) & ABITS) == WBIT) {
                return 0L;
            }
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) {
                    return next;
                }
            }
            else if ((next = tryIncReaderOverflow(s)) != 0L) {
                return next;
            }
        }
    }

    /**
     * 尝试获取读锁
     */
    public long tryReadLock(long time, TimeUnit unit) throws InterruptedException {
        long s, m, next, deadline;
        long nanos = unit.toNanos(time);
        if (!Thread.interrupted()) {
            if ((m = (s = state) & ABITS) != WBIT) {
                if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) {
                        return next;
                    }
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L) {
                    return next;
                }
            }
            if (nanos <= 0L) {
                return 0L;
            }
            if ((deadline = System.nanoTime() + nanos) == 0L) {
                deadline = 1L;
            }
            if ((next = acquireRead(true, deadline)) != INTERRUPTED) {
                return next;
            }
        }
        throw new InterruptedException();
    }

    /**
     * 尝试获取可中断的读锁
     */
    public long readLockInterruptibly() throws InterruptedException {
        long next;
        if (!Thread.interrupted() && (next = acquireRead(true, 0L)) != INTERRUPTED) {
            return next;
        }
        throw new InterruptedException();
    }

    /**
     * 尝试获取乐观锁
     */
    public long tryOptimisticRead() {
        long s;
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * 尝试将读锁升级为写锁
     */
    public long tryConvertToWriteLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            //无锁状态或乐观状态
            if ((m = s & ABITS) == 0L) {
                //传近的stamp不处于无锁状态或者乐观状态  直接退出
                if (a != 0L) {
                    break;
                }
                //CAS获取写锁 成功 退出
                if (U.compareAndSwapLong(this, STATE, s, next = s + WBIT)) {
                    return next;
                }
            }
            //处于写锁状态
            else if (m == WBIT) {
                //传进的Stamp不处于写状态 退出
                if (a != m) {
                    break;
                }
                //否则直接返回处于写锁的stamp
                return stamp;
            }
            //只有一个读锁 CAS将读锁升级为写锁
            else if (m == RUNIT && a != 0L) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT + WBIT)) {
                    return next;
                }
            }
            //否则 直接退出
            else {
                break;
            }
        }
        return 0L;
    }

    /**
     * 将写锁转换为读锁 (锁降级)
     */
    public long tryConvertToReadLock(long stamp) {
        long a = stamp & ABITS, m, s, next;
        StampedLock.WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                if (a != 0L) {
                    break;
                }
                else if (m < RFULL) {
                    if (U.compareAndSwapLong(this, STATE, s, next = s + RUNIT)) {
                        return next;
                    }
                }
                else if ((next = tryIncReaderOverflow(s)) != 0L) {
                    return next;
                }
            }
            else if (m == WBIT) {
                if (a != m) {
                    break;
                }
                state = next = s + (WBIT + RUNIT);
                if ((h = whead) != null && h.status != 0) {
                    release(h);
                }
                return next;
            }
            else if (a != 0L && a < WBIT) {
                return stamp;
            }
            else {
                break;
            }
        }
        return 0L;
    }

    /**
     * 升级为乐观读锁
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a = stamp & ABITS, m, s, next;
        StampedLock.WNode h;
        U.loadFence();
        for (; ; ) {
            if (((s = state) & SBITS) != (stamp & SBITS)) {
                break;
            }
            if ((m = s & ABITS) == 0L) {
                if (a != 0L) {
                    break;
                }
                return s;
            }
            else if (m == WBIT) {
                if (a != m) {
                    break;
                }
                state = next = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0) {
                    release(h);
                }
                return next;
            }
            else if (a == 0L || a >= WBIT) {
                break;
            }
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, next = s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0) {
                        release(h);
                    }
                    return next & SBITS;
                }
            }
            else if ((next = tryDecReaderOverflow(s)) != 0L) {
                return next & SBITS;
            }
        }
        return 0L;
    }

    /**
     * 尝试释放写锁
     */
    public boolean tryUnlockWrite() {
        long s;
        StampedLock.WNode h;
        if (((s = state) & WBIT) != 0L) {
            state = (s += WBIT) == 0L ? ORIGIN : s;
            if ((h = whead) != null && h.status != 0) {
                release(h);
            }
            return true;
        }
        return false;
    }

    /**
     * 尝试释放读锁
     */
    public boolean tryUnlockRead() {
        long s, m;
        StampedLock.WNode h;
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0) {
                        release(h);
                    }
                    return true;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验版本号是否发生变化
     */
    public boolean validate(long stamp) {
        U.loadFence();
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * 释放写锁
     */
    public void unlockWrite(long stamp) {
        StampedLock.WNode h;
        if (state != stamp || (stamp & WBIT) == 0L) {
            throw new IllegalMonitorStateException();
        }
        state = (stamp += WBIT) == 0L ? ORIGIN : stamp;
        if ((h = whead) != null && h.status != 0) {
            release(h);
        }
    }

    /**
     * 释放读锁
     */
    public void unlockRead(long stamp) {
        long s, m;
        StampedLock.WNode h;
        for (; ; ) {
            if (((s = state) & SBITS) != (stamp & SBITS) || (stamp & ABITS) == 0L || (m = s & ABITS) == 0L
                    || m == WBIT) {
                throw new IllegalMonitorStateException();
            }
            if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0) {
                        release(h);
                    }
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L) {
                break;
            }
        }
    }

    /**
     * 根据版本号释放锁
     */
    public void unlock(long stamp) {
        long a = stamp & ABITS, m, s;
        StampedLock.WNode h;
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            if ((m = s & ABITS) == 0L) {
                break;
            }
            else if (m == WBIT) {
                if (a != m) {
                    break;
                }
                state = (s += WBIT) == 0L ? ORIGIN : s;
                if ((h = whead) != null && h.status != 0) {
                    release(h);
                }
                return;
            }
            else if (a == 0L || a >= WBIT) {
                break;
            }
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0) {
                        release(h);
                    }
                    return;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L) {
                return;
            }
        }
        throw new IllegalMonitorStateException();
    }

    /*------------------------------功能方法------------------------------*/

    /**
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        if ((readers = s & RBITS) >= RFULL) {
            readers = RFULL + readerOverflow;
        }
        return (int) readers;
    }

    /**
     * Returns {@code true} if the lock is currently held exclusively.
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * Returns {@code true} if the lock is currently held non-exclusively.
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     *
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     *
     */
    @Override
    public String toString() {
        long s = state;
        return super.toString() + ((s & ABITS) == 0L ?
                "[Unlocked]" :
                (s & WBIT) != 0L ? "[Write-locked]" : "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     *
     */
    public Lock asReadLock() {
        StampedLock.ReadLockView v;
        return ((v = readLockView) != null ? v : (readLockView = new StampedLock.ReadLockView()));
    }

    /**
     *
     */
    public Lock asWriteLock() {
        StampedLock.WriteLockView v;
        return ((v = writeLockView) != null ? v : (writeLockView = new StampedLock.WriteLockView()));
    }

    /**
     *
     */
    public ReadWriteLock asReadWriteLock() {
        StampedLock.ReadWriteLockView v;
        return ((v = readWriteLockView) != null ? v : (readWriteLockView = new StampedLock.ReadWriteLockView()));
    }


    /*----------------------------视图类--------------------------------*/

    final class ReadLockView implements Lock {

        @Override
        public void lock() {
            readLock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return tryReadLock() != 0L;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }

        @Override
        public void unlock() {
            unstampedUnlockRead();
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class WriteLockView implements Lock {

        @Override
        public void lock() {
            writeLock();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }

        @Override
        public boolean tryLock() {
            return tryWriteLock() != 0L;
        }

        @Override
        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }

        @Override
        public void unlock() {
            unstampedUnlockWrite();
        }

        @Override
        public java.util.concurrent.locks.Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    final class ReadWriteLockView implements ReadWriteLock {

        @Override
        public Lock readLock() {
            return asReadLock();
        }

        @Override
        public Lock writeLock() {
            return asWriteLock();
        }
    }

    final void unstampedUnlockWrite() {
        StampedLock.WNode h;
        long s;
        if (((s = state) & WBIT) == 0L) {
            throw new IllegalMonitorStateException();
        }
        state = (s += WBIT) == 0L ? ORIGIN : s;
        if ((h = whead) != null && h.status != 0) {
            release(h);
        }
    }

    final void unstampedUnlockRead() {
        for (; ; ) {
            long s, m;
            StampedLock.WNode h;
            if ((m = (s = state) & ABITS) == 0L || m >= WBIT) {
                throw new IllegalMonitorStateException();
            }
            else if (m < RFULL) {
                if (U.compareAndSwapLong(this, STATE, s, s - RUNIT)) {
                    if (m == RUNIT && (h = whead) != null && h.status != 0) {
                        release(h);
                    }
                    break;
                }
            }
            else if (tryDecReaderOverflow(s) != 0L) {
                break;
            }
        }
    }

    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        state = ORIGIN; // reset to unlocked state
    }

    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                ++readerOverflow;
                state = s;
                return s;
            }
        }
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0) {
            Thread.yield();
        }
        return 0L;
    }

    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        if ((s & ABITS) == RFULL) {
            if (U.compareAndSwapLong(this, STATE, s, s | RBITS)) {
                int r;
                long next;
                if ((r = readerOverflow) > 0) {
                    readerOverflow = r - 1;
                    next = s;
                }
                else {
                    next = s - RUNIT;
                }
                state = next;
                return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0) {
            Thread.yield();
        }
        return 0L;
    }

    private void release(StampedLock.WNode h) {
        if (h != null) {
            StampedLock.WNode q;
            Thread w;
            U.compareAndSwapInt(h, WSTATUS, WAITING, 0);
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (StampedLock.WNode t = wtail; t != null && t != h; t = t.prev) {
                    if (t.status <= 0) {
                        q = t;
                    }
                }
            }
            if (q != null && (w = q.thread) != null) {
                U.unpark(w);
            }
        }
    }

    /**
     *
     */
    private long cancelWaiter(StampedLock.WNode node, StampedLock.WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            node.status = CANCELLED;

            for (StampedLock.WNode p = group, q; (q = p.cowait) != null; ) {
                if (q.status == CANCELLED) {
                    U.compareAndSwapObject(p, WCOWAIT, q, q.cowait);
                    p = group; // restart
                }
                else {
                    p = q;
                }
            }
            if (group == node) {
                for (StampedLock.WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null) {
                        U.unpark(w);       // wake up uncancelled co-waiters
                    }
                }
                for (StampedLock.WNode pred = node.prev; pred != null; ) { // unsplice
                    StampedLock.WNode succ, pp;        // find valid successor
                    while ((succ = node.next) == null || succ.status == CANCELLED) {
                        StampedLock.WNode q = null;    // find successor the slow way
                        for (StampedLock.WNode t = wtail; t != null && t != node; t = t.prev) {
                            if (t.status != CANCELLED) {
                                q = t;     // don't link if succ cancelled
                            }
                        }
                        if (succ == q ||   // ensure accurate successor
                                U.compareAndSwapObject(node, WNEXT, succ, succ = q)) {
                            if (succ == null && node == wtail) {
                                U.compareAndSwapObject(this, WTAIL, node, pred);
                            }
                            break;
                        }
                    }
                    if (pred.next == node) // unsplice pred link
                    {
                        U.compareAndSwapObject(pred, WNEXT, node, succ);
                    }
                    if (succ != null && (w = succ.thread) != null) {
                        succ.thread = null;
                        U.unpark(w);       // wake up succ to observe new pred
                    }
                    if (pred.status != CANCELLED || (pp = pred.prev) == null) {
                        break;
                    }
                    node.prev = pp;        // repeat if new pred wrong/cancelled
                    U.compareAndSwapObject(pp, WNEXT, pred, succ);
                    pred = pp;
                }
            }
        }
        StampedLock.WNode h; // Possibly release first waiter
        while ((h = whead) != null) {
            long s;
            StampedLock.WNode q; // similar to release() but check eligibility
            if ((q = h.next) == null || q.status == CANCELLED) {
                for (StampedLock.WNode t = wtail; t != null && t != h; t = t.prev) {
                    if (t.status <= 0) {
                        q = t;
                    }
                }
            }
            if (h == whead) {
                if (q != null && h.status == 0 && ((s = state) & ABITS) != WBIT && // waiter is eligible
                        (s == 0L || q.mode == RMODE)) {
                    release(h);
                }
                break;
            }
        }
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    /*--------------------------------CAS方法------------------------------------*/
    private static final sun.misc.Unsafe U;

    private static final long STATE;

    private static final long WHEAD;

    private static final long WTAIL;

    private static final long WNEXT;

    private static final long WSTATUS;

    private static final long WCOWAIT;

    private static final long PARKBLOCKER;

    static {
        try {
            U = sun.misc.Unsafe.getUnsafe();
            Class<?> k = StampedLock.class;
            Class<?> wk = StampedLock.WNode.class;
            STATE = U.objectFieldOffset(k.getDeclaredField("state"));
            WHEAD = U.objectFieldOffset(k.getDeclaredField("whead"));
            WTAIL = U.objectFieldOffset(k.getDeclaredField("wtail"));
            WSTATUS = U.objectFieldOffset(wk.getDeclaredField("status"));
            WNEXT = U.objectFieldOffset(wk.getDeclaredField("next"));
            WCOWAIT = U.objectFieldOffset(wk.getDeclaredField("cowait"));
            Class<?> tk = Thread.class;
            PARKBLOCKER = U.objectFieldOffset(tk.getDeclaredField("parkBlocker"));

        } catch (Exception e) {
            throw new Error(e);
        }
    }
}

