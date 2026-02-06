package com.yt.server.service;

import org.apache.commons.collections.map.MultiValueMap;
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

public class QueryFullTableHandler implements Callable<MultiValueMap> {

    private static final Logger logger = LoggerFactory.getLogger(QueryFullTableHandler.class);

    public final String queryTable;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final String fieldName;
    private final List<Map<String, String>> mapList;

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 60,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public QueryFullTableHandler(String queryTable, Long reqStartTimestamp, Long reqEndTimestamp,
                                 JdbcTemplate jdbcTemplate, String fieldName,
                                 List<Map<String, String>> mapList) {
        this.queryTable = queryTable;
        this.reqStartTimestamp = reqStartTimestamp;
        this.reqEndTimestamp = reqEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.fieldName = fieldName;
        this.mapList = mapList;
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
                    Future<MultiValueMap> future = pool.submit(
                            new QueryFullRowHandler(queryTable, reqStartTimestamp, reqStartTimestamp + shard * (i + 1),
                                    jdbcTemplate, innerCountDownLatch, fieldName, mapList));
                    resultList.add(future);
                } else {
                    Future<MultiValueMap> future = pool.submit(new QueryFullRowHandler(queryTable,
                            reqStartTimestamp + shard * (i) + 1, reqStartTimestamp + shard * (i + 1), jdbcTemplate,
                            innerCountDownLatch, fieldName, mapList));
                    resultList.add(future);
                }
            }
            if ((reqEndTimestamp - reqStartTimestamp) % 500 != 0) {
                Future<MultiValueMap> leftFuture = pool
                        .submit(new QueryFullRowHandler(queryTable, reqStartTimestamp + shard * 500 + 1,
                                reqEndTimestamp, jdbcTemplate, innerCountDownLatch, fieldName, mapList));
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
            logger.error(QueryFullTableHandler.class.getName(), e);
            // throw e;
        } finally {
            pool.shutdown();
        }
        return allMultiValueMap;
    }
}
