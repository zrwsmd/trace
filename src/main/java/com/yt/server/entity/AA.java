package com.yt.server.entity;

import com.yt.server.util.BaseUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.yt.server.service.IoComposeServiceDatabase.totalSize;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/3/17 14:40
 * @version:1.0
 */
@Component
public class AA implements FactoryBean {

    private String username;

    private Timestamp timestamp;

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    //    @Autowired
//    private BB bb;

    @Override
    public Object getObject() throws Exception {
         AA aa = new AA();
        aa.setUsername("zrw");
        return aa;
    }

    @Override
    public Class<?> getObjectType() {
        return AA.class;
    }

//    public static void main(String[] args) {
//        List<String>list=new ArrayList<>();
//        list.add("2");
//        list.add("12");
//        list.add("22");
//        list.add("16");
//        list.add("15");
//        list.remove("16");
//        System.out.println(list);
////        HashMap<Integer, String> sites = new HashMap<>();
////
////        // 往 HashMap 添加一些元素
////        sites.put(1, "Google");
////        sites.put(2, "Runoob");
////        sites.put(3, "Taobao");
////        final String aaa = sites.get(4);
////        System.out.println(aaa);
////        sites.remove(2);
////        System.out.println(sites);
////        Set<String>set=new HashSet<>();
////        set.add("id");
////        set.add("v0");
////        set.add("v1");
////        set.add("v2");
////        set.removeIf(item ->"v1".equals(item));
////        System.out.println(set);
//
//        final int i = chooseBucket333(18200000L);
//        System.out.println(i);
//    }

    private static int chooseBucket333(Long timestamp) {
        for (int i = 0; i < 10; i++) {
            final long first = (long) i * (totalSize / 10) * 10;
            //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
            if (timestamp >= first && timestamp <= first + (totalSize / 10) * 10) {
                return i;
            }
        }
        return 0;

    }
//static int shardNum = 10;
//  static   Map<String, Integer> perMap = new HashMap<>();
//
//    private static int chooseBucket(Long timestamp) {
//        perMap.put("per", 10);
//        for (int i = 0; i < shardNum; i++) {
//            final long first = (long) i * (totalSize / shardNum) * perMap.get("per");
//            //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
//            if (timestamp >= first && timestamp <= first + (long) (totalSize / shardNum) * perMap.get("per")) {
//                return i;
//            }
//        }
//        return 0;
//
//    }


    public static void writeTimestampToD(String content,String flag,String bucket,String loopNum) {
        Path path = Paths.get("D:\\timestamp333.txt");
        String data=content.concat(flag).concat("^^^^^^^^").concat(bucket).concat("------").concat(loopNum).concat("\r\n");
        //String content = String.valueOf(System.currentTimeMillis());
        try {
            Files.write(path,
                    data.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 捕获后转运行时异常，调用方可按需处理
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
    }
    public static void writeTimestampToD2(String content) {
        Path path = Paths.get("D:\\perData.txt");
//        String data=content
        String content2 = content.concat("\r\n");
        try {
            Files.write(path,
                    content2.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 捕获后转运行时异常，调用方可按需处理
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
    }

    public static void writeTimestampToD3(String downsampling,Long startTimestamp,Long endTimestamp) {
        Path path = Paths.get("D:\\downsamplingData.txt");
//        String data=content
        String content = downsampling.concat("------").concat(String.valueOf(startTimestamp)).concat("------").concat(String.valueOf(endTimestamp)).concat("\r\n");
        try {
            Files.write(path,
                    content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 捕获后转运行时异常，调用方可按需处理
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
    }

    public static void writeTimestampToD4(String tag,Long startTimestamp,Long endTimestamp) {
        Path path = Paths.get("D:\\asyncDownsampling.txt");
//        String data=content
        String content = tag.concat("------").concat(String.valueOf(startTimestamp)).concat("------").concat(String.valueOf(endTimestamp)).concat("\r\n");
        try {
            Files.write(path,
                    content.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 捕获后转运行时异常，调用方可按需处理
            throw new RuntimeException("写入失败: " + e.getMessage(), e);
        }
    }

//    private static Long getNearestRegion(Long timestamp) {
//        perMap.put("per", 10000);
//        try {
//            for (int i = 0; i < shardNum; i++) {
//                final long first = (long) i * (totalSize / shardNum) * perMap.get("per");
//                //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
//                if (timestamp >= first && timestamp <= first + (long) (totalSize / shardNum) * perMap.get("per")) {
//                    return  ((long) i * (totalSize / shardNum) * perMap.get("per") + (totalSize / shardNum) * perMap.get("per"));
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return -1L;
//    }
//
//    private static Integer getNearestRegion33(Long timestamp) {
//        perMap.put("per", 10);
//        try {
//            for (int i = 0; i < shardNum; i++) {
//                final long first = (long) i * (totalSize / shardNum) * perMap.get("per");
//                //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
//                if (timestamp >= first && timestamp <= first + (long) (totalSize / shardNum) * perMap.get("per")) {
//                    return   i * (totalSize / shardNum) * perMap.get("per") + (totalSize / shardNum) * perMap.get("per");
//                }
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return -1;
//    }
    public static void main(String[] args) throws IOException {
//        Process p = Runtime.getRuntime().exec("D:\\super-cmd\\cmd\\nircmd.exe elevate net start mysql80");
//        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
//        }
//        final int bucket = chooseBucket(9999780000l);
//        System.out.println(bucket);
       // System.out.println(10000810000l/10000020000l);
//        writeTimestampToD(String.valueOf(11111111111111111l),"firstBatchObjects333","0","1");
//        writeTimestampToD(String.valueOf(1555555555555l),"otherBatchObjects333","1","1");
//        final Long nearestRegion = getNearestRegion(10000330000L);
//        final Integer nearestRegion33 = getNearestRegion33(10000330000L);
//        System.out.println(nearestRegion);
//        System.out.println(nearestRegion33);
    }

}
