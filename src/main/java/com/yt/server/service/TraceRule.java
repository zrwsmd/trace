package com.yt.server.service;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/14 10:15
 * @version:1.0
 */
public interface TraceRule {
    int getReqNum();
    String downTableSuffix();
}
