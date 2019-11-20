package com.xz.concurrent.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 实现共享式获取锁 同时获取的线程只能有两个 state 初始化为2 获取一个减1 线程释放 加1
 * state = 0,1,2
 * @author xuanzhou
 * @date 2019/10/30 17:48
 */
public class TwiceLock implements Lock {

    private static final class Sync extends AbstractQueuedSynchronizer {

        Sync(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("count >0");
            }
            //初始化state = 2 因为只允许两个线程同时访问
            setState(count);
        }

        @Override
        public int tryAcquireShared(int arg) {
            for (; ; ) {
                int current = getState();
                int newCount = current = arg;
                if (newCount < 0 || compareAndSetState(current, newCount)) {
                    return newCount;
                }
            }
        }

        @Override
        public boolean tryReleaseShared(int arg) {
            for (; ; ) {
                int current = getState();
                int newCount = current + arg;
                if (compareAndSetState(current, newCount)) {
                    return true;
                }
            }
        }
    }

    private final Sync sync = new Sync(2);

    /**
     * 加锁
     */
    @Override
    public void lock() {
        sync.tryAcquireShared(1);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        sync.tryReleaseShared(1);
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
