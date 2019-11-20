package com.xz.concurrent.test;

/**
 * @author xuanzhou
 * @date 2019/10/29 11:30
 */
public class ParkAndUnparkTest {

    public static void main(String[] args) {

        MThread mThread = new MThread(Thread.currentThread());

        mThread.start();
        System.out.println("before park");
        //获取许可
        java.util.concurrent.locks.LockSupport.park("Park");
        System.out.println("after park");

    }

}

class MThread extends Thread {

    private Object object;

    public MThread(Object object) {
        this.object = object;
    }

    @Override
    public void run() {
        System.out.println("before unpark");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //获取block
        System.out.println("Bloc info" + java.util.concurrent.locks.LockSupport.getBlocker((Thread) object));
        //释放许可
        java.util.concurrent.locks.LockSupport.unpark((Thread) object);

        //休眠50秒
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //再次获取Blocker
        System.out.println("Blocker info" + java.util.concurrent.locks.LockSupport.getBlocker((Thread) object));

        System.out.println("after unpark");
    }
}
