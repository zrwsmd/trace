package com.yt.server.entity;

import java.io.Serializable;

public class TraceTableRelatedInfo implements Serializable {

    private Long traceId;

    private String tableName;

    private String downsamplingTableName;

    private String traceStatus;

    private String traceName;

    private String traceConfig;

    private String reachedBatchFlag;

    private String oldVarNames;

    private String oldFieldMetaIds;

    public Long getTraceId() {
        return traceId;
    }

    public void setTraceId(Long traceId) {
        this.traceId = traceId;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName == null ? null : tableName.trim();
    }

    public String getDownsamplingTableName() {
        return downsamplingTableName;
    }

    public void setDownsamplingTableName(String downsamplingTableName) {
        this.downsamplingTableName = downsamplingTableName == null ? null : downsamplingTableName.trim();
    }

    public String getTraceStatus() {
        return traceStatus;
    }

    public void setTraceStatus(String traceStatus) {
        this.traceStatus = traceStatus == null ? null : traceStatus.trim();
    }

    public String getTraceName() {
        return traceName;
    }

    public void setTraceName(String traceName) {
        this.traceName = traceName == null ? null : traceName.trim();
    }

    public String getTraceConfig() {
        return traceConfig;
    }

    public void setTraceConfig(String traceConfig) {
        this.traceConfig = traceConfig == null ? null : traceConfig.trim();
    }

    public String getReachedBatchFlag() {
        return reachedBatchFlag;
    }

    public void setReachedBatchFlag(String reachedBatchFlag) {
        this.reachedBatchFlag = reachedBatchFlag == null ? null : reachedBatchFlag.trim();
    }

    public String getOldVarNames() {
        return oldVarNames;
    }

    public void setOldVarNames(String oldVarNames) {
        this.oldVarNames = oldVarNames;
    }

    public String getOldFieldMetaIds() {
        return oldFieldMetaIds;
    }

    public void setOldFieldMetaIds(String oldFieldMetaIds) {
        this.oldFieldMetaIds = oldFieldMetaIds;
    }
}