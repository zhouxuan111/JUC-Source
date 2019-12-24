package com.xz.concurrent.test;

import java.util.Random;

/**
 * @author xuanzhou
 * @date 2019/12/24 10:50
 */
public class RandomTest {

    public static void main(String[] args) {
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            System.out.println(random.nextInt(5));
        }
    }
}
