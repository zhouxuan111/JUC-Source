package com.xz.concurrent.atomic;

import java.io.Serializable;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import sun.misc.Unsafe;

/**
 * @author xuanzhou
 * @date 2019/10/14 11:18
 */
public class MyAtomicInteger extends Number implements Serializable {

    /**
     * 获取Unsafe实例。提供CAS
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * 属性偏移量
     */
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset(AtomicInteger.class.getDeclaredField("value"));
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }

    private volatile int value;

    /**
     * 构造方法
     * @param value
     */
    public MyAtomicInteger(int value) {
        this.value = value;
    }

    public MyAtomicInteger() {
    }

    /**
     * 获取当前值
     * @return
     */
    public final int get() {
        return value;
    }

    /**
     * 设置新值
     * @param newValue
     */
    public final void set(int newValue) {
        value = newValue;
    }

    /**
     * 设置新值
     * 使用场景：不需要让共享变量的修改立即被其他线程可见的时候，以设置普通变量的形式来修改普通变量
     * 可以减少不必要的内存屏障 提高内存的执行效率
     * @param newValue
     */
    public final void lazySet(int newValue) {
        unsafe.putOrderedInt(this, valueOffset, newValue);
    }

    /**
     * 将当前值设置成新值，返回旧值
     * @param newValue
     * @return
     */
    public final int getAndSet(int newValue) {
        return unsafe.getAndSetInt(this, valueOffset, newValue);
    }

    /**
     * 将当前值与期望值进行比较 相同时设置新值，更新成功返回true，否则返回false
     * @param expect
     * @param update
     * @return
     */
    public final boolean compareAndSet(int expect, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }

    /**
     * 还未真正实现
     * @param except
     * @param update
     * @return
     */
    public final boolean weakCompareAndSet(int except, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, except, update);
    }

    /**
     * 自增 返回旧值
     * @return
     */
    public final int getAndIncrement() {
        return unsafe.getAndAddInt(this, valueOffset, 1);
    }

    /**
     * 自减 返回新值
     * @return
     */
    public final int getAndDecrement() {
        return unsafe.getAndAddInt(this, valueOffset, -1);
    }

    /**
     * 在原址基础上增加指定增量
     * @param delta
     * @return
     */
    public final int getAndAdd(int delta) {
        return unsafe.getAndAddInt(this, valueOffset, delta);
    }

    /**
     * 自增 返回新值
     * @return
     */
    public final int incrementAndGet() {
        return unsafe.getAndAddInt(this, valueOffset, 1) + 1;
    }

    /**
     * 自减 返回新值
     * @return
     */
    public final int decrementAndGet() {
        return unsafe.getAndAddInt(this, valueOffset, -1) - 1;
    }

    /**
     * 在原值基础上增加指定增量  返回新值
     * @param delta
     * @return
     */
    public final int addAndGet(int delta) {
        return unsafe.getAndAddInt(this, valueOffset, delta) + delta;
    }

    /*JDK 1.8 新增 关于lambda表达式*/

    /**
     * 更新值 返回旧值
     * @param unaryOperator
     * @return
     */
    public final int getAndUpdate(IntUnaryOperator unaryOperator) {
        int prev, next;
        do {
            prev = get();
            next = unaryOperator.applyAsInt(prev);
        }
        while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * 更新值 返回新值
     * @param intUnaryOperator
     * @return
     */
    public final int updateAndGet(IntUnaryOperator intUnaryOperator) {
        int prev, next;
        do {
            prev = get();
            next = intUnaryOperator.applyAsInt(prev);
        }
        while (!compareAndSet(prev, next));

        return next;
    }

    /**
     * 将给定值和当前值的结果作为新值进行更新 返回旧值
     * @param x
     * @param intBinaryOperator
     * @return
     */
    public final int getAndAccummulate(int x, IntBinaryOperator intBinaryOperator) {
        int prev, next;
        do {
            prev = get();
            next = intBinaryOperator.applyAsInt(prev, x);
        }
        while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * 将给定值和当前值作为新值进行更新，返回新值
     * @param x
     * @param intBinaryOperator
     * @return
     */
    public final int accumulateAndGet(int x, IntBinaryOperator intBinaryOperator) {
        int prev, next;
        do {
            prev = get();
            next = intBinaryOperator.applyAsInt(prev, x);
        }
        while (!compareAndSet(prev, next));
        return next;
    }


    /*重写Number方法*/

    @Override
    public int intValue() {
        return get();
    }

    @Override
    public long longValue() {
        return (long) get();
    }

    @Override
    public float floatValue() {
        return (float) get();
    }

    @Override
    public double doubleValue() {
        return (double) get();
    }
}
