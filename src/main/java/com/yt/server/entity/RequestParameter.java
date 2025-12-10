package com.yt.server.entity;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static com.yt.server.util.IoUtil.*;

public class RequestParameter implements Serializable {

    public RequestParameter() {
    }

    private Long startTimestamp;
    private Long endTimestamp;
    private Integer reqNum;

    private List<String> varList=new ArrayList<>();

    private String fileName;

    private String dataType;

    private boolean isSave;

    private String savePath;

    private Integer gap;

    private byte[] bytes;

    private String jsonStr;

    private Long traceId;

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public Integer getReqNum() {
        return reqNum;
    }

    public void setReqNum(Integer reqNum) {
        this.reqNum = reqNum;
    }

    public List<String> getVarList() {
        return varList;
    }

    public void setVarList(List<String> varList) {
        this.varList = varList;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public boolean getIsSave() {
        return isSave;
    }

    public void setSave(boolean save) {
        isSave = save;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }

    public Integer getGap() {
        return gap;
    }

    public void setGap(Integer gap) {
        this.gap = gap;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }


    public void setJsonStr(String jsonStr) {
        this.jsonStr = jsonStr;
    }

    public String getJsonStr() {
        return jsonStr;
    }

    public Long getTraceId() {
        return traceId;
    }

    public void setTraceId(Long traceId) {
        this.traceId = traceId;
    }

    //    public static void main(String[] args) throws IOException {
////        final byte[] bytes = bitString2ByteArray2("0111111111111111");
////        System.out.println(bytes);
//
//        final int i = Integer.parseInt("10100000", 2);
//        System.out.println(i);
////
//        final int compose = compose(new byte[]{-96});
//        System.out.println(compose);
//
////        final String s = Integer.toBinaryString(125);
////            System.out.println(s);
////       int num=2;
////        String str = String.format("%0"+num+"d", 100000);
////        System.out.println(str);
//
//        final String replace = String.format("%8s", "0100000").replace(" ", "0");
//        System.out.println(replace);
//
//        final char[] c ="10100000".toCharArray();
//        for (int j = 0; j < c.length; j++) {
//            System.out.println(Integer.valueOf(c[j]));
//        }
////        for (char c1 : c) {
////            System.out.println(c1);
////        }
//    }
}
