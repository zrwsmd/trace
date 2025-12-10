package com.yt.server.entity;

public class TraceTimestampStatistics {
    private Long traceId;

    private Long tempTimestamp;

    private Long lastEndTimestamp;

    private Integer reachedBatchNum;

    private Integer loopNum;

    public Long getTraceId() {
        return traceId;
    }

    public void setTraceId(Long traceId) {
        this.traceId = traceId;
    }

    public Long getTempTimestamp() {
        return tempTimestamp;
    }

    public void setTempTimestamp(Long tempTimestamp) {
        this.tempTimestamp = tempTimestamp;
    }

    public Long getLastEndTimestamp() {
        return lastEndTimestamp;
    }

    public void setLastEndTimestamp(Long lastEndTimestamp) {
        this.lastEndTimestamp = lastEndTimestamp;
    }

    public Integer getReachedBatchNum() {
        return reachedBatchNum;
    }

    public void setReachedBatchNum(Integer reachedBatchNum) {
        this.reachedBatchNum = reachedBatchNum;
    }

    public Integer getLoopNum() {
        return loopNum;
    }

    public void setLoopNum(Integer loopNum) {
        this.loopNum = loopNum;
    }
}