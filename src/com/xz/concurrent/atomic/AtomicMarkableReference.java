package com.xz.concurrent.atomic;

/**
 * @author xuanzhou
 * @date 2019/10/14 17:59
 * 在原子变量类中 对于引用类型的操作，用于解决CAS的ABA问题
 * AtomicMarkableReference:带boolean类型的引用型原子量 每次执行CAS操作都需要比较该标记位
 * 若版本吗，满足要求 则操作成功 版本不满足要求 则操作失败
 * 表示引用变量是否被更改过
 */
public class AtomicMarkableReference<V> {

    /**
     * 内部维护的[reference,boolean]二元组 AtomicMarkableReference的相关操作是对Pair内成员的操作
     */
    private static class Pair<T> {

        final T reference;

        final boolean mark;

        //构造方法
        private Pair(T reference, boolean mark) {
            this.reference = reference;
            this.mark = mark;
        }

        //创建Pair实例-类加载时
        static <T> AtomicMarkableReference.Pair<T> of(T reference, boolean mark) {
            return new AtomicMarkableReference.Pair<T>(reference, mark);
        }
    }

    /**
     * 多线程共享变量
     */
    private volatile AtomicMarkableReference.Pair<V> pair;

    /**
     * 创建AtomicMarkableReference.Pair实例
     */
    public AtomicMarkableReference(V initialRef, boolean initialMark) {
        pair = AtomicMarkableReference.Pair.of(initialRef, initialMark);
    }

    /**
     * 以原子方式获取当前引用值
     */
    public V getReference() {
        return pair.reference;
    }

    /**
     * 以原子方式获取当前标记
     */
    public boolean isMarked() {
        return pair.mark;
    }

    /**
     * 以原子的方式获取当前的引用值和标记
     */
    public V get(boolean[] markHolder) {
        AtomicMarkableReference.Pair<V> pair = this.pair;
        markHolder[ 0 ] = pair.mark;
        return pair.reference;
    }

    /**
     * 比较并交换新值
     */
    public boolean weakCompareAndSet(V expectedReference, V newReference, boolean expectedMark, boolean newMark) {
        return compareAndSet(expectedReference, newReference, expectedMark, newMark);
    }

    /**
     * CAS  比较并设置新值
     * 期望引用 ！= 当前引用 返回false
     * 期望标记 ！= 当前标记 返回false
     * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 = 当前值时 不更新 返回true
     * 期望标记和期望引用 = 当前值的前提下 新的标记和新的引用 ！= 当前值时 设置新的引用和新的标记 返回true
     * @param expectedReference 期望的引用
     * @param newReference 新引用
     * @param expectedMark 期望的标记
     * @param newMark 新的标记
     * @return 若成功 返回true
     */
    public boolean compareAndSet(V expectedReference, V newReference, boolean expectedMark, boolean newMark) {
        AtomicMarkableReference.Pair<V> current = pair;
        return expectedReference == current.reference && expectedMark == current.mark && (
                (newReference == current.reference && newMark == current.mark) || casPair(current,
                        AtomicMarkableReference.Pair.of(newReference, newMark)));
    }

    /**
     * 设置引用和标记 有一个和当前值不一样就进行更新
     */
    public void set(V newReference, boolean newMark) {
        AtomicMarkableReference.Pair<V> current = pair;
        if (newReference != current.reference || newMark != current.mark) {
            pair = AtomicMarkableReference.Pair.of(newReference, newMark);
        }
    }

    /**
     * 当期望值
     * @param expectedReference 期望引用
     * @param newMark 新的标记
     * @return {@code true} if successful
     */
    public boolean attemptMark(V expectedReference, boolean newMark) {
        AtomicMarkableReference.Pair<V> current = pair;
        return expectedReference == current.reference && (newMark == current.mark || casPair(current,
                AtomicMarkableReference.Pair.of(expectedReference, newMark)));
    }

    // Unsafe mechanics

    /**
     * 获取Unsafe实例
     */
    private static final sun.misc.Unsafe UNSAFE = sun.misc.Unsafe.getUnsafe();

    /**
     * 获取pair偏移量
     */
    private static final long pairOffset = objectFieldOffset(UNSAFE, "pair", AtomicMarkableReference.class);

    /**
     * 原子的交换两个对象(CAS交换)
     * @param cmp
     * @param val
     * @return
     */
    private boolean casPair(AtomicMarkableReference.Pair<V> cmp, AtomicMarkableReference.Pair<V> val) {
        return UNSAFE.compareAndSwapObject(this, pairOffset, cmp, val);
    }

    static long objectFieldOffset(sun.misc.Unsafe UNSAFE, String field, Class<?> klazz) {
        try {
            return UNSAFE.objectFieldOffset(klazz.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            NoSuchFieldError error = new NoSuchFieldError(field);
            error.initCause(e);
            throw error;
        }
    }
}
