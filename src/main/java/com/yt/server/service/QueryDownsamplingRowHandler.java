package com.yt.server.service;

import com.yt.server.entity.TraceDownsampling;
import org.apache.commons.collections.map.MultiValueMap;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.*;

import static com.yt.server.util.BaseUtils.convert2MultiMapForTraceDownSampling;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */
@Deprecated
public class QueryDownsamplingRowHandler implements Callable<MultiValueMap> {

    public final String queryTable;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;
    private final String varName;
    private final Integer closestRate;


    public QueryDownsamplingRowHandler(String queryTable, Long reqStartTimestamp, Long reqEndTimestamp, JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch, String varName, Integer closestRate) {
        this.queryTable = queryTable;
        this.reqStartTimestamp = reqStartTimestamp;
        this.reqEndTimestamp = reqEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.countDownLatch = countDownLatch;
        this.varName = varName;
        this.closestRate = closestRate;
    }


    @Override
    public MultiValueMap call() throws Exception {
        Object[] samplingParam = new Object[]{reqStartTimestamp, reqEndTimestamp,varName,closestRate };
        String samplingSql = " select varName, timestamp, value from " + queryTable + "  where  timestamp between ? and ? and varName=? and downSamplingRate=?  ";
        List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(samplingSql, samplingParam, new BeanPropertyRowMapper<>(TraceDownsampling.class));
        MultiValueMap multiValueMap = convert2MultiMapForTraceDownSampling(traceDownsamplingList,varName,null);
        countDownLatch.countDown();
        return multiValueMap;
    }
}
