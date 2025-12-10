package com.yt.server.service;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/14 10:17
 * @version:1.0
 */
public class SingleLineRule implements TraceRule{
    @Override
    public int getReqNum() {
        return 20000;
    }

    @Override
    public String downTableSuffix() {
        return IoComposeServiceDatabase.middleDownTableSuffix;
    }
}
