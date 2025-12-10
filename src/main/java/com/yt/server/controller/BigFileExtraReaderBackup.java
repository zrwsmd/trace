package com.yt.server.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class BigFileExtraReaderBackup {

    private final Logger logger = LoggerFactory.getLogger(BigFileExtraReaderBackup.class);

    private MappedByteBuffer mappedByteBuffers;
    private FileInputStream inputStream;
    private FileChannel fileChannel;

    private int bufferCountIndex = 0;
    private int bufferCount;

    private int byteBufferSize;
    private byte[] byteBuffer;

    private String fileName;


    public BigFileExtraReaderBackup() {
    }

    Integer size = 100 * 1024 * 1024;

    public BigFileExtraReaderBackup(String fileName, Integer byteBufferSize) throws IOException {
        this.fileName = fileName;
        this.byteBufferSize = byteBufferSize;

    }


    public synchronized int read() {
        if (bufferCountIndex >= bufferCount) {
            return -1;
        }

        int limit = mappedByteBuffers.limit();
        int position = mappedByteBuffers.position();

        int realSize = 0;
        try {
            realSize = byteBufferSize;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (limit - position < byteBufferSize) {
            realSize = limit - position;
        }
        byteBuffer = new byte[realSize];
        mappedByteBuffers.get(byteBuffer);

//        if (realSize < byteBufferSize && bufferCountIndex < bufferCount) {
//            bufferCountIndex++;
//        }
        return realSize;
    }

    public void close() throws IOException {
        fileChannel.close();
        inputStream.close();

        mappedByteBuffers.clear();

        byteBuffer = null;
    }

    public synchronized byte[] getCurrentBytes() {
        return byteBuffer;
    }

    public void handle() throws IOException {
        try {
            this.inputStream = new FileInputStream(fileName);
            this.fileChannel = inputStream.getChannel();
            long fileSize = fileChannel.size();
            this.bufferCount = (int) Math.ceil((double) fileSize / (double) Integer.MAX_VALUE);
            int loopNum = (int) Math.ceil((double) fileSize / size);

            long preLength = 0;
            long startTime = System.currentTimeMillis();
            for (int j = 0; j < loopNum; j++) {
                if (j < loopNum - 1) {
                    mappedByteBuffers = fileChannel.map(FileChannel.MapMode.READ_ONLY, preLength, size);
                } else {
                    mappedByteBuffers = fileChannel.map(FileChannel.MapMode.READ_ONLY, preLength, fileSize - preLength);
                }
                int num = 0;
                while (read() != 0) {
//               byte[] bytes = getCurrentBytes();
//                for (byte aByte : bytes) {
//                    System.out.println(aByte);
//                    num++;
//                }
//                System.out.println(bytes);
                }
                preLength += size;
            }
            long endTime = System.currentTimeMillis();
            logger.info("花费了{}秒", (double) (endTime - startTime) / 1000);
        } finally {
            fileChannel.close();
            inputStream.close();
            mappedByteBuffers.clear();
            byteBuffer = null;
        }

    }


//    public static void main(String[] args) throws Exception {
//        // BigFileReader2 reader = new BigFileReader2("e://3.pdf", 81920000);
//        BigFileExtraReaderBackup reader = new BigFileExtraReaderBackup("d://Downloads//CentOS-7-x86_64-DVD-2009.iso", 2048);
//        long startTime = System.currentTimeMillis();
//        reader.handle();
////        byte[] bytes = reader.getCurrentBytes();
////        for (byte aByte : bytes) {
////            System.out.println(aByte);
////        }
//        //System.out.println("num= "+num);
////        while (reader.read() != -1) {
////            byte[] bytes = reader.getCurrentBytes();
////            System.out.println();
////
////        }
//
//        // reader.close();
//    }
}

