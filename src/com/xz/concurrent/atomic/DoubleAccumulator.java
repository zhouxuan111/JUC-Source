package com.xz.concurrent.atomic;

/**
 * DoubleAccumulator 是对 AtomicDouble类的改进 在高并发情况下，DoubleAccumulator的性能比AtomicDouble的性能更高 并发比较低的时候 两者的性能相似
 * 实现思想：
 * @author xuanzhou
 * @date 2019/10/16 11:10
 */

import java.io.Serializable;
import java.util.function.DoubleBinaryOperator;

/**
 * @author Doug Lea
 * @since 1.8
 */
public class DoubleAccumulator extends Striped64 implements Serializable {

    private static final long serialVersionUID = 7249069246863182397L;

    private final DoubleBinaryOperator function;

    private final long identity;

    /**
     * Creates a new instance using the given accumulator function
     * and identity element.
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @param identity identity (initial value) for the accumulator function
     */
    public DoubleAccumulator(DoubleBinaryOperator accumulatorFunction, double identity) {
        function = accumulatorFunction;
        base = this.identity = Double.doubleToRawLongBits(identity);
    }

    /**
     * Updates with the given value.
     * @param x the value
     */
    public void accumulate(double x) {
        Cell[] as;
        long b, v, r;
        int m;
        Cell a;
        if ((as = cells) != null
                || (r = Double.doubleToRawLongBits(function.applyAsDouble(Double.longBitsToDouble(b = base), x))) != b
                && !casBase(b, r)) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 || (a = as[ getProbe() & m ]) == null || !(uncontended =
                    (r = Double.doubleToRawLongBits(function.applyAsDouble(Double.longBitsToDouble(v = a.value), x)))
                            == v || a.cas(v, r))) {
                doubleAccumulate(x, function, uncontended);
            }
        }
    }

    /**
     * Returns the current value.  The returned value is <em>NOT</em>
     * an atomic snapshot; invocation in the absence of concurrent
     * updates returns an accurate result, but concurrent updates that
     * occur while the value is being calculated might not be
     * incorporated.
     * @return the current value
     */
    public double get() {
        Cell[] as = cells;
        Cell a;
        double result = Double.longBitsToDouble(base);
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    result = function.applyAsDouble(result, Double.longBitsToDouble(a.value));
                }
            }
        }
        return result;
    }

    /**
     * Resets variables maintaining updates to the identity value.
     * This method may be a useful alternative to creating a new
     * updater, but is only effective if there are no concurrent
     * updates.  Because this method is intrinsically racy, it should
     * only be used when it is known that no threads are concurrently
     * updating.
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
     * Equivalent in effect to {@link #get} followed by {@link
     * #reset}. This method may apply for example during quiescent
     * points between multithreaded computations.  If there are
     * updates concurrent with this method, the returned value is
     * <em>not</em> guaranteed to be the final value occurring before
     * the reset.
     * @return the value before reset
     */
    public double getThenReset() {
        Cell[] as = cells;
        Cell a;
        double result = Double.longBitsToDouble(base);
        base = identity;
        if (as != null) {
            for (int i = 0; i < as.length; ++i) {
                if ((a = as[ i ]) != null) {
                    double v = Double.longBitsToDouble(a.value);
                    a.value = identity;
                    result = function.applyAsDouble(result, v);
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
        return Double.toString(get());
    }

    /**
     * Equivalent to {@link #get}.
     * @return the current value
     */
    @Override
    public double doubleValue() {
        return get();
    }

    /**
     * Returns the {@linkplain #get current value} as a {@code long}
     * after a narrowing primitive conversion.
     */
    @Override
    public long longValue() {
        return (long) get();
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
     * after a narrowing primitive conversion.
     */
    @Override
    public float floatValue() {
        return (float) get();
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
        private final double value;

        /**
         * The function used for updates.
         * @serial
         */
        private final DoubleBinaryOperator function;

        /**
         * The identity value
         * @serial
         */
        private final long identity;

        SerializationProxy(DoubleAccumulator a) {
            function = a.function;
            identity = a.identity;
            value = a.get();
        }

        /**
         * Returns a {@code DoubleAccumulator} object with initial state
         * held by this proxy.
         * @return a {@code DoubleAccumulator} object with initial state
         * held by this proxy.
         */
        private Object readResolve() {
            double d = Double.longBitsToDouble(identity);
            DoubleAccumulator a = new DoubleAccumulator(function, d);
            a.base = Double.doubleToRawLongBits(value);
            return a;
        }
    }

    /**
     * Returns a
     * <a href="../../../../serialized-form.html#DoubleAccumulator.SerializationProxy">
     * SerializationProxy</a>
     * representing the state of this instance.
     * @return a {@link DoubleAccumulator.SerializationProxy}
     * representing the state of this instance
     */
    private Object writeReplace() {
        return new DoubleAccumulator.SerializationProxy(this);
    }

    /**
     * @param s the stream
     * @throws java.io.InvalidObjectException always
     */
    private void readObject(java.io.ObjectInputStream s) throws java.io.InvalidObjectException {
        throw new java.io.InvalidObjectException("Proxy required");
    }

}
