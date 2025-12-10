package com.yt.server;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server
 * @author:赵瑞文
 * @createTime:2024/9/10 17:04
 * @version:1.0
 */
public class Test2 {
    static HashSet<ByteWrapper> receivedDataHistory = new HashSet<>();

    public static void main(String[] args) {
//        List<Byte[]> list = new LinkedList<>();
//        Byte[] bytes1 = {11, 22, 33, 44, 55, 66, 77, 88, 99};
//        Byte[] bytes2 = {11, 96, 89, 77, 55, 35, 77, 88, 99};
//        Byte[] bytes3 = {11, 22, 33, 44, 55, 66, 77, 88, 99};
//        Byte[] bytes4 = {11, 22, 33, 44, 55, 78, 77, 109, 99};
//        byte[] bytes5 = {-105, 88, 77, 66, 55, 44, 33, 22, -23};
//        Byte[] bytes6 = {11, 22, 33, 44, 55, 66, 77, 88, 99};
//        list.add(bytes1);
//        list.add(bytes2);
//        list.add(bytes3);
//        list.add(bytes4);
//       // list.add(bytes5);
//        list.add(bytes6);
       // handleReceivedData(list);
//        System.out.println(receivedDataHistory.size());
//        for (ByteWrapper byteWrapper : receivedDataHistory) {
//            System.out.println(byteWrapper.toString());
//            System.out.println("--------------------------------");
//        }
//        String fileName = "video1.mp4,video2.mp4,video3.mp4";
//        final int length = fileName.getBytes(Charset.forName("GBK")).length;
//        System.out.println(length);
//        final String str = bytesToHexString(new byte[]{-105});
//        System.out.println(str);

 //      System.out.println("E9".equals(firstByteToHex(new byte[]{-47})));
//        ByteBuffer buffer = ByteBuffer.allocate(9);
//        String fileName = "photo.jpg";
//        final byte[] bytes = fileName.getBytes(Charset.forName("GBK"));
//        buffer.put(bytes);
//        System.out.println(bytesToHexString(bytes));
//        // 创建一个新的字节数组用于存储倒序后的数据
//        byte[] reversedBytes = new byte[bytes.length];
//        // 倒序复制字节数据
//        for (int i = 0; i < bytes.length; i++) {
//            reversedBytes[i] = bytes[bytes.length - 1 - i];
//        }
//        // 输出倒序后的字节数据的十六进制表示
//        System.out.println("Reversed hex string: " + bytesToHexString(reversedBytes));
//        String input = "d2y2jJt6500003";
//        String sixBytesHex = stringToSixBytesHexWithSpaces(input.substring(0, Math.min(input.length(), 6)));
//        System.out.println("Six Bytes Hex: " + sixBytesHex);
        // 测试不同的电量字符串
//        final String s = bytesToHexString(new byte[]{-103});
//        System.out.println(s);
       // System.out.printf("%02X %n", -16);
        System.out.println(parseIntToBCD(120));
        System.out.println(getCurrentDateTime());
    }
    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        final int MAX_LENGTH = 128;
        StringBuilder hexString = new StringBuilder();
        hexString.append(String.format("(len:%d)", bytes.length));
        hexString.append('[');

        int length = Math.min(bytes.length, MAX_LENGTH);
        for (int i = 0; i < length; i++) {
            hexString.append(String.format("%02X ", bytes[i]));
        }

        if (bytes.length > MAX_LENGTH) {
            hexString.append("... ");
        }

        hexString.append(']');
        return hexString.toString();
    }



    public static synchronized void handleReceivedData(List<Byte[]> data) {
        for (Byte[] single : data) {
            ByteWrapper dataWrapper = new ByteWrapper(single);
            if (!receivedDataHistory.contains(dataWrapper)) {
                receivedDataHistory.add(dataWrapper);
            }
        }


    }
    public static void reverseByteArray(byte[] array) {
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    public static byte[] hexStringToByteArray(String s) {
        int decimalValue = Integer.parseInt(s); // 将字符串转换为十进制数

        // 将十进制数转换为十六进制字符串
        String hexString = Integer.toHexString(decimalValue).toUpperCase();

        // 确保十六进制字符串长度为4位
        while (hexString.length() < 4) {
            hexString = "0" + hexString; // 在前面补零
        }

        // 创建字节数组
        byte[] data = new byte[2];
        for (int i = 0; i < hexString.length(); i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }

        reverseByteArray(data); // 反转字节数组
        return data;
    }


    public static String lastByteToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int lastByte = bytes[bytes.length - 1] & 0xFF; // 获取最后一个字节并转换为无符号
        return String.format("%02X", lastByte);
    }

    public static String firstByteToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int firstByte = bytes[0] & 0xFF; // 获取第一个字节并转换为无符号
        return String.format("%02X", firstByte);
    }

    public static String stringToSixBytesHexWithSpaces(String input) {
        byte[] bytes = new byte[6];
        byte[] inputBytes = input.getBytes(StandardCharsets.US_ASCII);

        // 如果输入的字节数小于6，则用0填充
        System.arraycopy(inputBytes, 0, bytes, 0, Math.min(inputBytes.length, 6));

        // 将字节转换为HEX格式，并在每个字节之间添加空格
        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            hexBuilder.append(String.format("%02X", bytes[i]));
            if (i < bytes.length - 1) {
                hexBuilder.append(" "); // 添加空格分隔符
            }
        }

        return hexBuilder.toString();
    }

    public static byte[] parseIntToBCD(int value) {
        String strValue = Integer.toString(value);
        int length = strValue.length();
        // 如果是奇数位，在前面补零
        if (length % 2 != 0) {
            strValue = "0" + strValue;
            length++;
        }
        byte[] bcdBytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int digit1 = strValue.charAt(i) - '0';
            int digit2 = strValue.charAt(i + 1) - '0';
            bcdBytes[i / 2] = (byte) ((digit1 << 4) + digit2);
        }
        return bcdBytes;
    }

    public static String getCurrentDateTime() {
        // 获取当前的日期和时间
        LocalDateTime now = LocalDateTime.now();
        // 定义日期时间格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 格式化日期时间
        String formattedDateTime = now.format(formatter);
        return formattedDateTime;
    }
}
