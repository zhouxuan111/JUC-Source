package com.xz.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;

public abstract class AbstractQueuedLongSynchronizer extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414692L;

    /**
     * Creates a new {@code AbstractQueuedLongSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedLongSynchronizer() {
    }

    static final class Node {

        /**
         * Marker to indicate a node is waiting in shared mode
         */
        static final AbstractQueuedLongSynchronizer.Node SHARED = new AbstractQueuedLongSynchronizer.Node();

        /**
         * Marker to indicate a node is waiting in exclusive mode
         */
        static final AbstractQueuedLongSynchronizer.Node EXCLUSIVE = null;

        /**
         * waitStatus value to indicate thread has cancelled
         */
        static final int CANCELLED = 1;

        /**
         * waitStatus value to indicate successor's thread needs unparking
         */
        static final int SIGNAL = -1;

        /**
         * waitStatus value to indicate thread is waiting on condition
         */
        static final int CONDITION = -2;

        /**
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate
         */
        static final int PROPAGATE = -3;

        volatile int waitStatus;

        volatile AbstractQueuedLongSynchronizer.Node prev;

        volatile AbstractQueuedLongSynchronizer.Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        AbstractQueuedLongSynchronizer.Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        final AbstractQueuedLongSynchronizer.Node predecessor() throws NullPointerException {
            AbstractQueuedLongSynchronizer.Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            }
            else {
                return p;
            }
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, AbstractQueuedLongSynchronizer.Node mode) {     // Used by addWaiter
            nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    private transient volatile AbstractQueuedLongSynchronizer.Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile AbstractQueuedLongSynchronizer.Node tail;

    /**
     * The synchronization state.
     */
    private volatile long state;

    protected final long getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(long newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(long expect, long update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapLong(this, stateOffset, expect, update);
    }

    // Queuing utilities

    static final long spinForTimeoutThreshold = 1000L;

    private AbstractQueuedLongSynchronizer.Node enq(final AbstractQueuedLongSynchronizer.Node node) {
        for (; ; ) {
            AbstractQueuedLongSynchronizer.Node t = tail;
            if (t == null) { // Must initialize
                if (compareAndSetHead(new AbstractQueuedLongSynchronizer.Node())) {
                    tail = head;
                }
            }
            else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    private AbstractQueuedLongSynchronizer.Node addWaiter(AbstractQueuedLongSynchronizer.Node mode) {
        AbstractQueuedLongSynchronizer.Node node = new AbstractQueuedLongSynchronizer.Node(Thread.currentThread(),
                mode);
        // Try the fast path of enq; backup to full enq on failure
        AbstractQueuedLongSynchronizer.Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    private void setHead(AbstractQueuedLongSynchronizer.Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    private void unparkSuccessor(AbstractQueuedLongSynchronizer.Node node) {

        int ws = node.waitStatus;
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }

        AbstractQueuedLongSynchronizer.Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (AbstractQueuedLongSynchronizer.Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

    private void doReleaseShared() {

        for (; ; ) {
            AbstractQueuedLongSynchronizer.Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == AbstractQueuedLongSynchronizer.Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, AbstractQueuedLongSynchronizer.Node.SIGNAL, 0)) {
                        continue;            // loop to recheck cases
                    }
                    unparkSuccessor(h);
                }
                else if (ws == 0 && !compareAndSetWaitStatus(h, 0, AbstractQueuedLongSynchronizer.Node.PROPAGATE)) {
                    continue;                // loop on failed CAS
                }
            }
            if (h == head)                   // loop if head changed
            {
                break;
            }
        }
    }

    private void setHeadAndPropagate(AbstractQueuedLongSynchronizer.Node node, long propagate) {
        AbstractQueuedLongSynchronizer.Node h = head; // Record old head for check below
        setHead(node);

        if (propagate > 0 || h == null || h.waitStatus < 0 || (h = head) == null || h.waitStatus < 0) {
            AbstractQueuedLongSynchronizer.Node s = node.next;
            if (s == null || s.isShared()) {
                doReleaseShared();
            }
        }
    }

    // Utilities for various versions of acquire

    private void cancelAcquire(AbstractQueuedLongSynchronizer.Node node) {
        if (node == null) {
            return;
        }

        node.thread = null;

        AbstractQueuedLongSynchronizer.Node pred = node.prev;
        while (pred.waitStatus > 0) {
            node.prev = pred = pred.prev;
        }

        AbstractQueuedLongSynchronizer.Node predNext = pred.next;

        node.waitStatus = AbstractQueuedLongSynchronizer.Node.CANCELLED;

        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        }
        else {

            int ws;
            if (pred != head && ((ws = pred.waitStatus) == AbstractQueuedLongSynchronizer.Node.SIGNAL || (ws <= 0
                    && compareAndSetWaitStatus(pred, ws, AbstractQueuedLongSynchronizer.Node.SIGNAL)))
                    && pred.thread != null) {
                AbstractQueuedLongSynchronizer.Node next = node.next;
                if (next != null && next.waitStatus <= 0) {
                    compareAndSetNext(pred, predNext, next);
                }
            }
            else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    private static boolean shouldParkAfterFailedAcquire(AbstractQueuedLongSynchronizer.Node pred,
            AbstractQueuedLongSynchronizer.Node node) {
        int ws = pred.waitStatus;
        if (ws == AbstractQueuedLongSynchronizer.Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */ {
            return true;
        }
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            }
            while (pred.waitStatus > 0);
            pred.next = node;
        }
        else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            compareAndSetWaitStatus(pred, ws, AbstractQueuedLongSynchronizer.Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    final boolean acquireQueued(final AbstractQueuedLongSynchronizer.Node node, long arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private void doAcquireInterruptibly(long arg) throws InterruptedException {
        final AbstractQueuedLongSynchronizer.Node node = addWaiter(AbstractQueuedLongSynchronizer.Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
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

    private boolean doAcquireNanos(long arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final AbstractQueuedLongSynchronizer.Node node = addWaiter(AbstractQueuedLongSynchronizer.Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
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

    private void doAcquireShared(long arg) {
        final AbstractQueuedLongSynchronizer.Node node = addWaiter(AbstractQueuedLongSynchronizer.Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
                if (p == head) {
                    long r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted) {
                            selfInterrupt();
                        }
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private void doAcquireSharedInterruptibly(long arg) throws InterruptedException {
        final AbstractQueuedLongSynchronizer.Node node = addWaiter(AbstractQueuedLongSynchronizer.Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
                if (p == head) {
                    long r = tryAcquireShared(arg);
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

    private boolean doAcquireSharedNanos(long arg, long nanosTimeout) throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final AbstractQueuedLongSynchronizer.Node node = addWaiter(AbstractQueuedLongSynchronizer.Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final AbstractQueuedLongSynchronizer.Node p = node.predecessor();
                if (p == head) {
                    long r = tryAcquireShared(arg);
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

    protected boolean tryAcquire(long arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryRelease(long arg) {
        throw new UnsupportedOperationException();
    }

    protected long tryAcquireShared(long arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     * <p>This method is always invoked by the thread performing release.
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     * @param arg the release argument. This value is always the one
     * passed to a release method, or the current state value upon
     * entry to a condition wait.  The value is otherwise
     * uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     * waiting acquire (shared or exclusive) to succeed; and
     * {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(long arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link AbstractQueuedLongSynchronizer.ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link AbstractQueuedLongSynchronizer.ConditionObject} methods, so need
     * not be defined if conditions are not used.
     * @return {@code true} if synchronization is held exclusively;
     * {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     */
    public final void acquire(long arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(AbstractQueuedLongSynchronizer.Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(long arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     * @param arg the acquire argument.  This value is conveyed to
     * {@link #tryAcquire} but is otherwise uninterpreted and
     * can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(long arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     * @param arg the release argument.  This value is conveyed to
     * {@link #tryRelease} but is otherwise uninterpreted and
     * can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(long arg) {
        if (tryRelease(arg)) {
            AbstractQueuedLongSynchronizer.Node h = head;
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    public final void acquireShared(long arg) {
        if (tryAcquireShared(arg) < 0) {
            doAcquireShared(arg);
        }
    }

    public final void acquireSharedInterruptibly(long arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (tryAcquireShared(arg) < 0) {
            doAcquireSharedInterruptibly(arg);
        }
    }

    public final boolean tryAcquireSharedNanos(long arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquireShared(arg) >= 0 || doAcquireSharedNanos(arg, nanosTimeout);
    }

    public final boolean releaseShared(long arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    public final boolean hasContended() {
        return head != null;
    }

    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    private Thread fullGetFirstQueuedThread() {

        AbstractQueuedLongSynchronizer.Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null) || (
                (h = head) != null && (s = h.next) != null && s.prev == head && (st = s.thread) != null)) {
            return st;
        }

        AbstractQueuedLongSynchronizer.Node t = tail;
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

    public final boolean isQueued(Thread thread) {
        if (thread == null) {
            throw new NullPointerException();
        }
        for (AbstractQueuedLongSynchronizer.Node p = tail; p != null; p = p.prev) {
            if (p.thread == thread) {
                return true;
            }
        }
        return false;
    }

    final boolean apparentlyFirstQueuedIsExclusive() {
        AbstractQueuedLongSynchronizer.Node h, s;
        return (h = head) != null && (s = h.next) != null && !s.isShared() && s.thread != null;
    }

    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        AbstractQueuedLongSynchronizer.Node t = tail; // Read fields in reverse initialization order
        AbstractQueuedLongSynchronizer.Node h = head;
        AbstractQueuedLongSynchronizer.Node s;
        return h != t && ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    public final int getQueueLength() {
        int n = 0;
        for (AbstractQueuedLongSynchronizer.Node p = tail; p != null; p = p.prev) {
            if (p.thread != null) {
                ++n;
            }
        }
        return n;
    }

    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (AbstractQueuedLongSynchronizer.Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (AbstractQueuedLongSynchronizer.Node p = tail; p != null; p = p.prev) {
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
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (AbstractQueuedLongSynchronizer.Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null) {
                    list.add(t);
                }
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     * @return a string identifying this synchronizer, as well as its state
     */
    @Override
    public String toString() {
        long s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() + "[State = " + s + ", " + q + "empty queue]";
    }

    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(AbstractQueuedLongSynchronizer.Node node) {
        if (node.waitStatus == AbstractQueuedLongSynchronizer.Node.CONDITION || node.prev == null) {
            return false;
        }
        if (node.next != null) // If has successor, it must be on queue
        {
            return true;
        }
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     * @return true if present
     */
    private boolean findNodeFromTail(AbstractQueuedLongSynchronizer.Node node) {
        AbstractQueuedLongSynchronizer.Node t = tail;
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
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(AbstractQueuedLongSynchronizer.Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, AbstractQueuedLongSynchronizer.Node.CONDITION, 0)) {
            return false;
        }

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        AbstractQueuedLongSynchronizer.Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, AbstractQueuedLongSynchronizer.Node.SIGNAL)) {
            LockSupport.unpark(node.thread);
        }
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(AbstractQueuedLongSynchronizer.Node node) {
        if (compareAndSetWaitStatus(node, AbstractQueuedLongSynchronizer.Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }
        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final long fullyRelease(AbstractQueuedLongSynchronizer.Node node) {
        boolean failed = true;
        try {
            long savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            }
            else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed) {
                node.waitStatus = AbstractQueuedLongSynchronizer.Node.CANCELLED;
            }
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedLongSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedLongSynchronizer}.
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     * @since 1.6
     */
    public class ConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

        /**
         * First node of condition queue.
         */
        private transient AbstractQueuedLongSynchronizer.Node firstWaiter;

        /**
         * Last node of condition queue.
         */
        private transient AbstractQueuedLongSynchronizer.Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * @return its new wait node
         */
        private AbstractQueuedLongSynchronizer.Node addConditionWaiter() {
            AbstractQueuedLongSynchronizer.Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != AbstractQueuedLongSynchronizer.Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            AbstractQueuedLongSynchronizer.Node node = new AbstractQueuedLongSynchronizer.Node(Thread.currentThread(),
                    AbstractQueuedLongSynchronizer.Node.CONDITION);
            if (t == null) {
                firstWaiter = node;
            }
            else {
                t.nextWaiter = node;
            }
            lastWaiter = node;
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(AbstractQueuedLongSynchronizer.Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null) {
                    lastWaiter = null;
                }
                first.nextWaiter = null;
            }
            while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(AbstractQueuedLongSynchronizer.Node first) {
            lastWaiter = firstWaiter = null;
            do {
                AbstractQueuedLongSynchronizer.Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            }
            while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            AbstractQueuedLongSynchronizer.Node t = firstWaiter;
            AbstractQueuedLongSynchronizer.Node trail = null;
            while (t != null) {
                AbstractQueuedLongSynchronizer.Node next = t.nextWaiter;
                if (t.waitStatus != AbstractQueuedLongSynchronizer.Node.CONDITION) {
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

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        @Override
        public final void signal() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            AbstractQueuedLongSynchronizer.Node first = firstWaiter;
            if (first != null) {
                doSignal(first);
            }
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        @Override
        public final void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            AbstractQueuedLongSynchronizer.Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * </ol>
         */
        @Override
        public final void awaitUninterruptibly() {
            AbstractQueuedLongSynchronizer.Node node = addConditionWaiter();
            long savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
            if (acquireQueued(node, savedState) || interrupted) {
                selfInterrupt();
            }
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * Mode meaning to reinterrupt on exit from wait
         */
        private static final int REINTERRUPT = 1;

        /**
         * Mode meaning to throw InterruptedException on exit from wait
         */
        private static final int THROW_IE = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(AbstractQueuedLongSynchronizer.Node node) {
            return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            if (interruptMode == THROW_IE) {
                throw new InterruptedException();
            }
            else if (interruptMode == REINTERRUPT) {
                selfInterrupt();
            }
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        @Override
        public final void await() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            AbstractQueuedLongSynchronizer.Node node = addConditionWaiter();
            long savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            if (node.nextWaiter != null) // clean up if cancelled
            {
                unlinkCancelledWaiters();
            }
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        @Override
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            AbstractQueuedLongSynchronizer.Node node = addConditionWaiter();
            long savedState = fullyRelease(node);
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
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        @Override
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            AbstractQueuedLongSynchronizer.Node node = addConditionWaiter();
            long savedState = fullyRelease(node);
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
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        @Override
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            AbstractQueuedLongSynchronizer.Node node = addConditionWaiter();
            long savedState = fullyRelease(node);
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

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedLongSynchronizer sync) {
            return sync == AbstractQueuedLongSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedLongSynchronizer#hasWaiters(AbstractQueuedLongSynchronizer.ConditionObject)}.
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            for (AbstractQueuedLongSynchronizer.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == AbstractQueuedLongSynchronizer.Node.CONDITION) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedLongSynchronizer#getWaitQueueLength(AbstractQueuedLongSynchronizer.ConditionObject)}.
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            int n = 0;
            for (AbstractQueuedLongSynchronizer.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == AbstractQueuedLongSynchronizer.Node.CONDITION) {
                    ++n;
                }
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedLongSynchronizer#getWaitingThreads(AbstractQueuedLongSynchronizer.ConditionObject)}.
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         * returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (AbstractQueuedLongSynchronizer.Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == AbstractQueuedLongSynchronizer.Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null) {
                        list.add(t);
                    }
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicLong, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    private static final long stateOffset;

    private static final long headOffset;

    private static final long tailOffset;

    private static final long waitStatusOffset;

    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset(AbstractQueuedLongSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset(AbstractQueuedLongSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset(AbstractQueuedLongSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe
                    .objectFieldOffset(AbstractQueuedLongSynchronizer.Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset(AbstractQueuedLongSynchronizer.Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(AbstractQueuedLongSynchronizer.Node expect,
            AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(AbstractQueuedLongSynchronizer.Node node, int expect,
            int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(AbstractQueuedLongSynchronizer.Node node,
            AbstractQueuedLongSynchronizer.Node expect, AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
