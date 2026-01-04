package com.yt.server.service;

import com.yt.server.entity.TraceDownsampling;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yt.server.util.BaseUtils.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/3/6 15:13
 * @version:1.0
 */

public class QueryEachDownsamplingRowHandler implements Callable<List<UniPoint>> {

    private static final Logger logger = LoggerFactory.getLogger(QueryEachDownsamplingRowHandler.class);

    public final String queryTable;
    private final Long currentStartTimestamp;
    private final Long currentEndTimestamp;
    private final JdbcTemplate jdbcTemplate;
    private final CountDownLatch countDownLatch;
    private final String varName;
    private final Integer closestRate;
    private final Long reqStartTimestamp;
    private final Long reqEndTimestamp;
    private final Integer shardNum;
    private final List<Map<String, String>> mapList;


    public QueryEachDownsamplingRowHandler(String queryTable, Long currentStartTimestamp, Long currentEndTimestamp, JdbcTemplate jdbcTemplate, CountDownLatch countDownLatch, String varName, Integer closestRate, Long reqStartTimestamp, Long reqEndTimestamp, Integer shardNum, List<Map<String, String>> mapList) {
        this.queryTable = queryTable;
        this.currentStartTimestamp = currentStartTimestamp;
        this.currentEndTimestamp = currentEndTimestamp;
        this.jdbcTemplate = jdbcTemplate;
        this.countDownLatch = countDownLatch;
        this.varName = varName;
        this.closestRate = closestRate;
        this.reqStartTimestamp = reqStartTimestamp;
        this.reqEndTimestamp = reqEndTimestamp;
        this.shardNum = shardNum;
        this.mapList = mapList;
    }


    @Override
    public List<UniPoint> call() throws Exception {
        List<UniPoint> uniPointList = null;
        try {
            Object[] samplingParam = new Object[]{currentStartTimestamp, currentEndTimestamp};
//            if (currentStartTimestamp.equals(reqStartTimestamp)) {
//                queryBorderData(allValueMap, currentStartTimestamp);
//            }
            String realQueryTable = queryTable.concat("_").concat(String.valueOf(closestRate));
            String samplingSql = " select timestamp, value from " + realQueryTable + "  where  timestamp between ? and ?  ";
            List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(samplingSql, samplingParam, new BeanPropertyRowMapper<>(TraceDownsampling.class));
            uniPointList = convertTraceDownsampling2UniPoint(traceDownsamplingList, varName);
//            if (currentEndTimestamp.equals(reqEndTimestamp)) {
//                queryBorderData(allValueMap, currentEndTimestamp);
//            }
            countDownLatch.countDown();
        } catch (DataAccessException e) {
            logger.error(QueryEachDownsamplingRowHandler.class.getName(), e);
        }
        return uniPointList;
    }


    private void queryBorderData(MultiValueMap allValueMap, Long currentTimestamp) throws NoSuchFieldException, IllegalAccessException {
        try {
            String realQueryTable = queryTable.concat("_").concat(String.valueOf(closestRate));
            MultiValueMap multiValueMap = new MultiValueMap();
            Object[] singleParam = new Object[]{currentTimestamp};
            String samplingSql = " select timestamp, value from " + realQueryTable + "  where  timestamp =?  ";
            List<TraceDownsampling> traceDownsamplingList = jdbcTemplate.query(samplingSql, singleParam, new BeanPropertyRowMapper<>(TraceDownsampling.class));
            //降采样表没有这条数据，才去全量表查询
            if (CollectionUtils.isEmpty(traceDownsamplingList)) {
                final String shardTable = getShardTable(realQueryTable, currentTimestamp, shardNum);
                Object[] fullTableParam = new Object[]{currentTimestamp};
                String fullTableSql = "select " + varName + " from " + shardTable + " where id =? ";
                final List<Map<String, Object>> list = jdbcTemplate.queryForList(fullTableSql, fullTableParam);
                if (!CollectionUtils.isEmpty(list)) {
                    final Map<String, Object> map = list.get(0);
                    final Set<String> keySet = map.keySet();
                    for (String varName : keySet) {
                        multiValueMap.put(parseOriginalVarName(varName, mapList), new BigDecimal[]{BigDecimal.valueOf(currentTimestamp), getBigDecimal(map.get(varName))});
                    }
                    allValueMap.putAll(multiValueMap);
                } else {
                    logger.info("请求的时间点【{}】不存在", currentTimestamp);
                }
            }
        } catch (Exception e) {
            logger.error(QueryEachDownsamplingTableHandler.class.getName(), e);
        }
    }
}
