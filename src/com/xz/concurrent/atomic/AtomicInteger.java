package com.xz.concurrent.atomic;

/**
 * @author xuanzhou
 * @date 2019/10/12 16:53
 */

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import sun.misc.Unsafe;

/**
 * 保证int类型变量在多线程的情况下线程安全。
 */
public class AtomicInteger extends Number implements java.io.Serializable {

    private static final long serialVersionUID = 6214790243416807050L;

    /**
     * 获取Unsafe实例 提供比较并替换的作用
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();

    /**
     * Integer值的内存偏移量
     */
    private static final long valueOffset;

    /**
     * 获取value属性的内存偏移量
     */
    static {
        try {
            valueOffset = unsafe.objectFieldOffset(AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * 属性值：用volatile修饰变量，保证其他线程对该线程修改属性值的可见性,但不保证原子性
     */
    private volatile int value;

    /**
     * 给定初始值的构造方法
     * @param initialValue
     */
    public AtomicInteger(int initialValue) {
        value = initialValue;
    }

    /**
     * 无参构造
     */
    public AtomicInteger() {
    }

    /**
     * 获取当前最新的value
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
     * 使用场景：不需要让共享变量的修改立即被其他线程可见的时候，以设置普通变量的形式来修改共享状态
     * 可以减少不必要的内存屏障 以提高内存的执行效率
     * @param newValue
     */
    public final void lazySet(int newValue) {
        // putOrderInt() Unsafe类的方法 有序 延迟设置 不保证值的改变被其他线程立即见到。
        unsafe.putOrderedInt(this, valueOffset, newValue);
    }

    /**
     * 把新值设置成当前值 返回旧值 先get旧值 在set新值
     * @param newValue
     * @return
     */
    public final int getAndSet(int newValue) {
        return unsafe.getAndSetInt(this, valueOffset, newValue);
    }

    /**
     * CAS操作：将当前值与期望值比较，相同才更新到新值，更新成功返回true，否则返回false
     * @param expect
     * @param update
     * @return
     */
    public final boolean compareAndSet(int expect, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }

    /**
     * 写操作不能被其他线程立即可见 - 还没有真正实现 - 想法上
     * @param expect
     * @param update
     * @return
     */
    public final boolean weakCompareAndSet(int expect, int update) {
        return unsafe.compareAndSwapInt(this, valueOffset, expect, update);
    }

    /**
     * 自增 返回旧值
     * @return
     */
    public final int getAndIncrement() {
        return unsafe.getAndAddInt(this, valueOffset, 1);
    }

    /**
     * 自减  返回旧值
     * @return
     */
    public final int getAndDecrement() {
        return unsafe.getAndAddInt(this, valueOffset, -1);
    }

    /**
     * 在原值基础上把原值增加指定增量
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
     * 在原值基础上增加指定增量 返回新值
     * @param delta
     * @return
     */
    public final int addAndGet(int delta) {
        return unsafe.getAndAddInt(this, valueOffset, delta) + delta;
    }

    /**
     * IntUnaryOperator:用于处理自变量为int类型的一元函数 自变量必须为int类型
     * package java.util.function;
     * import java.util.Objects;
     * * @see UnaryOperator
     * * @since 1.8
     * @FunctionalInterface ： 函数式接口 指定义了唯一的抽象方法的接口
     * public interface IntUnaryOperator {
     * int applyAsInt(int operand);
     * default IntUnaryOperator compose(IntUnaryOperator before) {
     * Objects.requireNonNull(before);
     * return (int v) -> applyAsInt(before.applyAsInt(v));
     * }
     * default IntUnaryOperator andThen(IntUnaryOperator after) {
     * Objects.requireNonNull(after);
     * return (int t) -> after.applyAsInt(applyAsInt(t));
     * }
     * static IntUnaryOperator identity() {
     * return t -> t;
     * }
     */

    /**
     * 更新值 返回旧值
     * @param updateFunction
     * @return
     */
    public final int getAndUpdate(IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            //获取当前值
            prev = get();
            //一个操作数的函数
            next = updateFunction.applyAsInt(prev);
        }
        //使用自旋的方式（CAS） 设置新值 直到返回true才结束
        while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * 更新值 返回新值
     * @param updateFunction
     * @return
     */
    public final int updateAndGet(IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            //获取当前值
            prev = get();
            //回去更新值
            next = updateFunction.applyAsInt(prev);
        }
        while (!compareAndSet(prev, next));
        return next;
    }

    /**
     * 将给定函数用于当前值和给定值的结果以原始方式更新当前值， 返回旧值
     * @since 1.8
     */
    public final int getAndAccumulate(int x, IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get();
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSet(prev, next));
        return prev;
    }

    /**
     * 将给定函数用于当前值和给定值的结果以原始方式更新当前值， 返回新值(累加器)
     * @since 1.8
     */
    public final int accumulateAndGet(int x, IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get();
            //操作数的函数 -- 进行累加
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSet(prev, next));
        return next;
    }

    /*重写Number类的方法*/

    @Override
    public String toString() {
        return Integer.toString(get());
    }

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
