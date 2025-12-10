package com.yt.server.entity;

import java.math.BigDecimal;
import java.util.List;

import static com.yt.server.util.BaseUtils.acquireAllField;

public class TraceDownsampling {
    private Long id;

//    private String varName;

    private Long timestamp;

    private BigDecimal value;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

//    public String getVarName() {
//        return varName;
//    }
//
//    public void setVarName(String varName) {
//        this.varName = varName == null ? null : varName.trim();
//    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

//    public static void main(String[] args) {
//        final List<String> list = acquireAllField(TraceDownsampling.class);
//        System.out.println(list.size());
//    }
}