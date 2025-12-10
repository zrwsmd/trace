package com.yt.server.service;

/**
 * @description: 2-5条线
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/14 10:18
 * @version:1.0
 */
public class MiddleLineRule implements TraceRule{
    @Override
    public int getReqNum() {
        return 10000;
    }

    @Override
    public String downTableSuffix() {
        return IoComposeServiceDatabase.middleDownTableSuffix;
    }
}
