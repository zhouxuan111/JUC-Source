package com.xz.concurrent.atomic;

/**
 * @author xuanzhou
 * @date 2019/10/14 12:00
 * 使用场景:对一系列的整数进行同步更新,可以使用AtomicIntegerArray;
 * 用原子数组更新int数组的元素
 */

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import sun.misc.Unsafe;

public class AtomicIntegerArray implements java.io.Serializable {

    private static final long serialVersionUID = 2862133569453604235L;

    //获取Unsafe实例 提供CAS操作
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * 获取int类型数组第一个元素的内存偏移量
     */
    private static final int base = unsafe.arrayBaseOffset(int[].class);

    /**
     * 每个元素的地址偏移量
     */
    private static final int shift;

    private final int[] array;

    static {
        //获取int数组中元素的字节数
        int scale = unsafe.arrayIndexScale(int[].class);
        if ((scale & (scale - 1)) != 0) {
            throw new Error("data type scale not a power of two");
        }
        //返回位移量 Integer.numberOfLeadingZeros(int scale):返回无符号整型i的最高非零位前面的0的个数
        // int类型的前置0的个数为29
        shift = 31 - Integer.numberOfLeadingZeros(scale);
    }

    /**
     * 获取第i个元素的偏移量 但是会先检查元素i的下标是否合理
     * @param i
     * @return
     */
    private long checkedByteOffset(int i) {
        if (i < 0 || i >= array.length) {
            throw new IndexOutOfBoundsException("index " + i);
        }

        return byteOffset(i);
    }

    /**
     * 计算第i个元素的偏移量
     */
    private static long byteOffset(int i) {
        return ((long) i << shift) + base;
    }

    /**
     * 构造函数
     * @param length
     */
    public AtomicIntegerArray(int length) {
        array = new int[ length ];
    }

    public AtomicIntegerArray(int[] array) {
        // Visibility guaranteed by final field guarantees
        this.array = array.clone();
    }

    /**
     * 获取数组长度
     */
    public final int length() {
        return array.length;
    }

    /**
     * 获取数组第i个元素的值
     */
    public final int get(int i) {
        return getRaw(checkedByteOffset(i));
    }

    /**
     * 根据偏移量获取内存中的值
     * @param offset
     * @return
     */
    private int getRaw(long offset) {
        return unsafe.getIntVolatile(array, offset);
    }

    /**
     * 将数组中的第i个元素设置为新值
     * @param i
     * @param newValue
     */
    public final void set(int i, int newValue) {
        unsafe.putIntVolatile(array, checkedByteOffset(i), newValue);
    }

    /**
     * 设置新值：不保证修改元素对其他线程的立即可见性
     */
    public final void lazySet(int i, int newValue) {
        unsafe.putOrderedInt(array, checkedByteOffset(i), newValue);
    }

    /**
     * 获取元素并修改元素为新值
     * 返回旧值
     */
    public final int getAndSet(int i, int newValue) {
        return unsafe.getAndSetInt(array, checkedByteOffset(i), newValue);
    }

    /**
     * 比较并修改元素 修改成功 ：true 否则返回false
     */
    public final boolean compareAndSet(int i, int expect, int update) {
        return compareAndSetRaw(checkedByteOffset(i), expect, update);
    }

    /**
     * 比较并修改元素  修改成功 ：true 否则返回false
     * @param offset
     * @param expect
     * @param update
     * @return
     */
    private boolean compareAndSetRaw(long offset, int expect, int update) {
        return unsafe.compareAndSwapInt(array, offset, expect, update);
    }

    /**
     * 同上
     */
    public final boolean weakCompareAndSet(int i, int expect, int update) {
        return compareAndSet(i, expect, update);
    }

    /**
     * 下表为i的元素自增，并返回旧值
     */
    public final int getAndIncrement(int i) {
        return getAndAdd(i, 1);
    }

    /**
     * 下标为i的元素自减 并返回新值
     */
    public final int getAndDecrement(int i) {
        return getAndAdd(i, -1);
    }

    /**
     * 下标为i的元素增加指定增量 返回旧值
     */
    public final int getAndAdd(int i, int delta) {
        return unsafe.getAndAddInt(array, checkedByteOffset(i), delta);
    }

    /**
     * 下标为i的元素自增 不返回新值
     */
    public final int incrementAndGet(int i) {
        return getAndAdd(i, 1) + 1;
    }

    /**
     * 下标为i的元素自减，并返回新值
     */
    public final int decrementAndGet(int i) {
        return getAndAdd(i, -1) - 1;
    }

    /**
     * 下标为i的元素增加指定增量  并返回新值
     */
    public final int addAndGet(int i, int delta) {
        return getAndAdd(i, delta) + delta;
    }

    /**
     * 更新下标为i的元素 并返回旧值
     * @param i the index
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final int getAndUpdate(int i, IntUnaryOperator updateFunction) {
        long offset = checkedByteOffset(i);
        int prev, next;
        do {
            prev = getRaw(offset);
            next = updateFunction.applyAsInt(prev);
        }
        while (!compareAndSetRaw(offset, prev, next));
        return prev;
    }

    /**
     * IntUnaryOperator:函数式接口 接受一个T类型的参数，输出一个有一样的参数
     */

    /**
     * 更新下标i的值 并且返回新值
     * @param i the index
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final int updateAndGet(int i, IntUnaryOperator updateFunction) {
        long offset = checkedByteOffset(i);
        int prev, next;
        do {
            prev = getRaw(offset);
            next = updateFunction.applyAsInt(prev);
        }
        while (!compareAndSetRaw(offset, prev, next));
        return next;
    }

    /**
     * IntBinaryOperator:函数式接口 ，对两个相同类型的数据进行操作，并返回结果-- 累加器
     */
    /**
     * 更新下标i的值 并返回旧值
     * @param i the index
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final int getAndAccumulate(int i, int x, IntBinaryOperator accumulatorFunction) {
        long offset = checkedByteOffset(i);
        int prev, next;
        do {
            prev = getRaw(offset);
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSetRaw(offset, prev, next));
        return prev;
    }

    /**
     * 更新下标i的值 并返回新值
     * @param i the index
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final int accumulateAndGet(int i, int x, IntBinaryOperator accumulatorFunction) {
        long offset = checkedByteOffset(i);
        int prev, next;
        do {
            prev = getRaw(offset);
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSetRaw(offset, prev, next));
        return next;
    }

    /**
     * Returns the String representation of the current values of array.
     * @return the String representation of the current values of array
     */
    @Override
    public String toString() {
        int iMax = array.length - 1;
        if (iMax == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int i = 0; ; i++) {
            b.append(getRaw(byteOffset(i)));
            if (i == iMax) {
                return b.append(']').toString();
            }
            b.append(',').append(' ');
        }
    }
}

