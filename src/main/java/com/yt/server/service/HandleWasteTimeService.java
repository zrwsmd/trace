package com.yt.server.service;

import com.yt.server.entity.TraceFieldMeta;
import com.yt.server.entity.TraceTableRelatedInfo;
import com.yt.server.entity.TraceTimestampStatistics;
import com.yt.server.entity.UniPoint;
import com.yt.server.mapper.TraceFieldMetaMapper;
import com.yt.server.mapper.TraceTableRelatedInfoMapper;
import com.yt.server.mapper.TraceTimestampStatisticsMapper;
import com.yt.server.util.AdaptiveDownsamplingSelector;
import com.yt.server.util.BaseUtils;
import com.yt.server.util.VarConst;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import static com.yt.server.service.IoComposeServiceDatabase.*;
import static com.yt.server.util.BaseUtils.convertList2Uni;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/5/9 17:29
 * @version:1.0
 */
@Service
public class HandleWasteTimeService {

    private static final Logger logger = LoggerFactory.getLogger(HandleWasteTimeService.class);

    @Autowired
    private TraceFieldMetaMapper traceFieldMetaMapper;

    @Autowired
    private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

    @Autowired
    private TraceTimestampStatisticsMapper traceTimestampStatisticsMapper;
    final Object lock = new Object();

    int currentShardNum = 0;

    @Autowired
    private DataSource dataSource;

    @Async(VarConst.THREAD_POOL)
    @Order(100)
    public void handleDownSamplingBusiness(List<TraceFieldMeta> traceFieldMetaList, ThreadPoolExecutor pool, JdbcTemplate jdbcTemplate, String downsamplingTableName) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(traceFieldMetaList.size());
        for (TraceFieldMeta traceFieldMeta : traceFieldMetaList) {
            final String varName = traceFieldMeta.getVarName();
            pool.submit(new BackGroundDownSamplingTask(varName, jdbcTemplate, downsamplingTableName, countDownLatch));
        }
        countDownLatch.await();
        logger.info("async handleDownSamplingBusiness method completed");
    }

    @Async(VarConst.THREAD_POOL)
    @Order(101)
    public void handleHistoryDownSamplingData(String tableName, TraceTableRelatedInfo traceTableRelatedInfo, JdbcTemplate jdbcTemplate, Long traceId, String oldFieldMetaIds) {
        String downsamplingTableName = tableName.concat("_downsampling");
        final String oldName = traceTableRelatedInfo.getOldVarNames();
        String[] oldVarNameList = oldName.split(",");
        for (String oldVarName : oldVarNameList) {
            for (Integer downRate : IoComposeServiceDatabase.data) {
                String downsamplingSql = "drop table " + downsamplingTableName.concat("_").concat(oldVarName).concat("_").concat(String.valueOf(downRate));
                jdbcTemplate.update(downsamplingSql);
            }
        }
        String tableSql = "drop table " + tableName;
        jdbcTemplate.update(tableSql);
        Consumer<String> consumer = (s) -> {
            for (int a = 0; a < shardNum; a++) {
                String sql = "drop table " + s.concat("_").concat(String.valueOf(a));
                jdbcTemplate.update(sql);
            }
        };
        consumer.accept(tableName);
        final List<String> oldFieldMetaList = Arrays.asList(oldFieldMetaIds.split(","));
        List<Long> idList = new ArrayList<>();
        for (String str : oldFieldMetaList) {
            idList.add(Long.parseLong(str));
        }
        traceFieldMetaMapper.deleteByIds(idList);
//        Object[] param = new Object[]{oldFieldMetaList};
//        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
//        String traceFieldMetaDeleteSql = "DELETE FROM trace_field_meta WHERE id (:oldFieldMetaList) ";
//        jdbcTemplate.update(traceFieldMetaDeleteSql, param);
        logger.info("async handleHistoryDownSamplingData method completed");
    }

    @Async(VarConst.THREAD_POOL)
    public void insertDownsamplingData(Long traceId, JdbcTemplate jdbcTemplate, int shardNum, Collection<String> varNames, Integer per, String fromType) throws SQLException, ClassNotFoundException {
        synchronized (lock) {
            try (Connection connection = dataSource.getConnection()) {
                TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
                final String parentDownsamplingTableName = traceTableRelatedInfo.getDownsamplingTableName();
                final String tableName = traceTableRelatedInfo.getTableName();
                long total = 0L;
                TraceTimestampStatistics traceTimestampStatistics = traceTimestampStatisticsMapper.selectByPrimaryKey(traceId);
                logger.info("traceId={}", traceId);
                int downSamplingRate = 4;
//                if (traceTimestampStatistics == null || traceTimestampStatistics.getLastEndTimestamp() == 0) {
//                    for (int i = 0; i < shardNum; i++) {
//                        String originalRegionCountSql = "select count(*) from " + tableName.concat("_").concat(String.valueOf(i));
//                        Integer eachNum = jdbcTemplate.queryForObject(originalRegionCountSql, Integer.class);
//                        if (eachNum == null || eachNum == 0) {
//                            currentShardNum = i - 1;
//                            break;
//                        }
//                        total += eachNum;
//                    }
//                    if (total >= IoComposeServiceDatabase.DOWNSAMPLING_BATCH) {
//                        String originalRegionCountSql = "select min(id),max(id) from " + tableName.concat("_").concat(String.valueOf(currentShardNum));
//                        List<TraceParamCount> traceParamCountList = jdbcTemplate.query(originalRegionCountSql, (rs, rowNum) -> {
//                            TraceParamCount traceParamCount = new TraceParamCount();
//                            traceParamCount.setMin(rs.getLong(1));
//                            traceParamCount.setMax(rs.getLong(2));
//                            return traceParamCount;
//                        });
//                        TraceParamCount traceParamCount = traceParamCountList.get(0);
//                        Object[] regionParam = new Object[]{traceParamCount.getMin(), traceParamCount.getMax()};
//                        String originalRegionSql = "select * from " + tableName.concat("_").concat(String.valueOf(currentShardNum)) + " where id between ? and ? ";
//                        // List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
//                        final List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
//                        List<UniPoint> uniPointList = convertList2Uni(list);
//                        for (String varName : varNames) {
////                            varName = varName.trim();
////                            String finalVarName = varName;
//                            List<UniPoint> singleVarDataList = uniPointList.stream().filter(item -> varName.equals(item.getVarName())).toList();
//                            if (CollectionUtils.isNotEmpty(singleVarDataList)) {
//                                List<UniPoint> originalFilterVarDataList = new CopyOnWriteArrayList<>(singleVarDataList);
//                                if (originalFilterVarDataList.size() <= downSamplingRate) {
//                                    continue;
//                                }
//                                int bucketSize = originalFilterVarDataList.size() / downSamplingRate;
//                                List<UniPoint> downsampledList = AdaptiveDownsamplingSelector.downsample(originalFilterVarDataList, bucketSize, AdaptiveDownsamplingSelector.ExecType.ASYNC_TYPE);
//                                String downsamplingTableName = parentDownsamplingTableName.concat("_").concat(varName).concat("_").concat(String.valueOf(downSamplingRate));
//                                //save to database
//                                List<Object[]> dataObjArr = convertPojoList2ObjListArr(downsampledList, 2);
//                                BaseUtils.executeDownsamplingBatchUpdate(connection, downsamplingTableName, dataObjArr);
//                                Pair<List<UniPoint>, Integer> firstDownsamplingPair = handleBigDownsampling(downsampledList, varName, 2, connection, downSamplingRate, parentDownsamplingTableName);
//                                Pair<List<UniPoint>, Integer> secondDownsamplingPair = handleBigDownsampling(downsampledList, varName, 8, connection, downSamplingRate, parentDownsamplingTableName);
//                                Pair<List<UniPoint>, Integer> middleDownsamplingPair = handleBigDownsampling(secondDownsamplingPair.getLeft(), varName, 2, connection, secondDownsamplingPair.getRight(), parentDownsamplingTableName);
//                                Pair<List<UniPoint>, Integer> thirdDownsamplingPair = handleBigDownsampling(middleDownsamplingPair.getLeft(), varName, 2, connection, middleDownsamplingPair.getRight(), parentDownsamplingTableName);
//                                handleBigDownsampling(thirdDownsamplingPair.getLeft(), varName, 2, connection, thirdDownsamplingPair.getRight(), parentDownsamplingTableName);
//                                handleBigDownsampling(thirdDownsamplingPair.getLeft(), varName, 4, connection, thirdDownsamplingPair.getRight(), parentDownsamplingTableName);
//                            }
//                        }
//                        traceTimestampStatistics = new TraceTimestampStatistics();
//                        traceTimestampStatistics.setTraceId(traceId);
//                        traceTimestampStatistics.setTempTimestamp(traceParamCount.getMin());
//                        traceTimestampStatistics.setLastEndTimestamp(traceParamCount.getMax());
//                        traceTimestampStatistics.setReachedBatchNum(1);
//                        traceTimestampStatisticsMapper.insert(traceTimestampStatistics);
//                    }
//                }
                for (int i = 0; i < shardNum; i++) {
                    String originalRegionCountSql = "select count(*) from " + tableName.concat("_").concat(String.valueOf(i));
                    Integer eachNum = jdbcTemplate.queryForObject(originalRegionCountSql, Integer.class);
                    if (eachNum == null || eachNum == 0) {
                        currentShardNum = i - 1;
                        break;
                    }
                }
                Long lastMaxTimestamp = traceTimestampStatistics.getLastEndTimestamp();
                String originalRegionCountSql = "select max(id) from " + tableName.concat("_").concat(String.valueOf(currentShardNum));
                Long currentMaxTimestamp = jdbcTemplate.queryForObject(originalRegionCountSql, Long.class);
                String countSql = "select count(*) from " + tableName.concat("_").concat(String.valueOf(currentShardNum));
                Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
                if (count != null) {
                    if (count >= (totalSize / shardNum)) {
                        //假如lastMaxTimestamp=9965990，currentMaxTimestamp=9999990，此时0号表已经写满，但是9965990-9999990还没写入因为不满DOWNSAMPLING_BATCH，如果不加这一行，下一次currentShardNum加1此时开始读取1号表，0号表这个区间的数据就不会被写入降采样表了，数据就有空缺了
                        handleDownData(jdbcTemplate, varNames, connection, parentDownsamplingTableName, tableName, traceTimestampStatistics, downSamplingRate, lastMaxTimestamp, currentMaxTimestamp);
                        if (currentShardNum < shardNum - 1) {
                            currentShardNum = currentShardNum + 1;
                        } else {
                            return;
                        }
                    }
                }
                if (currentMaxTimestamp == null || lastMaxTimestamp == null) {
                    logger.info("traceId:{}", traceId);
                    logger.info("count:{}", count);
                    logger.info("originalRegionCountSql:{}", originalRegionCountSql);
                }
                if (currentMaxTimestamp != null && lastMaxTimestamp != null && (currentMaxTimestamp - lastMaxTimestamp) / per >= DOWNSAMPLING_BATCH) {
                    //writeTimestampToD4("ok",lastMaxTimestamp, currentMaxTimestamp);
                    handleDownData(jdbcTemplate, varNames, connection, parentDownsamplingTableName, tableName, traceTimestampStatistics, downSamplingRate, lastMaxTimestamp, currentMaxTimestamp);
                }
            }
        }

    }

    @Async(VarConst.THREAD_POOL)
    public void handleDownData(JdbcTemplate jdbcTemplate, Collection<String> varNames, Connection connection, String parentDownsamplingTableName, String tableName, TraceTimestampStatistics traceTimestampStatistics, int downSamplingRate, Long lastMaxTimestamp, Long currentMaxTimestamp) throws SQLException, ClassNotFoundException {
        Object[] regionParam = new Object[]{lastMaxTimestamp, currentMaxTimestamp};
        String originalRegionSql = "select * from " + tableName.concat("_").concat(String.valueOf(currentShardNum)) + " where id between ? and ? ";
        // List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
        final List<Map<String, Object>> list = jdbcTemplate.queryForList(originalRegionSql, regionParam);
        List<UniPoint> uniPointList = convertList2Uni(list);
        if (CollectionUtils.isNotEmpty(uniPointList)) {
            for (String varName : varNames) {
                List<UniPoint> singleVarDataList = uniPointList.stream().filter(item -> varName.equals(item.getVarName())).toList();
                if (CollectionUtils.isNotEmpty(singleVarDataList)) {
                    List<UniPoint> originalFilterVarDataList = new CopyOnWriteArrayList<>(singleVarDataList);
                    if (originalFilterVarDataList.size() <= downSamplingRate) {
                        continue;
                    }
                    int bucketSize = originalFilterVarDataList.size() / downSamplingRate;
                    List<UniPoint> downsampledList = AdaptiveDownsamplingSelector.downsample(originalFilterVarDataList, bucketSize, AdaptiveDownsamplingSelector.ExecType.HANDLE_DOWNDATA);
                    String downsamplingTableName = parentDownsamplingTableName.concat("_").concat(varName).concat("_").concat(String.valueOf(downSamplingRate));
                    //save to database
                    List<Object[]> dataObjArr = convertPojoList2ObjListArr(downsampledList, 2);
                    BaseUtils.executeDownsamplingBatchUpdate(connection, downsamplingTableName, dataObjArr);
                    Pair<List<UniPoint>, Integer> firstDownsamplingPair = handleBigDownsampling(downsampledList, varName, 2, connection, downSamplingRate, parentDownsamplingTableName);
                    Pair<List<UniPoint>, Integer> secondDownsamplingPair = handleBigDownsampling(downsampledList, varName, 8, connection, downSamplingRate, parentDownsamplingTableName);
                    Pair<List<UniPoint>, Integer> middleDownsamplingPair = handleBigDownsampling(secondDownsamplingPair.getLeft(), varName, 2, connection, secondDownsamplingPair.getRight(), parentDownsamplingTableName);
                    Pair<List<UniPoint>, Integer> thirdDownsamplingPair = handleBigDownsampling(middleDownsamplingPair.getLeft(), varName, 2, connection, middleDownsamplingPair.getRight(), parentDownsamplingTableName);
                    handleBigDownsampling(thirdDownsamplingPair.getLeft(), varName, 2, connection, thirdDownsamplingPair.getRight(), parentDownsamplingTableName);
                    handleBigDownsampling(thirdDownsamplingPair.getLeft(), varName, 4, connection, thirdDownsamplingPair.getRight(), parentDownsamplingTableName);
                }
                traceTimestampStatistics.setTempTimestamp(lastMaxTimestamp);
                traceTimestampStatistics.setLastEndTimestamp(currentMaxTimestamp);
                traceTimestampStatistics.setReachedBatchNum(traceTimestampStatistics.getReachedBatchNum() + 1);
                traceTimestampStatisticsMapper.updateByPrimaryKey(traceTimestampStatistics);
            }
        }

//    @Async(VarConst.THREAD_POOL)
//    public void handleGc() {
//        System.gc();
//    }

    }

    public void resetShardState() {
        this.currentShardNum = 0;
        logger.info("HandleWasteTimeService: currentShardNum reset to 0");
    }
}
