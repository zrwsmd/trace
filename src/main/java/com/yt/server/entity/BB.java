package com.yt.server.entity;

import com.yt.server.util.BaseUtils;
import com.yt.server.util.ZipArchiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/3/17 14:40
 * @version:1.0
 */
@Component
public class BB {

    @Autowired
    private AA aa;

    public static void main(String[] args) {
        String directoryPath = "D:\\0\\MySQLData";
        // 压缩后路径
        String zipPath = "e://0726-th3.trace";
        long beginTime = System.currentTimeMillis();
        final int processors = Runtime.getRuntime().availableProcessors();
        new ZipArchiveUtils().createZip(directoryPath, zipPath ,processors);
        long endTime = System.currentTimeMillis();
        System.out.println("使用 " + processors + " 线程，压缩耗时：" + (endTime - beginTime) + "毫秒");
    }
}
