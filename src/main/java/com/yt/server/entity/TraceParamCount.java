package com.yt.server.entity;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/5/18 13:28
 * @version:1.0
 */
public class TraceParamCount {

    private Long min;
    private Long max;

    public Long getMin() {
        return min;
    }

    public void setMin(Long min) {
        this.min = min;
    }

    public Long getMax() {
        return max;
    }

    public void setMax(Long max) {
        this.max = max;
    }
}
