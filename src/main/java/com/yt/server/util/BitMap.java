package com.yt.server.util;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/2/7 17:03
 * @version:1.0
 */
public class BitMap {
    private byte[] bytes;

    public BitMap(byte[] bytes) {
        super();
        this.bytes = bytes;
    }

    public BitMap() {
        super();
    }

    public BitMap(int length) {
//        super();
//        int number = size / 8;// may waste a byte, which does not matter
        bytes = new byte[length%8==0 ? length/8 : length/8+1];
    }

    /**
     *
     * @param n
     *            n>=1
     */
    public void setBit(int n) {
        if (n <= 0)
            return;
        int index = -1;
        int offset = -1;
        if (0 == n % 8) {
            index = n / 8 - 1;
            offset = 7;
        } else {
            index = n / 8;
            offset = n % 8 - 1;
        }
        switch (offset) {
            case 0:
                bytes[index] = (byte)(bytes[index]|0x01);
                break;
            case 1:
                bytes[index] = (byte)(bytes[index]|0x02);
                break;
            case 2:
                bytes[index] = (byte)(bytes[index]|0x04);
                break;
            case 3:
                bytes[index] = (byte)(bytes[index]|0x08);
                break;
            case 4:
                bytes[index] = (byte)(bytes[index]|0x10);
                break;
            case 5:
                bytes[index] = (byte)(bytes[index]|0x20);
                break;
            case 6:
                bytes[index] = (byte)(bytes[index]|0x40);
                break;
            case 7:
                bytes[index] = (byte)(bytes[index]|0x80);
                break;
        }
    }

    public boolean get(int n){
        if (n <= 0)
            return false;
        int index = -1;
        int offset = -1;
        if (0 == n % 8) {
            index = n / 8 - 1;
            offset = 7;
        } else {
            index = n / 8;
            offset = n % 8 - 1;
        }
        switch (offset) {
            case 0:
                return (byte)(bytes[index]&0x01)!=0;//2^0
            case 1:
                return (byte)(bytes[index]&0x02)!=0;
            case 2:
                return (byte)(bytes[index]&0x04)!=0;
            case 3:
                return (byte)(bytes[index]&0x08)!=0;
            case 4:
                return (byte)(bytes[index]&0x10)!=0;
            case 5:
                return (byte)(bytes[index]&0x20)!=0;
            case 6:
                return (byte)(bytes[index]&0x40)!=0;
            case 7:
                return (byte)(bytes[index]&0x80)!=0;
        }
        return false;
    }

    public byte[] get(){
        return bytes;
    }

//    public static void main(String[] args) {
//        BitMap bMap = new BitMap(8);
//        bMap.setBit(0);
////        bMap.setBit(0);
////        bMap.setBit(1);
////        bMap.setBit(2);
////        bMap.setBit(0);
////        bMap.setBit(1);
////        bMap.setBit(0);
////        bMap.setBit(0);
////        final byte[] bytes1 = bMap.get();
////        System.out.println(bytes1);
//    }
}
