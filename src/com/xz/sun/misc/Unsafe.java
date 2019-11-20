package com.xz.sun.misc;

/**
 * @author xuanzhou
 * @date 2019/10/10 15:32
 */
/*
    类：执行硬件级别的原子操作，该类中均为native方法，连接Java应用程序和操作系统底层的方法，
        使用单例模式。不安全的操作集合，通过内存地址存取fields(double-register,single-register)。
    两大功能：
        绕过JVM,直接修改内存对象
        使用硬件CPU指令实现CAS原子操作
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;

import sun.misc.VM;
import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

public final class Unsafe {

    private static native void registerNatives();

    static {
        //注册本地方法
        registerNatives();
        //将Unsafe类的getUnsafe()方法添加到Reflection的反射过滤列表当中，让该类不能通过反射访问。
        sun.reflect.Reflection.registerMethodsToFilter(Unsafe.class, "getUnsafe");
    }

    /**
     * 构造方法私有化-单例模式
     */
    private Unsafe() {
    }

    /**
     * 创建单例实例-theUnsafe
     */
    private static final Unsafe theUnsafe = new Unsafe();

    /**
     * @CallerSensitive : 所有调用Reflection.getCallerClass()方法的方法都要用此注解注释
     * Reflection.getCallerClass()此方法的调用者必须有权限。
     * 由BootstrapClassLoader、ExtensionClassLoader加载的类可以调用
     * 用户自定义的类的类加载器是ApplicationClassLoader，所以无法调用此方法
     * 该注解的作用：通过此方法获取Class对象会跳过所有用该注解标注的方法的类，返回第一个没用注解标注的Class对象。
     */
    /**
     * 获取Unsafe对象，会检查类加载器，不符合的类加载器会抛出SecurityException异常。
     * @return Unsafe
     * 该方法直接调用 只能通过反射来调用
     * Field field = Unsafe.class.getDeclaredField("theUnsafe");
     * field.setAccessible(true);
     * Unsafe unsafe = (Unsafe) field.get(null);
     */
    @CallerSensitive
    public static Unsafe getUnsafe() {
        Class<?> caller = Reflection.getCallerClass();
        //判断是否是BootstrapClassLoader类加载器,不是的话抛出SecurityException
        if (!VM.isSystemDomainLoader(caller.getClassLoader())) {
            throw new SecurityException("Unsafe");
        }
        return theUnsafe;
    }


    /*内存偏移量的获取*/

    /**
     * 默认的属性偏移量
     */
    public static final int INVALID_FIELD_OFFSET = -1;

    /**
     * 获取Object类型字段在所在类的内存偏移量
     * @param f
     * @return
     */
    public native long objectFieldOffset(Field f);

    /**
     * 获取静态字段在所在类的内存偏移量
     * @param f
     * @return
     */
    public native long staticFieldOffset(Field f);

    /**
     * 获取任意属性的内存偏移量，过时 不建议使用
     * @param f
     * @return
     */
    @Deprecated
    public int fieldOffset(Field f) {
        //java.lang.reflect.Modifier:用于判断和获取某个类，变量，方法的修饰符
        //Modifier.isStatic(int mod)判断是否包含静态修饰符
        // Field getModifiers() : 获取成员变量的修饰符，返回整数
        if (Modifier.isStatic(f.getModifiers())) {
            return (int) staticFieldOffset(f);
        }
        else {
            return (int) objectFieldOffset(f);
        }
    }

    /**
     * 获取指定静态字段的位置 和staticFieldOffset()一起使用
     * 获取字段所在的对象
     * @param f
     * @return 返回用于访问静态字段的基准地址
     */
    public native Object staticFieldBase(Field f);

    /**
     * 获取访问静态字段的基准地址（获取多有的静态字段）
     * @param c
     * @return 过时
     */
    @Deprecated
    public Object staticFieldBase(Class<?> c) {
        Field[] fields = c.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            if (Modifier.isStatic(fields[ i ].getModifiers())) {
                return staticFieldBase(fields[ i ]);
            }
        }
        return null;
    }

    /**
     * 获取数组中第一个元素的内存地址偏移量
     * @param arrayClass
     * @return
     */
    public native int arrayBaseOffset(Class<?> arrayClass);

    /**
     * 获取数组中单个元素占用的字节数
     * @param arrayClass
     * @return
     */
    public native int arrayIndexScale(Class<?> arrayClass);

    /*需要Java对象地址作为基准地址 获取和设置属性在内存中值 offset为属性在java对象内存地址中的偏移量
     * double-register
     * */

    /**
     * 获取指定对象中指定偏移量的字段的值
     * @param o ：若偏移量是根据objectFieldOffset()获取的，o为变量关联的Java堆的对象
     * 若偏移量是根据staticFieldOffset()获取的，o为通staticFieldBase()获取的对象
     * 若o是数组，offset的值为B+N*S N：数组的合法下标 B:arrayBaseOffset()获取的
     * S:arrayIndexScale()获取的
     * @param offset
     * @return
     */
    public native int getInt(Object o, long offset);

    /**
     * 将值存储到Java变量中（int）
     * @param o：关联的Java的堆对象 可以为null
     * @param offset：该变量在对象中的位置 若o为null 则是内存的绝对地址
     * @param x
     */
    public native void putInt(Object o, long offset, int x);

    /**
     * 获取Object对象中Object类型的值
     */
    public native Object getObject(Object o, long offset);

    /**
     * 将值存到Java变量中(Object)
     */
    public native void putObject(Object o, long offset, Object x);

    /**
     * @see #getInt(Object, long)
     */
    public native boolean getBoolean(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putBoolean(Object o, long offset, boolean x);

    /**
     * @see #getInt(Object, long)
     */
    public native byte getByte(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putByte(Object o, long offset, byte x);

    /**
     * @see #getInt(Object, long)
     */
    public native short getShort(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putShort(Object o, long offset, short x);

    /**
     * @see #getInt(Object, long)
     */
    public native char getChar(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putChar(Object o, long offset, char x);

    /**
     * @see #getInt(Object, long)
     */
    public native long getLong(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putLong(Object o, long offset, long x);

    /**
     * @see #getInt(Object, long)
     */
    public native float getFloat(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putFloat(Object o, long offset, float x);

    /**
     * @see #getInt(Object, long)
     */
    public native double getDouble(Object o, long offset);

    /**
     * @see #putInt(Object, int, int)
     */
    public native void putDouble(Object o, long offset, double x);

    /**
     * 过时方法 (作用同上)
     */
    @Deprecated
    public int getInt(Object o, int offset) {
        return getInt(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putInt(Object o, int offset, int x) {
        putInt(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public Object getObject(Object o, int offset) {
        return getObject(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putObject(Object o, int offset, Object x) {
        putObject(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public boolean getBoolean(Object o, int offset) {
        return getBoolean(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putBoolean(Object o, int offset, boolean x) {
        putBoolean(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public byte getByte(Object o, int offset) {
        return getByte(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putByte(Object o, int offset, byte x) {
        putByte(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public short getShort(Object o, int offset) {
        return getShort(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putShort(Object o, int offset, short x) {
        putShort(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public char getChar(Object o, int offset) {
        return getChar(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putChar(Object o, int offset, char x) {
        putChar(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public long getLong(Object o, int offset) {
        return getLong(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putLong(Object o, int offset, long x) {
        putLong(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public float getFloat(Object o, int offset) {
        return getFloat(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putFloat(Object o, int offset, float x) {
        putFloat(o, (long) offset, x);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public double getDouble(Object o, int offset) {
        return getDouble(o, (long) offset);
    }

    /**
     * See {@link #staticFieldOffset}.
     */
    @Deprecated
    public void putDouble(Object o, int offset, double x) {
        putDouble(o, (long) offset, x);
    }

    /*不需要Java对象地址作为基准地址，只需要内存绝对地址获取和设置内存中的属性的值
     * 如内存中的地址是0 或者是不能通过allocateMemory()方法获取的内存块 则结果是未知的
     */

    /**
     * 用内存中的绝对地址获取属性的值
     * @param address
     * @return
     */
    public native byte getByte(long address);

    /**
     * 用内存中的绝对地址设置水属性的值
     */
    public native void putByte(long address, byte x);

    /**
     * @see #getByte(long)
     */
    public native short getShort(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putShort(long address, short x);

    /**
     * @see #getByte(long)
     */
    public native char getChar(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putChar(long address, char x);

    /**
     * @see #getByte(long)
     */
    public native int getInt(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putInt(long address, int x);

    /**
     * @see #getByte(long)
     */
    public native long getLong(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putLong(long address, long x);

    /**
     * @see #getByte(long)
     */
    public native float getFloat(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putFloat(long address, float x);

    /**
     * @see #getByte(long)
     */
    public native double getDouble(long address);

    /**
     * @see #putByte(long, byte)
     */
    public native void putDouble(long address, double x);

    /**
     * 从一个给定的内存地址获取本地指针，
     * @see #allocateMemory
     */
    public native long getAddress(long address);

    /**
     * 根据给定的内存地址设置本地指针的值
     */
    public native void putAddress(long address, long x);


    /*和本地内存有关的方法*/

    /**
     * 用于分配本地内存：分配指定大小的本地内存，分配的内存不会被初始化 通常是无用的数据
     * freeMemory()释放内存
     * reallocateMemory()重新分配内存
     * @param bytes
     * @return 返回的本地内存不会是0
     */
    public native long allocateMemory(long bytes);

    /**
     * 重新分配内存：超出老字节的不会被初始化
     * freeMemory()释放内存
     * @param address address = null 方法 = allocateMemory() 分配新的内存
     * @param bytes
     * @return 当请求的大小为0 时，该方法返回的本地指针为0
     */
    public native long reallocateMemory(long address, long bytes);

    /**
     * 将给定内存块的所有字节设置成固定的值（通常是0）
     * @param o ： Java堆对象：对象的基准地址
     * @param offset ： 属性在Java堆对象的偏移量 ，若o = null offset为绝对地址
     * @param bytes ： 设置值的字节大小
     * @param value ： 设置的值
     */
    public native void setMemory(Object o, long offset, long bytes, byte value);

    /**
     * 将给定的绝对内存地址的中设置成另一个值
     * @param address ： 绝对地址
     * @param bytes
     * @param value
     */
    public void setMemory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

    /**
     * 复制给定内存块的值到另一个内存块
     * @param srcBase
     * @param srcOffset
     * @param destBase
     * @param destOffset
     * @param bytes
     */
    public native void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);

    /**
     * 复制指定内存块的字节到另一内存块，但使用 single-register 地址模型
     * @param srcAddress
     * @param destAddress
     * @param bytes
     */
    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        copyMemory(null, srcAddress, null, destAddress, bytes);
    }

    /**
     * 释放内存
     */
    public native void freeMemory(long address);

    /*获取类变量相关信息的方法*/

    /**
     * 判断该类是否需要初始化  通常和staticOffsetBase()一起使用
     * 只有当 ensureClassInitialized 方法不产生任何影响时才会返回 false
     * @param c
     * @return
     */
    public native boolean shouldBeInitialized(Class<?> c);

    /**
     * 确保给定的类已经被初始化.
     */
    public native void ensureClassInitialized(Class<?> c);


    /*返回不同类型数组中第一个元素在内存中的偏移量*/

    /**
     * The value of {@code arrayBaseOffset(boolean[].class)}
     */
    public static final int ARRAY_BOOLEAN_BASE_OFFSET = theUnsafe.arrayBaseOffset(boolean[].class);

    /**
     * The value of {@code arrayBaseOffset(byte[].class)}
     */
    public static final int ARRAY_BYTE_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

    /**
     * The value of {@code arrayBaseOffset(short[].class)}
     */
    public static final int ARRAY_SHORT_BASE_OFFSET = theUnsafe.arrayBaseOffset(short[].class);

    /**
     * The value of {@code arrayBaseOffset(char[].class)}
     */
    public static final int ARRAY_CHAR_BASE_OFFSET = theUnsafe.arrayBaseOffset(char[].class);

    /**
     * The value of {@code arrayBaseOffset(int[].class)}
     */
    public static final int ARRAY_INT_BASE_OFFSET = theUnsafe.arrayBaseOffset(int[].class);

    /**
     * The value of {@code arrayBaseOffset(long[].class)}
     */
    public static final int ARRAY_LONG_BASE_OFFSET = theUnsafe.arrayBaseOffset(long[].class);

    /**
     * The value of {@code arrayBaseOffset(float[].class)}
     */
    public static final int ARRAY_FLOAT_BASE_OFFSET = theUnsafe.arrayBaseOffset(float[].class);

    /**
     * The value of {@code arrayBaseOffset(double[].class)}
     */
    public static final int ARRAY_DOUBLE_BASE_OFFSET = theUnsafe.arrayBaseOffset(double[].class);

    /**
     * The value of {@code arrayBaseOffset(Object[].class)}
     */
    public static final int ARRAY_OBJECT_BASE_OFFSET = theUnsafe.arrayBaseOffset(Object[].class);

    /*返回不同类型数组中元素的scale(所占用的字节数)*/

    /**
     * The value of {@code arrayIndexScale(boolean[].class)}
     */
    public static final int ARRAY_BOOLEAN_INDEX_SCALE = theUnsafe.arrayIndexScale(boolean[].class);

    /**
     * The value of {@code arrayIndexScale(byte[].class)}
     */
    public static final int ARRAY_BYTE_INDEX_SCALE = theUnsafe.arrayIndexScale(byte[].class);

    /**
     * The value of {@code arrayIndexScale(short[].class)}
     */
    public static final int ARRAY_SHORT_INDEX_SCALE = theUnsafe.arrayIndexScale(short[].class);

    /**
     * The value of {@code arrayIndexScale(char[].class)}
     */
    public static final int ARRAY_CHAR_INDEX_SCALE = theUnsafe.arrayIndexScale(char[].class);

    /**
     * The value of {@code arrayIndexScale(int[].class)}
     */
    public static final int ARRAY_INT_INDEX_SCALE = theUnsafe.arrayIndexScale(int[].class);

    /**
     * The value of {@code arrayIndexScale(long[].class)}
     */
    public static final int ARRAY_LONG_INDEX_SCALE = theUnsafe.arrayIndexScale(long[].class);

    /**
     * The value of {@code arrayIndexScale(float[].class)}
     */
    public static final int ARRAY_FLOAT_INDEX_SCALE = theUnsafe.arrayIndexScale(float[].class);

    /**
     * The value of {@code arrayIndexScale(double[].class)}
     */
    public static final int ARRAY_DOUBLE_INDEX_SCALE = theUnsafe.arrayIndexScale(double[].class);

    /**
     * The value of {@code arrayIndexScale(Object[].class)}
     */
    public static final int ARRAY_OBJECT_INDEX_SCALE = theUnsafe.arrayIndexScale(Object[].class);

    /**
     * 获取本地指针占用的字节大小 值为4或8
     */
    public native int addressSize();

    /**
     * The value of {@code addressSize()}
     */
    public static final int ADDRESS_SIZE = theUnsafe.addressSize();

    /**
     * 获取本地内存页的大小 为2的N次方
     */
    public native int pageSize();

    /*JNI信任的操作*/

    /**
     * 告诉虚拟机定义一个类，加载类不做安全检查，默认情况下，参数类加载器(ClassLoader)和保护域(ProtectionDomain)来自调用者类
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


    /*CAS方法 没有加锁 性能更好*/

    /**
     * 通过Object o long offset获取的值 = expect(预期的值)，更新变量的值 原子操作 修改成功返回true
     */
    public final native boolean compareAndSwapObject(Object o, long offset, Object expected, Object x);

    /**
     * 同上 Int类型的数据
     */
    public final native boolean compareAndSwapInt(Object o, long offset, int expected, int x);

    /**
     * Long 类型数据
     */
    public final native boolean compareAndSwapLong(Object o, long offset, long expected, long x);

    /*带有volatile语义的获取指定偏移量在所在类的值和设置值
     * 这些方法可以使非volatile变量具有volatile语义
     * */

    /**
     * volatile加载语义
     * 写的内存语义：当写一个volatile变量时，JMM会把该线程对应的本地内存中的共享变量刷新到主内存
     * 读的内存语义：当读一个volatile变量时，JMM会把该线程对应的本地内存置为无效，线程从主内存读取龚爱那个变量。
     */

    public native Object getObjectVolatile(Object o, long offset);

    /**
     *
     */
    public native void putObjectVolatile(Object o, long offset, Object x);

    /**
     * Volatile version of {@link #getInt(Object, long)}
     */
    public native int getIntVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putInt(Object, long, int)}
     */
    public native void putIntVolatile(Object o, long offset, int x);

    /**
     * Volatile version of {@link #getBoolean(Object, long)}
     */
    public native boolean getBooleanVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putBoolean(Object, long, boolean)}
     */
    public native void putBooleanVolatile(Object o, long offset, boolean x);

    /**
     * Volatile version of {@link #getByte(Object, long)}
     */
    public native byte getByteVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putByte(Object, long, byte)}
     */
    public native void putByteVolatile(Object o, long offset, byte x);

    /**
     * Volatile version of {@link #getShort(Object, long)}
     */
    public native short getShortVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putShort(Object, long, short)}
     */
    public native void putShortVolatile(Object o, long offset, short x);

    /**
     * Volatile version of {@link #getChar(Object, long)}
     */
    public native char getCharVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putChar(Object, long, char)}
     */
    public native void putCharVolatile(Object o, long offset, char x);

    /**
     * Volatile version of {@link #getLong(Object, long)}
     */
    public native long getLongVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putLong(Object, long, long)}
     */
    public native void putLongVolatile(Object o, long offset, long x);

    /**
     * Volatile version of {@link #getFloat(Object, long)}
     */
    public native float getFloatVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putFloat(Object, long, float)}
     */
    public native void putFloatVolatile(Object o, long offset, float x);

    /**
     * Volatile version of {@link #getDouble(Object, long)}
     */
    public native double getDoubleVolatile(Object o, long offset);

    /**
     * Volatile version of {@link #putDouble(Object, long, double)}
     */
    public native void putDoubleVolatile(Object o, long offset, double x);

    /**
     * 有序 延迟 不保证其他线程能够立刻看到修改
     */
    public native void putOrderedObject(Object o, long offset, Object x);

    /**
     * Ordered/Lazy version of {@link #putIntVolatile(Object, long, int)}
     */
    public native void putOrderedInt(Object o, long offset, int x);

    /**
     * Ordered/Lazy version of {@link #putLongVolatile(Object, long, long)}
     */
    public native void putOrderedLong(Object o, long offset, long x);

    /**
     * 有三类很相近的方法：putXx、putXxVolatile 与 putOrderedXx：
     *
     * putXx 只是写本线程缓存，不会将其它线程缓存置为失效，所以不能保证其它线程一定看到此次修改；
     * putXxVolatile 相反，它可以保证其它线程一定看到此次修改；
     * putOrderedXx 也不保证其它线程一定看到此次修改，但和 putXx 又有区别，它的注释上有两个关键字：
     *          顺序性（Ordered）和延迟性（lazy），顺序性是指不会发生重排序，延迟性是指其它线程不会立即看到此次修改，只有当调用 putXxVolatile 使才能看到。
     */

    /*java.util.concurrent 中的锁就是通过这两个方法实现线程阻塞和释放的。*/

    /**
     * 释放当前阻塞的线程 若当前线程没有阻塞 则下一次park不会阻塞
     * unpark()  直接设置count = 1 若之前count = 0 还要调用唤醒在park()中等待的线程
     */
    public native void unpark(Object thread);

    /**
     * 阻塞当前线程 在下列方法之前都会被阻塞
     * 1、调用 unpark 方法 释放该线程的许可
     * 2、线程被中断
     * 3、时间过期 并且time为绝对时间，isAbsolute为true 否则 isAbsolute为false。当time = 0 表示无线等地 知道unpark(Thread thread)发生
     * 4、spuriously
     * 该操作放在 Unsafe 类里没有其它意义，它可以放在其它的任何地方
     * park()执行过程：先尝试能否直接拿到许可(count>0) 若成功 则把count设置为0 并返回  若不成功 则构造一个ThreadBlocklnVM 检查count是否>0 若是 则把count设置为0
     */
    public native void park(boolean isAbsolute, long time);

    /**
     * 获取一段时间内，运行的任务队列分配到可用处理器的平均数(平常说的 CPU 使用率)
     */
    public native int getLoadAverage(double[] loadavg, int nelems);

    /*基于 CAS 的 Java 实现，用于不支持本地指令的平台*/

    /**
     * 在给定的字段或数组元素的当前的原子性的增加给定的值 返回旧值
     * @param o 字段数据所在的对象
     * @param offset 字段元素的偏移量
     * @param delta 需要增加的值
     * @return 原值
     * @since 1.8
     */
    public final int getAndAddInt(Object o, long offset, int delta) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        }
        while (!compareAndSwapInt(o, offset, v, v + delta));
        return v;
    }

    /**
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param delta the value to add
     * @return the previous value
     * @since 1.8
     */
    public final long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        }
        while (!compareAndSwapLong(o, offset, v, v + delta));
        return v;
    }

    /**
     * 将给定字段或数组元素的当前值原子性的替换值 返回旧值
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        }
        while (!compareAndSwapInt(o, offset, v, newValue));
        return v;
    }

    /**
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        }
        while (!compareAndSwapLong(o, offset, v, newValue));
        return v;
    }

    /**
     * @param o object/array to update the field/element in
     * @param offset field/element offset
     * @param newValue new value
     * @return the previous value
     * @since 1.8
     */
    public final Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        }
        while (!compareAndSwapObject(o, offset, v, newValue));
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