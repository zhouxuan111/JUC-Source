package com.xz.concurrent.atomic;

/**
 * 源码分析
 * @author xuanzhou
 * @date 2019/10/25 16:10
 */

import java.io.Serializable;
import java.util.function.LongBinaryOperator;

/**
 * @author Doug Lea
 * @since 1.8
 */
public class LongAccumulator extends Striped64 implements Serializable {

    private static final long serialVersionUID = 7249069246863182397L;

    //函数型接口
    private final LongBinaryOperator function;

    private final long identity;

    /**
     * 构造方法
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @param identity identity (initial value) for the accumulator function
     */
    public LongAccumulator(LongBinaryOperator accumulatorFunction, long identity) {
        function = accumulatorFunction;
        base = this.identity = identity;
    }

    /**
     * 同add方法
     * @param x the value
     */
    public void accumulate(long x) {
        Cell[] as;
        long b, v, r;
        int m;
        Cell a;
        if ((as = cells) != null || (r = function.applyAsLong(b = base, x)) != b && !casBase(b, r)) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 || (a = as[ getProbe() & m ]) == null || !(uncontended =
                    (r = function.applyAsLong(v = a.value, x)) == v || a.cas(v, r))) {
                longAccumulate(x, function, uncontended);
            }
        }
    }

    /**
     * 将内部所有的零散值通过函数算出一个最终值
     * @return the current value
     */
    public long get() {
        Cell[] as = cells;
        Cell a;
        long result = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    result = function.applyAsLong(result, a.value);
                }
            }
        }
        return result;
    }

    /**
     * 初始化：但是这里与LongAdder不同会将base 和 cells的value都重置成初始值 identify
     */
    public void reset() {
        Cell[] as = cells;
        Cell a;
        base = identity;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    a.value = identity;
                }
            }
        }
    }

    /**
     * 重置并返回重置之前的总和
     * @return the value before reset
     */
    public long getThenReset() {
        Cell[] as = cells;
        Cell a;
        long result = base;
        base = identity;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    long v = a.value;
                    a.value = identity;
                    result = function.applyAsLong(result, v);
                }
            }
        }
        return result;
    }

    /**
     * Returns the String representation of the current value.
     * @return the String representation of the current value
     */
    @Override
    public String toString() {
        return Long.toString(get());
    }

    /**
     * Equivalent to {@link #get}.
     * @return the current value
     */
    @Override
    public long longValue() {
        return get();
    }

    /**
     * Returns the {@linkplain #get current value} as an {@code int}
     * after a narrowing primitive conversion.
     */
    @Override
    public int intValue() {
        return (int) get();
    }

    /**
     * Returns the {@linkplain #get current value} as a {@code float}
     * after a widening primitive conversion.
     */
    @Override
    public float floatValue() {
        return (float) get();
    }

    /**
     * Returns the {@linkplain #get current value} as a {@code double}
     * after a widening primitive conversion.
     */
    @Override
    public double doubleValue() {
        return (double) get();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by get().
         * @serial
         */
        private final long value;

        /**
         * The function used for updates.
         * @serial
         */
        private final LongBinaryOperator function;

        /**
         * The identity value
         * @serial
         */
        private final long identity;

        SerializationProxy(LongAccumulator a) {
            function = a.function;
            identity = a.identity;
            value = a.get();
        }

        /**
         * Returns a {@code LongAccumulator} object with initial state
         * held by this proxy.
         * @return a {@code LongAccumulator} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAccumulator a = new LongAccumulator(function, identity);
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * SerializationProxy</a>
     * representing the state of this instance.
     * @return a {@linkLongAccumulator.SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new LongAccumulator.SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}

