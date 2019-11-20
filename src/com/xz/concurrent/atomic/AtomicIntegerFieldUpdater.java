package com.xz.concurrent.atomic;

/**
 * @author xuanzhou
 * @date 2019/10/14 16:54
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import sun.reflect.CallerSensitive;
import sun.reflect.Reflection;

/**
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AtomicIntegerFieldUpdater<T> {

    /**
     * 基于反射的工具:对指定类的volatile int字段进行原子更新
     * @param tclass 属性所在类的Class对象
     * @param fieldName 属性的名称
     * @param <U> 泛型
     */
    @CallerSensitive
    public static <U> AtomicIntegerFieldUpdater<U> newUpdater(Class<U> tclass, String fieldName) {
        return new AtomicIntegerFieldUpdaterImpl<U>(tclass, fieldName, Reflection.getCallerClass());
    }

    /**
     * Protected do-nothing constructor for use by subclasses.
     */
    protected AtomicIntegerFieldUpdater() {
    }

    /**
     * 抽象方法：比较并交换
     */
    public abstract boolean compareAndSet(T obj, int expect, int update);

    /**
     * 比较并交换 - 还未实现
     */
    public abstract boolean weakCompareAndSet(T obj, int expect, int update);

    /**
     * 设置新值
     */
    public abstract void set(T obj, int newValue);

    /**
     * 设置新值 不需要让共享变量的修改立即让其他线程可见 已设置普通变量的形式修改共享变量的值
     * 减少不必要的屏障 提高内存执行效率
     * @since 1.6
     */
    public abstract void lazySet(T obj, int newValue);

    /**
     * 获取当前值
     * @return the current value
     */
    public abstract int get(T obj);

    /**
     * 获取并设置新值 返回原值
     * @param obj An object whose field to get and set
     * @param newValue the new value
     * @return the previous value
     */
    public int getAndSet(T obj, int newValue) {
        int prev;
        do {
            prev = get(obj);
        }
        while (!compareAndSet(obj, prev, newValue));
        return prev;
    }

    /**
     * 自增返回旧值
     * @param obj An object whose field to get and set
     * @return the previous value
     */
    public int getAndIncrement(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        }
        while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * 自减返回旧值
     * @param obj An object whose field to get and set
     * @return the previous value
     */
    public int getAndDecrement(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        }
        while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * 获取并将变量增加指定增量 返回旧值
     * @param obj An object whose field to get and set
     * @param delta the value to add
     * @return the previous value
     */
    public int getAndAdd(T obj, int delta) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        }
        while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * 自增并返回新值
     * @param obj An object whose field to get and set
     * @return the updated value
     */
    public int incrementAndGet(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        }
        while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 自减并返回新值
     * @param obj An object whose field to get and set
     * @return the updated value
     */
    public int decrementAndGet(T obj) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        }
        while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 增加指定增量并返回新值
     * @return the updated value
     */
    public int addAndGet(T obj, int delta) {
        int prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        }
        while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 函数式接口更新并返回原值
     * @param obj An object whose field to get and set
     * @param updateFunction a side-effect-free function
     * @return the previous value
     * @since 1.8
     */
    public final int getAndUpdate(T obj, IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsInt(prev);
        }
        while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * 函数值接口更新并返回新值
     * @param obj An object whose field to get and set
     * @param updateFunction a side-effect-free function
     * @return the updated value
     * @since 1.8
     */
    public final int updateAndGet(T obj, IntUnaryOperator updateFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsInt(prev);
        }
        while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 更新两个操作并返回原值
     * @param obj An object whose field to get and set
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the previous value
     * @since 1.8
     */
    public final int getAndAccumulate(T obj, int x, IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSet(obj, prev, next));
        return prev;
    }

    /**
     * 更新两个操作数并返回新值
     * @param obj An object whose field to get and set
     * @param x the update value
     * @param accumulatorFunction a side-effect-free function of two arguments
     * @return the updated value
     * @since 1.8
     */
    public final int accumulateAndGet(T obj, int x, IntBinaryOperator accumulatorFunction) {
        int prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsInt(prev, x);
        }
        while (!compareAndSet(obj, prev, next));
        return next;
    }

    /**
     * 实现类
     */
    private static final class AtomicIntegerFieldUpdaterImpl<T> extends AtomicIntegerFieldUpdater<T> {

        private static final sun.misc.Unsafe U = sun.misc.Unsafe.getUnsafe();

        //偏移量
        private final long offset;

        /**
         * if field is protected, the subclass constructing updater, else
         * the same as tclass
         */
        private final Class<?> cclass;

        /**
         * class holding the field
         */
        private final Class<T> tclass;

        AtomicIntegerFieldUpdaterImpl(final Class<T> tclass, final String fieldName, final Class<?> caller) {
            final Field field;
            final int modifiers;
            try {
                field = AccessController.doPrivileged(new PrivilegedExceptionAction<Field>() {

                    @Override
                    public Field run() throws NoSuchFieldException {
                        //字段不存在抛出异常
                        return tclass.getDeclaredField(fieldName);
                    }
                });
                //获取属性的修饰符
                modifiers = field.getModifiers();
                sun.reflect.misc.ReflectUtil.ensureMemberAccess(caller, tclass, null, modifiers);
                ClassLoader cl = tclass.getClassLoader();
                ClassLoader ccl = caller.getClassLoader();
                if ((ccl != null) && (ccl != cl) && ((cl == null) || !isAncestor(cl, ccl))) {
                    sun.reflect.misc.ReflectUtil.checkPackageAccess(tclass);
                }
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            //属性必须是int类型
            if (field.getType() != int.class) {
                throw new IllegalArgumentException("Must be integer type");
            }
            //属性必须用volatile修饰
            if (!Modifier.isVolatile(modifiers)) {
                throw new IllegalArgumentException("Must be volatile type");
            }

            cclass = (Modifier.isProtected(modifiers) && tclass.isAssignableFrom(caller) && !isSamePackage(tclass,
                    caller)) ? caller : tclass;
            this.tclass = tclass;
            offset = U.objectFieldOffset(field);
        }

        /**
         * Returns true if the second classloader can be found in the first
         * classloader's delegation chain.
         * Equivalent to the inaccessible: first.isAncestor(second).
         */
        private static boolean isAncestor(ClassLoader first, ClassLoader second) {
            ClassLoader acl = first;
            do {
                acl = acl.getParent();
                if (second == acl) {
                    return true;
                }
            }
            while (acl != null);
            return false;
        }

        /**
         * Returns true if the two classes have the same class loader and
         * package qualifier
         */
        private static boolean isSamePackage(Class<?> class1, Class<?> class2) {
            return class1.getClassLoader() == class2.getClassLoader() && Objects
                    .equals(getPackageName(class1), getPackageName(class2));
        }

        private static String getPackageName(Class<?> cls) {
            String cn = cls.getName();
            int dot = cn.lastIndexOf('.');
            return (dot != -1) ? cn.substring(0, dot) : "";
        }

        /**
         * 判断是否是同一个类型
         */
        private final void accessCheck(T obj) {
            if (!cclass.isInstance(obj)) {
                throwAccessCheckException(obj);
            }
        }

        /**
         * 抛出校验不通过异常
         */
        private final void throwAccessCheckException(T obj) {
            if (cclass == tclass) {
                throw new ClassCastException();
            }
            else {
                throw new RuntimeException(new IllegalAccessException(
                        "Class " + cclass.getName() + " can not access a protected member of class " + tclass.getName()
                                + " using an instance of " + obj.getClass().getName()));
            }
        }

        @Override
        public final boolean compareAndSet(T obj, int expect, int update) {
            accessCheck(obj);
            return U.compareAndSwapInt(obj, offset, expect, update);
        }

        @Override
        public final boolean weakCompareAndSet(T obj, int expect, int update) {
            accessCheck(obj);
            return U.compareAndSwapInt(obj, offset, expect, update);
        }

        @Override
        public final void set(T obj, int newValue) {
            accessCheck(obj);
            U.putIntVolatile(obj, offset, newValue);
        }

        @Override
        public final void lazySet(T obj, int newValue) {
            accessCheck(obj);
            U.putOrderedInt(obj, offset, newValue);
        }

        @Override
        public final int get(T obj) {
            accessCheck(obj);
            return U.getIntVolatile(obj, offset);
        }

        @Override
        public final int getAndSet(T obj, int newValue) {
            accessCheck(obj);
            return U.getAndSetInt(obj, offset, newValue);
        }

        @Override
        public final int getAndAdd(T obj, int delta) {
            accessCheck(obj);
            return U.getAndAddInt(obj, offset, delta);
        }

        @Override
        public final int getAndIncrement(T obj) {
            return getAndAdd(obj, 1);
        }

        @Override
        public final int getAndDecrement(T obj) {
            return getAndAdd(obj, -1);
        }

        @Override
        public final int incrementAndGet(T obj) {
            return getAndAdd(obj, 1) + 1;
        }

        @Override
        public final int decrementAndGet(T obj) {
            return getAndAdd(obj, -1) - 1;
        }

        @Override
        public final int addAndGet(T obj, int delta) {
            return getAndAdd(obj, delta) + delta;
        }
    }
}

