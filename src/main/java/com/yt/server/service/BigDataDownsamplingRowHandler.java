package com.yt.server.service;

import com.yt.server.entity.TraceDownsampling;
import com.yt.server.entity.UniPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yt.server.util.BaseUtils.convertTraceDownsampling2UniPoint;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */

public class BigDataDownsamplingRowHandler implements Callable<List<UniPoint>> {

    private static final Logger logger = LoggerFactory.getLogger(QueryEachDownsamplingRowHandler.class);

    public final String queryTable;
    private final Long currentStartTimestamp;
    private final Long currentEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;
    private final String varName;
    private final List<Map<String, String>> mapList;


    public BigDataDownsamplingRowHandler(String queryTable, Long currentStartTimestamp, Long currentEndTimestamp, JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch, String varName, List<Map<String, String>> mapList) {
        this.queryTable = queryTable;
        this.currentStartTimestamp = currentStartTimestamp;
        this.currentEndTimestamp = currentEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.countDownLatch = countDownLatch;
        this.varName = varName;
        this.mapList = mapList;
    }


    @Override
    public List<UniPoint> call() throws Exception {
        List<UniPoint> uniPointList = null;
        try {
            Object[] samplingParam = new Object[]{currentStartTimestamp, currentEndTimestamp};
            String samplingSql = " select timestamp, value from " + queryTable + "  where  timestamp between ? and ?  ";
            List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(samplingSql, samplingParam, new BeanPropertyRowMapper<>(TraceDownsampling.class));
            uniPointList = convertTraceDownsampling2UniPoint(traceDownsamplingList, varName);
            countDownLatch.countDown();
        } catch (DataAccessException e) {
            logger.error(BigDataDownsamplingRowHandler.class.getName(), e);
        }
        return uniPointList;
    }


}
