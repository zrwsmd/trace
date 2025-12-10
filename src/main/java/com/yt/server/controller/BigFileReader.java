package com.yt.server.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class BigFileReader {

    private MappedByteBuffer[] mappedByteBuffers;
    private FileInputStream inputStream;
    private FileChannel fileChannel;

    private int bufferCountIndex = 0;
    private int bufferCount;

    private int byteBufferSize;
    private byte[] byteBuffer;


    public BigFileReader() {
    }

    public BigFileReader(String fileName, Integer byteBufferSize) throws IOException {
        this.inputStream = new FileInputStream(fileName);
        this.fileChannel = inputStream.getChannel();
        long fileSize = fileChannel.size();
        this.bufferCount = (int) Math.ceil((double) fileSize / (double) Integer.MAX_VALUE);
        this.mappedByteBuffers = new MappedByteBuffer[bufferCount];
        this.byteBufferSize = byteBufferSize;

        long preLength = 0;
        //100000000
        long regionSize = Integer.MAX_VALUE;
        for (int i = 0; i < bufferCount; i++) {
            if (fileSize - preLength < Integer.MAX_VALUE) {
                regionSize = fileSize - preLength;
            }
            mappedByteBuffers[i] = fileChannel.map(FileChannel.MapMode.READ_ONLY, preLength, regionSize);
            preLength += regionSize;
        }
    }

    public synchronized int read() {
        if (bufferCountIndex >= bufferCount) {
            return -1;
        }

        int limit = mappedByteBuffers[bufferCountIndex].limit();
        int position = mappedByteBuffers[bufferCountIndex].position();

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
        mappedByteBuffers[bufferCountIndex].get(byteBuffer);

        if (realSize < byteBufferSize && bufferCountIndex < bufferCount) {
            bufferCountIndex++;
        }
        return realSize;
    }

    public void close() throws IOException {
        fileChannel.close();
        inputStream.close();
        for (MappedByteBuffer byteBuffer : mappedByteBuffers) {
            byteBuffer.clear();
        }
        byteBuffer = null;
    }

    public synchronized byte[] getCurrentBytes() {
        return byteBuffer;
    }

    public void handle(){}


//    public static void main(String[] args) throws Exception {
//        BigFileReader reader = new BigFileReader("e://3.pdf", 1024);
//        long startTime =System.currentTimeMillis();
//        while (reader.read() != -1) {
//            byte[] bytes = reader.getCurrentBytes();
//            System.out.println(new String(bytes) + "\t");
//        }
//        long endTime =System.currentTimeMillis();
//        System.out.println("花费了"+(endTime-startTime)+"秒");
//        reader.close();
//    }
}

