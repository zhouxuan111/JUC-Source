package com.xz.concurrent.atomic;

import java.io.Serializable;

/**
 * 对AtomicDouble的增强，在高并发情况下  DoubleAdder的性能高于AtomicDouble 在低并发情况下 两者的性能相似 但会消耗更多的内存量
 * 累加器：用于状态采集 统计
 * @author xuanzhou
 * @date 2019/10/16 11:54
 */
public class DoubleAdder extends Striped64 implements Serializable {

    /**
     * Creates a new adder with initial sum of zero.
     */
    public DoubleAdder() {
    }

    /**
     * Adds the given value.
     * @param x the value to add
     */
    public void add(double x) {
        Striped64.Cell[] as;
        long b, v;
        int m;
        Striped64.Cell a;
        if ((as = cells) != null || !casBase(b = base, Double.doubleToRawLongBits(Double.longBitsToDouble(b) + x))) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 || (a = as[ getProbe() & m ]) == null || !(uncontended = a
                    .cas(v = a.value, Double.doubleToRawLongBits(Double.longBitsToDouble(v) + x)))) {
                doubleAccumulate(x, null, uncontended);
            }
        }
    }

    /**
     * Returns the current sum.  The returned value is <em>NOT</em> an
     * atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the sum is being calculated might not be
     * incorporated.  Also, because floating-point arithmetic is not
     * strictly associative, the returned result need not be identical
     * to the value that would be obtained in a sequential series of
     * updates to a single variable.
     * @return the sum
     */
    public double sum() {
        Striped64.Cell[] as = cells;
        Striped64.Cell a;
        double sum = Double.longBitsToDouble(base);
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    sum += Double.longBitsToDouble(a.value);
                }
            }
        }
        return sum;
    }

    /**
     * Resets variables maintaining the sum to zero.  This method may
     * be a useful alternative to creating a new adder, but is only
     * effective if there are no concurrent updates.  Because this
     * method is intrinsically racy, it should only be used when it is
     * known that no threads are concurrently updating.
     */
    public void reset() {
        Striped64.Cell[] as = cells;
        Striped64.Cell a;
        base = 0L; // relies on fact that double 0 must have same rep as long
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    a.value = 0L;
                }
            }
        }
    }

    /**
     * Equivalent in effect to {@link #sum} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     * @return the sum
     */
    public double sumThenReset() {
        Striped64.Cell[] as = cells;
        Striped64.Cell a;
        double sum = Double.longBitsToDouble(base);
        base = 0L;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    long v = a.value;
                    a.value = 0L;
                    sum += Double.longBitsToDouble(v);
                }
            }
        }
        return sum;
    }

    /**
     * Returns the String representation of the {@link #sum}.
     * @return the String representation of the {@link #sum}
     */
    @Override
    public String toString() {
        return Double.toString(sum());
    }

    /**
     * Equivalent to {@link #sum}.
     * @return the sum
     */
    @Override
    public double doubleValue() {
        return sum();
    }

    /**
     * Returns the {@link #sum} as a {@code long} after a
     * narrowing primitive conversion.
     */
    @Override
    public long longValue() {
        return (long) sum();
    }

    /**
     * Returns the {@link #sum} as an {@code int} after a
     * narrowing primitive conversion.
     */
    @Override
    public int intValue() {
        return (int) sum();
    }

    /**
     * Returns the {@link #sum} as a {@code float}
     * after a narrowing primitive conversion.
     */
    @Override
    public float floatValue() {
        return (float) sum();
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
        private final double value;

        SerializationProxy(DoubleAdder a) {
            value = a.sum();
        }

        /**
         * Returns a {@code DoubleAdder} object with initial state
         * held by this proxy.
         * @return a {@code DoubleAdder} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            DoubleAdder a = new DoubleAdder();
            a.base = Double.doubleToRawLongBits(value);
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#DoubleAdder.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     * @return a {@link DoubleAdder.SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new DoubleAdder.SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }
}
