package com.yt.server.service;

import com.yt.server.util.VarConst;
import org.apache.commons.collections.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;


import static com.yt.server.util.BaseUtils.convertList2MultiMap;

/**
 * @description:针对每个分区表后的查询再次进行多线程优化(针对起始时间戳分片优化)
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */

public class QueryFullRowHandler implements Callable<MultiValueMap> {

    private static final Logger logger = LoggerFactory.getLogger(QueryFullRowHandler.class);

    public final String queryTable;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;
    private final String fieldName;
    private final List<Map<String, String>> mapList;

    public QueryFullRowHandler(String queryTable, Long reqStartTimestamp, Long reqEndTimestamp, JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch, String fieldName, List<Map<String, String>> mapList) {
        this.queryTable = queryTable;
        this.reqStartTimestamp = reqStartTimestamp;
        this.reqEndTimestamp = reqEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.countDownLatch = countDownLatch;
        this.fieldName = fieldName;
        this.mapList = mapList;
    }


    @Override
    public MultiValueMap call() throws Exception {
        MultiValueMap multiValueMap = null;
        try {
            Object[] regionParam = new Object[]{reqStartTimestamp, reqEndTimestamp};
            String allFieldName = VarConst.ID.concat(",").concat(fieldName);
            String originalRegionSql = "select " + allFieldName + " from " + queryTable + " where id between ? and ? ";
            // List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
            final List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
            multiValueMap = convertList2MultiMap(list, mapList);
            countDownLatch.countDown();
        } catch (Exception e) {
            logger.error(QueryFullRowHandler.class.getName(), e);
        }
        return multiValueMap;
    }
}
