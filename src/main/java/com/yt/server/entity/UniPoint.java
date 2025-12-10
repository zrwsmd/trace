package com.yt.server.entity;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ggalmazor.ltdownsampling.Point;
import com.yt.server.util.ConnectionManager;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import static com.yt.server.util.IoUtil.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/1/6 10:03
 * @version:1.0
 */
public class UniPoint extends Point implements Serializable {

    @JsonIgnore
    private String varName;

    @JsonIgnore
    private String desc;


    public UniPoint(BigDecimal x, BigDecimal y) {
        super(x, y);
    }

    public UniPoint(BigDecimal x, BigDecimal y, String varName) {
        super(x, y);
        this.varName = varName;
    }


    public UniPoint(BigDecimal x, BigDecimal y, String varName, String desc) {
        super(x, y);
        this.varName = varName;
        this.desc = desc;
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;//地址相等
        }

        if (obj == null) {
            return false;//非空性：对于任意非空引用x，x.equals(null)应该返回false。
        }

        if (obj instanceof UniPoint other) {
            //需要比较的字段相等，则这两个对象相等
            return this.getX().compareTo(other.getX()) == 0 && this.getY().compareTo(other.getY()) == 0
                     && this.getVarName().equals(other.getVarName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + (getX() == null ? 0 : getX().hashCode());
        result = 31 * result + (getY() == null ? 0 : getY().hashCode());
        result = 31 * result + (getDesc() == null ? 0 : getDesc().hashCode());
        result = 31 * result + (getVarName() == null ? 0 : getVarName().hashCode());
        return result;
    }

//    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
//        ConcurrentHashMap<String, Integer> varNumHashMap = new ConcurrentHashMap<>();
//        ConcurrentHashMap<String, Byte> varHexHashMap = new ConcurrentHashMap<>();
//        varNumHashMap.put("v0", 4);
//        varNumHashMap.put("v1", 1);
//        varNumHashMap.put("v2", 4);
//        varHexHashMap.put("v0", (byte) 0x13);
//        varHexHashMap.put("v1", (byte) 0x15);
//        varHexHashMap.put("v2", (byte) 0x13);
//
//        int sum = 9;
//        final MetaParam metaParam = JSON.parseObject(" {\"fileHeader\":\"SN-TRACE\",\"state\":1,\"silentField\":\"blz\",\"timeName\":\"0x10\",\"varNum\":3,\"v0Length\":4,\"v0Name\":\"earn\",\n" +
//                "             \"v0Type\":\"0x13\",\"v1Length\":3,\"v1Name\":\"age\",\"v1Type\":\"0x15\",\"v2Length\":3,\"v2Name\":\"pwd\"," +
//                "\"v2Type\":\"0x13\"}", MetaParam.class);
//
//
//        ByteBuffer buffer = ByteBuffer.allocate(1024);
//        String fileHeader = metaParam.getFileHeader();
//        buffer.put(fileHeader.getBytes());
//        final Integer state = metaParam.getState();
//        buffer.put(new byte[]{state.byteValue()});
//        final String silentField = metaParam.getSilentField();
//        buffer.put(silentField.getBytes());
//        String timeName = metaParam.getTimeName();
//        timeName = timeName.replace("0x", "");
//        buffer.put(Byte.parseByte(timeName, 16));
//        buffer.put(metaParam.getVarNum().byteValue());
//        buffer.put(metaParam.getV0Length().byteValue());
//        buffer.put(metaParam.getV0Name().getBytes());
//        String v0Type = metaParam.getV0Type();
//        v0Type = v0Type.replace("0x", "");
//        buffer.put(Byte.parseByte(v0Type, 16));
//
//        buffer.put(metaParam.getV1Length().byteValue());
//        buffer.put(metaParam.getV1Name().getBytes());
//        String v1Type = metaParam.getV1Type();
//        v1Type = v1Type.replace("0x", "");
//        buffer.put(Byte.parseByte(v1Type, 16));
//
//        buffer.put(metaParam.getV2Length().byteValue());
//        buffer.put(metaParam.getV2Name().getBytes());
//        String v2Type = metaParam.getV2Type();
//        v2Type = v2Type.replace("0x", "");
//        buffer.put(Byte.parseByte(v2Type, 16));
//
//
//        varNumHashMap.put("v0", metaParam.getV0Length());
//        varNumHashMap.put("v1", metaParam.getV1Length());
//        varNumHashMap.put("v2", metaParam.getV2Length());
//
//
////        varHexHashMap.put("v0",Byte.parseByte(v0Type, 16));
////        varHexHashMap.put("v1",Byte.parseByte(v1Type, 16));
////        varHexHashMap.put("v2",Byte.parseByte(v2Type, 16));
////
////        getTypeByCode(Byte.parseByte(v0Type, 16));
//
//
//        List<String> filedNameList = new ArrayList<>();
//        filedNameList.add(metaParam.getV0Name());
//        filedNameList.add(metaParam.getV1Name());
//        filedNameList.add(metaParam.getV2Name());
//
//
//        List<Pair<String, Byte>> data = new ArrayList<>();
//        data.add(Pair.of(metaParam.getV0Name(), Byte.parseByte(v0Type, 16)));
//        data.add(Pair.of(metaParam.getV1Name(), Byte.parseByte(v1Type, 16)));
//        data.add(Pair.of(metaParam.getV2Name(), Byte.parseByte(v2Type, 16)));
//        //earn int  age  tinyint
//       // final List<Pair<String, String>> list = getTypePairByCode(data);
//        //字段名称
////        List<String>fieldNames=new ArrayList<>();
////        for (Pair<String, String> pair : list) {
////            fieldNames.add(pair.getLeft());
////        }
//
//
//
//
//        Connection conn = ConnectionManager.getConnection("test");
//        Statement stat = conn.createStatement();
//        String table = "traceName";
//        /**
//         *
//         CREATE TABLE `hero` (
//         `id` bigint NOT NULL,
//         `username` varchar(65) DEFAULT NULL,
//         `age` int DEFAULT NULL,
//         `email` varchar(100) DEFAULT NULL,
//         `password` varchar(100) DEFAULT NULL,
//         `salary` float DEFAULT NULL,
//         PRIMARY KEY (`id`)
//         ) ENGINE=InnoDB
//         */
//        //String sql =  "CREATE TABLE "+table+"(";
//        StringBuilder sql = new StringBuilder();
//        sql.append("CREATE TABLE " + "`" + table + "`" + "(");
//        sql.append("`id` bigint NOT NULL,");
////        for (int i = 0; i < list.size(); i++) {
////            if (i == list.size() - 1) {
////                sql.append("`" + list.get(i).getLeft() + "`" + "  " + list.get(i).getRight() + " DEFAULT NULL, PRIMARY KEY (`id`))ENGINE=InnoDB ");
////            } else {
////                sql.append("`" + list.get(i).getLeft() + "`" + "  " + list.get(i).getRight() + " DEFAULT NULL, ");
////            }
////
////        }
//        String sqlStr = sql.toString();
//        System.out.println(sqlStr);
//        //获取数据库表名
//        ResultSet rs = conn.getMetaData().getTables(null, null, table, null);
//        // 判断表是否存在，如果存在则什么都不做，否则创建表
//        if (!rs.next()) {
//            stat.executeLargeUpdate(sqlStr);
//        }
//
////        for (String str:filedNameList) {
////            sql = sql+str;
////        }
////        sql = sql + " ) charset=utf8; ";
//
//
////        final Object replace = Byte.parseByte(metaParam.getV0Type().replace("\"", "").replace("\"", ""),16);
////        System.out.println(replace instanceof  String);
//
////        varHexHashMap.put("v0",metaParam.getV0Type().replace(""));
////        varHexHashMap.put("v1",Integer.valueOf(metaParam.getV1Type()).byteValue());
////        varHexHashMap.put("v2",Integer.valueOf(metaParam.getV2Type()).byteValue());
////
////
////
////        final int i = 3;
////        byte[] b13 = {0x13};
////        System.out.println(buffer);
//
////        final Set<String> set = jsonObj.keySet();
////        final ByteBuffer buffer = ByteBuffer.allocate(1024);
////        for (String key : set) {
////            final Object o = jsonObj.get(key);
////            if (o  instanceof  String){
////                String data= (String) o;
////                if (data.startsWith("0x")){
////                    byte aByte = Byte.parseByte(data);
////                    buffer.put(aByte);
////                }else {
////                    buffer.put(data.getBytes());
////                }
////            } else if (o instanceof  Integer) {
////                Integer data= (Integer) o;
////
////            }
////        }
////        byte2File(new byte[]{83,78,45,84,82,65,67,69, 25,48,48,48, 38,0,3,4,101,97,114,110,19,3,97,103,101,21,3,112,119,100,19},
////                "e://metadata.bin");
//
//
////        byte2File(new byte[]{0,0,0,1,0,0,39,16,10,0,0,7,-48,0,0,0,2,0,0,39,16,10,0,0,7,-48,0,0,0,3,0,0,7,-48,55,0,0,20,30,0,0,0,4,0,0,1,-12,80,0,0,1,104,0,0,0,5,0,0,39,16,10,0,0,7,-48,0,0,0,6,0,0,7,-48,55,0,0,20,30,0,0,0,7,0,0,39,16,10,0,0,7,-48,0,0,0,8,0,0,1,-12,80,0,0,1,104,0,0,0,9,0,0,7,-48,55,0,0,20,30,0,0,0,10,0,0,39,16,10,0,0,7,-48,0,0,0,11,0,0,39,16,10,0,0,7,-48,0,0,0,12,0,0,1,-12,80,0,0,1,104,0,0,0,13,0,0,39,16,10,0,0,7,-48,0,0,0,14,0,0,39,16,10,0,0,7,-48,0,0,0,15,0,0,7,-48,55,0,0,20,30,0,0,0,16,0,0,1,-12,80,0,0,1,104,0,0,0,17,0,0,39,16,10,0,0,7,-48,0,0,0,18,0,0,7,-48,55,0,0,20,30,0,0,0,19,0,0,39,16,10,0,0,7,-48,0,0,0,20,0,0,1,-12,80,0,0,1,104,0,0,0,21,0,0,7,-48,55,0,0,20,30,0,0,0,22,0,0,39,16,10,0,0,7,-48,0,0,0,23,0,0,39,16,10,0,0,7,-48,0,0,0,24,0,0,1,-12,80,0,0,1,104,0,0,0,25,0,0,39,16,10,0,0,7,-48,0,0,0,26,0,0,39,16,10,0,0,7,-48,0,0,0,27,0,0,7,-48,55,0,0,20,30,0,0,0,28,0,0,1,-12,80,0,0,1,104,0,0,0,29,0,0,39,16,10,0,0,7,-48,0,0,0,30,0,0,7,-48,55,0,0,20,30,0,0,0,31,0,0,39,16,10,0,0,7,-48,0,0,0,32,0,0,1,-12,80,0,0,1,104,0,0,0,33,0,0,7,-48,55,0,0,20,30,0,0,0,34,0,0,39,16,10,0,0,7,-48,0,0,0,35,0,0,39,16,10,0,0,7,-48,0,0,0,36,0,0,1,-12,80,0,0,1,104,0,0,0,37,0,0,39,16,10,0,0,7,-48,0,0,0,38,0,0,39,16,10,0,0,7,-48,0,0,0,39,0,0,7,-48,55,0,0,20,30,0,0,0,40,0,0,1,-12,80,0,0,1,104,0,0,0,41,0,0,39,16,10,0,0,7,-48,0,0,0,42,0,0,7,-48,55,0,0,20,30,0,0,0,43,0,0,39,16,10,0,0,7,-48,0,0,0,44,0,0,1,-12,80,0,0,1,104,0,0,0,45,0,0,7,-48,55,0,0,20,30,0,0,0,46,0,0,39,16,10,0,0,7,-48,0,0,0,47,0,0,39,16,10,0,0,7,-48,0,0,0,48,0,0,1,-12,80,0,0,1,104,0,0,0,49,0,0,39,16,10,0,0,7,-48,0,0,0,50,0,0,39,16,10,0,0,7,-48,0,0,0,51,0,0,7,-48,55,0,0,20,30,0,0,0,52,0,0,1,-12,80,0,0,1,104,0,0,0,53,0,0,39,16,10,0,0,7,-48,0,0,0,54,0,0,7,-48,55,0,0,20,30,0,0,0,55,0,0,39,16,10,0,0,7,-48,0,0,0,56,0,0,1,-12,80,0,0,1,104,0,0,0,57,0,0,7,-48,55,0,0,20,30,0,0,0,58,0,0,39,16,10,0,0,7,-48,0,0,0,59,0,0,39,16,10,0,0,7,-48,0,0,0,60,0,0,1,-12,80,0,0,1,104,0,0,0,61,0,0,39,16,10,0,0,7,-48,0,0,0,62,0,0,39,16,10,0,0,7,-48,0,0,0,63,0,0,7,-48,55,0,0,20,30,0,0,0,64,0,0,1,-12,80,0,0,1,104,0,0,0,65,0,0,39,16,10,0,0,7,-48,0,0,0,66,0,0,7,-48,55,0,0,20,30,0,0,0,67,0,0,39,16,10,0,0,7,-48,0,0,0,68,0,0,1,-12,80,0,0,1,104,0,0,0,69,0,0,7,-48,55,0,0,20,30,0,0,0,70,0,0,39,16,10,0,0,7,-48,0,0,0,71,0,0,39,16,10,0,0,7,-48,0,0,0,72,0,0,1,-12,80,0,0,1,104,0,0,0,73,0,0,39,16,10,0,0,7,-48,0,0,0,74,0,0,39,16,10,0,0,7,-48,0,0,0,75,0,0,7,-48,55,0,0,20,30,0,0,0,76,0,0,1,-12,80,0,0,1,104,0,0,0,77,0,0,39,16,10,0,0,7,-48,0,0,0,78,0,0,7,-48,55,0,0,20,30,0,0,0,79,0,0,39,16,10,0,0,7,-48,0,0,0,80,0,0,1,-12,80,0,0,1,104,0,0,0,81,0,0,7,-48,55,0,0,20,30,0,0,0,82,0,0,39,16,10,0,0,7,-48,0,0,0,83,0,0,39,16,10,0,0,7,-48,0,0,0,84,0,0,1,-12,80,0,0,1,104,0,0,0,85,0,0,39,16,10,0,0,7,-48,0,0,0,86,0,0,39,16,10,0,0,7,-48,0,0,0,87,0,0,7,-48,55,0,0,20,30,0,0,0,88,0,0,1,-12,80,0,0,1,104,0,0,0,89,0,0,39,16,10,0,0,7,-48,0,0,0,90,0,0,7,-48,55,0,0,20,30,0,0,0,91,0,0,39,16,10,0,0,7,-48,0,0,0,92,0,0,1,-12,80,0,0,1,104,0,0,0,93,0,0,7,-48,55,0,0,20,30,0,0,0,94,0,0,39,16,10,0,0,7,-48,0,0,0,95,0,0,39,16,10,0,0,7,-48,0,0,0,96,0,0,1,-12,80,0,0,1,104,0,0,0,97,0,0,39,16,10,0,0,7,-48,0,0,0,98,0,0,39,16,10,0,0,7,-48,0,0,0,99,0,0,7,-48,55,0,0,20,30,0,0,0,100,0,0,1,-12,80,0,0,1,104,0,0,0,101,0,0,39,16,10,0,0,7,-48,0,0,0,102,0,0,7,-48,55,0,0,20,30,0,0,0,103,0,0,39,16,10,0,0,7,-48,0,0,0,104,0,0,1,-12,80,0,0,1,104,0,0,0,105,0,0,7,-48,55,0,0,20,30,0,0,0,106,0,0,39,16,10,0,0,7,-48,0,0,0,107,0,0,39,16,10,0,0,7,-48,0,0,0,108,0,0,1,-12,80,0,0,1,104,0,0,0,109,0,0,39,16,10,0,0,7,-48,0,0,0,110,0,0,39,16,10,0,0,7,-48,0,0,0,111,0,0,7,-48,55,0,0,20,30,0,0,0,112,0,0,1,-12,80,0,0,1,104,0,0,0,113,0,0,39,16,10,0,0,7,-48,0,0,0,114,0,0,7,-48,55,0,0,20,30,0,0,0,115,0,0,39,16,10,0,0,7,-48,0,0,0,116,0,0,1,-12,80,0,0,1,104,0,0,0,117,0,0,7,-48,55,0,0,20,30,0,0,0,118,0,0,39,16,10,0,0,7,-48,0,0,0,119,0,0,39,16,10,0,0,7,-48,0,0,0,120,0,0,1,-12,80,0,0,1,104,0,0,0,121,0,0,39,16,10,0,0,7,-48,0,0,0,122,0,0,39,16,10,0,0,7,-48,0,0,0,123,0,0,7,-48,55,0,0,20,30,0,0,0,124,0,0,1,-12,80,0,0,1,104,0,0,0,125,0,0,39,16,10,0,0,7,-48,0,0,0,126,0,0,7,-48,55,0,0,20,30,0,0,0,127,0,0,39,16,10,0,0,7,-48,0,0,0,-128,0,0,1,-12,80,0,0,1,104,0,0,0,-127,0,0,7,-48,55,0,0,20,30,0,0,0,-126,0,0,39,16,10,0,0,7,-48,0,0,0,-125,0,0,39,16,10,0,0,7,-48,0,0,0,-124,0,0,1,-12,80,0,0,1,104,0,0,0,-123,0,0,39,16,10,0,0,7,-48,0,0,0,-122,0,0,39,16,10,0,0,7,-48,0,0,0,-121,0,0,7,-48,55,0,0,20,30,0,0,0,-120,0,0,1,-12,80,0,0,1,104,0,0,0,-119,0,0,39,16,10,0,0,7,-48,0,0,0,-118,0,0,7,-48,55,0,0,20,30,0,0,0,-117,0,0,39,16,10,0,0,7,-48,0,0,0,-116,0,0,1,-12,80,0,0,1,104,0,0,0,-115,0,0,7,-48,55,0,0,20,30,0,0,0,-114,0,0,39,16,10,0,0,7,-48,0,0,0,-113,0,0,39,16,10,0,0,7,-48,0,0,0,-112,0,0,1,-12,80,0,0,1,104,0,0,0,-111,0,0,39,16,10,0,0,7,-48,0,0,0,-110,0,0,39,16,10,0,0,7,-48,0,0,0,-109,0,0,7,-48,55,0,0,20,30,0,0,0,-108,0,0,1,-12,80,0,0,1,104,0,0,0,-107,0,0,39,16,10,0,0,7,-48,0,0,0,-106,0,0,7,-48,55,0,0,20,30,0,0,0,-105,0,0,39,16,10,0,0,7,-48,0,0,0,-104,0,0,1,-12,80,0,0,1,104,0,0,0,-103,0,0,7,-48,55,0,0,20,30,0,0,0,-102,0,0,39,16,10,0,0,7,-48,0,0,0,-101,0,0,39,16,10,0,0,7,-48,0,0,0,-100,0,0,1,-12,80,0,0,1,104,0,0,0,-99,0,0,39,16,10,0,0,7,-48,0,0,0,-98,0,0,39,16,10,0,0,7,-48,0,0,0,-97,0,0,7,-48,55,0,0,20,30,0,0,0,-96,0,0,1,-12,80,0,0,1,104,0,0,0,-95,0,0,39,16,10,0,0,7,-48,0,0,0,-94,0,0,7,-48,55,0,0,20,30,0,0,0,-93,0,0,39,16,10,0,0,7,-48,0,0,0,-92,0,0,1,-12,80,0,0,1,104,0,0,0,-91,0,0,7,-48,55,0,0,20,30,0,0,0,-90,0,0,39,16,10,0,0,7,-48,0,0,0,-89,0,0,39,16,10,0,0,7,-48,0,0,0,-88,0,0,1,-12,80,0,0,1,104,0,0,0,-87,0,0,39,16,10,0,0,7,-48,0,0,0,-86,0,0,39,16,10,0,0,7,-48,0,0,0,-85,0,0,7,-48,55,0,0,20,30,0,0,0,-84,0,0,1,-12,80,0,0,1,104,0,0,0,-83,0,0,39,16,10,0,0,7,-48,0,0,0,-82,0,0,7,-48,55,0,0,20,30,0,0,0,-81,0,0,39,16,10,0,0,7,-48,0,0,0,-80,0,0,1,-12,80,0,0,1,104,0,0,0,-79,0,0,7,-48,55,0,0,20,30,0,0,0,-78,0,0,39,16,10,0,0,7,-48,0,0,0,-77,0,0,39,16,10,0,0,7,-48,0,0,0,-76,0,0,1,-12,80,0,0,1,104,0,0,0,-75,0,0,39,16,10,0,0,7,-48,0,0,0,-74,0,0,39,16,10,0,0,7,-48,0,0,0,-73,0,0,7,-48,55,0,0,20,30,0,0,0,-72,0,0,1,-12,80,0,0,1,104,0,0,0,-71,0,0,39,16,10,0,0,7,-48,0,0,0,-70,0,0,7,-48,55,0,0,20,30,0,0,0,-69,0,0,39,16,10,0,0,7,-48,0,0,0,-68,0,0,1,-12,80,0,0,1,104,0,0,0,-67,0,0,7,-48,55,0,0,20,30,0,0,0,-66,0,0,39,16,10,0,0,7,-48,0,0,0,-65,0,0,39,16,10,0,0,7,-48,0,0,0,-64,0,0,1,-12,80,0,0,1,104,0,0,0,-63,0,0,39,16,10,0,0,7,-48,0,0,0,-62,0,0,39,16,10,0,0,7,-48,0,0,0,-61,0,0,7,-48,55,0,0,20,30,0,0,0,-60,0,0,1,-12,80,0,0,1,104,0,0,0,-59,0,0,39,16,10,0,0,7,-48,0,0,0,-58,0,0,7,-48,55,0,0,20,30,0,0,0,-57,0,0,39,16,10,0,0,7,-48,0,0,0,-56,0,0,1,-12,80,0,0,1,104,0,0,0,-55,0,0,7,-48,55,0,0,20,30,0,0,0,-54,0,0,39,16,10,0,0,7,-48,0,0,0,-53,0,0,39,16,10,0,0,7,-48,0,0,0,-52,0,0,1,-12,80,0,0,1,104,0,0,0,-51,0,0,39,16,10,0,0,7,-48,0,0,0,-50,0,0,39,16,10,0,0,7,-48,0,0,0,-49,0,0,7,-48,55,0,0,20,30,0,0,0,-48,0,0,1,-12,80,0,0,1,104,0,0,0,-47,0,0,39,16,10,0,0,7,-48,0,0,0,-46,0,0,7,-48,55,0,0,20,30,0,0,0,-45,0,0,39,16,10,0,0,7,-48,0,0,0,-44,0,0,1,-12,80,0,0,1,104,0,0,0,-43,0,0,7,-48,55,0,0,20,30,0,0,0,-42,0,0,39,16,10,0,0,7,-48,0,0,0,-41,0,0,39,16,10,0,0,7,-48,0,0,0,-40,0,0,1,-12,80,0,0,1,104,0,0,0,-39,0,0,39,16,10,0,0,7,-48,0,0,0,-38,0,0,39,16,10,0,0,7,-48,0,0,0,-37,0,0,7,-48,55,0,0,20,30,0,0,0,-36,0,0,1,-12,80,0,0,1,104,0,0,0,-35,0,0,39,16,10,0,0,7,-48,0,0,0,-34,0,0,7,-48,55,0,0,20,30,0,0,0,-33,0,0,39,16,10,0,0,7,-48,0,0,0,-32,0,0,1,-12,80,0,0,1,104,0,0,0,-31,0,0,7,-48,55,0,0,20,30,0,0,0,-30,0,0,39,16,10,0,0,7,-48,0,0,0,-29,0,0,39,16,10,0,0,7,-48,0,0,0,-28,0,0,1,-12,80,0,0,1,104,0,0,0,-27,0,0,39,16,10,0,0,7,-48,0,0,0,-26,0,0,39,16,10,0,0,7,-48,0,0,0,-25,0,0,7,-48,55,0,0,20,30,0,0,0,-24,0,0,1,-12,80,0,0,1,104,0,0,0,-23,0,0,39,16,10,0,0,7,-48,0,0,0,-22,0,0,7,-48,55,0,0,20,30,0,0,0,-21,0,0,39,16,10,0,0,7,-48,0,0,0,-20,0,0,1,-12,80,0,0,1,104,0,0,0,-19,0,0,7,-48,55,0,0,20,30,0,0,0,-18,0,0,39,16,10,0,0,7,-48,0,0,0,-17,0,0,39,16,10,0,0,7,-48,0,0,0,-16,0,0,1,-12,80,0,0,1,104,0,0,0,-15,0,0,39,16,10,0,0,7,-48,0,0,0,-14,0,0,39,16,10,0,0,7,-48,0,0,0,-13,0,0,7,-48,55,0,0,20,30,0,0,0,-12,0,0,1,-12,80,0,0,1,104,0,0,0,-11,0,0,39,16,10,0,0,7,-48,0,0,0,-10,0,0,7,-48,55,0,0,20,30,0,0,0,-9,0,0,39,16,10,0,0,7,-48,0,0,0,-8,0,0,1,-12,80,0,0,1,104,0,0,0,-7,0,0,7,-48,55,0,0,20,30,0,0,0,-6,0,0,39,16,10,0,0,7,-48,0,0,0,-5,0,0,39,16,10,0,0,7,-48,0,0,0,-4,0,0,1,-12,80,0,0,1,104,0,0,0,-3,0,0,39,16,10,0,0,7,-48,0,0,0,-2,0,0,39,16,10,0,0,7,-48,0,0,0,-1,0,0,7,-48,55,0,0,20,30,0,0,1,0,0,0,1,-12,80,0,0,1,104,0,0,1,1,0,0,39,16,10,0,0,7,-48,0,0,1,2,0,0,7,-48,55,0,0,20,30,0,0,1,3,0,0,39,16,10,0,0,7,-48,0,0,1,4,0,0,1,-12,80,0,0,1,104,0,0,1,5,0,0,7,-48,55,0,0,20,30,0,0,1,6,0,0,39,16,10,0,0,7,-48,0,0,1,7,0,0,39,16,10,0,0,7,-48,0,0,1,8,0,0,1,-12,80,0,0,1,104,0,0,1,9,0,0,39,16,10,0,0,7,-48,0,0,1,10,0,0,39,16,10,0,0,7,-48,0,0,1,11,0,0,7,-48,55,0,0,20,30,0,0,1,12,0,0,1,-12,80,0,0,1,104,0,0,1,13,0,0,39,16,10,0,0,7,-48,0,0,1,14,0,0,7,-48,55,0,0,20,30,0,0,1,15,0,0,39,16,10,0,0,7,-48,0,0,1,16,0,0,1,-12,80,0,0,1,104,0,0,1,17,0,0,7,-48,55,0,0,20,30,0,0,1,18,0,0,39,16,10,0,0,7,-48,0,0,1,19,0,0,39,16,10,0,0,7,-48,0,0,1,20,0,0,1,-12,80,0,0,1,104,0,0,1,21,0,0,39,16,10,0,0,7,-48,0,0,1,22,0,0,39,16,10,0,0,7,-48,0,0,1,23,0,0,7,-48,55,0,0,20,30,0,0,1,24,0,0,1,-12,80,0,0,1,104,0,0,1,25,0,0,39,16,10,0,0,7,-48,0,0,1,26,0,0,7,-48,55,0,0,20,30,0,0,1,27,0,0,39,16,10,0,0,7,-48,0,0,1,28,0,0,1,-12,80,0,0,1,104,0,0,1,29,0,0,7,-48,55,0,0,20,30,0,0,1,30,0,0,39,16,10,0,0,7,-48,0,0,1,31,0,0,39,16,10,0,0,7,-48,0,0,1,32,0,0,1,-12,80,0,0,1,104,0,0,1,33,0,0,39,16,10,0,0,7,-48,0,0,1,34,0,0,39,16,10,0,0,7,-48,0,0,1,35,0,0,7,-48,55,0,0,20,30,0,0,1,36,0,0,1,-12,80,0,0,1,104,0,0,1,37,0,0,39,16,10,0,0,7,-48,0,0,1,38,0,0,7,-48,55,0,0,20,30,0,0,1,39,0,0,39,16,10,0,0,7,-48,0,0,1,40,0,0,1,-12,80,0,0,1,104,0,0,1,41,0,0,7,-48,55,0,0,20,30,0,0,1,42,0,0,39,16,10,0,0,7,-48,0,0,1,43,0,0,39,16,10,0,0,7,-48,
////
////                },
////                "e://realdata.bin");
//
//
////                byte2File(new byte[]{0,0,0,1,0,0,39,16,10,0,0,7,-48,0,0,0,2,0,0,39,16,10,0,0,7,-48,0,0,0,3,0,0,7,-48,55,0,0,20,30,0,0,0,4,0,0,1,-12,80,0,0,1,104,0,0,0,5,0,0,39,16,10,0,0,7,-48,0,0,0,6,0,0,7,-48,55,0,0,20,30,0,0,0,7,0,0,39,16,10,0,0,7,-48,0,0,0,8,0,0,1,-12,80,0,0,1,104,0,0,0,9,0,0,7,-48,55,0,0,20,30,0,0,0,10,0,0,39,16,10,0,0,7,-48,0,0,0,11,0,0,39,16,10,0,0,7,-48,0,0,0,12,0,0,1,-12,80,0,0,1,104,0,0,0,13,0,0,39,16,10,0,0,7,-48,0,0,0,14,0,0,39,16,10,0,0,7,-48,0,0,0,15,0,0,7,-48,55,0,0,20,30,0,0,0,16,0,0,1,-12,80,0,0,1,104,0,0,0,17,0,0,39,16,10,0,0,7,-48,0,0,0,18,0,0,7,-48,55,0,0,20,30,0,0,0,19,0,0,39,16,10,0,0,7,-48,0,0,0,20,0,0,1,-12,80,0,0,1,104,0,0,0,21,0,0,7,-48,55,0,0,20,30,0,0,0,22,0,0,39,16,10,0,0,7,-48,0,0,0,23,0,0,39,16,10,0,0,7,-48,0,0,0,24,0,0,1,-12,80,0,0,1,104,0,0,0,25,0,0,39,16,10,0,0,7,-48,0,0,0,26,0,0,39,16,10,0,0,7,-48,0,0,0,27,0,0,7,-48,55,0,0,20,30,0,0,0,28,0,0,1,-12,80,0,0,1,104,0,0,0,29,0,0,39,16,10,0,0,7,-48,19
////
////
////                        },
////                "e://small-realdata.bin");
////       BitSet set = new BitSet(8);
////        set.set(3);// 将3设置为true
////        set.set(5);// 将3设置为tr
////        set.set(60);// 将3设置为tr
////        System.out.println(set.get(400));
//
//
////        final byte[] bytes = file2Byte(new File("e://savebbb.bin"));
////        System.out.println(bytes);
//        byte[] b19 = bitString2ByteArray2("0000000000001010");
////        System.out.println(b19);
//
//        final byte[] bytes = shortToByte((short) 10);
//        final Long aLong = composeLong(bytes);
//        System.out.println(aLong);
//    }

    // char转byte
    public static byte[] charToBytes(char[] chars) {
        Charset charset = StandardCharsets.ISO_8859_1;
        CharBuffer charBuffer = CharBuffer.allocate(chars.length);
        charBuffer.put(chars);
        charBuffer.flip();
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        return byteBuffer.array();
    }


    public static byte[] addMaskFlag(BitSet bitSet) {
        Set<Integer> filterSet = new HashSet<>();
        filterSet.add(0);
        filterSet.add(2);
        for (int i = 0; i < bitSet.size(); i++) {
            if (filterSet.contains(i)) {
                bitSet.set(i);
            }
        }
        return bitSet.toByteArray();
    }

    //yt_100-1000_6459024492721362038.tmp
    private static Pair<Long, Long> getRegionStamp(String fileName) {
        String[] split = fileName.split("-");
        String pre = split[0];
        String suffix = split[1];
        pre = pre.replace("yt_", "");
        Long preLong = Long.parseLong(pre);
        int suffixStart = suffix.indexOf("_");
        suffix = suffix.substring(0, suffixStart);
        Long suffixLong = Long.parseLong(suffix);
        return Pair.of(preLong, suffixLong);
    }


}
