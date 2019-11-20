package com.xz.concurrent.atomic;

/**
 * @author xuanzhou
 * @date 2019/10/14 18:21
 * 在原子变量类中 对于引用类型的操作  解决CAS的ABA问题
 * AtomicStampedReference:带int类型的引用型原子量  带版本号的原子引用
 */
public class AtomicStampedReference<V> {

    private static class Pair<T> {

        //数据的引用地址
        final T reference;

        //int类型的版本号
        final int stamp;

        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }

        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    /**
     * 线程间共享变量
     */
    private volatile Pair<V> pair;

    /**
     * 有参构造
     */
    public AtomicStampedReference(V initialRef, int initialStamp) {
        pair = Pair.of(initialRef, initialStamp);
    }

    /**
     * 原子的获取当前引用
     */
    public V getReference() {
        return pair.reference;
    }

    /**
     * 原子的获取时间戳
     */
    public int getStamp() {
        return pair.stamp;
    }

    /**
     * 以原子的方式获取引用和时间戳版本号
     */
    public V get(int[] stampHolder) {
        Pair<V> pair = this.pair;
        stampHolder[ 0 ] = pair.stamp;
        return pair.reference;
    }

    /**
     * 比较并交换
     * 期望引用 ！= 当前引用 返回false
     * * 期望标记 ！= 当前标记 返回false
     * * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 = 当前值时 不更新 返回true
     * * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 ！= 当前值时 设置新的引用和新的标记 返回true
     * @param expectedReference 期望引用
     * @param newReference 新引用
     * @param expectedStamp 期望版本号
     * @param newStamp 新 版本号
     * @return {@code true} if successful
     */
    public boolean weakCompareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        return compareAndSet(expectedReference, newReference, expectedStamp, newStamp);
    }

    /**
     * 比较并交换
     * 期望引用 ！= 当前引用 返回false
     * * 期望标记 ！= 当前标记 返回false
     * * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 = 当前值时 不更新 返回true
     * * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 ！= 当前值时 设置新的引用和新的标记 返回true
     * @param expectedReference 期望引用
     * @param newReference 新引用
     * @param expectedStamp 期望版本号
     * @param newStamp 新 版本号
     * @return {@code true} if successful
     */
    public boolean compareAndSet(V expectedReference, V newReference, int expectedStamp, int newStamp) {
        Pair<V> current = pair;
        return expectedReference == current.reference && expectedStamp == current.stamp && (
                (newReference == current.reference && newStamp == current.stamp) || casPair(current,
                        Pair.of(newReference, newStamp)));
    }

    /**
     * 设置引用和时间戳版本号
     */
    public void set(V newReference, int newStamp) {
        Pair<V> current = pair;
        if (newReference != current.reference || newStamp != current.stamp) {
            //设置新的引用
            pair = Pair.of(newReference, newStamp);
        }
    }

    /**
     * 更改时间戳版本号
     * @param expectedReference the expected value of the reference
     * @param newStamp the new value for the stamp
     * @return {@code true} if successful
     */
    public boolean attemptStamp(V expectedReference, int newStamp) {
        Pair<V> current = pair;
        return expectedReference == current.reference && (newStamp == current.stamp || casPair(current,
                Pair.of(expectedReference, newStamp)));
    }

    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();

    private static final long pairOffset = objectFieldOffset(UNSAFE, "pair", AtomicStampedReference.class);

    private boolean casPair(Pair<V> cmp, Pair<V> val) {
        return UNSAFE.compareAndSwapObject(this, pairOffset, cmp, val);
    }

    static long objectFieldOffset(sun.misc.Unsafe UNSAFE, String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            // Convert Exception to corresponding Error
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }
}
