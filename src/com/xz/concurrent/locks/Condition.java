package com.xz.concurrent.locks;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 有界阻塞队列
 * <pre>{@code
 * class BoundedBuffer {
 *  final Lock lock = new ReentrantLock();
 *  final Condition notFull  = lock.newCondition();
 *  final Condition notEmpty = lock.newCondition();
 *  final Object[] items = new Object[100];
 *  int putptr, takeptr, count;
 *  public void put(Object x) throws InterruptedException {
 *      lock.lock();
 *      try {
 *          while (count == items.length)
 *              notFull.await();
 *          items[putptr] = x;
 *          if (++putptr == items.length) putptr = 0;
 *          ++count;
 *          notEmpty.signal();
 *      } finally {
 *          lock.unlock();
 *      }
 *  }
 *  public Object take() throws InterruptedException {
 *      lock.lock();
 *      try {
 *          while (count == 0)
 *              notEmpty.await();
 *          Object x = items[takeptr];
 *          if (++takeptr == items.length) takeptr = 0;
 *          --count;
 *          notFull.signal();
 *          return x;
 *      } finally {
 *          lock.unlock();
 *      }
 *  }
 * }
 * @author Doug Lea
 * @since 1.5
 */
public interface Condition {

    /**
     * 当前线程进行等待 同时释放锁 其他线程中使用single() singleAll()线程重新获取锁并继续执行
     * 或者当前线程被中断时  也可以跳出等待
     */
    void await() throws InterruptedException;

    /**
     * 同上 但是不响应中断
     */
    void awaitUninterruptibly();

    /**
     * boolean aMethod(long timeout, TimeUnit unit) {
     * long nanos = unit.toNanos(timeout);
     * lock.lock();
     * try {
     * while (!conditionBeingWaitedFor()) {
     * if (nanos <= 0L)
     * return false;
     * nanos = theCondition.awaitNanos(nanos);
     * }
     * // ...
     * } finally {
     * lock.unlock();
     * }
     * }}
     * 当前线程等待 知道线程被唤醒和中断 或者等待时间耗尽 时间单位为纳秒
     * 同时释放锁
     */
    long awaitNanos(long nanosTimeout) throws InterruptedException;

    /**
     * 当前线程等待，直到线程被唤醒或者中断，或者等待时间耗尽 等待时释放锁
     */
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     * <pre> {@code
     * boolean aMethod(Date deadline) {
     *   boolean stillWaiting = true;
     *   lock.lock();
     *   try {
     *     while (!conditionBeingWaitedFor()) {
     *       if (!stillWaiting)
     *         return false;
     *       stillWaiting = theCondition.awaitUntil(deadline);
     *     }
     *     // ...
     *   } finally {
     *     lock.unlock();
     *   }
     * }}</pre>
     * 当前线程等待 知道线程被唤醒或者中断 或者到达指定日期
     */
    boolean awaitUntil(Date deadline) throws InterruptedException;

    /**
     * 唤醒一个等待的线程
     */
    void signal();

    /**
     * 唤醒所有等待的线程
     */
    void signalAll();
}

