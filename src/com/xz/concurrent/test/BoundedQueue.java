package com.xz.concurrent.test;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xuanzhou
 * @date 2019/11/18 16:01
 */
public class BoundedQueue<T> {

    private int count, addIndex, removeIndex;

    private Object[] item;

    private Lock lock = new ReentrantLock();

    private Condition noEmpty = lock.newCondition();

    private Condition noFull = lock.newCondition();

    public BoundedQueue(int size) {
        item = new Object[ size ];
    }

    public void add(T t) throws InterruptedException {
        lock.lock();
        try {
            while (count == item.length) {
                noFull.wait();
            }

            item[ addIndex ] = t;

            if (++addIndex == item.length) {
                addIndex = 0;
            }

            ++count;
            noEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public T remove() throws InterruptedException {
        lock.lock();

        try {
            while (count == 0) {
                noEmpty.wait();
            }
            Object x = item[ removeIndex ];

            if (++removeIndex == item.length) {
                removeIndex = 0;
            }
            --count;

            noFull.signal();
            return (T) x;
        } finally {
            lock.unlock();
        }
    }
}
