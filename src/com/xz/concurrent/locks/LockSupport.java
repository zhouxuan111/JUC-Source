package com.xz.concurrent.locks;

/**
 * 类简介：用来阻塞当前线程和唤醒指定被阻塞的线程 park() unpark(Thread thread)实现的
 * 实现：调用unpark(thread) , 会将线程的许可permit设置为1,
 * 调用park()方法时，若将当前线程的permit是1.将permit的值设置为0，并立即返回，若当前线程的permit
 * 是0，那么当前线程就会被阻塞，知道别的线程价格当前线程的permit值设置为1，然后park方法再将它设置为0，并返回。
 * park()：若permit为1 ，则permit减为0，
 */
public class LockSupport {

    /**
     * CAS操作
     */
    private static final sun.misc.Unsafe UNSAFE;

    private static final long parkBlockerOffset;

    private static final long SEED;

    /**
     * 线程的hashcode
     */
    private static final long PROBE;

    private static final long SECONDARY;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> tk = Thread.class;
            parkBlockerOffset = UNSAFE.objectFieldOffset(tk.getDeclaredField("parkBlocker"));
            SEED = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomSeed"));
            PROBE = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));
            SECONDARY = UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomSecondarySeed"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    //构造方法私有化
    private LockSupport() {
    }

    /**
     * 设置线程的parkBlocker属性 此对象在线程受阻塞时被记录，允许监视 工具和诊断工具确定线程受阻塞的原因
     * 替换Thread中parkBlocker属性的值为arg
     * @param t
     * @param arg
     */
    private static void setBlocker(Thread t, Object arg) {
        UNSAFE.putObject(t, parkBlockerOffset, arg);
    }

    /**
     * 释放当前阻塞的线程 若当前线程没有阻塞 则下一次park不会阻塞
     * unpark()  直接设置count = 1 若之前count = 0 还要调用唤醒在park()中等待的线程
     */
    public static void unpark(Thread thread) {
        if (thread != null) {
            //线程不为空是 通过Unsafe的unpark()唤醒被阻塞的线程
            UNSAFE.unpark(thread);
        }
    }

    /**
     * 阻塞当前线程 在下列方法之前都会被阻塞
     * 1、调用 unpark 方法 释放该线程的许可
     * 2、线程被中断
     * 3、时间过期 并且time为绝对时间，isAbsolute为true 否则 isAbsolute为false。当time = 0 表示无线等地 知道unpark(Thread thread)发生
     * 4、spuriously
     * 该操作放在 Unsafe 类里没有其它意义，它可以放在其它的任何地方
     * park()执行过程：先尝试能否直接拿到许可(count>0) 若成功 则把count设置为0 并返回  若不成功 则构造一个ThreadBlocklnVM 检查count是否>0 若是 则把count设置为0
     */
    public static void park(Object blocker) {
        Thread t = Thread.currentThread();
        //设置线程t的parkBlocker属性为blocker 用于记录线程阻塞情况
        setBlocker(t, blocker);
        //Unsafe的park方法阻塞线程，isAbsolute = false time = 0表示无限等待,知道调用unpark(Thread thread)唤醒
        //此时已经堵塞 等待unpark()函数调用 继续运行 运行之后会将blocker属性重新设置为null
        UNSAFE.park(false, 0L);
        //返回后将值设置为null
        setBlocker(t, null);
    }

    /**
     * 阻塞当前线程nanos纳秒时间，超出时间线程就会被唤醒
     */
    public static void parkNanos(Object blocker, long nanos) {
        if (nanos > 0) {
            Thread t = Thread.currentThread();
            setBlocker(t, blocker);
            UNSAFE.park(false, nanos);
            setBlocker(t, null);
        }
    }

    /**
     * 阻塞当前线程 超过deadline日期线程就会被唤醒返回
     */
    public static void parkUntil(Object blocker, long deadline) {
        Thread t = Thread.currentThread();
        setBlocker(t, blocker);
        UNSAFE.park(true, deadline);
        setBlocker(t, null);
    }

    /**
     * 获取线程t的parkBlocker属性
     */
    public static Object getBlocker(Thread t) {
        if (t == null) {
            throw new NullPointerException();
        }
        return UNSAFE.getObjectVolatile(t, parkBlockerOffset);
    }

    /**
     * 阻塞当前线程 不设置parkBlocker属性
     * 获取许可 设置时间无限长 知道可以获取许可
     * 调用后 会禁用当前线程 除非许可可用 当前线程都处于休眠状态
     * 以下情况：线程可以继续运行
     * 其他某个线程将当前线程作为目标调用unpark(),
     * 某个线程中断当前线程
     * 该调用不合逻辑的返回
     */
    public static void park() {
        UNSAFE.park(false, 0L);
    }

    /**
     *
     */
    public static void parkNanos(long nanos) {
        if (nanos > 0) {
            UNSAFE.park(false, nanos);
        }
    }

    /**
     * 在指定的时限前禁用当前线程 除非许可可用
     */
    public static void parkUntil(long deadline) {
        UNSAFE.park(true, deadline);
    }

    /**
     * 根据当前线程中属性为threadLocalRandomSecondarySeed的变量生成的随机数
     */
    static final int nextSecondarySeed() {
        int r;
        Thread t = Thread.currentThread();
        if ((r = UNSAFE.getInt(t, SECONDARY)) != 0) {
            r ^= r << 13;   // xorshift
            r ^= r >>> 17;
            r ^= r << 5;
        }
        else if ((r = java.util.concurrent.ThreadLocalRandom.current().nextInt()) == 0) {
            r = 1; // avoid zero
        }
        UNSAFE.putInt(t, SECONDARY, r);
        return r;
    }
}

