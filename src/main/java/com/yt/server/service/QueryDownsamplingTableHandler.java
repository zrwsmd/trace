package com.yt.server.service;

import org.apache.commons.collections.map.MultiValueMap;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */
@Deprecated
public class QueryDownsamplingTableHandler implements Callable<MultiValueMap> {

    public static final String DOMAIN_PREFIX = "com.yt.server.entity.";
    public final String queryTable;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;
    private final String varName;
    private final Integer closestRate;

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000));

    public QueryDownsamplingTableHandler(String queryTable, Long reqStartTimestamp, Long reqEndTimestamp,
                                         JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch, String varName, Integer closestRate) {
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
        MultiValueMap allMultiValueMap = null;
        try {
            allMultiValueMap = new MultiValueMap();
            int size = 0;
            if ((reqEndTimestamp - reqStartTimestamp) % 500 != 0) {
                size = 501;
            } else if ((reqEndTimestamp - reqStartTimestamp) % 500 == 0) {
                size = 500;
            }
            CountDownLatch innerCountDownLatch = new CountDownLatch(size);
            final long shard = (reqEndTimestamp - reqStartTimestamp) / 500;
            List<Future<MultiValueMap>> resultList = new ArrayList<>();
            // 60000 1000000 60000 154000 154001 248000
            for (int i = 0; i < 500; i++) {
                if (i == 0) {
                    Future<MultiValueMap> future = pool.submit(new QueryDownsamplingRowHandler(queryTable,
                            reqStartTimestamp, reqStartTimestamp + shard * (i + 1), jdbcTemplate, innerCountDownLatch,
                            varName, closestRate));
                    resultList.add(future);
                } else {
                    Future<MultiValueMap> future = pool.submit(new QueryDownsamplingRowHandler(queryTable,
                            reqStartTimestamp + shard * (i) + 1, reqStartTimestamp + shard * (i + 1), jdbcTemplate,
                            innerCountDownLatch, varName, closestRate));
                    resultList.add(future);
                }
            }
            if ((reqEndTimestamp - reqStartTimestamp) % 500 != 0) {
                Future<MultiValueMap> leftFuture = pool
                        .submit(new QueryDownsamplingRowHandler(queryTable, reqStartTimestamp + shard * 500 + 1,
                                reqEndTimestamp, jdbcTemplate, innerCountDownLatch, varName, closestRate));
                resultList.add(leftFuture);
            }
            innerCountDownLatch.await();
            for (Future<MultiValueMap> future : resultList) {
                MultiValueMap map = future.get();
                if (map != null) {
                    allMultiValueMap.putAll(map);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
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
        return allMultiValueMap;
    }
}
