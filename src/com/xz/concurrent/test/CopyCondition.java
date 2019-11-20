package com.xz.concurrent.test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author xuanzhou
 * @date 2019/11/18 14:07
 */
public interface CopyCondition {

    /**
     * 等待 释放锁 并且可中断
     * @throws InterruptedException
     */
    void await() throws InterruptedException;

    /**
     * 当前线程进行等待 不支持中断
     */
    void awaitUninterruptibly();

    /**
     * 当前线程等待 直到线程被唤醒或者中断 或等待时间耗尽 释放锁
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * 当前线程等待 知道被唤醒或者中断 或者等待时间耗尽
     * @param time
     * @param unit
     * @return
     * @throws InterruptedException
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     * 当前线程等待 直到线程被唤醒或者中断 或者到达指定日期
     * @param deadline
     * @return
     * @throws InterruptedException
     */
    boolean awaitUnitil(Date deadline) throws InterruptedException;

    /**
     * 唤醒一个线程
     */
    void single();

    /**
     * 唤醒所有线程
     */
    void singelAll();
}
