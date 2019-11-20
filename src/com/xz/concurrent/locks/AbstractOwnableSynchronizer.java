package com.xz.concurrent.locks;

/**
 * 监控独占锁
 * @author Doug Lea
 * @since 1.6
 */
public abstract class AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 3737899427754241961L;

    /**
     * 无参构造
     */
    protected AbstractOwnableSynchronizer() {
    }

    /**
     * 独占锁的线程
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * 设置当前独占的线程
     * @param thread the owner thread
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * 获取当前独占锁的线程
     * @return the owner thread
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
