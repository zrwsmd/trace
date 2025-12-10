package com.yt.server.service;

import com.yt.server.entity.TraceTableRelatedInfo;
import com.yt.server.util.BaseUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yt.server.util.BaseUtils.convertList2MultiMap;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/17 15:14
 * @version:1.0
 */
public class SingleTimestampFileHandler implements Callable<MultiValueMap> {

    private final String allFieldName;
    private final List<Map<String, String>> mapList;
    private Long reqTimestamp;
    private final TraceTableRelatedInfo traceTableRelatedInfo;
    private final int per;
    private final int bucket;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;

    public SingleTimestampFileHandler(String allFieldName, List<Map<String, String>> mapList, Long reqTimestamp, TraceTableRelatedInfo traceTableRelatedInfo, int per, int bucket, JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch) {
        this.allFieldName = allFieldName;
        this.mapList = mapList;
        this.reqTimestamp = reqTimestamp;
        this.traceTableRelatedInfo = traceTableRelatedInfo;
        this.per = per;
        this.bucket = bucket;
        this.jdbcTemplate = jdbcTemplate;
        this.countDownLatch = countDownLatch;
    }


    @Override
    public MultiValueMap call() throws Exception {
        final String currentTraceTableName = traceTableRelatedInfo.getTableName();
        reqTimestamp = BaseUtils.getCircleStamp(reqTimestamp, per);
        Object[] regionParam = new Object[]{reqTimestamp};
        String sql = "select " + allFieldName + "  from " + currentTraceTableName.concat("_").concat(String.valueOf(bucket)) + " where id=?";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, regionParam);
        MultiValueMap multiValueMap = convertList2MultiMap(list, mapList);
        System.out.println("getSingleTimestampFileHandleExecutor=" + Thread.currentThread().getName());
        countDownLatch.countDown();
        return multiValueMap;
    }
}
