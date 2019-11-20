package com.xz.concurrent.test;

import java.util.concurrent.locks.Lock;

import org.junit.Test;

/**
 * @author xuanzhou
 * @date 2019/10/30 18:47
 */
public class TwiceLockTest {

    @Test
    public void test() {
        final Lock lock = new TwiceLock();

        class Worker extends Thread {

            @Override
            public void run() {
                while (true) {
                    lock.lock();
                    try {
                        Thread.sleep(1);
                        System.out.println(Thread.currentThread().getName());
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

        //启动10个线程
        for (int i = 0; i < 10; i++) {
            Worker worker = new Worker();
            worker.setDaemon(true);
            worker.start();
        }

        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println();
        }
    }
}
