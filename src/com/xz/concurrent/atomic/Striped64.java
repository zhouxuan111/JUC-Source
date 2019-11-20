package com.xz.concurrent.atomic;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * 是Long Double类型等累加器的基础类
 * 设计核心：通过内部的分散设计来避免竞争（多线程CAS操作的时候的竞争）
 * Strip:拆分 条纹化的意思 -- 包内使用
 * @author xuanzhou
 * @date 2019/10/16 11:12
 */
abstract class Striped64 extends Number {

    /**
     * @Contended: 对Cell的缓存行填充 避免伪共享
     * 每个Cell对象都是原子更新 每个Cell对象都有一个long类型的value属性
     */
    @sun.misc.Contended
    static final class Cell {

        //原子操作-计数变量 - 为该变量提供CAS操作
        volatile long value;

        //构造方法
        Cell(long x) {
            value = x;
        }

        //比较并交换
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        private static final sun.misc.Unsafe UNSAFE;

        //value属性的内存偏移量
        private static final long valueOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Striped64.Cell.class;
                valueOffset = UNSAFE.objectFieldOffset(ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * 获取可用CPU的数量
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Cell数组 长度为2^n  当第一次对CAS操作失败的时候初始化为2  知道大于CPU的数量
     */
    transient volatile Striped64.Cell[] cells;

    /**
     * 累积器的基本值 使用情况
     * 1.没有并发情况 直接使用base，速度快
     * 2.多线程并发初始化数组 必须保证table数组只被初始化一次 因此只有一个线程能够竞争成功 此时竞争失败的线程会尝试在base上只进行一次累积操作
     * 2.多线程并发初始化数组 必须保证table数组只被初始化一次 因此只有一个线程能够竞争成功 此时竞争失败的线程会尝试在base上只进行一次累积操作
     */
    transient volatile long base;

    /**
     * 自旋锁 在对cells进行初始化或者扩容时 需要通过CAS操作将此标识设置为1(busy,加锁)，
     * 取消busy 直接使用cellsBusy = 0,相当于释放锁
     */
    transient volatile int cellsBusy;

    /**
     * 无参构造
     */
    Striped64() {
    }

    private static final sun.misc.Unsafe UNSAFE;

    /**
     * 基础值
     */
    private static final long BASE;

    /**
     * /通过CAS实现锁，0：无锁 1：获取锁
     */
    private static final long CELLSBUSY;

    /**
     * 相当于线程的hash值  索引就是每个线程的HASH值
     */
    private static final long PROBE;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            //回去base属性的内存偏移量
            BASE = UNSAFE.objectFieldOffset(sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset(sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    /**
     * CAS更细Base值
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * 使用CAS将cells自旋表示更新为1
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * 根据偏移量 获取线程中的PROBE值 理解为线程本身的hash值
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * 重新算一遍线程的hash值
     * 利用伪随机算法加强标识后 将为当前线程记录这个标识
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * 此方法建议在外部进行一次CAS操作 cell == null 时，尝试CAS更新Base值，cells != null时，CAS更新hash值取模后对应的cell.value值
     * @param x 外部提供的操作数
     * @param fn 外部提供的二元算数操作 实例持有 并且只有一个好声明周期保持不变
     * @param wasUncontended 为false 表明调用者预先调用的CAS操作都失败了
     */
    final void longAccumulate(long x, LongBinaryOperator fn, boolean wasUncontended) {
        int h;
        //线程hash==0时，生成一个新的线程hash
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current();
            h = getProbe();
            wasUncontended = true;
        }
        // 若上一个slot不为空 置位为true  碰撞标记
        boolean collide = false;
        for (; ; ) {
            Striped64.Cell[] as;
            Striped64.Cell a;
            //数组长度
            int n;
            long v;
            //Cell[] 数组不为空 进行操作
            if ((as = cells) != null && (n = as.length) > 0) {
                //对应的桶Cell为空 需要初始化
                if ((a = as[ (n - 1) & h ]) == null) {
                    // 判断锁状态 尝试添加新的Cell  cellBusy = 0 无锁
                    if (cellsBusy == 0) {
                        // 创建新的Cell
                        Striped64.Cell r = new Striped64.Cell(x);
                        //双重检查 再次判断锁状态 同时获取锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            //创建Cell
                            boolean created = false;
                            try {
                                Striped64.Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null && (m = rs.length) > 0 && rs[ j = (m - 1) & h ] == null) {
                                    //Cell添加
                                    rs[ j ] = r;
                                    //标识创建
                                    created = true;
                                }
                            } finally {
                                //释放锁
                                cellsBusy = 0;
                            }
                            //创建成功跳出 否则重试
                            if (created) {
                                break;
                            }
                            continue;
                        }
                    }
                    //锁占用了 扩容或者hash进行下一轮循环
                    collide = false;
                }
                //到这位置上 说明Cell的对应的位置上已经有相应的Cell 不需要初始化 CAS操作失败了 出现竞争
                else if (!wasUncontended) {
                    // Continue after rehash
                    wasUncontended = true;
                }
                //尝试修改a上的计数 a为Cell数组中index位置上的Cell
                else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x)))) {
                    break;
                }
                //Cell数组最大为CPU的数量
                //cells != as表明cells数组已经被更新 标记为最大状态或者说是过期状态
                else if (n >= NCPU || cells != as) {
                    // At max size or stale
                    collide = false;
                }
                else if (!collide) {
                    collide = true;
                }
                //尝试获取锁之后扩大Cells
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        //扩大数组 每次扩容为原来的两倍
                        if (cells == as) {
                            Striped64.Cell[] rs = new Striped64.Cell[ n << 1 ];
                            for (int i = 0; i < n; ++i) {
                                rs[ i ] = as[ i ];
                            }
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;
                }
                h = advanceProbe(h);
            }
            //此分支表明Cell数组是空的 所以要获取锁 进行初始化Cells 并将锁设置为1 表明该锁被占用
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {
                    // Initialize table
                    if (cells == as) {
                        Striped64.Cell[] rs = new Striped64.Cell[ 2 ];
                        rs[ h & 1 ] = new Striped64.Cell(x);
                        cells = rs;
                        init = true;
                    }
                }
                //释放锁 已经操作完毕
                finally {
                    cellsBusy = 0;
                }
                if (init) {
                    break;
                }
            }
            //表明Cells为空 并且在初始化的时候获取锁失败 直接在base上进行CAS
            else if (casBase(v = base, ((fn == null) ? v + x : fn.applyAsLong(v, x)))) {
                break;
            }
        }
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn, boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current();
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;
        for (; ; ) {
            Striped64.Cell[] as;
            Striped64.Cell a;
            int n;
            long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[ (n - 1) & h ]) == null) {
                    if (cellsBusy == 0) {
                        Striped64.Cell r = new Striped64.Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {
                                Striped64.Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null && (m = rs.length) > 0 && rs[ j = (m - 1) & h ] == null) {
                                    rs[ j ] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created) {
                                break;
                            }
                            continue;
                        }
                    }
                    collide = false;
                }
                else if (!wasUncontended) {
                    wasUncontended = true;
                }
                else if (a.cas(v = a.value, ((fn == null) ?
                        Double.doubleToRawLongBits(Double.longBitsToDouble(v) + x) :
                        Double.doubleToRawLongBits(fn.applyAsDouble(Double.longBitsToDouble(v), x))))) {
                    break;
                }
                else if (n >= NCPU || cells != as) {
                    collide = false;
                }
                else if (!collide) {
                    collide = true;
                }
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {
                            Striped64.Cell[] rs = new Striped64.Cell[ n << 1 ];
                            for (int i = 0; i < n; ++i) {
                                rs[ i ] = as[ i ];
                            }
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;
                }
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {
                    if (cells == as) {
                        Striped64.Cell[] rs = new Striped64.Cell[ 2 ];
                        rs[ h & 1 ] = new Striped64.Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init) {
                    break;
                }
            }
            else if (casBase(v = base, ((fn == null) ?
                    Double.doubleToRawLongBits(Double.longBitsToDouble(v) + x) :
                    Double.doubleToRawLongBits(fn.applyAsDouble(Double.longBitsToDouble(v), x))))) {
                break;
            }
        }
    }
}

