package com.xz.concurrent.locks;

import com.sun.istack.internal.NotNull;

/**
 * 读写锁
 * @author xuanzhou
 * @date 2019/11/21 14:51
 */
public interface ReadWriteLock {

    /**
     * 获取读锁
     * @return
     */
    @NotNull
    Lock readLock();

    /**
     * 获取写锁的抽象方法
     * @return
     */
    @NotNull
    Lock writeLock();
}

