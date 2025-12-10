package com.yt.server.service;

/**
 * @description: 11-15条线
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/14 10:18
 * @version:1.0
 */
public class LastLineRule implements TraceRule{
    @Override
    public int getReqNum() {
        return 4500;
    }

    @Override
    public String downTableSuffix() {
        return IoComposeServiceDatabase.otherDownTableSuffix;
    }
}
