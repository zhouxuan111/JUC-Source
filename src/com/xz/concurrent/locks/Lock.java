package com.xz.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Lock l = ...;
 * //上锁
 * l.lock();
 * try {
 * // 访问被锁住的对象
 * } finally {
 * //释放锁
 * l.unlock();
 * }}
 */
public interface Lock {

    /**
     * 获取锁资源
     */
    void lock();

    /**
     * 尝试获取锁 若当前线程被调用interrupt进行中断，并抛出异常 否则获取锁
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * 判断能否获取锁 若能获取锁 则获取锁 并返回true 返回true代表已经返回锁
     * Lock lock = ...;
     * if (lock.tryLock()) {
     * try {
     * // manipulate protected state
     * } finally {
     * lock.unlock();
     * }
     * } else {
     * // perform alternative actions
     * }}
     */
    boolean tryLock();

    /**
     * 尝试超时的获取锁 当前线程在一下三种情况下返回
     * 1.但前线程在超时时间内获取锁
     * 2.当前线程在超时时间内被中断
     * 3.超时时间结束 返回false
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁
     */
    void unlock();

    /**
     * 返回与此Lock对象绑定Condition实例
     */
    Condition newCondition();
}
