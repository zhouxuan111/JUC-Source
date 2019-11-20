package com.xz.concurrent.atomic;

/**
 * StringBuffer和StringBuilder的性能产局
 * @author xuanzhou
 * @date 2019/10/25 9:38
 */
public class StringBufferAndStringBuilderTest {

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < 1000000; i++) {
            stringBuffer.append("CustomerName=周旋").append("&Phone=18829525953").append("hhhh").append("12312");
        }
        System.out.println(System.currentTimeMillis() - start);

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < 1000000; i++) {
            stringBuilder.append("CustomerName=周旋").append("&Phone=18829525953").append("hhhh").append("12312");
        }
        System.out.println(System.currentTimeMillis() - start);
    }
}
