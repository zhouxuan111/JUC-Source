package com.xz.concurrent.atomic;

import java.io.Serializable;

/**
 * 码分析源
 * @author xuanzhou
 * @date 2019/10/25 15:31
 */
public class LongAdder extends Striped64 implements Serializable {

    /**
     * JDK 1.8提供
     * 实现方式:锁分段的实现 维护一组按需分配的计数单元 并发计数时，不同的线程可以在不同的计数单元上进行计数，这样少了线程竞争
     * 提高并发效率 以空间换时间
     */
    private static final long serialVersionUID = 7249069246863182397L;

    /**
     * 无参构造
     */
    public LongAdder() {
    }

    /**
     * 增加指定的值
     * @param x the value to add
     */
    public void add(long x) {
        Cell[] as;
        long b, v;
        //数组的长度
        int m;
        Cell a;
        //不是在base上进行更新就是找到对应的Cell+x进行更新
        //cells != null 表明已经开始分段计算了
        if ((as = cells) != null || !casBase(b = base, b + x)) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 || (a = as[ getProbe() & m ]) == null || !(uncontended = a
                    .cas(v = a.value, v + x))) {
                longAccumulate(x, null, uncontended);
            }
        }
    }

    /**
     * 自增
     */
    public void increment() {
        add(1L);
    }

    /**
     * 自减
     */
    public void decrement() {
        add(-1L);
    }

    /**
     * 将多个Cell[]数组中的值加起来
     * @return the sum
     */
    public long sum() {
        Cell[] as = cells;
        Cell a;
        long sum = base;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    sum += a.value;
                }
            }
        }
        return sum;
    }

    /**
     * 重置
     */
    public void reset() {
        Cell[] as = cells;
        Cell a;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    a.value = 0L;
                }
            }
        }
    }

    /**
     * 求和然后重置
     * @return 返回和
     */
    public long sumThenReset() {
        Cell[] as = cells;
        Cell a;
        long sum = base;
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    sum += a.value;
                    a.value = 0L;
                }
            }
        }
        return sum;
    }

    /**
     * @return the String representation of the {@link #sum}
     */
    @Override
    public String toString() {
        return Long.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     * @return the sum
     */
    @Override
    public long longValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a narrowing
     * primitive conversion.
     */
    @Override
    public int intValue() {
        return (int) sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a widening primitive conversion.
     */
    @Override
    public float floatValue() {
        return (float) sum();
    }

    /**
     * Returns the {@link #sum} as a {@code double} after a widening
     * primitive conversion.
     */
    @Override
    public double doubleValue() {
        return (double) sum();
    }

    /**
     * Serialization proxy, used to avoid reference to the non-public
     * Striped64 superclass in serialized forms.
     * @serial include
     */
    private static class SerializationProxy implements Serializable {

        private static final long serialVersionUID = 7249069246863182397L;

        /**
         * The current value returned by sum().
         * @serial
         */
        private final long value;

        SerializationProxy(LongAdder a) {
            value = a.sum();
        }

        /**
         * Return a {@code LongAdder} object with initial state
         * held by this proxy.
         * @return a {@code LongAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            LongAdder a = new LongAdder();
            a.base = value;
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#java.util.concurrent.atomic.LongAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     * @return a {@link java.util.concurrent.atomic.LongAdder.SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}

