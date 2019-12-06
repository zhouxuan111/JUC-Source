package com.xz.concurrent.collection;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @since 1.2
 */
public interface Map<K, V> {

    /*--------------------------抽象方法-----------------------------------*/

    int size();

    boolean isEmpty();

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    V get(Object key);

    V put(K key, V value);

    V remove(Object key);

    void putAll(Map<? extends K, ? extends V> m);

    void clear();

    /*---------------------------三种视图--------------------------------*/

    /**
     * key的集合
     */
    Set<K> keySet();

    /**
     * value的集合
     */
    Collection<V> values();

    /**
     * key-value的结合
     */
    Set<Map.Entry<K, V>> entrySet();

    @Override
    boolean equals(Object o);

    @Override
    int hashCode();


    /*-----------------------------默认方法-----------------------------------*/

    /**
     * get(Object key)返回null存在两种情况
     * 1.map中没有以key为键的entry 2.以key为键的entry的value为null
     * 返回指定key对应的value 若没有 返回默认key的value
     * 线程不安全
     */
    default V getOrDefault(Object key, V defaultValue) {
        V v;
        //单线程情况下 Map线程安全 允许key和value为null，因为可以辨别是哪一种情况返回null
        return (((v = get(key)) != null) || containsKey(key)) ? v : defaultValue;
    }

    /**
     * 遍历 ：对外提供 用于消费者行为
     * 线程不安全
     * BiConsumer:消费型接口 参数类型是T 无返回值 void accept(T t)
     * @since 1.8
     */
    default void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        for (Map.Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch (IllegalStateException ise) {
                throw new ConcurrentModificationException(ise);
            }
            action.accept(k, v);
        }
    }

    /**
     * 不存在时添加(不存在是指 key == null  或者key对应的value == null)
     * @since 1.8
     * absent == map.get(key) == null
     */
    default V putIfAbsent(K key, V value) {
        V v = get(key);
        if (v == null) {
            v = put(key, value);
        }
        return v;
    }

    /**
     * 若给定的参数key和value在map中是一个entry，则删除这个entry、
     * 参数key对应的值与参数value的值相等则进行删除
     * @since 1.8
     */
    default boolean remove(Object key, Object value) {
        Object curValue = get(key);
        if (!Objects.equals(curValue, value) || (curValue == null && !containsKey(key))) {
            return false;
        }
        remove(key);
        return true;
    }

    /**
     * 对map中的每一个entry，将value替换成BiFunction接口返回的值 直到所有entry替换完成或者出现异常为止
     * 线程不安全
     * BiFunction<T,R>：函数型接口 对T类型参数操作 返回R类型操作  R apply(T)
     * @since 1.8
     * 利用功能函数计算出一个新的value , 将map的value全部进行替换操作
     */
    default void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        for (Map.Entry<K, V> entry : entrySet()) {
            K k;
            V v;
            try {
                k = entry.getKey();
                v = entry.getValue();
            } catch (IllegalStateException ise) {
                throw new ConcurrentModificationException(ise);
            }
            v = function.apply(k, v);

            try {
                entry.setValue(v);
            } catch (IllegalStateException ise) {
                throw new ConcurrentModificationException(ise);
            }
        }
    }

    /**
     * 若给定的参数key存在 将其对应的value替换为新的value
     * 参数key对应的value与参数oldValue相等 将key对应的value进行替换
     * @since 1.8
     */
    default boolean replace(K key, V oldValue, V newValue) {
        Object curValue = get(key);
        //key不存在  或者值不相等时
        if (!Objects.equals(curValue, oldValue) || (curValue == null && !containsKey(key))) {
            return false;
        }
        put(key, newValue);
        return true;
    }

    /**
     * 若key有对应的value 则将value退换为新的value
     * @since 1.8
     */
    default V replace(K key, V value) {
        V curValue;
        if (((curValue = get(key)) != null) || containsKey(key)) {
            curValue = put(key, value);
        }
        return curValue;
    }

    /**
     * computeIfAbsent() computeIfPresent() compute()均使用函数型接口计算一个新的value
     * 函数型接口：Function 功能型
     */

    /**
     * key无对应的value，则使用接口函数mappingFunction计算一个value，
     * 若计算的value不为null ，将value插入到map中
     * 若计算的value为null，不插入任何映射 若mappingFunction抛出异常 也不会进行插入
     * @since 1.8
     */
    default V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        V v;
        if ((v = get(key)) == null) {
            V newValue;
            if ((newValue = mappingFunction.apply(key)) != null) {
                put(key, newValue);
                return newValue;
            }
        }

        return v;
    }

    /**
     * 可以对应的value存在 value！=null 尝试利用function 并利用key生成一个新的value
     * value为null，将删除key对应的entry 若function抛出异常 map本身不会发生改变
     * value不为null 进行更新
     * @since 1.8
     */
    default V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue;
        if ((oldValue = get(key)) != null) {
            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue != null) {
                put(key, newValue);
                return newValue;
            }
            else {
                remove(key);
                return null;
            }
        }
        else {
            return null;
        }
    }

    /**
     * 利用指定key和value计算一个新映射
     * function抛出异常 map本身不会发生改变
     * newValue == null oldValue != null 删除key对应的entry
     * newValue ！= null 添加或替换原来的映射
     * @since 1.8
     */
    default V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        V oldValue = get(key);

        V newValue = remappingFunction.apply(key, oldValue);
        if (newValue == null) {
            // delete mapping
            if (oldValue != null || containsKey(key)) {
                // something to remove
                remove(key);
                return null;
            }
            else {
                // nothing to do. Leave things as they were.
                return null;
            }
        }
        else {

            put(key, newValue);
            return newValue;
        }
    }

    /**
     * 为一个key对应的value 创建或者append值为msg的字符串
     * 与compute方法类似
     * @since 1.8
     */
    default V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        Objects.requireNonNull(value);
        V oldValue = get(key);
        //key没有value或者value == null 将其更改为入参的value 否则使用remappingFunction生成新的value
        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
        //newValue == null 删除key对应的entry
        if (newValue == null) {
            remove(key);
        }
        //添加或更改entry
        else {
            put(key, newValue);
        }
        return newValue;
    }


    /*-----------------------内部接口Entry--------------------------*/

    interface Entry<K, V> {

        K getKey();

        V getValue();

        V setValue(V value);

        @Override
        boolean equals(Object o);

        @Override
        int hashCode();

        /*---------------------------------JDK 1.8新增方法------------------------------------*/

        /**
         * 返回一个map.entry的比较器，按照key的自然排序升序
         * key存在null 报空指针异常
         * @since 1.8
         */
        public static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K, V>> comparingByKey() {
            return (Comparator<Map.Entry<K, V>> & Serializable) (c1, c2) -> c1.getKey().compareTo(c2.getKey());
        }

        /**
         * 返回map.entry的比较器 按照value自然顺序排序
         * value存在null 报空指针异常
         * @since 1.8
         */
        public static <K, V extends Comparable<? super V>> Comparator<Map.Entry<K, V>> comparingByValue() {
            return (Comparator<Map.Entry<K, V>> & Serializable) (c1, c2) -> c1.getValue().compareTo(c2.getValue());
        }

        /**
         * 返回一个map.entry比较器 根据传入的比较器对key进行排序
         * @since 1.8
         */
        public static <K, V> Comparator<Map.Entry<K, V>> comparingByKey(Comparator<? super K> cmp) {
            Objects.requireNonNull(cmp);
            return (Comparator<Map.Entry<K, V>> & Serializable) (c1, c2) -> cmp.compare(c1.getKey(), c2.getKey());
        }

        /**
         * 返回一个map.entry比较器 根据传入的比较器对value进行排序
         * @since 1.8
         */
        public static <K, V> Comparator<Map.Entry<K, V>> comparingByValue(Comparator<? super V> cmp) {
            //判空 若比较器为空 报空指针
            Objects.requireNonNull(cmp);
            return (Comparator<Map.Entry<K, V>> & Serializable) (c1, c2) -> cmp.compare(c1.getValue(), c2.getValue());
        }
    }
}
