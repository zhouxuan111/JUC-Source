package com.xz.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

/**
 * 类简介：AQS：队列同步器，提供了一种实现阻塞锁和一些列依赖FIFO等待队列的同步器的框架
 * 基于模板方法模式
 * 使用方式：继承该类作为一个内部辅助类实现同步原语，并重写指定的方法
 * 实现思路：AQS内部维护一个CLH队列来管理锁 双向链表的队列 和一个Condition的等待队列
 * 线程会首先尝试获取锁，如果失败，则将当前线程以及等待状态等信息包成一个Node节点加到同步队列里
 * 接着不断尝试去获取当前锁（条件是当前节点为head的直接后继才会尝试），若失败会阻塞自己，直至被唤醒
 * 而当持有锁的线程释放锁时，会唤醒队列中的后继线程
 * 需要做的三件事：同步状态的管理 线程的阻塞与唤醒 同步队列的维护
 * 使用AQS需要重写protected方法
 */
public abstract class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * 构造方法
     */
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * 数据结构 双向列表 FIFO
     * 总的来说；保存节点状态，前驱节点 后继节点 线程
     */
    static final class Node {

        /**
         * 用于标记一个节点在共享模式下等待
         */
        static final Node SHARED = new Node();

        /**
         * 用于标记一个节点在独占模式下等待
         */
        static final Node EXCLUSIVE = null;

        /**
         * 等待状态：取消 当前线程因为取消或者中断被取消 终结状态 需要从等待队列中删除
         */
        static final int CANCELLED = 1;

        /**
         * 等待状态：通知 当前线程的后继线程被阻塞或者即将被阻塞，当前线程释放锁或者取消后需要唤醒后继线程
         * 一般是后继线程来设置前驱线程
         */
        static final int SIGNAL = -1;

        /**
         * 等待状态：条件等待 当前线程在Condition队列（等待队列）当中 将不会被用于sync queue(同步队列) 直到节点状态被设置为0
         */
        static final int CONDITION = -2;

        /**
         * 等待状态:传播 用于将唤醒后继线程传递下去 是为了完善和增强共享锁的唤醒机制
         * 在一个节点成为头结点之前 是不会变成此状态
         * 仅在共享模式下使用
         */
        static final int PROPAGATE = -3;

        /**
         * 等待状态
         * 0：当前节点在同步队列中 等待获取锁
         * 1：取消（终结状态）
         * -1：表示当前节点的后继节点包含的线程需要运行
         * -2：条件等待 表明在Condition等待队列中 将不会被用于sync queue(同步队列) 直到节点状态被设置为0
         * -3：仅用于共享模式 用于将唤醒后继线程传播下去
         */
        volatile int waitStatus;

        /**
         * 前驱节点
         */
        volatile Node prev;

        /**
         * 后继节点
         */
        volatile Node next;

        /**
         * 节点对应的线程
         */
        volatile Thread thread;

        /**
         * 等待队列中的后继节点  是指Condition
         */
        Node nextWaiter;

        /**
         * 判断当前节点是否在共享模式下等待
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 获取前驱节点 若为空抛出空指针异常
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            }
            else {
                return p;
            }
        }

        Node() {
        }

        /**
         * addWaiter会使用此构造方法
         * @param thread
         * @param mode
         */
        Node(Thread thread, Node mode) {
            nextWaiter = mode;
            this.thread = thread;
        }

        /**
         * Condition会调用此函数（等待队列 条件队列）
         * @param thread
         * @param waitStatus
         */
        Node(Thread thread, int waitStatus) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
        /**
         * 状态分析：0和CONDITION为始态，CANCELLED为终态 0状态同时也可以是节点生命周期的终态
         */
    }

    /*---------------------------------------------------------------------------------------------*/

    /*--------------------------------------------CLH同步队列------------------------------------------*/

    /**
     * CLH中头结点
     * 逻辑上意义：当前持有锁的线程 本身并不会存储线程信息 延迟初始化
     */
    private transient volatile Node head;

    /**
     * CLH尾结点
     * 当一个线程无法获取锁而被加入到同步队列中，会用CAS来设置尾结点tail为当前线的对应节点
     */
    private transient volatile Node tail;

    /**
     * 同步状态 volatile:保证其他线程可见性  0 表示锁可获取状态
     * state<0 表示获取同步状态失败
     * state>0 表示获取同步状态成功 并且代表后继节点对应的线程很可能获取成功
     * state=0 表示获取同步状态成功 但是后继节点获取同步状态很有可能失败
     * 多线程同步获取资源成功 则state字段会自增 若有线程释放资源 state自减
     */
    private volatile int state;

    /**
     * 获取当前同步状态
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置当前同步状态
     * @param newState 返回新的状态
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * CAS设置当前同步状态 该方法保证状态设置的原子性
     */
    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * 线程自旋的等待时间 微秒
     */
    static final long spinForTimeoutThreshold = 1000L;

    /*----------------------------------CLH队列操作  添加节点----------------------------------*/

    /**
     * 该方法使用自旋的方式来保证向队列中添加Node
     * for(;;)自旋
     * 若队列为空 ，则把当前Node设置成头结点
     * 若队列不为空，则在队列尾部添加Node
     * 该方式还是条件队列转换为同步队列的方法  原因：同步队列和等待队列的数据结构均为Node
     * 返回的是前驱节点
     */
    private Node enq(final Node node) {
        //保证并发是能够正确的将node添加到队列尾端
        for (; ; ) { // CAS自旋方式 - 死循环
            Node t = tail;
            //尾结点不存在  -- 队列为空
            if (t == null) {
                //CAS操作设置头结点 新创建一个节点
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            }
            else {
                //node节点的前驱节点指向尾结点
                //当前的前驱是之前的尾结点
                node.prev = t;
                //CAS操作将尾结点指向node节点
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 将构造的同步节点加入到同步队列尾部
     * 使用链表的方式将Node节点添加到队列尾部，若tail的前驱节点不为空（队列不为空）则进行CAS添加到尾部
     * 如果更新失败（存在并发竞争） 进入enq()方法进行更新
     * @param mode Node.EXCLUSIVE 表示独占, Node.SHARED 表示共享
     * @return 返回新加的节点
     */
    private Node addWaiter(Node mode) { //节点模式  独占还是共享
        //生成当前线程的节点
        Node node = new Node(Thread.currentThread(), mode);
        Node pred = tail;
        //快速入队 若失败 进行enq()插入
        if (pred != null) {
            //尾结点存在 将新生成的节点添加在尾结点tail后面
            node.prev = pred;
            //CAS操作将尾结点指向新生成的节点 之前尾结点的后继指向新生成的节点
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        //尾结点不存在 表示同步队列还没有初始化 将新生成的节点添加到尾结点 通过自旋的方式进入队列
        enq(node);
        return node;
    }

    /**
     * 设置头结点
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 使用LockSupport.unpark(Thread thread)来唤醒被阻塞的线程
     * @param node the node
     */
    private void unparkSuccessor(Node node) {

        int ws = node.waitStatus;
        // 修改头结点的状态
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }
        //获取后继线程
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {//寻找下一个符合条件的节点
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        //唤醒后继线程
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * 共享模式下 释放同步状态
     */
    private void doReleaseShared() {

        for (; ; ) {
            //获取头结点
            Node h = head;
            //头结点不为空并且不等于尾结点
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                //若头结点对应的状态是SIGNAL 代表后继节点对应的线程需要被unpark()唤醒
                if (ws == Node.SIGNAL) {
                    //将头结点对应的线程状态 = 空状态
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        continue;
                    }
                    //唤醒后继节点的线程
                    unparkSuccessor(h);
                }
                //头结点对应的状态为空状态 ， 则设置尾结点对应的线程所拥有的共享锁为其他线程获取锁的空状态
                else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;
                }
            }
            //若头节点发生变化，则继续循环
            if (h == head) {
                break;
            }
        }
    }

    /**
     * 改方法与setHead()相比 在设置头节点的基础上 还会唤醒后继线程 因为是共享模式
     * 将当前节点设置为头结点 并调用doReleaseShared()方法
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        //将当前节点设置为头节点
        Node h = head;
        setHead(node);
        //还有剩余量 继续唤醒后继线程
        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            //共享模式 唤醒后继线程
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    /**
     * 获取同步状态失败 取消节点  不懂存在的意义
     */
    private void cancelAcquire(Node node) {
        // 节点不存在
        if (node == null) {
            return;
        }

        node.thread = null;

        // 跳过取消状态的节点
        Node pred = node.prev;
        while (pred.waitStatus > 0) {
            node.prev = pred = pred.prev;
        }

        Node predNext = pred.next;

        //将当前节点状态设置成CANCELLED
        node.waitStatus = Node.CANCELLED;

        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        }
        else {
            int ws;
            if (pred != head && ((ws = pred.waitStatus) == Node.SIGNAL || (ws <= 0 && compareAndSetWaitStatus(pred, ws,
                    Node.SIGNAL))) && pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0) {
                    compareAndSetNext(pred, predNext, next);
                }
            }
            else {
                //唤醒后继节点
                unparkSuccessor(node);
            }
            node.next = node;
        }
    }

    /**
     * pred:前驱节点 node:当前节点(尾结点)
     * ws = SINGLE 表示可以被前驱节点唤醒 当前线程成可以被挂起 等待前驱节点唤醒 返回true
     * ws>0 前驱节点取消 并循环检查此前驱节点之前所有连续取消的节点 并返回false(不能挂起)
     * 尝试将当前节点的前驱节点的等待状态设置为SINGLE
     * 判断当前节点中的线程是否可以安全的进入park() 返回true 进程可以进入park()阻塞
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //获取前驱节点的状态
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
        //若前驱节点状态为SINGLE 则当前线程可以被阻塞
        {
            return true;
        }
        //更换前驱节点 当前驱节点的状态为CANCELLED = 1  前驱节点不合理
        //将前驱节点移除队列
        if (ws > 0) {
            //前驱节点的状态为CANCELLED = 1 那就一直向前遍历 知道找到一个正常等待的状态 ws<=0
            do {
                node.prev = pred = pred.prev;
            }
            while (pred.waitStatus > 0);
            //并将当前Node排在后面
            pred.next = node;
        }
        else {
            //前驱节点正常 CAS修改前驱节点的状态为SIGNAL
            //ws=0 表示前驱节点获取到同步状态 当前线程不应该被挂起
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 中断当前线程
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * 阻塞线程 并 检测线程是否被中断
     */
    private final boolean parkAndCheckInterrupt() {
        //阻塞当前线程 让线程进入waiting状态 等待被唤醒或者中断
        LockSupport.park(this);
        //检测线程是否被中断
        return Thread.interrupted();
    }

    /**
     * 只有前驱节点是头结点，才能获取同步状态
     * 以自旋的方式获取同步状态 若获取不到则阻塞下节点中的线程 阻塞后的线程等待前驱线程来唤醒  若在整个过程中被中断 返回true 否则返回false
     */
    final boolean acquireQueued(final Node node, int arg) {
        //获取同步状态失败的标志 failed = true 没有获取到同步状态 failed = false 表示成功
        boolean failed = true;
        try {
            //默认线程没有被中断过
            boolean interrupted = false;
            for (; ; ) {
                //获取当前节点的前驱节点
                final Node p = node.predecessor();
                //检测P是否是头结点 若是 再次调用tryAcquire方法 尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    //是头结点 返回true（获取同步状态成功了） 将当前节点设置为头结点
                    setHead(node);
                    p.next = null; // 便于垃圾回收
                    failed = false;
                    //返回中断状态
                    return interrupted;
                }
                //若P节点不是头结点 或者tryAcquire返回false 获取失败
                //shouldParkAfterFailedAcquire 判断当前线程是否应该被阻塞
                // parkAndCheckInterrupt 阻塞当前线程
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    //若有中断 设置中断状态
                    interrupted = true;
                }
            }
        } finally {
            //若获取失败 移除当前节点
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 可中断式的获取同步状态（独占式）
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null;
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 独占式 响应中断 超时时间的自旋获取同步状态
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 共享式获取状态 不可中断
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        //添加节点到等待队列中
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            //自旋的获取同步状态
            for (; ; ) {
                //前驱节点
                final Node p = node.predecessor();
                //前驱节点 == 头结点
                if (p == head) {
                    //尝试获取同步状态
                    int r = tryAcquireShared(arg);
                    //返回值>0 代表获取同步状态成功，从自旋中退出
                    if (r >= 0) {
                        //将当前节点设置为头结点 唤醒后继节点
                        setHeadAndPropagate(node, r);
                        //帮助GC
                        p.next = null;
                        if (interrupted) {
                            selfInterrupt();
                        }
                        failed = false;
                        return;
                    }
                }
                //shouldParkAfterFailedAcquire 判断线程是否需要被阻塞
                //parkAndCheckInterrupt 阻塞线程
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            //获取失败 取消节点
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 共享模式 可中断 自旋获取同步状态
     */
    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 共享模式下 可中断 超时时间 自旋获取同步状态
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) && nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 独占式的获取锁 实现该方法需要查询当前状态并判断当前状态的是否符合预期，再进行CAS设置同步状态
     * 成功返回true  失败返回false  用于实现Lock中的tryLock方法
     * 由子类实现
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 可重写方法 独占式释放同步状态
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 重写方法 共享模式释放锁
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 重写方法 共享模式释放锁
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 检查当前线程是否是持有锁的线程
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 独占式获取同步状态
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            //获取同步资源之后自行中断
            selfInterrupt();
        }
    }

    /**
     * 响应中断当前线程状态未获取到同步状态而进入到同步队列 若当前线程被中断 抛出中断异常
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    /**
     * acquireInterruptibly()在此基础上添加超时限制若当前线程在超时时间内没有获取到同步状态  返回false 否则返回true
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 独占锁释放同步状态 释放同步状态之后 将同步队列中的第一个节点包含的线程唤醒
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0) {
                //唤醒后继线程
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    /**
     * 共享式获取锁
     * tryAcquireShared（）尝试获取同步状态 当返回值>0时，代表到获取同步状态
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    /**
     * 共享式获取锁 响应中断
     */
    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryAcquireShared(arg) < 0) {
            doAcquireSharedInterruptibly(arg);
        }
    }

    /**
     * 共享式获取锁 响应中断  添加超时
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 共享式释放锁
     */
    public final boolean releaseShared(int arg) {
        //tryReleaseShared()尝试释放锁
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    /*-------------------------------------------功能方法-----------------------------------------------*/

    /**
     * 判断是否存在同步队列
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     *
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * 获取第一个节点的线程
     */
    public final Thread getFirstQueuedThread() {
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    private Thread fullGetFirstQueuedThread() {
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null) || (
                (h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null)) {
            return st;
        }

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null) {
                firstThread = tt;
            }
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * 判断线程是否在队列当中
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null) {
            throw new NullPointerException();
        }
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread == thread) {
                return true;
            }
        }
        return false;
    }

    /**
     * 第一个节点是否是独占模式锁
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
    }

    /**
     * 判断同步队列是否有前驱节点
     */
    public final boolean hasQueuedPredecessors() {

        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    /**
     * 获取同步队列的长度
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null) {
                ++n;
            }
        }
        return n;
    }

    /**
     * 获取同步队列的线程集合
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    /**
     * 获取同步队列独占模式的线程集合
     * @return
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * 获取同步队列共享式的线程集合
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    @Override
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() + "[State = " + s + ", " + q + "empty queue]";
    }

    /*--------------------------------------Condition功能方法----------------------------------------*/

    /**
     * 判断某个节点是否在同步队列中
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null) {
            return false;
        }
        if (node.next != null) {
            return true;
        }

        return findNodeFromTail(node);
    }

    /**
     * 在同步队列中查找某个节点
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node) {
                return true;
            }
            if (t == null) {
                return false;
            }
            t = t.prev;
        }
    }

    /**
     * 将条件队列中的节点转换到同步队列
     */
    final boolean transferForSignal(Node node) {
        //将节点的状态设置为初始状态 0 若修改不成功 说明节点被取消了
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            return false;
        }
        //利用enq()将节点添加到同步队列尾部  返回的是前驱节点
        Node p = enq(node);
        int ws = p.waitStatus;
        //将前驱节点的状态设置为SIGNAL  不成功 释放线程
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {
            LockSupport.unpark(node.thread);
        }
        return true;
    }

    /**
     * 判断是否发生了signal()
     * 判断是否发生signal 只需要判断是不是离开条件队列 进入到同步队列
     * 一个节点的状态要是CONDITION 就没有进入到同步队列当中
     */
    final boolean transferAfterCancelledWait(Node node) {
        //没有发生signal()方法
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            //将节点添加到同步队列  但是在此过程中并没有断开node的nextWaiter（后续断开）
            enq(node);
            return true;
        }
        //中断发生前 线程被signal 只需要等待线程重新进入同步队列
        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }
        return false;
    }

    /**
     * 释放当前线程占用的锁（调用await()释放锁）
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();
            //一次性释放所有的锁 -- 针对重入锁
            if (release(savedState)) {
                failed = false;
                return savedState;
            }
            else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed) {
                //释放失败 修改状态为删除状态
                node.waitStatus = Node.CANCELLED;
            }
        }
    }

    /**
     *
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     *
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.hasWaiters();
    }

    /**
     * 获取条件队列的长度
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitQueueLength();
    }

    /**
     * 获取条件队列的线程集合
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitingThreads();
    }

    /**
     * 1.每创建一个Condition对象 都会有一个Condition队列 每一个调用Condition对象的await()方法的线程都会被包装成Node节点扔进条件队列当中
     * 2.单向列表 所以在Node数据结构中真正关系的属性：thread waitStatus nextWaiter(指向条件队列的下一节点)  条件队列用nextWaiter串联链表
     * 3.在条件队列中 关注CONDITION属性 只要不是CONDITION属性 就要从条件队列中出队
     * 4.同步队列和条件队列的转化条件：
     * 调用条件队列的single()方法时 会将某个或者所有等待在条件队列的线程唤醒 被唤醒的线程和普通线程争锁
     * 若失败，则同样将线程对应的节点添加到同步队列 此时节点从条件队列转移到同步队列。
     * 5.条件队列入队和出队的锁状态(与同步队列相反)
     * 入队前获取锁状态，调用await()方法 释放锁状态 入队
     * 出队，调用single()方法出队 线程不持有锁状态
     */
    public class ConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

        /**
         * 条件队列的头结点
         */
        private transient Node firstWaiter;

        /**
         * 条件队列的尾结点
         */
        private transient Node lastWaiter;

        /**
         * 无参构造
         */
        public ConditionObject() {
        }

        /**
         * 将当前线程封装成Node节点 加入到条件队列
         * 不存在并发情况 因为调用await()方法代表已经获取锁 不需要CAS操作
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            //如果尾节点状态为CANCELLED 则先遍历真个链表 清除被CANCELLED的节点）
            if (t != null && t.waitStatus != Node.CONDITION) {
                //遍历条件队列 删除状态为已取消状态的节点
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            //封装节点
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            //尾结点 == null 将入列节点设置为条件队列的头结点
            if (t == null) {
                firstWaiter = node;
            }
            //反之将其添加在尾结点的后面
            else {
                t.nextWaiter = node;
            }
            lastWaiter = node;
            return node;
        }

        /**
         * 唤醒后继节点
         * 将先入条件队列的节点移除或者转到同步队列 直到有取消的或者为null的节点为止
         */
        private void doSignal(Node first) {
            //循环找到第一个没有被CANCELLED的节点 进行唤醒
            do {
                //将firstWaiter指向条件队列对头的下一个节点
                if ((firstWaiter = first.nextWaiter) == null) {
                    //first节点的nextWaiter == null 将尾节点设置为null
                    lastWaiter = null;
                }
                first.nextWaiter = null;
            }
            while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

        /**
         * 唤醒所有后继线程 (自旋唤醒)
         * 将所有节点从条件队列中移除或者转到同步队列
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            //条件队列清空
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                //将节点转换到同步队列当中
                transferForSignal(first);
                first = next;
            }
            while (first != null);
        }

        /**
         * 遍历所有条件队列的节点，移除状态为取消状态(CANCELLED)的节点
         */
        private void unlinkCancelledWaiters() {
            //从头结点开始遍历
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null) {
                        firstWaiter = next;
                    }
                    else {
                        trail.nextWaiter = next;
                    }
                    if (next == null) {
                        lastWaiter = trail;
                    }
                }
                else {
                    trail = t;
                }
                t = next;
            }
        }

        /**
         * 唤醒一个节点
         * 将最先入列的条件节点转到同步队列当中
         * returns {@code false}
         */
        @Override
        public final void signal() {
            //检查是否是当前线程持有锁  因为调用这个方法的线程的前提都是获取了锁
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignal(first);
            }
        }

        /**
         * 唤醒所有后继线程 -- 唤醒条件队列中的线程
         */
        @Override
        public final void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }

        /**
         * 不可中断的条件等待
         */
        @Override
        public final void awaitUninterruptibly() {
            //添加条件队列节点
            Node node = addConditionWaiter();
            //获取节点释放锁之前的状态
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                //如果条件队列节点还没有被转到同步队列上，则阻塞当前线程
                LockSupport.park(this);
                //线程中断
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
            //此时线程已在同步队列上 此时去竞争独占锁
            if (acquireQueued(node, savedState) || interrupted) {
                //竞争锁中断或者之前已经被中断 则中断当前线程
                selfInterrupt();
            }
        }

        /**
         * 表示退出await()方法需要进行自我中断一下，中断发生在signal之后
         */
        private static final int REINTERRUPT = 1;

        /**
         * 表示退出await()方法时需要抛出中断异常 这种模式发生在signal之前
         */
        private static final int THROW_IE = -1;

        /**
         * 检查节点的中断模式
         * 是否发生中断  是 否
         * 发生中断的情况 ： 在signal()之前 - 在signal()之后
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
        }

        /**
         * 进行再次中断
         */
        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            //调用signal之前
            if (interruptMode == THROW_IE) {
                throw new InterruptedException();
            }
            //调用signal之后
            else if (interruptMode == REINTERRUPT) {
                selfInterrupt();
            }
        }

        /**
         * 等待
         */
        @Override
        public final void await() throws InterruptedException {
            //当前线程已经被中断了 退出
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            //将当前线程包装成Node节点 加入到条件队列
            Node node = addConditionWaiter();
            //释放当前线程占用的锁 保存当前的锁状态
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            //当前节点不再同步队列中 说明刚刚被await()还没有调用single()方法 直接将当前线程挂起
            while (!isOnSyncQueue(node)) {
                //线程挂起  被唤醒继续向下执行
                LockSupport.park(this);

                //执行下面代码 线程被唤醒 唤醒的原因：中断 调用signal()方法
                //检查线程被唤醒的原因 如是因为中断 跳出循环
                //中断的两种情况：在signal()之前中断抛出中断异常 在signal()之后中断 补一个自我中断
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            //此时线程在同步队列 调用AQS的acquireQueued()进行争抢同步状态（返回中断状态）所以当await()方法返回时 一定是争夺资源成功了
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            //再次过滤所有状态为CANCELLED的节点 上面的断开
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            //对中断状态进行处理 知道最后获取锁成功之后才会进行中断处理
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
        }

        /**
         *
         */
        @Override
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return deadline - System.nanoTime();
        }

        /**
         * 带时间期限的等待
         */
        @Override
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return !timedout;
        }

        /**
         * 超时等待
         */
        @Override
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
            return !timedout;
        }

        //  功能支持方法

        /**
         *
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * 判读是否存在条件队列
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 获取条件队列长度
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    ++n;
                }
            }
            return n;
        }

        /**
         * 获取条件队列中的所有线程集合
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null) {
                        list.add(t);
                    }
                }
            }
            return list;
        }
    }

    /*-------------------------------------------CAS操作---------------------------------------------*/

    /**
     * CAS操作
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long stateOffset;

    private static final long headOffset;

    private static final long tailOffset;

    private static final long waitStatusOffset;

    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS 设置头结点 仅用于enq()方法
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS 设置尾节点 仅用于enq()方法
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS设置等待状态
     */
    private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * CAS设置后继节点
     */
    private static final boolean compareAndSetNext(Node node, Node expect, Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}