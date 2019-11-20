package com.xz.sun.misc;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import sun.misc.VM;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * @author xuanzhou
 * @date 2019/10/12 11:12
 */
public final class CopyUnsafe {

    private static native void registerNatives();

    static {
        //注册本本地方法
        registerNatives();
        //将Unsafe类的getUnsafe()方法添加到Reflection的反射过滤列表中，该类不能通过反射访问
        Reflection.registerMethodsToFilter(CopyUnsafe.class, "getUnsafe");
    }

    /**
     * 构造方法私有化
     */
    private CopyUnsafe() {
    }

    /**
     * 创建实例
     */
    private static final CopyUnsafe copyUnsafe = new CopyUnsafe();

    /**
     * 获取Unsafe实例，会检查是否是BootStrapClassLoader，不是的话抛出SecurityException
     * 该方法不允许直接调用 可以通过反射调用
     * Field field = CopyUnsafe.class.getDeclaredField("unsafe");
     * field.setAccessible(true);
     * CopyUnsafe copyUnsafe = (CopyUnsafe) field.get(null);
     * @return
     */
    @CallerSensitive
    public static CopyUnsafe getUnsafe() {
        Class<?> caller = Reflection.getCallerClass();
        //判断是否是BootstrapClassLoader,不是的话抛出SecurityException
        if (!VM.isSystemDomainLoader(caller.getClassLoader())) {
            throw new SecurityException("Unsafe");
        }
        return copyUnsafe;
    }

    /*内存偏移量的获取*/

    public static final int INVALID_FIELD_OFFSET = -1;

    /**
     * 获取属性在所在类的偏移量
     * @param field
     * @return
     */
    public native long objectFieldOffset(Field field);

    /**
     * 获取静态属性在所在类的偏移量
     * @param field
     * @return
     */
    public native long staticFieldOffset(Field field);

    /**
     * 获取属性在所在类的偏移量（object static）
     * @param field
     * @return
     */
    @Deprecated
    public int fieldOffset(Field field) {
        //判断是否有静态属性
        if (Modifier.isStatic(field.getModifiers())) {
            return (int) staticFieldOffset(field);
        }
        else {
            return (int) objectFieldOffset(field);
        }
    }

    /**
     * 获取静态属性对应对象的基准地址
     * @param field
     * @return
     */
    public native long staticFieldBase(Field field);

    /**
     * 获取访问静态字段的基准地址(获取所有静态字段)
     * @param c
     * @return
     */
    public Object staticFieldBase(Class<?> c) {
        Field[] fields = c.getFields();
        for (int i = 0; i < fields.length; i++) {
            if (Modifier.isStatic(fields[ i ].getModifiers())) {
                return staticFieldBase(fields[ i ]);
            }
        }
        return null;
    }

    /**
     * 获取数组中第一个元素的内存地址偏移量
     * @param c
     * @return
     */
    public native int arrayBaseOffset(Class<?> c);

    /**
     * 获取数组中元素的字节数
     * @param c
     * @return
     */
    public native int arrayIndexScale(Class<?> c);

    /*
     * 通过Java对象和属性偏移量获取值 double-register
     * Object o,long offset
     * 不保证其他线程可见性
     */

    /**
     * 获取值
     * @param o
     * @param offset
     * @return
     */
    public native int getInt(Object o, long offset);

    /**
     * 设置值
     * @param o
     * @param offset
     * @param x
     */
    public native void putInt(Object o, long offset, int x);

    /**
     *  Object boolean byte short char long float double
     */

    /**
     * 过时方法 同上
     */

    /*根据属性绝对地址获取和设置属性的值*/

    /**
     * 设置值
     * @param address
     * @return
     */
    public native byte getByte(long address);

    /**
     * 设置值
     * @param address
     * @param x
     */
    public native void putByte(long address, byte x);

    /**
     * short int long float double boolean
     */

    /*本地指针*/

    /**
     * 获取本地指针
     * @param address
     * @return
     */
    public native long getAdderss(long address);

    /**
     * 设置本地指针的值
     * @param address
     * @param x
     */
    public native void putAddress(long address, long x);

    /**
     * 获取本地指针占用的字节数大小
     * @return
     */
    public native int addressSize();

    public static final int ADDRESS_SIZE = copyUnsafe.addressSize();

    /**
     * 获取本地内存页的大小
     * @return
     */
    public native int pageSize();
    /*和本地内存有关的方法*/

    /**
     * 分配本地内存(指定大小) 返回的不会是0
     * @param bytes
     * @return
     */
    public native long allocateMemory(long bytes);

    /**
     * 重新分配内存
     * @param address address = null 时，与allocateMemory()相同
     * @param bytes 当请求的大小 bytes = 0 方法返回的本地指针为0
     * @return
     */
    public native long reallocateMemory(long address, long bytes);

    /**
     * 将给定内存块的所有字节设置成指定的值(通常为0)
     * @param o Java堆对象 基准地址
     * @param offset ： 属性在Java堆对象中的偏移量 o = null offset为内存绝对地址
     * @param bytes 设置的字节大小
     * @param values ： 设置的值
     */
    public native void setMemory(Object o, long offset, long bytes, byte values);

    /**
     * 将给定的内存绝对地址设置成另一个值
     * @param address
     * @param bytes
     * @param value
     */
    public void setMemeory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

    /**
     * 将给定内存块的值复制到另一个内存块 double-register
     * @param srcBase
     * @param srcOffset
     * @param destOffset
     * @param bytes
     */
    public native void copyMemory(Object srcBase, long srcOffset, Object destObject, long destOffset, long bytes);

    /**
     * 将给定内存块的值赋值到另一个内存块 single-register
     * @param srcMessage
     * @param destAddress
     * @param bytes
     */
    public void copyMemory(long srcMessage, long destAddress, long bytes) {
        copyMemory(null, srcMessage, null, destAddress, bytes);
    }

    /**
     * 释放内存
     * @param address
     */
    public native void freeMemory(long address);

    /*获取类变量相关方法*/

    /**
     * 判断该类是否需要初始化
     * @param c
     * @return
     */
    public native boolean shouldBeInitialized(Class<?> c);

    /**
     * 确保给定的类被初始化
     * @param c
     */
    public native void ensureClassInitialized(Class<?> c);

    /*获取不同类型数组的第一个元素在内存中的位置*/

    public static final int ARRAY_BOOLEAN_BASE_OFFSET = copyUnsafe.arrayBaseOffset(boolean[].class);

    /**
     * byte short int long float double char Object
     */

    /*获取不同类型数组元素的字节数*/

    public static final int ARRAY_BOOLEAN_INDEX_SCALE = copyUnsafe.arrayIndexScale(boolean[].class);

    /**
     * byte short int long float double char Object
     */

    /*JNI 的信任操作*/

    /**
     * 通知虚拟机定义一个类，加载类不做安全检查
     * @param name
     * @param b
     * @param off
     * @param len
     * @param loader
     * @param protectionDomain
     * @return
     */
    public native Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader,
            ProtectionDomain protectionDomain);

    /**
     * 定义一个匿名类，这里说的和我们代码里写的匿名内部类不是一个东西
     */
    public native Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches);

    /**
     * 分配实例的内存空间，但不会执行构造函数。如果没有执行初始化，则会执行初始化
     */
    public native Object allocateInstance(Class<?> cls) throws InstantiationException;

    /**
     * 获取对象内置锁(即 synchronized 关键字获取的锁)，必须通过 monitorExit 方法释放锁
     * (synchronized 代码块在编译后会产生两个指令:monitorenter,monitorexit)
     */
    @Deprecated
    public native void monitorEnter(Object o);

    /**
     * 释放锁
     */
    @Deprecated
    public native void monitorExit(Object o);

    /**
     * 尝试获取对象内置锁，通过返回 true 和 false 表示是否成功获取锁
     */
    @Deprecated
    public native boolean tryMonitorEnter(Object o);

    /**
     * 不通知验证器(verifier)直接抛出异常
     */
    public native void throwException(Throwable ee);

    /*CAS 方法 没有加锁 性能好*/

    /**
     * 通过Object o 和 long offset获取的值= excepted(预期的值) 更新变量的值 原子操作 修改成功返回true
     * @param o
     * @param offset
     * @param excepted
     * @param x
     * @return
     */
    public final native boolean compareAndSwapObject(Object o, long offset, Object excepted, Object x);

    public final native boolean compareAndSwapInt(Object o, long offset, int excepted, int x);

    public final native boolean compareAndSwapLong(Object o, long offset, long excepted, long x);

    /*带有volatile语义的获取和设置指定偏移量的值*/

    /**
     * 获取值
     * @param o
     * @param offset
     * @return
     */
    public native Object getObjectVolatile(Object o, long offset);

    /**
     * 设置值
     * @param o
     * @param offset
     * @param x
     */
    public native void putObjectVolatile(Object o, long offset, Object x);

    public native int getIntVolatile(Object o, long offset);

    public native int getLongVolatile(Object o, long offset);

    /**
     * boolean byte short int long float double char
     */
    /**
     * 有序 ，有延迟，不能保证其他线程能够立即看到修改
     * @param o
     * @param offset
     * @param x
     */
    public native void putOrderedObject(Object o, long offset, Object x);
    /**
     * int long
     */

    /*加锁与释放锁*/

    /**
     * 释放当前阻塞的线程 若当前线程没有阻塞，下一次park不会阻塞
     * @param thread
     */
    public native void unpark(Object thread);

    /**
     * 阻塞当前线程
     * @param isAbsolute
     * @param time
     */
    public native void park(boolean isAbsolute, long time);

    /**
     * 获取一段时间内，运行的任务队列分配到可用处理器的平均数(平常说的 CPU 使用率)
     */
    public native int getLoadAverage(double[] loadavg, int nelems);

    /*重点*/
    /*基于CAS的java实现 用于不支持本地命令的平台*/

    /**
     * 在给定的字段或数组的元素的当前的原子性的增加给定的值
     * @param o ：Java堆对象 基准内存
     * @param offset ： 属性的偏移量
     * @param delta ：增量
     * @return
     */
    public final int getAndAddInt(Object o, long offset, int delta) {

        int v;
        //自旋,预期值与内存值不相同时就一直自旋
        do {
            //获取当前偏移量的值
            v = getIntVolatile(o, offset);
        }
        //设置成功时返回true
        while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    public final long getAndAddLong(Object o, long offset, long delta) {
        int v;
        do {
            v = getLongVolatile(o, offset);
        }
        while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    /**
     * 将给定字段或数组元素的当前值原子性的替换值
     * @param o
     * @param offset
     * @param newValue
     * @return
     */
    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        }
        while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        }
        while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    public final Object getAndSetObject(Object o, long offset, Object x) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        }
        while (!compareAndSwapObject(o, offset, v, x));
        return v;
    }

    /*基于原子的CAS操作 -- 保证内存可见性和指令重排序*/

    /**
     * 确保该栏杆前的读操作不会和栏杆后的读写操作发生重排序
     * @since 1.8
     */
    public native void loadFence();

    /**
     * /确保该栏杆前的写操作不会和栏杆后的读写操作发生重排序
     * @since 1.8
     */
    public native void storeFence();

    /**
     * 确保该栏杆前的读写操作不会和栏杆后的读写操作发生重排序
     * @since 1.8
     */
    public native void fullFence();

    /**
     * 抛出非法访问错误，仅用于VM内部
     * @since 1.8
     */
    private static void throwIllegalAccessError() {
        throw new IllegalAccessError();
    }

}
