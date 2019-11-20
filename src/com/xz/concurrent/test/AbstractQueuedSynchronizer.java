package com.xz.concurrent.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.locks.LockSupport;

import com.xz.concurrent.locks.AbstractOwnableSynchronizer;
import sun.misc.Unsafe;

/**
 * @author xuanzhou
 * @date 2019/11/19 10:44
 */
public class AbstractQueuedSynchronizer extends AbstractOwnableSynchronizer implements java.io.Serializable {

    /*构造方法*/
    public AbstractQueuedSynchronizer() {
    }

    /*数据结构*/
    static final class Node {

        /**
         * 共享
         */
        static final Node SHARED = new Node();

        /**
         * 独占
         */
        static final Node EXCLUSIVE = null;

        /*四大结构*/

        volatile Node prev;

        volatile Node next;

        /**
         * 代表当前节点的线程等待锁的状态
         * 独占模式：只关注CANCELLED SINGLE
         */
        volatile int waitStatus;

        volatile Thread thread;

        /**
         * 等待队列中的后继节点 用于条件队列和共享锁
         * 在独占模式下 恒为null
         */
        Node nextWaiter;

        /*五大状态  0 -1 -2 -3 1*/

        /**
         * 等待状态：取消 当前线程因为取消或者中断被取消 终结状态 需要从同步队列中删除
         */
        static final int CANCELLED = 1;

        /**
         * 等待状态：通知 当前线程的后继线程被阻塞或者即将被阻塞，当前线程释放锁或者取消后需要唤醒后继线程
         * 一般是后继线程来设置前驱线程
         * 当前节点的后继节点处于等待状态 当前节点的线程若释放同步状态或者被取消 会通知后继节点 是后继节点的线程得以运行
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


        /*方法*/

        /**
         * 获取前驱节点
         */
        final Node processor() {
            Node pre = prev;
            if (null == pre) {
                throw new NullPointerException();
            }
            else {
                return pre;
            }
        }

        //判断是否是共享模式
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /*构造方法*/

        public Node() {
        }

        /**
         * 同步队列使用addWaiter
         * @param thread
         * @param mode
         */
        public Node(Thread thread, Node mode) {
            this.thread = thread;
            nextWaiter = mode;
        }

        /**
         * 条件队列使用 Condition
         * @param waitStatus
         * @param thread
         */
        public Node(int waitStatus, Thread thread) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /*CAS操作*/
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long stateOffset;

    private static final long headOffset;

    private static final long tailOffset;

    private static final long waitStatusOffset;

    private static final long nextOffset;

    //获取属性内存偏移量
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
     * CAS设置头结点 enq()使用
     * @param update
     * @return
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS设置尾结点 enq()使用
     * @param except
     * @param update
     * @return
     */
    private final boolean compareAndSetTail(Node except, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, except, update);
    }

    /**
     * CAS设置等待节点的waitStatus
     * @param node
     * @param expect
     * @param update
     * @return
     */
    private static final boolean compareAndSetWaitStatus(Node node, int expect, int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * CAS设置后继节点
     * @param node
     * @param expect
     * @param update
     * @return
     */
    public static final boolean compareAndSetNext(Node node, Node expect, Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }



    /*CLH同步队列*/

    /*属性*/

    /**
     * 头结点 不代表任何节点
     */
    private transient volatile Node head;

    /**
     * 尾结点
     */
    private transient volatile Node tail;

    /**
     * 状态
     */
    private volatile int state;

    /**
     * 线程自旋的等待时间 微秒
     */
    static final long spinForTimeoutThreshold = 1000L;



    /*state方法*/

    protected final int getState() {
        return state;
    }

    protected final void setState(int state) {
        state = state;

    }

    /**
     * CAS设置state状态
     * @param except
     * @param update
     * @return
     */
    protected final boolean compareAndSetState(int except, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, except, update);
    }

    /*--------------------------------------------添加同步队列节点--------------------------------------------------*/

    /**
     * 自旋添加同步队列节点
     * @param node
     * @return
     */
    private Node enq(Node node) {
        for (; ; ) {
            Node pre = tail;
            //同步队列不存在 构造
            if (pre == null) {
                //新创建一个节点
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            }
            else {
                node.prev = pre;
                if (compareAndSetTail(pre, node)) {
                    pre.next = node;
                    return pre;
                }
            }
        }
    }

    /**
     * 将节点添加到同步队列尾部
     * mode 为共享模式 独占模式
     */

    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);

        Node pre = tail;
        //快速入队
        if (null != pre) {
            node.prev = pre;
            if (compareAndSetTail(pre, node)) {
                pre.next = node;
                return node;
            }
        }
        //快速入队失败 存在竞争 进行自旋入队
        enq(node);
        return node;
    }

    /**
     * 设置头结点
     * 调用这个方法代表已经成功获取到锁了
     * @param node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /*---------------------------------------------------独占式获取锁--------------------------------------------------*/

    /**
     * 可重写方法
     * 独占式获取锁
     * 用于实现Lock中的tryLock()方法
     * @param arg
     * @return
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * SINGLE:代表的不是当前节点 而是当前节点的下一个状态 说明他的下一个节点被挂起或者马上被
     *        挂起 当当前节点的waitStatus为SINGLE,当释放锁或者放弃锁时要做额外的操作 唤醒后继线程。
     * CANCELLED:Node代表的当前线程取消了排队 自动放弃获取锁
     */
    /**
     * 判断当前节点的线程是否可以安全的进入阻塞
     * @param prev
     * @param node
     * @return
     */
    private static boolean shouldParkAfterFailedAcquire(Node prev, Node node) {
        //获取前驱节点的状态
        int ws = prev.waitStatus;
        if (ws == Node.SIGNAL) {
            return true;
        }
        //前驱节点的状态为CANCELLED  更换前驱节点为状态不为CANCELLED的节点
        if (ws > 0) {
            do {
                node.prev = prev = prev.prev;
            }
            while (prev.waitStatus > 0);

            prev.next = node;
        }
        //前驱节点既不是SINGLE CANCELLED，用CAS设置前驱节点的状态SINGLE
        else {
            compareAndSetWaitStatus(prev, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 阻塞并获取线程是否被中断
     * @return
     */
    private final boolean parkAndCheckInterrupt() {
        //阻塞
        LockSupport.park();
        //返回中断状态
        return Thread.interrupted();
    }

    /**
     * 中断当前线程
     */
    static void selfInterrupted() {
        Thread.currentThread().interrupt();
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
     * 当前节点以自旋的方式获取同步状态 获取失败 则阻塞节点中的线程 阻塞后的线程等待前驱线程来唤醒
     * 这个过程中有中断 返回true 否则返回false
     * @param node
     * @param arg
     * @return
     */
    final boolean accquireQueued(final Node node, int arg) {
        //获取同步状态标志 默认 失败
        boolean failed = true;
        try {
            //默认线程没有被中断过
            boolean interrupt = false;
            for (; ; ) {
                //获取当前节点的前驱节点
                Node prev = node.processor();
                //前驱节点是头结点 并且尝试获取同步状态成功
                if (prev == head && tryAcquire(arg)) {
                    //当前节点设置为头结点
                    setHead(node);
                    prev.next = null;
                    failed = false;
                    return interrupt;
                }

                //不是头节点或者未获取成功
                //shouldParkAfterFailedAcquire返回false 会继续回到循环中尝试获取锁
                if (shouldParkAfterFailedAcquire(prev, node) && parkAndCheckInterrupt()) {
                    interrupt = true;
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
     * 独占式 可中断的自旋获取同步状态 若失败 阻塞当前线程
     * @param arg
     * @throws InterruptedException
     */
    private void doAcquireInterruptibly(int arg) throws InterruptedException {
        Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.processor();
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
     * 独占式 响应中断 超时时间 自旋的获取同步状态
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout < 0L) {
            return false;
        }
        final long deadLine = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;

        try {
            for (; ; ) {
                final Node p = node.processor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null;
                    failed = false;
                    return true;
                }
                nanosTimeout = deadLine - System.nanoTime();
                if (nanosTimeout < 0L) {
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
     * 模板方法  不响应中断 若存在中断状况 不会将线程从同步队列中移除
     * 独占式的获取锁
     * @param arg
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) && accquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupted();
        }
    }

    /**
     * 模板方法 独占式响应中断的获取同步状态
     * @param arg
     * @throws InterruptedException
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        doAcquireInterruptibly(arg);
    }

    /**
     * 模板方法  在acquireInterruptibly基础上添加超时设置
     * @param arg
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }


    /*----------------------------------------独占式释放锁-----------------------------------------*/

    /**
     * 可重写方法  独占式释放同步状态
     * @param arg
     * @return
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 使用LockSupport.unpark(Thread thread)来唤醒被阻塞的线程
     */
    private void unparkSuccessor(Node node) {

        int ws = node.waitStatus;
        //-1 代表 等待状态
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }
        //获取后继线程
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
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
     * 处理头节点
     * 独占式释放同步状态
     * @param arg
     * @return
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    /*------------------------------------------共享模式-----------------------------------------------*/

    /**
     * 模板方法
     * 共享式获取锁 不响应中断
     * tryAcquireShared(arg)>0代表获取到同步状态
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    /**
     * 模板方法  共享模式 可中断 获取同步状态
     * @param arg
     * @throws InterruptedException
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
     * 模板方法 共享模式 可中断 超时时间 获取同步状态
     * @param arg
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    public boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        return tryAcquireShared(arg) > 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 模板方法 共享模式释放同步状态
     * @param arg
     * @return
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    /**
     * 共享模式 可中断 超时时间 自旋获取同步状态
     * @param arg
     * @param nanosTimeout
     * @return
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout < 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node pre = node.processor();
                if (pre == head) {
                    int r = tryAcquireShared(arg);
                    if (r > 0) {
                        setHeadAndPropagate(node, r);
                        pre.next = null;
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }

                if (shouldParkAfterFailedAcquire(pre, node) && nanosTimeout > spinForTimeoutThreshold) {
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
     * 共享式 可中断 自旋获取同步状态
     * @param arg
     */
    private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
        Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {

            for (; ; ) {
                Node pre = node.processor();
                if (pre == head) {
                    int r = tryAcquireShared(arg);
                    if (r > 0) {
                        setHeadAndPropagate(node, r);
                        pre.next = null;
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(pre, node) && parkAndCheckInterrupt()) {
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
     * 共享模式 不响应中断 自旋的获取同步状态
     * @param arg
     */
    private void doAcquireShared(int arg) {
        //将线程封装Node
        Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupt = false;
            for (; ; ) {
                final Node pre = node.processor();
                if (pre == head) {
                    int r = tryAcquireShared(arg);
                    //同步状态获取成功
                    if (r > 0) {
                        //设置头结点 唤醒后继节点
                        setHeadAndPropagate(node, r);
                        pre.next = null;
                        failed = false;
                        if (interrupt) {
                            selfInterrupted();
                        }
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(pre, node) && parkAndCheckInterrupt()) {
                    interrupt = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 共享模式设置头结点 在设置头结点的基础上 若有剩余量 继续唤醒后继线程 因为是共享模式
     * @param node
     * @param r
     */
    private void setHeadAndPropagate(Node node, int r) {
        Node h = head;
        setHead(node);
        if (r > 0 || h != null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s != null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    /**
     * 共享模式下 自旋释放同步状态
     * 共享模式在释放资源时  存在并发问题 通过自旋+CAS来进行处理
     * 独占模式只需要更新头结点
     */
    private void doReleaseShared() {
        for (; ; ) {
            Node h = head;
            //头结点不为空 存在后继节点
            if (h != null || h != tail) {
                int ws = h.waitStatus;
                //后继节点处于待唤醒状态爱
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) {
                        continue;
                    }
                    //唤醒后继节点
                    unparkSuccessor(h);
                }
                // 后继节点没有准备唤醒 将0状态设置成PROPAGATE 以待下次传播
                else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE)) {
                    continue;
                }
            }
            //头结点未发生变化 跳出循环
            if (h == head) {
                break;
            }
        }
    }

    /**
     * 重写方法 共享式获取锁
     * @param arg
     * @return
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 重写方法 共享模式释放锁
     * @param arg
     * @return
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }


    /*--------------------------------------功能方法------------------------------------------------------*/

    /**
     * 是否存在同步队列
     * @return
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    public final boolean hasContented() {
        return head != null;
    }

    /**
     * 获取同步队列的第一个线程
     * @return
     */
    public final Thread getFirstQueuedThread() {
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * 获取同步队列中的第一个线程
     * @return
     */
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
     * 判断线程是否在队列中
     * @param thread
     * @return
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
     * 判断是否当前节点有前驱
     * @return
     */
    public final boolean hasQueuedProdecessors() {
        Node t = tail;

        Node h = head;

        Node s;

        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    /**
     * 获取同步队列的长度
     * @return
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * 获取同步队列的线程集合
     * @return
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (null != t) {
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
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (null != t) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * 获取同步队列中共享模式的线程集合
     * @return
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (null != t) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * 可重写方法 判断当前锁是否拥有独占锁
     * @return
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 判断第一个节点是否是独占模式锁
     * @return
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;

        return (h = head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
    }

    /**
     * 判断是否有前驱节点
     */
    public final boolean hasQueuedPredecessors() {
        Node t = tail;

        Node h = head;

        Node s;

        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }

}
