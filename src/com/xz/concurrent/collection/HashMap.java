package com.xz.concurrent.collection;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import sun.misc.SharedSecrets;

/**
 * 1.数据结构：数组+链表+红黑树
 * 2.支持key、value 为null
 * 3.key在数组中的下标为(n-1)&hash
 * 5.hash冲突：将两个不同的key映射成同一个index，此现象成为hash冲突，在HashMap中使用链地址法，即在产生冲突的存储桶中进行单链表存储
 * 6.每次扩容将数组扩大为原来的两倍
 */
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;

    /*----------------------------------常量-----------------------------------*/

    /**
     * HashMap的初始化长度 16
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;

    /**
     * HashMap的最大长度
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * HashMap的扩容因子 每次扩容原数组的两倍
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * 红黑树的阈值 链表长度>8 转化为红黑树
     */
    static final int TREEIFY_THRESHOLD = 8;

    /**
     * 链表的阈值 红黑树长度<6 转化为链表 扩容的时候可能会用到
     */
    static final int UNTREEIFY_THRESHOLD = 6;

    /**
     * 最小树化容量阈值 只有链表的长度大于此值，才会转化为红黑树
     */
    static final int MIN_TREEIFY_CAPACITY = 64;

    /*----------------------------------数据结构---------------------------------*/

    /*----------------------单链表：Node<K,V>---------------------*/

    /**
     * JDK 1.7使用Entry表示Map的元素 JDK 1.8使用Node表示Map的元素
     */
    static class Node<K, V> implements Map.Entry<K, V> {

        final int hash;

        //key不可变
        final K key;

        V value;

        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        @Override
        public final K getKey() {
            return key;
        }

        @Override
        public final V getValue() {
            return value;
        }

        @Override
        public final String toString() {
            return key + "=" + value;
        }

        /**
         * 获取Map的元素对象的hashcode 由key的hashcode()^value.hashcode()
         */
        @Override
        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        @Override
        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        @Override
        public final boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                if (Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue())) {
                    return true;
                }
            }
            return false;
        }
    }

    /* -------------------------静态方法-------------------------- */

    /**
     * 散列算法
     * 将key映射成index(数组下标)的方法 根据Index利用O(n)查找到该key对应的value
     * 计算key的hashcode方法，为了减少碰撞，将key均匀的分不到数组中
     * 从该函数获取到信息：若key == null 存在数组的第一个位置。table[0]
     */
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    static Class<?> comparableClassFor(Object x) {
        if (x instanceof Comparable) {
            Class<?> c;
            Type[] ts, as;
            Type t;
            ParameterizedType p;
            if ((c = x.getClass()) == String.class) {
                return c;
            }
            if ((ts = c.getGenericInterfaces()) != null) {
                for (int i = 0; i < ts.length; ++i) {
                    if (((t = ts[ i ]) instanceof ParameterizedType) && ((p = (ParameterizedType) t).getRawType()
                            == Comparable.class) && (as = p.getActualTypeArguments()) != null && as.length == 1
                            && as[ 0 ] == c) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    static int compareComparables(Class<?> kc, Object k, Object x) {
        return (x == null || x.getClass() != kc ? 0 : ((Comparable) k).compareTo(x));
    }

    /**
     * 保证自定义的集合长度大于自定义输入的长度,并且是2的整数幂
     * 将最高位的1后的位全部变为1 再+1 得到的是2的N次幂
     * 实际计算扩容阈值：能够得到真正的定义的HashMap长度
     */
    static final int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /* ---------------- -------属性------------------------ */

    /**
     * 存储Node<K,V>的数组 但是为什么使用transient关键字修饰？不让其被序列化
     * 答：当序列化HashMap对象时，保存Node的table不需要序列化 因为在两台机器上进行
     * 序列化与反序列化计算出同一个对象的hashcode和索引是不同的，所以HashMap重写readObject() readObject()方法
     */

    /**
     * bucket数组 数组的每一个元素可以成为桶
     */
    transient Node<K, V>[] table;

    /**
     * key,value视图
     */
    transient Set<Map.Entry<K, V>> entrySet;

    /**
     * map长度
     */
    transient int size;

    /**
     * 每次更改map和扩容map的计数器
     */
    transient int modCount;

    /**
     * 扩容的临界值 = 容量*填充因子
     * 推荐在数组使用到3/4时进行扩容。所以为什么填充因子默认为0.75
     */
    int threshold;

    /**
     * 填充因子 默认值0.75
     */
    final float loadFactor;

    /* -----------------------------------构造方法----------------------------------- */

    /**
     * 自定义大小 initialCapacity并不是真正的HashMap集合的大小
     * 只是被用来计算threshold(扩容阈值)
     * this.threshold = tableSizeFor(initialCapacity) 最后定义的容量大小必是2^n
     * 在构造函数中 没有指定initialCapacity 则不会给threshold赋值 默认为0  若指定initialCapacity，threshold初始化为initialCapacity的最小的2^n
     */
    /**
     * 自定义初始大小 负载因子
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        //校验参数
        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        //计算扩容阈值  并将HashMap的大小设置为2^n
        threshold = tableSizeFor(initialCapacity);
    }

    /**
     * 自定义初始大小 默认负载因子0.75
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * 默认大小 16 负载因子 0.75 所以在12的时候进行扩容
     */
    public HashMap() {
        loadFactor = DEFAULT_LOAD_FACTOR;
    }

    /**
     * 利用已经存在的Map创建HashMap
     * 在使用此构造函数的时候 table还未初始化 所以table == null
     * table初始化是在put()方法中进行的
     */
    public HashMap(Map<? extends K, ? extends V> m) {
        loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }

    /*------------------------------------功能方法-----------------------------------------*/

    /**
     * 所以根据传入的Map的长度 *默认的负载因子 = 扩容阈值 计算创建的HashMap的大小
     * 来判断需不需要扩容
     * threshold：扩容阈值
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            //table 未初始化
            if (table == null) {
                //计算是否大于阈值 +1是补足
                float ft = ((float) s / loadFactor) + 1.0F;

                int t = ((ft < (float) MAXIMUM_CAPACITY) ? (int) ft : MAXIMUM_CAPACITY);
                if (t > threshold) {
                    //返回最接近自定义长度的2^n
                    threshold = tableSizeFor(t);
                }
            }
            //HashMap的实际长度>扩容阈值 进行扩容
            else if (s > threshold) {
                resize();
            }
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false, evict);
            }
        }
    }

    /**
     * Implements Map.get and related methods
     * @param hash hash for key
     * @param key the key
     * @return the node, or null if none
     */
    final Node<K, V> getNode(int hash, Object key) {
        Node<K, V>[] tab;
        Node<K, V> first, e;
        int n;
        K k;
        if ((tab = table) != null && (n = tab.length) > 0 && (first = tab[ (n - 1) & hash ]) != null) {
            if (first.hash == hash && // always check first node
                    ((k = first.key) == key || (key != null && key.equals(k)))) {
                return first;
            }
            if ((e = first.next) != null) {
                if (first instanceof TreeNode) {
                    return ((TreeNode<K, V>) first).getTreeNode(hash, key);
                }
                do {
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        return e;
                    }
                }
                while ((e = e.next) != null);
            }
        }
        return null;
    }

    /**
     * 添加元素
     * @param onlyIfAbsent true：在key已存在的情况下 保留原值 false：覆盖原值
     * @param evict 判断当前是否是构造模式 false:构造函数下 true：非构造函数下
     * table真正的初始化是在resize()中
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
        Node<K, V>[] tab;
        Node<K, V> p;
        // n = 数组长度 i:元素的下标位置
        int n, i;
        //数组为null或者size = 0 调用resize()进行初始化
        if ((tab = table) == null || (n = tab.length) == 0) {
            n = (tab = resize()).length;
        }
        //key所对应的桶不存在 创建Node节点存放在桶里
        if ((p = tab[ i = (n - 1) & hash ]) == null) {
            tab[ i ] = newNode(hash, key, value, null);
        }
        //对应的桶存在
        else {
            Node<K, V> e;
            K k;
            //首节点
            //判断桶的key和当前key是否相同 相同要满足的两个条件：hash相同，两者 == 或 equals()相等
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k)))) {
                e = p;
            }
            /*-----桶被占用-----*/
            //桶中是红黑树
            else if (p instanceof HashMap.TreeNode) {
                e = ((HashMap.TreeNode<K, V>) p).putTreeVal(this, tab, hash, key, value);
            }
            //桶中是链表
            else {
                for (int binCount = 0; ; ++binCount) {
                    //从尾节点开始
                    if ((e = p.next) == null) {
                        //在尾部插入新的节点
                        p.next = newNode(hash, key, value, null);
                        //长度>8 将链表转为红黑树
                        if (binCount >= TREEIFY_THRESHOLD - 1) {
                            treeifyBin(tab, hash);
                        }
                        break;
                    }
                    //查找到key相同的 直接退出循环
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        break;
                    }
                    p = e;
                }
            }

            //待存储的key已经存在
            if (e != null) {
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null) {
                    e.value = value;
                }
                afterNodeAccess(e);
                return oldValue;
            }
        }
        //累计操作次数
        ++modCount;
        //判断是否需要扩容
        if (++size > threshold) {
            resize();
        }
        //该方法只在LinkHashMap中使用到
        afterNodeInsertion(evict);
        return null;
    }

    /**
     * 使用该方法的两种情况
     * table为空：初始化默认大小
     * 扩容：每次扩容2的幂，并且将旧表中的数据移动到新表中，保持在同一索引位置
     */
    final Node<K, V>[] resize() {
        Node<K, V>[] oldTab = table;
        //旧数组长度
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        //旧数组的扩容阈值
        int oldThr = threshold;
        int newCap, newThr = 0;
        //1.旧数组长度 > 0
        if (oldCap > 0) {
            //旧数组长度>最大长度 不再扩容 直接返回
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            //旧数组的长度扩大两倍 <最大容量 并且旧数组的长度>16
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY && oldCap >= DEFAULT_INITIAL_CAPACITY) {
                newThr = oldThr << 1;
            }
        }
        //有参构造
        //若构造函数中指定initialCapacity，则threshold被初始化过
        else if (oldThr > 0) {
            //新数组长度 = 旧数组阈值
            newCap = oldThr;
        }
        //无参构造
        //没有指定initialCapacity 使用默认值
        else {
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }

        //计算指定initialCapacity情况下 threshold的值
        //else if (oldThr > 0) {
        //    newCap = oldThr;
        //}
        if (newThr == 0) {
            float ft = (float) newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE);
        }
        threshold = newThr;

        //初始化table数组
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[ newCap ];
        table = newTab;
        //进行数据转移
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K, V> e;
                if ((e = oldTab[ j ]) != null) {
                    oldTab[ j ] = null;
                    //存储桶中只有一个元素 直接将其放到新表中的指定位置
                    if (e.next == null) {
                        newTab[ e.hash & (newCap - 1) ] = e;
                    }
                    //若存储桶中是红黑树 拆分树
                    else if (e instanceof HashMap.TreeNode) {
                        ((HashMap.TreeNode<K, V>) e).split(this, newTab, j, oldCap);
                    }
                    //链表查分成两个链表
                    else {
                        //两个链表 lo链表 hi链表
                        Node<K, V> loHead = null, loTail = null;
                        Node<K, V> hiHead = null, hiTail = null;
                        Node<K, V> next;
                        do {
                            next = e.next;
                            //若(e.hash & oldCap) == 0 插入lo链表，否则 插入hi链表
                            if ((e.hash & oldCap) == 0) {
                                //插入lo链表
                                if (loTail == null) {
                                    loHead = e;
                                }
                                else {
                                    loTail.next = e;
                                }
                                loTail = e;
                            }
                            else {
                                //插入hi链表
                                if (hiTail == null) {
                                    hiHead = e;
                                }
                                else {
                                    hiTail.next = e;
                                }
                                hiTail = e;
                            }
                        }
                        while ((e = next) != null);
                        //lo链表不为空 将lo链表元素存放在新table的j位置
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[ j ] = loHead;
                        }
                        //hi链表不为空 将hi链表元素存放在新table的[j+oldCap]位置上
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[ j + oldCap ] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

    /**
     * Replaces all linked nodes in bin at index for given hash unless
     * table is too small, in which case resizes instead.
     */
    final void treeifyBin(Node<K, V>[] tab, int hash) {
        int n, index;
        Node<K, V> e;
        if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY) {
            resize();
        }
        else if ((e = tab[ index = (n - 1) & hash ]) != null) {
            HashMap.TreeNode<K, V> hd = null, tl = null;
            do {
                HashMap.TreeNode<K, V> p = replacementTreeNode(e, null);
                if (tl == null) {
                    hd = p;
                }
                else {
                    p.prev = tl;
                    tl.next = p;
                }
                tl = p;
            }
            while ((e = e.next) != null);
            if ((tab[ index ] = hd) != null) {
                hd.treeify(tab);
            }
        }
    }

    /**
     *
     */
    final Node<K, V> removeNode(int hash, Object key, Object value, boolean matchValue, boolean movable) {
        Node<K, V>[] tab;
        Node<K, V> p;
        int n, index;
        if ((tab = table) != null && (n = tab.length) > 0 && (p = tab[ index = (n - 1) & hash ]) != null) {
            Node<K, V> node = null, e;
            K k;
            V v;
            if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k)))) {
                node = p;
            }
            else if ((e = p.next) != null) {
                if (p instanceof HashMap.TreeNode) {
                    node = ((HashMap.TreeNode<K, V>) p).getTreeNode(hash, key);
                }
                else {
                    do {
                        if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                            node = e;
                            break;
                        }
                        p = e;
                    }
                    while ((e = e.next) != null);
                }
            }
            if (node != null && (!matchValue || (v = node.value) == value || (value != null && value.equals(v)))) {
                if (node instanceof HashMap.TreeNode) {
                    ((HashMap.TreeNode<K, V>) node).removeTreeNode(this, tab, movable);
                }
                else if (node == p) {
                    tab[ index ] = node.next;
                }
                else {
                    p.next = node.next;
                }
                ++modCount;
                --size;
                afterNodeRemoval(node);
                return node;
            }
        }
        return null;
    }
    /*-------------------------重写Map的抽象方法--------------------------------*/

    /**
     * hashMap长度
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * hashMap是否为空
     */
    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 根据key获取value
     */
    @Override
    public V get(Object key) {
        Node<K, V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    /**
     * 是否包含key
     */
    @Override
    public boolean containsKey(Object key) {
        return getNode(hash(key), key) != null;
    }

    /**
     * 添加
     */
    @Override
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * 添加集合
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * 删除
     */
    @Override
    public V remove(Object key) {
        Node<K, V> e;
        return (e = removeNode(hash(key), key, null, false, true)) == null ? null : e.value;
    }

    /**
     * 清除集合
     */
    @Override
    public void clear() {
        Node<K, V>[] tab;
        modCount++;
        if ((tab = table) != null && size > 0) {
            size = 0;
            for (int i = 0; i < tab.length; ++i) {
                tab[ i ] = null;
            }
        }
    }

    /**
     * 是否包含value
     */
    @Override
    public boolean containsValue(Object value) {
        Node<K, V>[] tab;
        V v;
        if ((tab = table) != null && size > 0) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                    if ((v = e.value) == value || (value != null && value.equals(v))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*--------------------------------三种视图---------------------------------*/

    /**
     * key集合
     */
    @Override
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    final class KeySet extends AbstractSet<K> {

        @Override
        public final int size() {
            return size;
        }

        @Override
        public final void clear() {
            HashMap.this.clear();
        }

        @Override
        public final Iterator<K> iterator() {
            return new HashMap.KeyIterator();
        }

        @Override
        public final boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }

        @Override
        public final Spliterator<K> spliterator() {
            return new HashMap.KeySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        @Override
        public final void forEach(Consumer<? super K> action) {
            Node<K, V>[] tab;
            if (action == null) {
                throw new NullPointerException();
            }
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                        action.accept(e.key);
                    }
                }
                if (modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    /**
     * value集合
     */
    @Override
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new HashMap.Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {

        @Override
        public final int size() {
            return size;
        }

        @Override
        public final void clear() {
            HashMap.this.clear();
        }

        @Override
        public final Iterator<V> iterator() {
            return new HashMap.ValueIterator();
        }

        @Override
        public final boolean contains(Object o) {
            return containsValue(o);
        }

        @Override
        public final Spliterator<V> spliterator() {
            return new HashMap.ValueSpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        @Override
        public final void forEach(Consumer<? super V> action) {
            Node<K, V>[] tab;
            if (action == null) {
                throw new NullPointerException();
            }
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                        action.accept(e.value);
                    }
                }
                if (modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    /**
     * entrySet集合
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {

        @Override
        public final int size() {
            return size;
        }

        @Override
        public final void clear() {
            HashMap.this.clear();
        }

        @Override
        public final Iterator<Map.Entry<K, V>> iterator() {
            return new HashMap.EntryIterator();
        }

        @Override
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object key = e.getKey();
            Node<K, V> candidate = getNode(hash(key), key);
            return candidate != null && candidate.equals(e);
        }

        @Override
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }

        @Override
        public final Spliterator<Map.Entry<K, V>> spliterator() {
            return new HashMap.EntrySpliterator<>(HashMap.this, 0, -1, 0, 0);
        }

        @Override
        public final void forEach(Consumer<? super Map.Entry<K, V>> action) {
            Node<K, V>[] tab;
            if (action == null) {
                throw new NullPointerException();
            }
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (int i = 0; i < tab.length; ++i) {
                    for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                        action.accept(e);
                    }
                }
                if (modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }
    }

    /*------------------------------重写Map的抽象方法------------------------------*/

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        Node<K, V> e;
        return (e = getNode(hash(key), key)) == null ? defaultValue : e.value;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true, true);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true) != null;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Node<K, V> e;
        V v;
        if ((e = getNode(hash(key), key)) != null && ((v = e.value) == oldValue || (v != null && v.equals(oldValue)))) {
            e.value = newValue;
            afterNodeAccess(e);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Node<K, V> e;
        if ((e = getNode(hash(key), key)) != null) {
            V oldValue = e.value;
            e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
        return null;
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        HashMap.TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0) {
            n = (tab = resize()).length;
        }
        if ((first = tab[ i = (n - 1) & hash ]) != null) {
            if (first instanceof HashMap.TreeNode) {
                old = (t = (HashMap.TreeNode<K, V>) first).getTreeNode(hash, key);
            }
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                }
                while ((e = e.next) != null);
            }
            V oldValue;
            if (old != null && (oldValue = old.value) != null) {
                afterNodeAccess(old);
                return oldValue;
            }
        }
        V v = mappingFunction.apply(key);
        if (v == null) {
            return null;
        }
        else if (old != null) {
            old.value = v;
            afterNodeAccess(old);
            return v;
        }
        else if (t != null) {
            t.putTreeVal(this, tab, hash, key, v);
        }
        else {
            tab[ i ] = newNode(hash, key, v, first);
            if (binCount >= TREEIFY_THRESHOLD - 1) {
                treeifyBin(tab, hash);
            }
        }
        ++modCount;
        ++size;
        afterNodeInsertion(true);
        return v;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new NullPointerException();
        }
        Node<K, V> e;
        V oldValue;
        int hash = hash(key);
        if ((e = getNode(hash, key)) != null && (oldValue = e.value) != null) {
            V v = remappingFunction.apply(key, oldValue);
            if (v != null) {
                e.value = v;
                afterNodeAccess(e);
                return v;
            }
            else {
                removeNode(hash, key, null, false, true);
            }
        }
        return null;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        HashMap.TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0) {
            n = (tab = resize()).length;
        }
        if ((first = tab[ i = (n - 1) & hash ]) != null) {
            if (first instanceof HashMap.TreeNode) {
                old = (t = (HashMap.TreeNode<K, V>) first).getTreeNode(hash, key);
            }
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                }
                while ((e = e.next) != null);
            }
        }
        V oldValue = (old == null) ? null : old.value;
        V v = remappingFunction.apply(key, oldValue);
        if (old != null) {
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            else {
                removeNode(hash, key, null, false, true);
            }
        }
        else if (v != null) {
            if (t != null) {
                t.putTreeVal(this, tab, hash, key, v);
            }
            else {
                tab[ i ] = newNode(hash, key, v, first);
                if (binCount >= TREEIFY_THRESHOLD - 1) {
                    treeifyBin(tab, hash);
                }
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return v;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (value == null) {
            throw new NullPointerException();
        }
        if (remappingFunction == null) {
            throw new NullPointerException();
        }
        int hash = hash(key);
        Node<K, V>[] tab;
        Node<K, V> first;
        int n, i;
        int binCount = 0;
        HashMap.TreeNode<K, V> t = null;
        Node<K, V> old = null;
        if (size > threshold || (tab = table) == null || (n = tab.length) == 0) {
            n = (tab = resize()).length;
        }
        if ((first = tab[ i = (n - 1) & hash ]) != null) {
            if (first instanceof HashMap.TreeNode) {
                old = (t = (HashMap.TreeNode<K, V>) first).getTreeNode(hash, key);
            }
            else {
                Node<K, V> e = first;
                K k;
                do {
                    if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k)))) {
                        old = e;
                        break;
                    }
                    ++binCount;
                }
                while ((e = e.next) != null);
            }
        }
        if (old != null) {
            V v;
            if (old.value != null) {
                v = remappingFunction.apply(old.value, value);
            }
            else {
                v = value;
            }
            if (v != null) {
                old.value = v;
                afterNodeAccess(old);
            }
            else {
                removeNode(hash, key, null, false, true);
            }
            return v;
        }
        if (value != null) {
            if (t != null) {
                t.putTreeVal(this, tab, hash, key, value);
            }
            else {
                tab[ i ] = newNode(hash, key, value, first);
                if (binCount >= TREEIFY_THRESHOLD - 1) {
                    treeifyBin(tab, hash);
                }
            }
            ++modCount;
            ++size;
            afterNodeInsertion(true);
        }
        return value;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Node<K, V>[] tab;
        if (action == null) {
            throw new NullPointerException();
        }
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                    action.accept(e.key, e.value);
                }
            }
            if (modCount != mc) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Node<K, V>[] tab;
        if (function == null) {
            throw new NullPointerException();
        }
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                    e.value = function.apply(e.key, e.value);
                }
            }
            if (modCount != mc) {
                throw new ConcurrentModificationException();
            }
        }
    }

    /* ---------------------------重写serializeable方法--------------------------------- */
    // Cloning and serialization

    /**
     * Returns a shallow copy of this <tt>HashMap</tt> instance: the keys and
     * values themselves are not cloned.
     * @return a shallow copy of this map
     */
    @Override
    public Object clone() {
        HashMap<K, V> result;
        try {
            result = (HashMap<K, V>) super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // These methods are also used when serializing HashSets
    final float loadFactor() {
        return loadFactor;
    }

    final int capacity() {
        return (table != null) ? table.length : (threshold > 0) ? threshold : DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance to a stream (i.e.,
     * serialize it).
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     * bucket array) is emitted (int), followed by the
     * <i>size</i> (an int, the number of key-value
     * mappings), followed by the key (Object) and value (Object)
     * for each key-value mapping.  The key-value mappings are
     * emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s) throws IOException {
        int buckets = capacity();
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }

    /**
     * Reconstitute the {@code HashMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s) throws IOException, ClassNotFoundException {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        reinitialize();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " + loadFactor);
        }
        s.readInt();                // Read and ignore number of buckets
        int mappings = s.readInt(); // Read number of mappings (size)
        if (mappings < 0) {
            throw new InvalidObjectException("Illegal mappings count: " + mappings);
        }
        else if (mappings > 0) { // (if zero, use defaults)
            // Size the table using given load factor only if within
            // range of 0.25...4.0
            float lf = Math.min(Math.max(0.25f, loadFactor), 4.0f);
            float fc = (float) mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                    DEFAULT_INITIAL_CAPACITY :
                    (fc >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : tableSizeFor((int) fc));
            float ft = (float) cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ? (int) ft : Integer.MAX_VALUE);

            // Check Map.Entry[].class since it's the nearest public type to
            // what we're actually creating.
            SharedSecrets.getJavaOISAccess().checkArray(s, Map.Entry[].class, cap);
            Node<K, V>[] tab = (Node<K, V>[]) new Node[ cap ];
            table = tab;

            // Read the keys and values, and put the mappings in the HashMap
            for (int i = 0; i < mappings; i++) {
                K key = (K) s.readObject();
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false, false);
            }
        }
    }

    /* ------------------------------迭代器 iterators------------------------------ */

    abstract class HashIterator {

        Node<K, V> next;        // next entry to return

        Node<K, V> current;     // current entry

        int expectedModCount;  // for fast-fail

        int index;             // current slot

        HashIterator() {
            expectedModCount = modCount;
            Node<K, V>[] t = table;
            current = next = null;
            index = 0;
            if (t != null && size > 0) { // advance to first entry
                do {
                }
                while (index < t.length && (next = t[ index++ ]) == null);
            }
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Node<K, V> nextNode() {
            Node<K, V>[] t;
            Node<K, V> e = next;
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            if (e == null) {
                throw new NoSuchElementException();
            }
            if ((next = (current = e).next) == null && (t = table) != null) {
                do {
                }
                while (index < t.length && (next = t[ index++ ]) == null);
            }
            return e;
        }

        public final void remove() {
            Node<K, V> p = current;
            if (p == null) {
                throw new IllegalStateException();
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            current = null;
            K key = p.key;
            removeNode(hash(key), key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class KeyIterator extends HashIterator implements Iterator<K> {

        @Override
        public final K next() {
            return nextNode().key;
        }
    }

    final class ValueIterator extends HashIterator implements Iterator<V> {

        @Override
        public final V next() {
            return nextNode().value;
        }
    }

    final class EntryIterator extends HashIterator implements Iterator<Map.Entry<K, V>> {

        @Override
        public final Map.Entry<K, V> next() {
            return nextNode();
        }
    }

    /* ---------------------------spliterators迭代器----------------------------- */

    static class HashMapSpliterator<K, V> {

        final HashMap<K, V> map;

        Node<K, V> current;          // current node

        int index;                  // current index, modified on advance/split

        int fence;                  // one past last index

        int est;                    // size estimate

        int expectedModCount;       // for comodification checks

        HashMapSpliterator(HashMap<K, V> m, int origin, int fence, int est, int expectedModCount) {
            map = m;
            index = origin;
            this.fence = fence;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getFence() { // initialize fence and size on first use
            int hi;
            if ((hi = fence) < 0) {
                HashMap<K, V> m = map;
                est = m.size;
                expectedModCount = m.modCount;
                Node<K, V>[] tab = m.table;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            return hi;
        }

        public final long estimateSize() {
            getFence(); // force init
            return (long) est;
        }
    }

    static final class KeySpliterator<K, V> extends HashMap.HashMapSpliterator<K, V> implements Spliterator<K> {

        KeySpliterator(HashMap<K, V> m, int origin, int fence, int est, int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        @Override
        public HashMap.KeySpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ?
                    null :
                    new HashMap.KeySpliterator<>(map, lo, index = mid, est >>>= 1, expectedModCount);
        }

        @Override
        public void forEachRemaining(Consumer<? super K> action) {
            int i, hi, mc;
            if (action == null) {
                throw new NullPointerException();
            }
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else {
                mc = expectedModCount;
            }
            if (tab != null && tab.length >= hi && (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[ i++ ];
                    }
                    else {
                        action.accept(p.key);
                        p = p.next;
                    }
                }
                while (p != null || i < hi);
                if (m.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super K> action) {
            int hi;
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[ index++ ];
                    }
                    else {
                        K k = current.key;
                        current = current.next;
                        action.accept(k);
                        if (map.modCount != expectedModCount) {
                            throw new ConcurrentModificationException();
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) | Spliterator.DISTINCT;
        }
    }

    static final class ValueSpliterator<K, V> extends HashMap.HashMapSpliterator<K, V> implements Spliterator<V> {

        ValueSpliterator(HashMap<K, V> m, int origin, int fence, int est, int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        @Override
        public HashMap.ValueSpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ?
                    null :
                    new HashMap.ValueSpliterator<>(map, lo, index = mid, est >>>= 1, expectedModCount);
        }

        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            int i, hi, mc;
            if (action == null) {
                throw new NullPointerException();
            }
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else {
                mc = expectedModCount;
            }
            if (tab != null && tab.length >= hi && (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[ i++ ];
                    }
                    else {
                        action.accept(p.value);
                        p = p.next;
                    }
                }
                while (p != null || i < hi);
                if (m.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super V> action) {
            int hi;
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[ index++ ];
                    }
                    else {
                        V v = current.value;
                        current = current.next;
                        action.accept(v);
                        if (map.modCount != expectedModCount) {
                            throw new ConcurrentModificationException();
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0);
        }
    }

    static final class EntrySpliterator<K, V> extends HashMap.HashMapSpliterator<K, V>
            implements Spliterator<Map.Entry<K, V>> {

        EntrySpliterator(HashMap<K, V> m, int origin, int fence, int est, int expectedModCount) {
            super(m, origin, fence, est, expectedModCount);
        }

        @Override
        public HashMap.EntrySpliterator<K, V> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid || current != null) ?
                    null :
                    new HashMap.EntrySpliterator<>(map, lo, index = mid, est >>>= 1, expectedModCount);
        }

        @Override
        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            int i, hi, mc;
            if (action == null) {
                throw new NullPointerException();
            }
            HashMap<K, V> m = map;
            Node<K, V>[] tab = m.table;
            if ((hi = fence) < 0) {
                mc = expectedModCount = m.modCount;
                hi = fence = (tab == null) ? 0 : tab.length;
            }
            else {
                mc = expectedModCount;
            }
            if (tab != null && tab.length >= hi && (i = index) >= 0 && (i < (index = hi) || current != null)) {
                Node<K, V> p = current;
                current = null;
                do {
                    if (p == null) {
                        p = tab[ i++ ];
                    }
                    else {
                        action.accept(p);
                        p = p.next;
                    }
                }
                while (p != null || i < hi);
                if (m.modCount != mc) {
                    throw new ConcurrentModificationException();
                }
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
            int hi;
            if (action == null) {
                throw new NullPointerException();
            }
            Node<K, V>[] tab = map.table;
            if (tab != null && tab.length >= (hi = getFence()) && index >= 0) {
                while (current != null || index < hi) {
                    if (current == null) {
                        current = tab[ index++ ];
                    }
                    else {
                        Node<K, V> e = current;
                        current = current.next;
                        action.accept(e);
                        if (map.modCount != expectedModCount) {
                            throw new ConcurrentModificationException();
                        }
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public int characteristics() {
            return (fence < 0 || est == map.size ? Spliterator.SIZED : 0) | Spliterator.DISTINCT;
        }
    }

    /* -----------------------------Node相关方法------------------------- */

    /**
     * 创建Node节点
     */
    Node<K, V> newNode(int hash, K key, V value, Node<K, V> next) {
        return new Node<>(hash, key, value, next);
    }

    // For conversion from TreeNodes to plain nodes
    Node<K, V> replacementNode(Node<K, V> p, Node<K, V> next) {
        return new Node<>(p.hash, p.key, p.value, next);
    }

    // Create a tree bin node
    TreeNode<K, V> newTreeNode(int hash, K key, V value, Node<K, V> next) {
        return new TreeNode<>(hash, key, value, next);
    }

    // For treeifyBin
    TreeNode<K, V> replacementTreeNode(Node<K, V> p, Node<K, V> next) {
        return new TreeNode<>(p.hash, p.key, p.value, next);
    }

    /**
     * Reset to initial default state.  Called by clone and readObject.
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }

    // Callbacks to allow LinkedHashMap post-actions
    void afterNodeAccess(Node<K, V> p) {
    }

    void afterNodeInsertion(boolean evict) {
    }

    void afterNodeRemoval(Node<K, V> p) {
    }

    // Called only from writeObject, to ensure compatible ordering.
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        Node<K, V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (int i = 0; i < tab.length; ++i) {
                for (Node<K, V> e = tab[ i ]; e != null; e = e.next) {
                    s.writeObject(e.key);
                    s.writeObject(e.value);
                }
            }
        }
    }

    /*--------------------------------数据结构 红黑树-------------------------------*/

    static final class TreeNode<K, V> extends LinkedHashMap.Entry<K, V> {

        TreeNode<K, V> parent;

        TreeNode<K, V> left;

        TreeNode<K, V> right;

        TreeNode<K, V> prev;

        boolean red;

        TreeNode(int hash, K key, V val, Node<K, V> next) {
            super(hash, key, val, next);
        }

        /**
         * Returns root of tree containing this node.
         */
        final TreeNode<K, V> root() {
            for (TreeNode<K, V> r = this, p; ; ) {
                if ((p = r.parent) == null) {
                    return r;
                }
                r = p;
            }
        }

        /**
         * Ensures that the given root is the first node of its bin.
         */
        static <K, V> void moveRootToFront(Node<K, V>[] tab, HashMap.TreeNode<K, V> root) {
            int n;
            if (root != null && tab != null && (n = tab.length) > 0) {
                int index = (n - 1) & root.hash;
                HashMap.TreeNode<K, V> first = (HashMap.TreeNode<K, V>) tab[ index ];
                if (root != first) {
                    Node<K, V> rn;
                    tab[ index ] = root;
                    HashMap.TreeNode<K, V> rp = root.prev;
                    if ((rn = root.next) != null) {
                        ((HashMap.TreeNode<K, V>) rn).prev = rp;
                    }
                    if (rp != null) {
                        rp.next = rn;
                    }
                    if (first != null) {
                        first.prev = root;
                    }
                    root.next = first;
                    root.prev = null;
                }
                assert checkInvariants(root);
            }
        }

        /**
         * Finds the node starting at root p with the given hash and key.
         * The kc argument caches comparableClassFor(key) upon first use
         * comparing keys.
         */
        final TreeNode<K, V> find(int h, Object k, Class<?> kc) {
            HashMap.TreeNode<K, V> p = this;
            do {
                int ph, dir;
                K pk;
                TreeNode<K, V> pl = p.left, pr = p.right, q;
                if ((ph = p.hash) > h) {
                    p = pl;
                }
                else if (ph < h) {
                    p = pr;
                }
                else if ((pk = p.key) == k || (k != null && k.equals(pk))) {
                    return p;
                }
                else if (pl == null) {
                    p = pr;
                }
                else if (pr == null) {
                    p = pl;
                }
                else if ((kc != null || (kc = comparableClassFor(k)) != null)
                        && (dir = compareComparables(kc, k, pk)) != 0) {
                    p = (dir < 0) ? pl : pr;
                }
                else if ((q = pr.find(h, k, kc)) != null) {
                    return q;
                }
                else {
                    p = pl;
                }
            }
            while (p != null);
            return null;
        }

        /**
         * Calls find for root node.
         */
        final TreeNode<K, V> getTreeNode(int h, Object k) {
            return ((parent != null) ? root() : this).find(h, k, null);
        }

        /**
         * Tie-breaking utility for ordering insertions when equal
         * hashCodes and non-comparable. We don't require a total
         * order, just a consistent insertion rule to maintain
         * equivalence across rebalancings. Tie-breaking further than
         * necessary simplifies testing a bit.
         */
        static int tieBreakOrder(Object a, Object b) {
            int d;
            if (a == null || b == null || (d = a.getClass().getName().
                    compareTo(b.getClass().getName())) == 0) {
                d = (System.identityHashCode(a) <= System.identityHashCode(b) ? -1 : 1);
            }
            return d;
        }

        /**
         * Forms tree of the nodes linked from this node.
         * @return root of tree
         */
        final void treeify(Node<K, V>[] tab) {
            TreeNode<K, V> root = null;
            for (TreeNode<K, V> x = this, next; x != null; x = next) {
                next = (TreeNode<K, V>) x.next;
                x.left = x.right = null;
                if (root == null) {
                    x.parent = null;
                    x.red = false;
                    root = x;
                }
                else {
                    K k = x.key;
                    int h = x.hash;
                    Class<?> kc = null;
                    for (TreeNode<K, V> p = root; ; ) {
                        int dir, ph;
                        K pk = p.key;
                        if ((ph = p.hash) > h) {
                            dir = -1;
                        }
                        else if (ph < h) {
                            dir = 1;
                        }
                        else if ((kc == null && (kc = comparableClassFor(k)) == null)
                                || (dir = compareComparables(kc, k, pk)) == 0) {
                            dir = tieBreakOrder(k, pk);
                        }

                        TreeNode<K, V> xp = p;
                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
                            x.parent = xp;
                            if (dir <= 0) {
                                xp.left = x;
                            }
                            else {
                                xp.right = x;
                            }
                            root = balanceInsertion(root, x);
                            break;
                        }
                    }
                }
            }
            moveRootToFront(tab, root);
        }

        /**
         * Returns a list of non-TreeNodes replacing those linked from
         * this node.
         */
        final Node<K, V> untreeify(HashMap<K, V> map) {
            Node<K, V> hd = null, tl = null;
            for (Node<K, V> q = this; q != null; q = q.next) {
                Node<K, V> p = map.replacementNode(q, null);
                if (tl == null) {
                    hd = p;
                }
                else {
                    tl.next = p;
                }
                tl = p;
            }
            return hd;
        }

        /**
         * Tree version of putVal.
         */
        final HashMap.TreeNode<K, V> putTreeVal(HashMap<K, V> map, Node<K, V>[] tab, int h, K k, V v) {
            Class<?> kc = null;
            boolean searched = false;
            HashMap.TreeNode<K, V> root = (parent != null) ? root() : this;
            for (HashMap.TreeNode<K, V> p = root; ; ) {
                int dir, ph;
                K pk;
                if ((ph = p.hash) > h) {
                    dir = -1;
                }
                else if (ph < h) {
                    dir = 1;
                }
                else if ((pk = p.key) == k || (k != null && k.equals(pk))) {
                    return p;
                }
                else if ((kc == null && (kc = comparableClassFor(k)) == null)
                        || (dir = compareComparables(kc, k, pk)) == 0) {
                    if (!searched) {
                        HashMap.TreeNode<K, V> q, ch;
                        searched = true;
                        if (((ch = p.left) != null && (q = ch.find(h, k, kc)) != null) || ((ch = p.right) != null
                                && (q = ch.find(h, k, kc)) != null)) {
                            return q;
                        }
                    }
                    dir = tieBreakOrder(k, pk);
                }

                HashMap.TreeNode<K, V> xp = p;
                if ((p = (dir <= 0) ? p.left : p.right) == null) {
                    Node<K, V> xpn = xp.next;
                    HashMap.TreeNode<K, V> x = map.newTreeNode(h, k, v, xpn);
                    if (dir <= 0) {
                        xp.left = x;
                    }
                    else {
                        xp.right = x;
                    }
                    xp.next = x;
                    x.parent = x.prev = xp;
                    if (xpn != null) {
                        ((HashMap.TreeNode<K, V>) xpn).prev = x;
                    }
                    moveRootToFront(tab, balanceInsertion(root, x));
                    return null;
                }
            }
        }

        /**
         * Removes the given node, that must be present before this call.
         * This is messier than typical red-black deletion code because we
         * cannot swap the contents of an interior node with a leaf
         * successor that is pinned by "next" pointers that are accessible
         * independently during traversal. So instead we swap the tree
         * linkages. If the current tree appears to have too few nodes,
         * the bin is converted back to a plain bin. (The test triggers
         * somewhere between 2 and 6 nodes, depending on tree structure).
         */
        final void removeTreeNode(HashMap<K, V> map, Node<K, V>[] tab, boolean movable) {
            int n;
            if (tab == null || (n = tab.length) == 0) {
                return;
            }
            int index = (n - 1) & hash;
            HashMap.TreeNode<K, V> first = (HashMap.TreeNode<K, V>) tab[ index ], root = first, rl;
            HashMap.TreeNode<K, V> succ = (HashMap.TreeNode<K, V>) next, pred = prev;
            if (pred == null) {
                tab[ index ] = first = succ;
            }
            else {
                pred.next = succ;
            }
            if (succ != null) {
                succ.prev = pred;
            }
            if (first == null) {
                return;
            }
            if (root.parent != null) {
                root = root.root();
            }
            if (root == null || root.right == null || (rl = root.left) == null || rl.left == null) {
                tab[ index ] = first.untreeify(map);  // too small
                return;
            }
            HashMap.TreeNode<K, V> p = this, pl = left, pr = right, replacement;
            if (pl != null && pr != null) {
                HashMap.TreeNode<K, V> s = pr, sl;
                while ((sl = s.left) != null) // find successor
                {
                    s = sl;
                }
                boolean c = s.red;
                s.red = p.red;
                p.red = c; // swap colors
                HashMap.TreeNode<K, V> sr = s.right;
                HashMap.TreeNode<K, V> pp = p.parent;
                if (s == pr) { // p was s's direct parent
                    p.parent = s;
                    s.right = p;
                }
                else {
                    HashMap.TreeNode<K, V> sp = s.parent;
                    if ((p.parent = sp) != null) {
                        if (s == sp.left) {
                            sp.left = p;
                        }
                        else {
                            sp.right = p;
                        }
                    }
                    if ((s.right = pr) != null) {
                        pr.parent = s;
                    }
                }
                p.left = null;
                if ((p.right = sr) != null) {
                    sr.parent = p;
                }
                if ((s.left = pl) != null) {
                    pl.parent = s;
                }
                if ((s.parent = pp) == null) {
                    root = s;
                }
                else if (p == pp.left) {
                    pp.left = s;
                }
                else {
                    pp.right = s;
                }
                if (sr != null) {
                    replacement = sr;
                }
                else {
                    replacement = p;
                }
            }
            else if (pl != null) {
                replacement = pl;
            }
            else if (pr != null) {
                replacement = pr;
            }
            else {
                replacement = p;
            }
            if (replacement != p) {
                HashMap.TreeNode<K, V> pp = replacement.parent = p.parent;
                if (pp == null) {
                    root = replacement;
                }
                else if (p == pp.left) {
                    pp.left = replacement;
                }
                else {
                    pp.right = replacement;
                }
                p.left = p.right = p.parent = null;
            }

            HashMap.TreeNode<K, V> r = p.red ? root : balanceDeletion(root, replacement);

            if (replacement == p) {  // detach
                HashMap.TreeNode<K, V> pp = p.parent;
                p.parent = null;
                if (pp != null) {
                    if (p == pp.left) {
                        pp.left = null;
                    }
                    else if (p == pp.right) {
                        pp.right = null;
                    }
                }
            }
            if (movable) {
                moveRootToFront(tab, r);
            }
        }

        /**
         * 拆分红黑树
         * @param map the map
         * @param tab the table for recording bin heads
         * @param index the index of the table being split
         * @param bit the bit of hash to split on
         */
        final void split(HashMap<K, V> map, Node<K, V>[] tab, int index, int bit) {
            HashMap.TreeNode<K, V> b = this;
            // Relink into lo and hi lists, preserving order
            HashMap.TreeNode<K, V> loHead = null, loTail = null;
            HashMap.TreeNode<K, V> hiHead = null, hiTail = null;
            int lc = 0, hc = 0;
            for (HashMap.TreeNode<K, V> e = b, next; e != null; e = next) {
                next = (HashMap.TreeNode<K, V>) e.next;
                e.next = null;
                if ((e.hash & bit) == 0) {
                    if ((e.prev = loTail) == null) {
                        loHead = e;
                    }
                    else {
                        loTail.next = e;
                    }
                    loTail = e;
                    ++lc;
                }
                else {
                    if ((e.prev = hiTail) == null) {
                        hiHead = e;
                    }
                    else {
                        hiTail.next = e;
                    }
                    hiTail = e;
                    ++hc;
                }
            }

            if (loHead != null) {
                if (lc <= UNTREEIFY_THRESHOLD) {
                    tab[ index ] = loHead.untreeify(map);
                }
                else {
                    tab[ index ] = loHead;
                    if (hiHead != null) // (else is already treeified)
                    {
                        loHead.treeify(tab);
                    }
                }
            }
            if (hiHead != null) {
                if (hc <= UNTREEIFY_THRESHOLD) {
                    tab[ index + bit ] = hiHead.untreeify(map);
                }
                else {
                    tab[ index + bit ] = hiHead;
                    if (loHead != null) {
                        hiHead.treeify(tab);
                    }
                }
            }
        }

        /* ------------------------------------------------------------ */
        // Red-black tree methods, all adapted from CLR

        static <K, V> HashMap.TreeNode<K, V> rotateLeft(HashMap.TreeNode<K, V> root, HashMap.TreeNode<K, V> p) {
            HashMap.TreeNode<K, V> r, pp, rl;
            if (p != null && (r = p.right) != null) {
                if ((rl = p.right = r.left) != null) {
                    rl.parent = p;
                }
                if ((pp = r.parent = p.parent) == null) {
                    (root = r).red = false;
                }
                else if (pp.left == p) {
                    pp.left = r;
                }
                else {
                    pp.right = r;
                }
                r.left = p;
                p.parent = r;
            }
            return root;
        }

        static <K, V> HashMap.TreeNode<K, V> rotateRight(HashMap.TreeNode<K, V> root, HashMap.TreeNode<K, V> p) {
            HashMap.TreeNode<K, V> l, pp, lr;
            if (p != null && (l = p.left) != null) {
                if ((lr = p.left = l.right) != null) {
                    lr.parent = p;
                }
                if ((pp = l.parent = p.parent) == null) {
                    (root = l).red = false;
                }
                else if (pp.right == p) {
                    pp.right = l;
                }
                else {
                    pp.left = l;
                }
                l.right = p;
                p.parent = l;
            }
            return root;
        }

        static <K, V> HashMap.TreeNode<K, V> balanceInsertion(HashMap.TreeNode<K, V> root, HashMap.TreeNode<K, V> x) {
            x.red = true;
            for (HashMap.TreeNode<K, V> xp, xpp, xppl, xppr; ; ) {
                if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                else if (!xp.red || (xpp = xp.parent) == null) {
                    return root;
                }
                if (xp == (xppl = xpp.left)) {
                    if ((xppr = xpp.right) != null && xppr.red) {
                        xppr.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    }
                    else {
                        if (x == xp.right) {
                            root = rotateLeft(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateRight(root, xpp);
                            }
                        }
                    }
                }
                else {
                    if (xppl != null && xppl.red) {
                        xppl.red = false;
                        xp.red = false;
                        xpp.red = true;
                        x = xpp;
                    }
                    else {
                        if (x == xp.left) {
                            root = rotateRight(root, x = xp);
                            xpp = (xp = x.parent) == null ? null : xp.parent;
                        }
                        if (xp != null) {
                            xp.red = false;
                            if (xpp != null) {
                                xpp.red = true;
                                root = rotateLeft(root, xpp);
                            }
                        }
                    }
                }
            }
        }

        static <K, V> HashMap.TreeNode<K, V> balanceDeletion(HashMap.TreeNode<K, V> root, HashMap.TreeNode<K, V> x) {
            for (HashMap.TreeNode<K, V> xp, xpl, xpr; ; ) {
                if (x == null || x == root) {
                    return root;
                }
                else if ((xp = x.parent) == null) {
                    x.red = false;
                    return x;
                }
                else if (x.red) {
                    x.red = false;
                    return root;
                }
                else if ((xpl = xp.left) == x) {
                    if ((xpr = xp.right) != null && xpr.red) {
                        xpr.red = false;
                        xp.red = true;
                        root = rotateLeft(root, xp);
                        xpr = (xp = x.parent) == null ? null : xp.right;
                    }
                    if (xpr == null) {
                        x = xp;
                    }
                    else {
                        HashMap.TreeNode<K, V> sl = xpr.left, sr = xpr.right;
                        if ((sr == null || !sr.red) && (sl == null || !sl.red)) {
                            xpr.red = true;
                            x = xp;
                        }
                        else {
                            if (sr == null || !sr.red) {
                                if (sl != null) {
                                    sl.red = false;
                                }
                                xpr.red = true;
                                root = rotateRight(root, xpr);
                                xpr = (xp = x.parent) == null ? null : xp.right;
                            }
                            if (xpr != null) {
                                xpr.red = (xp == null) ? false : xp.red;
                                if ((sr = xpr.right) != null) {
                                    sr.red = false;
                                }
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateLeft(root, xp);
                            }
                            x = root;
                        }
                    }
                }
                else { // symmetric
                    if (xpl != null && xpl.red) {
                        xpl.red = false;
                        xp.red = true;
                        root = rotateRight(root, xp);
                        xpl = (xp = x.parent) == null ? null : xp.left;
                    }
                    if (xpl == null) {
                        x = xp;
                    }
                    else {
                        HashMap.TreeNode<K, V> sl = xpl.left, sr = xpl.right;
                        if ((sl == null || !sl.red) && (sr == null || !sr.red)) {
                            xpl.red = true;
                            x = xp;
                        }
                        else {
                            if (sl == null || !sl.red) {
                                if (sr != null) {
                                    sr.red = false;
                                }
                                xpl.red = true;
                                root = rotateLeft(root, xpl);
                                xpl = (xp = x.parent) == null ? null : xp.left;
                            }
                            if (xpl != null) {
                                xpl.red = (xp == null) ? false : xp.red;
                                if ((sl = xpl.left) != null) {
                                    sl.red = false;
                                }
                            }
                            if (xp != null) {
                                xp.red = false;
                                root = rotateRight(root, xp);
                            }
                            x = root;
                        }
                    }
                }
            }
        }

        /**
         * Recursive invariant check
         */
        static <K, V> boolean checkInvariants(HashMap.TreeNode<K, V> t) {
            HashMap.TreeNode<K, V> tp = t.parent, tl = t.left, tr = t.right, tb = t.prev, tn = (HashMap.TreeNode<K, V>) t.next;
            if (tb != null && tb.next != t) {
                return false;
            }
            if (tn != null && tn.prev != t) {
                return false;
            }
            if (tp != null && t != tp.left && t != tp.right) {
                return false;
            }
            if (tl != null && (tl.parent != t || tl.hash > t.hash)) {
                return false;
            }
            if (tr != null && (tr.parent != t || tr.hash < t.hash)) {
                return false;
            }
            if (t.red && tl != null && tl.red && tr != null && tr.red) {
                return false;
            }
            if (tl != null && !checkInvariants(tl)) {
                return false;
            }
            if (tr != null && !checkInvariants(tr)) {
                return false;
            }
            return true;
        }
    }

}

