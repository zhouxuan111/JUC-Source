package com.xz.concurrent.test;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xuanzhou
 * @date 2019/11/18 13:57
 */
public class ConditionTest {

    public static void main(String[] args) {

        ReentrantLock lock = new ReentrantLock();

        Condition condition = lock.newCondition();

        new Thread(() -> {
            //获取到锁
            lock.lock();
            System.out.println(Thread.currentThread().getName() + " get lock successfully");
            System.out.println(Thread.currentThread().getName() + " wait single");

            //进入等待状态  并且释放锁
            try {
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //重新获取锁
            System.out.println(Thread.currentThread().getName() + " get single successfully");
            //释放锁
            lock.unlock();
        }, "first thread").start();

        new Thread(() -> {
            lock.lock();

            System.out.println(Thread.currentThread().getName() + " get lock successfully");

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(Thread.currentThread().getName() + " send single");
            //唤醒其他线程
            condition.signal();

            System.out.println(Thread.currentThread().getName() + " get single successfully");
            //释放锁
            lock.unlock();
        }, "second thread").start();
    }
}
