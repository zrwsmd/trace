package com.yt.server.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.yt.server.util.FileUtils;
import com.yt.server.util.MysqlUtils;
import org.mybatis.generator.codegen.ibatis2.dao.DAOGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.yt.server.service.IoComposeServiceDatabase.DATABASE_NAME;
import static com.yt.server.util.IoUtil.intToBytesHighAhead;
import static com.yt.server.util.MysqlUtils.getMysqlDataDir;
import static com.yt.server.util.MysqlUtils.restartMysql;

public class Hero {
    private Integer id;

    private String email;

    private String password;

    private String username;

    private Integer age;


    public Hero() {
    }

    public Hero(Integer id, String username, Integer age) {
        this.id = id;
        this.username = username;
        this.age = age;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password == null ? null : password.trim();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public static void main(String[] args) throws SQLException, IOException, ClassNotFoundException {
        //MysqlUtils.terminateMysql("trace");
       // restartMysql("D:\\super-cmd\\cmd\\nircmdc.exe");
//        final String dataDir = getMysqlDataDir(DATABASE_NAME);
       // FileUtils.delete("D:/0/MySQLData/");
    }

//    public static void main(String[] args) throws IOException {
//
////        JSONObject jsonObj = JSON.parseObject("{\"166666678\":[1987451871111,24,5000],\"319301891\":[7843094001111,18,300],\"718937133\":[3829085801111,35,18790]}");
////        final Set<String> set = jsonObj.keySet();
////        Set<Long> filterSet = new TreeSet<>();
////        for (String str : set) {
////            filterSet.add(Long.parseLong(str));
////        }
////        List<Object[]>objects=new ArrayList<>();
////        for (Long timestamp : filterSet) {
////            byte[] timeStampBytes = intToBytesHighAhead(timestamp.intValue());
////            //buffer.put(timeStampBytes);
////            JSONArray jsonArray = (JSONArray) jsonObj.get(timestamp);
////
////            int size = jsonArray.size();
////            Object[]objArr=new Object[1+size];
////            for (int i = 0; i <size ; i++) {
////                if (i==0){
////                    objArr[0]=timestamp;
////                }
////                objArr[i+1]=jsonArray.get(i);
////            }
////            objects.add(objArr);
////        }
////        System.out.println(objects);
//        Set<Integer> set = new TreeSet<>();
//        Integer[] data = new Integer[]{2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384};
//        Collections.addAll(set, data);
//        System.out.println(set);
//
//    }
}