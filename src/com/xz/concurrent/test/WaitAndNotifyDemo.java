package com.xz.concurrent.test;

/**
 * @author xuanzhou
 * @date 2019/10/29 11:16
 */
public class WaitAndNotifyDemo {

    public static void main(String[] args) {
        MyThread myThread = new MyThread();
        synchronized (myThread) {
            myThread.start();
            try {
                Thread.sleep(3000);
                System.out.println("before wait");
                //阻塞主线程
                myThread.wait();
                System.out.println("after wait");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class MyThread extends Thread {

    @Override
    public void run() {
        synchronized (this) {
            System.out.println("before notify");
            notify();
            System.out.println("after notify");

        }
    }
}
