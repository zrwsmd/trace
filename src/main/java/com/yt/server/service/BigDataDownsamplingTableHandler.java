package com.yt.server.service;

import com.yt.server.entity.UniPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */

public class BigDataDownsamplingTableHandler implements Callable<List<UniPoint>> {

    private static final Logger logger = LoggerFactory.getLogger(QueryEachDownsamplingTableHandler.class);

    public static final String DOMAIN_PREFIX = "com.yt.server.entity.";
    public final String queryTable;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final String varName;
    private final List<Map<String, String>> mapList;

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public BigDataDownsamplingTableHandler(String queryTable, Long reqStartTimestamp, Long reqEndTimestamp,
                                           JdbcTemplate jdbcTemplate, String varName,
                                           List<Map<String, String>> mapList) {
        this.queryTable = queryTable;
        this.reqStartTimestamp = reqStartTimestamp;
        this.reqEndTimestamp = reqEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.varName = varName;
        this.mapList = mapList;
    }

    @Override
    public List<UniPoint> call() throws Exception {
        List<UniPoint> allUniPointList = new ArrayList<>();
        try {
            int size = 0;
            if ((reqEndTimestamp - reqStartTimestamp) % 500 != 0) {
                size = 501;
            } else if ((reqEndTimestamp - reqStartTimestamp) % 500 == 0) {
                size = 500;
            }
            CountDownLatch innerCountDownLatch = new CountDownLatch(size);
            final long shard = (reqEndTimestamp - reqStartTimestamp) / 500;
            List<Future<List<UniPoint>>> resultList = new ArrayList<>();
            // 60000 1000000 60000 154000 154001 248000
            for (int i = 0; i < 500; i++) {
                if (i == 0) {
                    Future<List<UniPoint>> future = pool.submit(new BigDataDownsamplingRowHandler(queryTable,
                            reqStartTimestamp, reqStartTimestamp + shard * (i + 1), jdbcTemplate, innerCountDownLatch,
                            varName, mapList));
                    resultList.add(future);
                } else {
                    Future<List<UniPoint>> future = pool.submit(new BigDataDownsamplingRowHandler(queryTable,
                            reqStartTimestamp + shard * (i) + 1, reqStartTimestamp + shard * (i + 1), jdbcTemplate,
                            innerCountDownLatch, varName, mapList));
                    resultList.add(future);
                }
            }
            if ((reqEndTimestamp - reqStartTimestamp) % 500 != 0) {
                Future<List<UniPoint>> leftFuture = pool
                        .submit(new BigDataDownsamplingRowHandler(queryTable, reqStartTimestamp + shard * 500 + 1,
                                reqEndTimestamp, jdbcTemplate, innerCountDownLatch, varName, mapList));
                resultList.add(leftFuture);
            }
            innerCountDownLatch.await();
            for (Future<List<UniPoint>> future : resultList) {
                List<UniPoint> list = future.get();
                if (list != null) {
                    allUniPointList.addAll(list);
                }
            }
        } catch (Exception e) {
            logger.error(BigDataDownsamplingTableHandler.class.getName(), e);
            // throw e;
        } finally {
            pool.shutdown();
        }

        // Object[] samplingParam = new Object[]{reqStartTimestamp,
        // reqEndTimestamp,varName,closestRate };
        // String samplingSql = " select varName, timestamp, value from " + queryTable +
        // " where timestamp between ? and ? and varName=? and downSamplingRate=? ";
        // List<TraceDownsampling> traceDownsamplingList =
        // jdbcTemplate.query(samplingSql, samplingParam, new
        // BeanPropertyRowMapper<>(TraceDownsampling.class));
        // MultiValueMap multiValueMap =
        // convert2MultiMapForTraceDownSampling(traceDownsamplingList);
        // countDownLatch.countDown();
        return allUniPointList;
    }
}
