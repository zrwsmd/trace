package com.yt.server.controller;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class IoDemo {

//    public static void main(String[] args) throws IOException, EncoderException {
//
//
//
//
//
////            byte[] readFileToByteArray = FileUtils.readFileToByteArray("f://pdf文件//Apache Kafka源码剖析.pdf");
////            return Base64.encodeBase64String(readFileToByteArray);
//
//
//
//
//
////        byte[] bytes = FileUtils.readFileToByteArray(new File("f://pdf文件//Apache Kafka源码剖析.pdf"));
////        String encodeStr = Base64.encodeBase64String(bytes);
////
////
////        System.out.println("=======我是分割线=="+encodeStr.length()+"==========================================");
////
////        byte[] decodeBase64 = Base64.decodeBase64(encodeStr);
////        FileUtils.writeByteArrayToFile(new File("f://pdf文件//Apache Kafka源码剖析2.pdf"), decodeBase64);
//
//
//        //AcaaaabA
////        String str = "ABab";
////        BinaryCodec binaryCodec = new BinaryCodec();
////
////        // 将字符转换成二进制字符串表示（即0和1）
////        byte[] encodeResult = binaryCodec.encode(str.getBytes());
////        System.out.println(new String(encodeResult)); // 输出：01000001
//
//        //readFile("f://2.txt");
//        readByChannelTest3(1024);
//
//    }
    public static void readFile(String fileName){

        File file = new File(fileName);
        if(file.exists()){
            try {
                FileInputStream in = new FileInputStream(file);
                DataInputStream dis=new DataInputStream(in);

                byte[] itemBuf = new byte[1024];
                if(dis.read()!=-1){
                    //市场编码
                    dis.read(itemBuf, 0, 8);
                    String marketID =new String(itemBuf,0,8);

                    //市场名称
                    dis.read(itemBuf, 0, 20);//read方法读取一定长度之后，被读取的数据就从流中去掉了，所以下次读取仍然从 0开始
                    String marketName =new String(itemBuf,0,20);

                    //上一交易日日期
                    dis.read(itemBuf, 0, 8);
                    String lastTradingDay = new String(itemBuf,0,8);

                    //当前交易日日期
                    dis.read(itemBuf, 0, 8);
                    String curTradingDay = new String(itemBuf,0,8);

                    //交易状态
                    dis.read(itemBuf, 0, 1);
                    String marketStatus = new String(itemBuf,0,1);


//                //交易时段数
//                short tradePeriodNum = dis.readShort();

                    System.out.println("市场代码:"+ marketID);
                    System.out.println("市场名称:"+ marketName);
                    System.out.println("上一交易日日期:"+ lastTradingDay);
                    System.out.println("当前交易日日期:"+ curTradingDay);
                    System.out.println("交易状态:"+ marketStatus);
                }

            //    System.out.println("交易时段数:"+ tradePeriodNum);

            } catch (IOException e) {
                e.printStackTrace();
            }finally{
                //close
            }
        }
    }

    public static byte[] readFromByteFile(String pathname) throws IOException{
        File filename = new File(pathname);
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] temp = new byte[1024];
        int size = 0;
        while((size = in.read(temp)) != -1){
            out.write(temp, 0, size);
        }
        in.close();
        byte[] content = out.toByteArray();
        return content;
    }

    public static void readByChannelTest3(int allocate) throws IOException {
        long start = System.currentTimeMillis();

        RandomAccessFile fis = new RandomAccessFile(new File("f://2.txt"), "rw");
        FileChannel channel = fis.getChannel();
        long size = channel.size();

        // 构建一个只读的MappedByteBuffer
        MappedByteBuffer mappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);

        // 如果文件不大,可以选择一次性读取到数组
        // byte[] all = new byte[(int)size];
        // mappedByteBuffer.get(all, 0, (int)size);
        // 打印文件内容
        // System.out.println(new String(all));

        // 如果文件内容很大,可以循环读取,计算应该读取多少次
        byte[] bytes = new byte[allocate];
        long cycle = size / allocate;
        int mode = (int)(size % allocate);
        //byte[] eachBytes = new byte[allocate];
        for (int i = 0; i < cycle; i++) {
            // 每次读取allocate个字节
            ByteBuffer byteBuffer = mappedByteBuffer.get(bytes);
            System.out.println("读取到"+new String(byteBuffer.array()));

            // 打印文件内容,关闭打印速度会很快
            // System.out.print(new String(eachBytes));
        }
        if(mode > 0) {
            bytes = new byte[mode];
            ByteBuffer byteBuffer = mappedByteBuffer.get(bytes);
            System.out.println("读取到"+byteBuffer.get());

            // 打印文件内容,关闭打印速度会很快
            // System.out.print(new String(eachBytes));
        }

        // 关闭通道和文件流
        channel.close();
        fis.close();

        long end = System.currentTimeMillis();
        System.out.println(String.format("\n===>文件大小：%s 字节", size));
        System.out.println(String.format("===>读取并打印文件耗时：%s毫秒", end - start));
    }

//    public static void main(String[] args){
//        /**
//         * FileChannel
//         * 方式1：输入输出流
//         * 方式2：RandomAccessFile
//         */
//        try (FileChannel channel = new FileInputStream("f://pdf文件//《RocketMQ技术内幕》.pdf").getChannel()){
//            //准备缓冲区
//            ByteBuffer buffer = ByteBuffer.allocate(10);
//            while (true){
//                //1.从 channel 读取数据，向buffer写入
//                int len = channel.read(buffer);
//                System.out.println("读取到的字节数:"+len);
//                if(len == -1){  //判断内容是否读取完
//                    break;
//                }
//
//                //打印buffer中的内容
//                buffer.flip(); //2.切换至读模式
//                while (buffer.hasRemaining()){  //是否还有剩余未读数据
//                    byte b = buffer.get();    //3.读取数据内容
//                    System.out.println("实际字节:{}"+(char) b);
//                }
//                buffer.clear(); //4.切换到写模式
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//    }


}
