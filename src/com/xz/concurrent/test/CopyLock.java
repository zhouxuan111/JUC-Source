package com.xz.concurrent.test;

import java.util.concurrent.TimeUnit;

import com.xz.concurrent.locks.Condition;

/**
 * @author xuanzhou
 * @date 2019/11/18 11:08
 */
public interface CopyLock {

    /**
     * 获取锁
     */
    void lock();

    /**
     * 阐释获取锁 当线程被中断，抛出异常 否则获取锁（interrupt）
     */
    void lockInterruptibly() throws InterruptedException;

    /**
     * 阐释获取锁 若能获取到 返回true 否则 返回false  只会尝试一次
     * @return
     */
    boolean tryLock();

    /**
     * 尝试超时的获取锁 当前线程在一下三种情况返回
     * 当前线程在超时时间内获取锁
     * 当前线程在超时时间内被中断 抛出异常
     * 超时时间结束 返回false
     * @param time
     * @param unit
     * @return
     */
    boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 释放锁
     */
    void unlock();

    /**
     * 返回与此Lock绑定的Condition对象
     * @return
     */
    Condition newCondition();

}
