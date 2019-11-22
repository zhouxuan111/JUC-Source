package com.xz.concurrent.locks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;

public abstract class AbstractQueuedLongSynchronizer extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414692L;

    protected AbstractQueuedLongSynchronizer() {
    }

    static final class Node {

        static final AbstractQueuedLongSynchronizer.Node SHARED = new AbstractQueuedLongSynchronizer.Node();

        static final AbstractQueuedLongSynchronizer.Node EXCLUSIVE = null;

        static final int CANCELLED = 1;

        static final int SIGNAL = -1;

        static final int CONDITION = -2;

        static final int PROPAGATE = -3;

        volatile int waitStatus;

        volatile AbstractQueuedLongSynchronizer.Node prev;

        volatile AbstractQueuedLongSynchronizer.Node next;

        volatile Thread thread;

        AbstractQueuedLongSynchronizer.Node nextWaiter;

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

    protected final void setState(long newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(long expect, long update) {
        return unsafe.compareAndSwapLong(this, stateOffset, expect, update);
    }

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
        if (ws == AbstractQueuedLongSynchronizer.Node.SIGNAL) {
            return true;
        }
        if (ws > 0) {

            do {
                node.prev = pred = pred.prev;
            }
            while (pred.waitStatus > 0);
            pred.next = node;
        }
        else {

            compareAndSetWaitStatus(pred, ws, AbstractQueuedLongSynchronizer.Node.SIGNAL);
        }
        return false;
    }

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

    protected boolean tryReleaseShared(long arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    public final void acquire(long arg) {
        if (!tryAcquire(arg) && acquireQueued(addWaiter(AbstractQueuedLongSynchronizer.Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    public final void acquireInterruptibly(long arg) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        if (!tryAcquire(arg)) {
            doAcquireInterruptibly(arg);
        }
    }

    public final boolean tryAcquireNanos(long arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) || doAcquireNanos(arg, nanosTimeout);
    }

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

    @Override
    public String toString() {
        long s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() + "[State = " + s + ", " + q + "empty queue]";
    }

    final boolean isOnSyncQueue(AbstractQueuedLongSynchronizer.Node node) {
        if (node.waitStatus == AbstractQueuedLongSynchronizer.Node.CONDITION || node.prev == null) {
            return false;
        }
        if (node.next != null) // If has successor, it must be on queue
        {
            return true;
        }

        return findNodeFromTail(node);
    }

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

    final boolean transferForSignal(AbstractQueuedLongSynchronizer.Node node) {

        if (!compareAndSetWaitStatus(node, AbstractQueuedLongSynchronizer.Node.CONDITION, 0)) {
            return false;
        }

        AbstractQueuedLongSynchronizer.Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, AbstractQueuedLongSynchronizer.Node.SIGNAL)) {
            LockSupport.unpark(node.thread);
        }
        return true;
    }

    final boolean transferAfterCancelledWait(AbstractQueuedLongSynchronizer.Node node) {
        if (compareAndSetWaitStatus(node, AbstractQueuedLongSynchronizer.Node.CONDITION, 0)) {
            enq(node);
            return true;
        }

        while (!isOnSyncQueue(node)) {
            Thread.yield();
        }
        return false;
    }

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

    public final boolean owns(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    public final boolean hasWaiters(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.hasWaiters();
    }

    public final int getWaitQueueLength(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitQueueLength();
    }

    public final Collection<Thread> getWaitingThreads(AbstractQueuedLongSynchronizer.ConditionObject condition) {
        if (!owns(condition)) {
            throw new IllegalArgumentException("Not owner");
        }
        return condition.getWaitingThreads();
    }

    public class ConditionObject implements Condition, java.io.Serializable {

        private static final long serialVersionUID = 1173984872572414699L;

        private transient AbstractQueuedLongSynchronizer.Node firstWaiter;

        private transient AbstractQueuedLongSynchronizer.Node lastWaiter;

        public ConditionObject() {
        }

        private AbstractQueuedLongSynchronizer.Node addConditionWaiter() {
            AbstractQueuedLongSynchronizer.Node t = lastWaiter;
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

        private void doSignal(AbstractQueuedLongSynchronizer.Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null) {
                    lastWaiter = null;
                }
                first.nextWaiter = null;
            }
            while (!transferForSignal(first) && (first = firstWaiter) != null);
        }

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

        private static final int REINTERRUPT = 1;

        private static final int THROW_IE = -1;

        private int checkInterruptWhileWaiting(AbstractQueuedLongSynchronizer.Node node) {
            return Thread.interrupted() ? (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
        }

        private void reportInterruptAfterWait(int interruptMode) throws InterruptedException {
            if (interruptMode == THROW_IE) {
                throw new InterruptedException();
            }
            else if (interruptMode == REINTERRUPT) {
                selfInterrupt();
            }
        }

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

        final boolean isOwnedBy(AbstractQueuedLongSynchronizer sync) {
            return sync == AbstractQueuedLongSynchronizer.this;
        }

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

    private final boolean compareAndSetHead(AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private final boolean compareAndSetTail(AbstractQueuedLongSynchronizer.Node expect,
            AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private static final boolean compareAndSetWaitStatus(AbstractQueuedLongSynchronizer.Node node, int expect,
            int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    private static final boolean compareAndSetNext(AbstractQueuedLongSynchronizer.Node node,
            AbstractQueuedLongSynchronizer.Node expect, AbstractQueuedLongSynchronizer.Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
