package com.yt.server.entity;

import java.util.function.Function;



public class TraceFieldMeta {
    private Long id;

    private Long traceId;

    private String varName;

    private String mysqlType;

    private String windowsType;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTraceId() {
        return traceId;
    }

    public void setTraceId(Long traceId) {
        this.traceId = traceId;
    }

    public String getVarName() {
        return varName;
    }

    public void setVarName(String varName) {
        this.varName = varName == null ? null : varName.trim();
    }

    public String getMysqlType() {
        return mysqlType;
    }

    public void setMysqlType(String mysqlType) {
        this.mysqlType = mysqlType == null ? null : mysqlType.trim();
    }

    public String getWindowsType() {
        return windowsType;
    }

    public void setWindowsType(String windowsType) {
        this.windowsType = windowsType == null ? null : windowsType.trim();
    }


}