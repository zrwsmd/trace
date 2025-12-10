package com.yt.server.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/7/27 10:19
 * @version:1.0
 */
public class FileUtils {

    public static void delete(String dataPath) throws IOException {
        Path path= Paths.get(dataPath);
        Files.walkFileTree(path,new SimpleFileVisitor<>(){
            //遍历删除文件
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            //遍历删除目录
            public FileVisitResult postVisitDirectory(Path dir,IOException exc) throws IOException{
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });

    }
}
