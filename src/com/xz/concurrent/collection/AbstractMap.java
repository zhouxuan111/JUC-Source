package com.xz.concurrent.collection;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public abstract class AbstractMap<K, V> implements Map<K, V> {

    /*------------------------构造---------------------------*/
    protected AbstractMap() {
    }

    /*-------------------------重写Map方法-----------------------------*/

    /**
     * 只要遍历 底层均使用Set<Map.Entry<K,V>> = entrySet()的迭代器iterator
     */

    @Override
    public int size() {
        return entrySet().size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        //value == null 时查找 null不能计算hashcode
        if (value == null) {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                //value(Object)
                if (e.getValue() == null) {
                    return true;
                }
            }
        }
        else {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (value.equals(e.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 逻辑同containValue() 根据两种情况  key == null key!=null
     */
    @Override
    public boolean containsKey(Object key) {
        Iterator<Map.Entry<K, V>> i = entrySet().iterator();
        if (key == null) {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (e.getKey() == null) {
                    return true;
                }
            }
        }
        else {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (key.equals(e.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 根据key == null  key != null
     */
    @Override
    public V get(Object key) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (key == null) {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (e.getKey() == null) {
                    return e.getValue();
                }
            }
        }
        else {
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                if (key.equals(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    //未实现 由子类实现
    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        Entry<K, V> correctEntry = null;
        if (key == null) {
            while (correctEntry == null && i.hasNext()) {
                Entry<K, V> e = i.next();
                if (e.getKey() == null) {
                    correctEntry = e;
                }
            }
        }
        else {
            while (correctEntry == null && i.hasNext()) {
                Entry<K, V> e = i.next();
                if (key.equals(e.getKey())) {
                    correctEntry = e;
                }
            }
        }

        V oldValue = null;
        if (correctEntry != null) {
            oldValue = correctEntry.getValue();
            //迭代器删除
            i.remove();
        }
        return oldValue;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        entrySet().clear();
    }

    /*-------------------------三种视图实现---------------------------*/

    transient Set<K> keySet;

    transient Collection<V> values;

    /**
     * keySet()、values()方法的内部实现相似
     * @return
     */

    @Override
    public Set<K> keySet() {
        Set<K> ks = keySet;
        //初始化set集合
        if (ks == null) {
            ks = new AbstractSet<K>() {

                /*--------------重写AbstractSet方法---------------*/
                @Override
                public Iterator<K> iterator() {
                    return new Iterator<K>() {

                        private Iterator<Entry<K, V>> i = entrySet().iterator();

                        /*-----------重写Iterator方法-----------*/
                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public K next() {
                            return i.next().getKey();
                        }

                        @Override
                        public void remove() {
                            i.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return AbstractMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    AbstractMap.this.clear();
                }

                @Override
                public boolean contains(Object k) {
                    return containsKey(k);
                }
            };
            //给keySet进行赋值
            keySet = ks;
        }
        return ks;
    }

    @Override
    public Collection<V> values() {
        Collection<V> vals = values;
        //初始化vals
        if (vals == null) {
            vals = new AbstractCollection<V>() {

                @Override
                public Iterator<V> iterator() {
                    return new Iterator<V>() {

                        private Iterator<Entry<K, V>> i = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public V next() {
                            return i.next().getValue();
                        }

                        @Override
                        public void remove() {
                            i.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return AbstractMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return AbstractMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    AbstractMap.this.clear();
                }

                @Override
                public boolean contains(Object v) {
                    return containsValue(v);
                }
            };
            //给values进行赋值
            values = vals;
        }
        return vals;
    }

    @Override
    public abstract Set<Map.Entry<K, V>> entrySet();

    /*----------------------重写Object方法------------------------*/
    @Override
    public boolean equals(Object o) {
        //比较引用地址
        if (o == this) {
            return true;
        }
        //类型不同
        if (!(o instanceof Map)) {
            return false;
        }
        //集合不同
        Map<?, ?> m = (Map<?, ?>) o;
        if (m.size() != size()) {
            return false;
        }

        try {
            Iterator<Entry<K, V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<K, V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key) == null && m.containsKey(key))) {
                        return false;
                    }
                }
                else {
                    if (!value.equals(m.get(key))) {
                        return false;
                    }
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Iterator<Entry<K, V>> i = entrySet().iterator();
        while (i.hasNext()) {
            h += i.next().hashCode();
        }
        return h;
    }

    @Override
    public String toString() {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (!i.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (; ; ) {
            Entry<K, V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key == this ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (!i.hasNext()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        AbstractMap<?, ?> result = (AbstractMap<?, ?>) super.clone();
        result.keySet = null;
        result.values = null;
        return result;
    }

    /**
     * 对两个对象进行比较
     */
    private static boolean eq(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    /*---------------------------------AbstractMap对Entry的两种实现---------------------------*/

    /**
     * SimpleEntry和SimpleImmutableEntry的equals()和hashcode()方法相同
     */

    /**
     * 可变的Entry  key不可变 value可变
     */
    public static class SimpleEntry<K, V> implements Entry<K, V>, java.io.Serializable {

        private static final long serialVersionUID = -8499721149061103585L;

        //key不可变
        private final K key;

        private V value;

        /*--------------------两种构造---------------------*/
        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public SimpleEntry(Entry<? extends K, ? extends V> entry) {
            key = entry.getKey();
            value = entry.getValue();
        }

        /**
         * 重写Map.Entry方法
         */
        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        //只比较key 和value
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return eq(key, e.getKey()) && eq(value, e.getValue());
        }

        @Override
        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

    }

    /**
     * 不可变的Entry key不可变 value不可变
     */
    public static class SimpleImmutableEntry<K, V> implements Entry<K, V>, java.io.Serializable {

        private static final long serialVersionUID = 7138329143949025153L;

        private final K key;

        private final V value;

        public SimpleImmutableEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public SimpleImmutableEntry(Entry<? extends K, ? extends V> entry) {
            key = entry.getKey();
            value = entry.getValue();
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        /**
         * value不可变 所以调用此方法报异常
         */
        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }

        //只比较key和value
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return eq(key, e.getKey()) && eq(value, e.getValue());
        }

        @Override
        public int hashCode() {
            return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }

    }
}

