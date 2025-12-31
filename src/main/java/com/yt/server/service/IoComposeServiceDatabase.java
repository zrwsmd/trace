package com.yt.server.service;


import com.alibaba.fastjson.JSONObject;
import com.yt.server.entity.*;
import com.yt.server.mapper.TableNumInfoMapper;
import com.yt.server.mapper.TraceFieldMetaMapper;
import com.yt.server.mapper.TraceTableRelatedInfoMapper;
import com.yt.server.mapper.TraceTimestampStatisticsMapper;
import com.yt.server.util.AdaptiveDownsamplingSelector;
import com.yt.server.util.BaseUtils;
import com.yt.server.util.MysqlUtils;
import com.yt.server.util.VarConst;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.yt.server.util.BaseUtils.*;


@Service
public class IoComposeServiceDatabase {

    private static final Logger logger = LoggerFactory.getLogger(IoComposeServiceDatabase.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TraceFieldMetaMapper traceFieldMetaMapper;

    @Autowired
    private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

    @Autowired
    private TableNumInfoMapper tableNumInfoMapper;

    @Autowired
    private TraceTimestampStatisticsMapper traceTimestampStatisticsMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HandleWasteTimeService handleWasteTimeService;


    public static final int lagNum = 3000;

    //16384减去首位2个固定桶
    public static final int DOWNSAMPLING_BATCH = 4096;


    final Object lock = new Object();

    public static final String DOMAIN_PREFIX = "com.yt.server.entity.";

    public static final String[] DEFAULT_TABLE = new String[]{"table_num_info", "trace_field_meta", "trace_table_related_info", "trace_timestamp_statistics"};


    public static final String DATABASE_NAME = "trace";

    public static final Integer totalSize = 10000000;

    public static final Integer shardNum = 10;

    Set<String> backUpTableList = new TreeSet<>();

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();


    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1000));

    {
        backUpTableList.addAll(Arrays.asList(DEFAULT_TABLE));
//        downsamplingRate.add(2);
//        downsamplingRate.add(4);
//        downsamplingRate.add(8);
//        downsamplingRate.add(16);
    }

    public static Integer[] data = new Integer[]{8, 32, 128, 256, 512};

    Map<String, Integer> perMap = new ConcurrentHashMap<>();

    public static final Integer LAG = 10000;

    public static String baseDownTableSuffix = "256";

    public static String otherDownTableSuffix = "512";

    public static String middleDownTableSuffix = "128";

    public static final Integer firstPoint = 400000;

    //大于2000000条数据就512倍降采样
    public static final Integer highPoint = 2000000;

    public static final Integer fullDataReqNum = 2000;

    ReentrantLock reentrantLock = new ReentrantLock();

    private static final Integer defaultPer = 1000;

    public VsCodeRespVo traceCreate(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            String traceName = "";
            String traceConfig = "";
            for (String key : keySet) {
                if (VarConst.NAME.equals(key)) {
                    traceName = String.valueOf(jsonObject.get(key));
                    continue;
                }
                if (VarConst.TRACE_CONFIG.equals(key)) {
                    JSONObject json = new JSONObject((Map<String, Object>) jsonObject.get(VarConst.TRACE_CONFIG));
                    traceConfig = json.toString();
                }
            }
            Long traceId = generateTraceId();
            TraceTableRelatedInfo traceTableRelatedInfo = new TraceTableRelatedInfo();
            traceTableRelatedInfo.setTraceId(traceId);
            traceTableRelatedInfo.setTableName(null);
            traceTableRelatedInfo.setDownsamplingTableName(null);
            traceTableRelatedInfo.setTraceName(traceName);
            traceTableRelatedInfo.setTraceConfig(traceConfig);
            traceTableRelatedInfo.setTraceStatus(vsCodeReqParam.getType());
            traceTableRelatedInfo.setReachedBatchFlag("false");
            traceTableRelatedInfoMapper.insert(traceTableRelatedInfo);
            Map<String, Object> map = new HashMap<>();
            map.put("id", traceId);
            map.put("uri", "http://localhost:17777/io/getFileHandleExecutor");
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceCreate");
            responseVo.settData(new JSONObject(map));
        } catch (Exception e) {
            responseVo.setRet(false);
            logger.error("trace create 异常,报错信息为: " + e);
            e.printStackTrace();
            //   rollbackShardingTable().accept(tableName);
            return responseVo;
        }
        responseVo.setRet(true);
        logger.info("trace create successfully executed");
        return responseVo;
    }

    public VsCodeRespVo traceStart(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        List<TraceFieldMeta> traceFieldMetaList = new ArrayList<>();
        Long traceId = 0L;
        String traceConfig = "";
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    continue;
                }
                if (VarConst.TRACE_CONFIG.equals(key)) {
                    JSONObject json = new JSONObject((Map<String, Object>) jsonObject.get(VarConst.TRACE_CONFIG));
                    traceConfig = json.toString();
                    continue;
                }
                if (VarConst.PER.equals(key)) {
                    String per = String.valueOf(jsonObject.get(key));
                    if (perMap.size() == 0) {
                        perMap.put("per", Integer.valueOf(per));
//                        writeTimestampToD2(String.valueOf(perMap.get("per")));
                    } else {
                        perMap.clear();
                        perMap.put("per", Integer.valueOf(per));
//                        writeTimestampToD2(String.valueOf(perMap.get("per")));
                    }
                    continue;
                }
                if (VarConst.VARS.equals(key)) {
                    Object o = jsonObject.get(VarConst.VARS);
                    if (o instanceof List<?>) {
                        List list = (List) o;
                        for (Object inner : list) {
                            Map map = (Map) inner;
                            TraceFieldMeta traceFieldMeta = new TraceFieldMeta();
                            String varName = String.valueOf(map.get(VarConst.NAME));
                            varName = BaseUtils.erasePoint(varName);
                            traceFieldMeta.setVarName(varName);
                            String windowsType = String.valueOf(map.get(VarConst.TYPE));
                            traceFieldMeta.setWindowsType(windowsType);
                            traceFieldMeta.setMysqlType(windowsType2DatabaseType(windowsType));
                            traceFieldMetaList.add(traceFieldMeta);
                        }
                    }
                }
            }
            if (CollectionUtils.isEmpty(traceFieldMetaList)) {
                throw new RuntimeException("start状态变量列表不能为空!");
            }
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            String tableName = traceTableRelatedInfo.getTableName();
            String downsamplingTableName = null;
            Long finalTraceId = traceId;
            String oldVarNames = "";
            TableNumInfo originalTableNumInfo = tableNumInfoMapper.selectByPrimaryKey(1);
            String tempTableName = tableName;
            if (StringUtils.isEmpty(tableName)) {
                if (originalTableNumInfo == null) {
                    TableNumInfo tableNumInfo = new TableNumInfo();
                    tableNumInfo.setId(1);
                    tableNumInfo.setTableSeqNum(1);
                    tableNumInfoMapper.insert(tableNumInfo);
                    tableName = "trace".concat(String.valueOf(1));
                } else {
                    Integer seqNum = originalTableNumInfo.getTableSeqNum();
                    originalTableNumInfo.setTableSeqNum(seqNum + 1);
                    tableNumInfoMapper.updateByPrimaryKey(originalTableNumInfo);
                    tableName = "trace".concat(String.valueOf(seqNum + 1));
                }
                downsamplingTableName = tableName.concat("_downsampling");
            } else {
                String oldFieldMetaIds = traceTableRelatedInfo.getOldFieldMetaIds();
                handleWasteTimeService.handleHistoryDownSamplingData(tableName, traceTableRelatedInfo, jdbcTemplate, traceId, oldFieldMetaIds);
            }
            //不为空的话就是调用了很多次start了,此时异步删除之前的全量表和降采样表还没完成，直接使用以前的tableName会报错cannot create table xxx ,exist table xxx,所以需要tableName加1，并且更新table_releated_info表
            if (StringUtils.isNotEmpty(tempTableName)) {
                tableName = "trace".concat(String.valueOf(originalTableNumInfo.getTableSeqNum() + 1));
                downsamplingTableName = tableName.concat("_downsampling");
            }
            for (int i = 0; i < traceFieldMetaList.size(); i++) {
                if (i == traceFieldMetaList.size() - 1) {
                    oldVarNames = oldVarNames.concat(traceFieldMetaList.get(i).getVarName());
                } else {
                    oldVarNames = oldVarNames.concat(traceFieldMetaList.get(i).getVarName()).concat(",");
                }
            }
            traceFieldMetaList.forEach(item -> item.setTraceId(finalTraceId));
            traceFieldMetaMapper.insertBatch(traceFieldMetaList);
            List<Long> oldIdLists = traceFieldMetaList.stream().map(TraceFieldMeta::getId).toList();
            String oldFieldMetaIds = StringUtils.join(oldIdLists, ",");
            //创建对应trace表
            StringBuilder sql = new StringBuilder();
            sql.append(" CREATE TABLE " + "`").append(tableName).append("`").append("(");
            sql.append("`id` bigint NOT NULL,");
            //earn int  age  tinyint
            for (int i = 0; i < traceFieldMetaList.size(); i++) {
                if (i == traceFieldMetaList.size() - 1) {
                    sql.append("`").append(traceFieldMetaList.get(i).getVarName()).append("`").append("  ").append(traceFieldMetaList.get(i).getMysqlType()).append(" DEFAULT NULL, PRIMARY KEY (`id`))ENGINE=InnoDB ");
                } else {
                    sql.append("`").append(traceFieldMetaList.get(i).getVarName()).append("`").append("  ").append(traceFieldMetaList.get(i).getMysqlType()).append(" DEFAULT NULL, ");
                }
            }
            String sqlStr = sql.toString();
            jdbcTemplate.execute(sqlStr);
            generateShardingTable(tableName, traceFieldMetaList, traceId);
            handleWasteTimeService.handleDownSamplingBusiness(traceFieldMetaList, pool, jdbcTemplate, downsamplingTableName);
            traceTableRelatedInfo.setTraceConfig(traceConfig);
            traceTableRelatedInfo.setTraceStatus(vsCodeReqParam.getType());
            traceTableRelatedInfo.setTableName(tableName);
            traceTableRelatedInfo.setDownsamplingTableName(downsamplingTableName);
            traceTableRelatedInfo.setOldVarNames(oldVarNames);
            traceTableRelatedInfo.setOldFieldMetaIds(oldFieldMetaIds);
            traceTableRelatedInfoMapper.updateByPrimaryKey(traceTableRelatedInfo);
            if (StringUtils.isNotEmpty(tempTableName)) {
                //此时numInfo表不可能为空，所以不需要判断  因为上面的start方法如果被多次调用的话
                Integer seqNum = originalTableNumInfo.getTableSeqNum();
                originalTableNumInfo.setTableSeqNum(seqNum + 1);
                tableNumInfoMapper.updateByPrimaryKey(originalTableNumInfo);
                //比如trace stop的时候traceTimestampStatistics之前表里的offset并没有清除，所以需要删除之前那条数据，重新从头异步写入高倍降采样表,否则会从之前的那个时间点开始写，假如改了配置或者变量就有问题了
                traceTimestampStatisticsMapper.deleteByPrimaryKey(traceId);
            }
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceStart");
            responseVo.setRet(true);
        } catch (Exception e) {
            responseVo.setRet(false);
            e.printStackTrace();
            logger.error("trace start 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace start successfully executed");
        return responseVo;

    }

    public VsCodeRespVo traceStop(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        Long traceId = 0L;
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    break;
                }
            }
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            if (traceTableRelatedInfo != null) {
                traceTableRelatedInfo.setTraceStatus(vsCodeReqParam.getType());
                traceTableRelatedInfoMapper.updateByPrimaryKey(traceTableRelatedInfo);
            } else {
                responseVo.setRet(false);
                throw new RuntimeException("trace stop获取元数据信息失败");
            }
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceStop");
            responseVo.setRet(true);
        } catch (Exception e) {
            responseVo.setRet(false);
            logger.error("trace stop 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace stop successfully executed");
        return responseVo;
    }

    public VsCodeRespVo traceDestroy(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        Long traceId = 0L;
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    break;
                }
            }
            //删除主表和降采样表
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            final String traceStatus = traceTableRelatedInfo.getTraceStatus();
//            if (!"traceStop".equalsIgnoreCase(traceStatus)) {
//                responseVo.setRet(false);
//                throw new RuntimeException("当前trace状态为【" + traceStatus + "】,不是stop状态不能执行删除操作!,traceId= " + traceId);
//            }
            String tableName = traceTableRelatedInfo.getTableName();
            if (StringUtils.isNotEmpty(tableName)) {
                String sql = "drop table " + tableName;
                jdbcTemplate.update(sql);
                rollbackShardingTable().accept(tableName);
            }
            String downsamplingTableName = traceTableRelatedInfo.getDownsamplingTableName();
            final List<TraceFieldMeta> traceFieldMetaList = traceFieldMetaMapper.getCurrentFieldNames(traceId);
            if (CollectionUtils.isNotEmpty(traceFieldMetaList)) {
                for (TraceFieldMeta traceFieldMeta : traceFieldMetaList) {
                    for (Integer downRate : data) {
                        String downsamplingSql = "drop table " + downsamplingTableName.concat("_").concat(traceFieldMeta.getVarName()).concat("_").concat(String.valueOf(downRate));
                        jdbcTemplate.update(downsamplingSql);
                    }
                }
                Object[] param = new Object[]{traceId};
                String traceFieldMetaDeleteSql = "DELETE FROM trace_field_meta WHERE traceId=? ";
                jdbcTemplate.update(traceFieldMetaDeleteSql, param);
            }
            traceTableRelatedInfoMapper.deleteByPrimaryKey(traceId);
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceDestroy");
            responseVo.setRet(true);
        } catch (Exception e) {
            responseVo.setRet(false);
            logger.error("trace destroy 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace destroy successfully executed");
        return responseVo;
    }


    public static List<Object[]> convertPojoList2ObjListArr(List<UniPoint> singleVarDataList, int size) {
        List<Object[]> objects = new ArrayList<>();
        for (UniPoint uniPoint : singleVarDataList) {
            Object[] obj = new Object[size];
            obj[0] = uniPoint.getX();
            obj[1] = uniPoint.getY();
            objects.add(obj);
        }
        return objects;

    }

    public MultiValueMap getFileHandleExecutor(RequestParameter vsCodeReqParam) throws Exception {
        System.out.println("getFileHandleExecutor=" + Thread.currentThread().getName());
        Long reqStartTimestamp = vsCodeReqParam.getStartTimestamp();
        Long reqEndTimestamp = vsCodeReqParam.getEndTimestamp();
        if (reqStartTimestamp == null) {
            throw new RuntimeException("开始时间戳不能为空!");
        }
        if (reqEndTimestamp == null) {
            throw new RuntimeException("结束时间戳不能为空!");
        }
        if (reqStartTimestamp > reqEndTimestamp) {
            throw new RuntimeException("开始时间戳不能大于结束时间戳!");
        }
        if (reqStartTimestamp < 0) {
            reqStartTimestamp = 0L;
        }
        final Long traceId = vsCodeReqParam.getTraceId();
        final List<String> originalVarList = vsCodeReqParam.getVarList();
        TraceRule traceRule = null;
        if (originalVarList.size() == 1) {
            traceRule = new SingleLineRule();
        } else if (originalVarList.size() > 1 && originalVarList.size() <= 5) {
            traceRule = new MiddleLineRule();
        } else if (originalVarList.size() > 5 && originalVarList.size() <= 10) {
            traceRule = new MiddleHigherLineRule();
        } else if (originalVarList.size() > 10 && originalVarList.size() <= 15) {
            traceRule = new LastLineRule();
        } else if (originalVarList.size() > 15) {
            traceRule = new LastHigherLineRule();
        }
//        Integer reqNum = traceRule.getReqNum();
        int reqNum = 8000;
        if (originalVarList.size() > 5 && originalVarList.size() <= 10) {
            reqNum = 5000;
        } else if (originalVarList.size() > 10) {
            reqNum = 3500;
        }
        List<String> filterVarList = new ArrayList<>();
        List<Map<String, String>> mapList = new ArrayList<>();
        for (String varName : originalVarList) {
            String filterVarName = erasePoint(varName);
            Map<String, String> innerMap = new HashMap<>();
            innerMap.put(filterVarName, varName);
            mapList.add(innerMap);
            filterVarList.add(filterVarName);
        }

        final TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
        final String currentTraceTableName = traceTableRelatedInfo.getTableName();
        final String downsamplingTableName = traceTableRelatedInfo.getDownsamplingTableName();
        if (StringUtils.isNotEmpty(currentTraceTableName)) {
            return parseLiveData(reqStartTimestamp, reqEndTimestamp, reqNum, downsamplingTableName, currentTraceTableName, filterVarList, mapList, traceRule);
        } else {
            throw new RuntimeException("查询原始表失败");
        }
    }


    private MultiValueMap parseLiveData(Long reqStartTimestamp, Long reqEndTimestamp, Integer reqNum, String downsamplingTableName, String currentTableName, List<String> filterVarList, List<Map<String, String>> mapList, TraceRule traceRule) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException, ExecutionException, InterruptedException {
        final MultiValueMap allMultiValueMap = new MultiValueMap();
        try {
            String fieldName = StringUtils.join(filterVarList, ",");
            reqStartTimestamp = getMinValue(reqStartTimestamp, getConfigPer());
            final Set<String> queryTableList = getQueryTable(reqStartTimestamp, reqEndTimestamp, currentTableName);
            if (reqEndTimestamp >= (long) totalSize * getConfigPer()) {
                reqEndTimestamp = (long) totalSize * getConfigPer() - 1;
            }
            int originalNum = Math.toIntExact((reqEndTimestamp - reqStartTimestamp) / getConfigPer());
            if (originalNum != 0) {
                //原始数据小于请求数据，所以都返回
                if (originalNum <= traceRule.getReqNum()) {
                    //writeTimestampToD3("query from full table,originalNum=" + originalNum + ",reqNum=" + reqNum, reqStartTimestamp, reqEndTimestamp);
                    return handleFromFull(reqStartTimestamp, reqEndTimestamp, currentTableName, mapList, allMultiValueMap, fieldName, queryTableList);
                }
                if ((reqEndTimestamp - reqStartTimestamp) / getConfigPer() >= firstPoint) {
                    String downTableSuffix = traceRule.downTableSuffix();
                    //比如 x/512/6000>1 x约等于3000000
                    if ((reqEndTimestamp - reqStartTimestamp) / getConfigPer() > highPoint) {
                        downTableSuffix = otherDownTableSuffix;
                    }
                    // int closestRate = 8;
                    Set<Future<List<UniPoint>>> resultList = new HashSet<>();
                    CountDownLatch countDownLatch = new CountDownLatch(filterVarList.size());
                    List<UniPoint> allUniPointList = new ArrayList<>();
                    for (String varName : filterVarList) {
                        Future<List<UniPoint>> future = pool.submit(new BigDataDownsamplingTableHandler(downsamplingTableName.concat("_").concat(varName).concat("_").concat(downTableSuffix), reqStartTimestamp, reqEndTimestamp, jdbcTemplate, countDownLatch, varName, mapList));
                        resultList.add(future);
                    }
                    countDownLatch.await();
                    for (Future<List<UniPoint>> future : resultList) {
                        allUniPointList.addAll(future.get());
                    }
                    final Map<String, List<UniPoint>> map = allUniPointList.stream().collect(Collectors.groupingBy(UniPoint::getVarName));
//                    Set<Future<MultiValueMap>> dichotomyResultList = new HashSet<>();
//                    CountDownLatch dichotomyCountDownLatch = new CountDownLatch(map.size());
                    int gap = -1;
                    for (Map.Entry<String, List<UniPoint>> entry : map.entrySet()) {
                        List<UniPoint> uniPointList = entry.getValue();
                        float f = (float) uniPointList.size() / (float) reqNum;
                        //  gap = Math.round(f);
                        gap = uniPointList.size() / reqNum;
                        if (gap >= 1) {
                            //writeTimestampToD3("gap>0,此时gap=" + gap + ",uniPointList.size=" + uniPointList.size(), reqStartTimestamp, reqEndTimestamp);
                            uniPointList = AdaptiveDownsamplingSelector.downsample(uniPointList, reqNum);
                            MultiValueMap multiValueMap = uniPoint2Map(uniPointList, mapList);
                            allMultiValueMap.putAll(multiValueMap);
//                            logger.info("uniPointList大小为:{}", uniPointList.size());
//                            closestRate = getSpecificClosestRate(gap);
//                            writeTimestampToD3("gap="+gap+",uniPointList大小为:"+uniPointList.size()+",closestRate="+closestRate,reqStartTimestamp,reqEndTimestamp);
//                            if (!downTableSuffix.equals(otherDownTableSuffix)) { //没达到512倍再降一半
//                                List<UniPoint> filterUniPointList = LTThreeBuckets.sorted(uniPointList, uniPointList.size() / 2);
//                                Future<MultiValueMap> future = pool.submit(new DichotomyHigherHandler(filterUniPointList, mapList, dichotomyCountDownLatch, traceRule.getReqNum() / 3, traceRule.getReqNum() / 3, traceRule.getReqNum(), traceRule.getReqNum() / 3));
//                                dichotomyResultList.add(future);
//                            } else {
//                                Future<MultiValueMap> future = pool.submit(new DichotomyHigherHandler(uniPointList, mapList, dichotomyCountDownLatch, traceRule.getReqNum() / 3, traceRule.getReqNum() / 3, traceRule.getReqNum(), traceRule.getReqNum() / 3));
//                                dichotomyResultList.add(future);
//                            }
                        } else if (gap == 0) {
                            //logger.info("gap = 0,uniPointList大小为:{}", uniPointList.size());
                            //writeTimestampToD3("gap=0,downTableSuffix=" + downTableSuffix + ",uniPointList大小为:" + uniPointList.size(), reqStartTimestamp, reqEndTimestamp);
                            MultiValueMap multiValueMap = uniPoint2Map(uniPointList, mapList);
                            allMultiValueMap.putAll(multiValueMap);
                        }
                    }
//                    if (gap >= 1) {
//                        dichotomyCountDownLatch.await();
//                        for (Future<MultiValueMap> dichotomyFuture : dichotomyResultList) {
//                            allMultiValueMap.putAll(dichotomyFuture.get());
//                        }
//                    }
                    // int totalDownsamplingRate = gap > 1 ? Integer.parseInt(downTableSuffix) * closestRate : Integer.parseInt(downTableSuffix);
                    handleDownTailData(reqStartTimestamp, reqEndTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, getConfigPer(), Integer.parseInt(downTableSuffix), filterVarList);
                    //writeTimestampToD3("gap="+gap+",downTableSuffix="+downTableSuffix+",closestRate="+closestRate,reqStartTimestamp,reqEndTimestamp);
                    //writeTimestampToD3("大数据量当前使用了" + downTableSuffix + "降采样", reqStartTimestamp, reqEndTimestamp);
                    //logger.info("大数据量当前使用了{}降采样", downTableSuffix);
                    return allMultiValueMap;
                }
                float f = (float) originalNum / (float) reqNum;
                int gap = Math.round(f);
                if (gap >= 1) {
                    //writeTimestampToD3("originalNum=" + originalNum + ",reqNum=" + reqNum, reqStartTimestamp, reqEndTimestamp);
                    //原始数据大于请求数据，查询降采样表
                    int closestRate = getClosestRate(gap);
                    Set<Future<MultiValueMap>> resultList = new HashSet<>();
                    CountDownLatch countDownLatch = new CountDownLatch(filterVarList.size());
                    for (String varName : filterVarList) {
                        Future<MultiValueMap> future = pool.submit(new QueryEachDownsamplingTableHandler(downsamplingTableName.concat("_").concat(varName), reqStartTimestamp, reqEndTimestamp, jdbcTemplate, countDownLatch, varName, closestRate, shardNum, mapList));
                        resultList.add(future);
                    }
                    countDownLatch.await();
                    for (Future<MultiValueMap> future : resultList) {
                        allMultiValueMap.putAll(future.get());
                    }
                    final Set<Map.Entry<BigDecimal, BigDecimal>> entrySet = allMultiValueMap.entrySet();
                    long threadLastValue = 0;
                    for (Map.Entry<BigDecimal, BigDecimal> entry : entrySet) {
                        List valueList = (List) entry.getValue();
                        if (valueList.size() > 2) {
                            BigDecimal[] endBd = (BigDecimal[]) valueList.get(valueList.size() - 2);
                            threadLastValue = endBd[0].longValue();
                        }
                        break;
                    }
                    //处理某些变量拉下的情况(这些变量对应的降采样表还没有插入数据，此时去全表查询降采样)
                    List<Pair<String, BigDecimal[]>> lagList = handleLagDownsampling(entrySet);
                    if (CollectionUtils.isNotEmpty(lagList)) {
                        String field = "";
                        BigDecimal currentTimestamp = null;
                        for (int i = 0; i < lagList.size(); i++) {
                            Pair<String, BigDecimal[]> pair = lagList.get(i);
                            String filterVarName = pair.getLeft();
                            if (i == 0) {
                                currentTimestamp = pair.getRight()[0];
                                field = field.concat(filterVarName);
                            } else {
                                field = field.concat(",").concat(filterVarName);
                            }
                        }
                        if ((reqEndTimestamp - currentTimestamp.longValue()) / getConfigPer() <= lagNum) {
//                            Set<String> lagQueryTableList = getQueryTable(currentTimestamp.longValue(), reqEndTimestamp, currentTableName);
                            handleTailOrHeadBusiness(currentTimestamp.longValue(), reqEndTimestamp, currentTableName, mapList, field, allMultiValueMap, closestRate, filterVarList);
                            //handleFromFull(currentTimestamp.longValue(), reqEndTimestamp, currentTableName, mapList, allMultiValueMap, field, lagQueryTableList);
                            //logger.info("handle other lag varNames,use full data");
                        }
                    }
                    if (reqEndTimestamp - threadLastValue > LAG) {
                        if (threadLastValue != 0) {
                            Set<String> otherQueryTableList = getQueryTable(threadLastValue + 1, reqEndTimestamp - 1, currentTableName);
                            CountDownLatch lagCountDownLatch = new CountDownLatch(otherQueryTableList.size());
                            List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
                            List<UniPoint> lagUniPointList = new ArrayList<>();
                            for (String table : otherQueryTableList) {
                                Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler(table, threadLastValue + 1, reqEndTimestamp - 1, jdbcTemplate, lagCountDownLatch, fieldName, mapList));
                                lagResultList.add(future);
                            }
                            lagCountDownLatch.await();
                            for (Future<List<UniPoint>> future : lagResultList) {
                                lagUniPointList.addAll(future.get());
                            }
                            for (String varName : filterVarList) {
                                List<UniPoint> singleVarDataList = lagUniPointList.stream().filter(item -> varName.equals(item.getVarName())).toList();
                                int bucketSize = singleVarDataList.size() / closestRate;
                                if (bucketSize > 0) {
                                    List<UniPoint> uniPoints = AdaptiveDownsamplingSelector.downsample(singleVarDataList, bucketSize);
                                    uniPoint2Map(uniPoints, allMultiValueMap, mapList);
                                }

                            }
                        } else {
                            if ((reqEndTimestamp - reqStartTimestamp) / getConfigPer() <= lagNum) {
                                //logger.info("async downsampling data is not completed,so handle from full table");
                                //writeTimestampToD3("async downsampling data is not completed,so handle from full table", reqStartTimestamp, reqEndTimestamp);
                                return handleFromFull(reqStartTimestamp, reqEndTimestamp, currentTableName, mapList, allMultiValueMap, fieldName, queryTableList);
                            }
                        }

                    }
                    //54-140  per=10  downrate=16  50 66 82 98 114 130 131-140再次降采样 需要
                    //54-210  per=10  downrate=16  50 66 82 98 114 130 146 162 178 194 210 不用了
                    if ((reqEndTimestamp - reqStartTimestamp) % closestRate != 0 || reqEndTimestamp % getConfigPer() != 0) {
                        handleDownTailData(reqStartTimestamp, reqEndTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, getConfigPer(), closestRate, filterVarList);
                    }
                    //实时数据请求的时候所有变量最新的数据还没有插入降采样表，因此所有变量都从全量表查询，同时数据小于3000才去全量表差，要不然前端渲染有压力
//                    if ((reqEndTimestamp - threadLastValue) / perMap.get("per") <= lagNum) {
//                        handleFromFull(threadLastValue, reqEndTimestamp, currentTableName, mapList, allMultiValueMap, fieldName, queryTableList);
//                    }
                    //logger.info("小数据量使用了{}倍降采样，数据返回成功", closestRate);
                    //writeTimestampToD3("小数据量使用了".concat(String.valueOf(closestRate).concat("降采样")), reqStartTimestamp, reqEndTimestamp);
                    return allMultiValueMap;
                }
            }
            return new MultiValueMap();
        } finally {
//            Set<Map.Entry<BigDecimal, BigDecimal>> entrySet = allMultiValueMap.entrySet();
//            if (!entrySet.isEmpty()) {
//                List list = (List) entrySet.iterator().next().getValue();
//                if (list.size() > traceRule.getReqNum()) {
//                    handleWasteTimeService.handleGc();
//                }
//            }
        }
    }

    private List<Pair<String, BigDecimal[]>> handleLagDownsampling(Set<Map.Entry<BigDecimal, BigDecimal>> entrySet) {
        List<Pair<String, BigDecimal[]>> lagList = new ArrayList<>();
        if (entrySet.size() == 0) {
            return lagList;
        }
        Map.Entry<BigDecimal, BigDecimal> first = entrySet.iterator().next();
        List list = (List) first.getValue();
        int size = list.size();
        for (Map.Entry<BigDecimal, BigDecimal> entry : entrySet) {
            List valueList = (List) entry.getValue();
            if (CollectionUtils.isNotEmpty(valueList)) {
                if (valueList.size() < size) {
                    String originalVarName = String.valueOf(entry.getKey());
                    Pair<String, BigDecimal[]> pair = Pair.of(erasePoint(originalVarName), (BigDecimal[]) valueList.get(valueList.size() - 1));
                    lagList.add(pair);
                }
            }
        }
        return lagList;
    }

    private MultiValueMap handleFromFull(Long reqStartTimestamp, Long reqEndTimestamp, String currentTableName, List<Map<String, String>> mapList, MultiValueMap allMultiValueMap, String fieldName, Set<String> queryTableList) throws InterruptedException, ExecutionException, NoSuchFieldException, IllegalAccessException {
        //查询原表
        List<Future<MultiValueMap>> resultList = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(queryTableList.size());
        for (String s : queryTableList) {
            Future<MultiValueMap> future = pool.submit(new QueryFullTableHandler(s, reqStartTimestamp, reqEndTimestamp, jdbcTemplate, countDownLatch, fieldName, mapList));
            resultList.add(future);
        }
        countDownLatch.await();
        for (Future<MultiValueMap> future : resultList) {
            allMultiValueMap.putAll(future.get());
        }
        //54-142  per=10  downrate=16  50 66 82 98 114 130 131-142再次降采样
        if (reqEndTimestamp % getConfigPer() != 0) {
            handleFullTailData(reqStartTimestamp, reqEndTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, getConfigPer());
        }
        //logger.info("查询全量表数据成功!");
        return allMultiValueMap;
    }

    private void handleDownTailData(Long reqStartTimestamp, Long reqEndTimestamp, String currentTableName, List<Map<String, String>> mapList, String fieldName,
                                    MultiValueMap allMultiValueMap, int per, int closestRate, List<String> filterVarList) throws NoSuchFieldException, IllegalAccessException, ExecutionException, InterruptedException {
        Long beginLeftStartTimestamp = null;
        Long endLeftStartTimestamp = null;
        final Set<Map.Entry<BigDecimal, BigDecimal>> entrySet = allMultiValueMap.entrySet();
        for (Map.Entry<BigDecimal, BigDecimal> entry : entrySet) {
            List valueList = (List) entry.getValue();
            BigDecimal[] startBd = (BigDecimal[]) valueList.get(0);
            /**
             * per=10为例
             * 需要处理的情况:假如是8倍降采样，假如起始点是48833,去降采样表查询发现最开始是48910，因为上一个点是48830，小于起始点，不需要查询，此时48833-48910这块儿数据就没返回，因为48910-10大于起始点48833，所以需要处理
             * 不需要处理的情况:但是需要判断假如前面的处理第一条数据返回117850,起始点是117843，此时小于一个per，不需要处理了
             */
            long threadStartValue = startBd[0].longValue();
            if (threadStartValue > reqStartTimestamp && whetherNeedHandleHeadData(threadStartValue, reqStartTimestamp, per)) {
                beginLeftStartTimestamp = threadStartValue - 1;
            }
            BigDecimal[] endBd = (BigDecimal[]) valueList.get(valueList.size() - 1);
            final long threadLastValue = endBd[0].longValue();
            //最后那条数据如果等于结束值，就不用下面的查询剩余数据了
            if (threadLastValue != reqEndTimestamp) {
                endLeftStartTimestamp = threadLastValue + 1;
            }
            break;
        }
        if (beginLeftStartTimestamp != null) {
            handleTailOrHeadBusiness(reqStartTimestamp, beginLeftStartTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, closestRate, filterVarList);
        }
        if (endLeftStartTimestamp != null) {
            handleTailOrHeadBusiness(endLeftStartTimestamp, reqEndTimestamp, currentTableName, mapList, fieldName, allMultiValueMap, closestRate, filterVarList);
        }
    }

    private void handleTailOrHeadBusiness(Long startTimestamp, Long endTimestamp, String currentTableName, List<Map<String, String>> mapList, String fieldName, MultiValueMap allMultiValueMap, int closestRate, List<String> filterVarList) throws NoSuchFieldException, IllegalAccessException, InterruptedException, ExecutionException {
        //小于一个任务周期就没必要请求了
        if (endTimestamp - startTimestamp < getConfigPer()) {
            return;
        }
        Set<String> queryTable = getQueryTable(startTimestamp, endTimestamp, currentTableName);
        CountDownLatch lagCountDownLatch = new CountDownLatch(queryTable.size());
        List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
        List<UniPoint> lagUniPointList = new ArrayList<>();
        for (String table : queryTable) {
            Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler(table, startTimestamp, endTimestamp, jdbcTemplate, lagCountDownLatch, fieldName, mapList));
            lagResultList.add(future);
        }
        lagCountDownLatch.await();
        for (Future<List<UniPoint>> future : lagResultList) {
            lagUniPointList.addAll(future.get());
        }
        for (String varName : filterVarList) {
            List<UniPoint> singleVarDataList = lagUniPointList.stream().filter(item -> varName.equals(item.getVarName())).toList();
            int bucketSize = singleVarDataList.size() > closestRate ? singleVarDataList.size() / closestRate : 0;
            Integer customDownsamplingRule = customDownsamplingRule(closestRate, singleVarDataList.size());
            if (bucketSize > 0) {//够一定数量进行降采样
                List<UniPoint> uniPoints = AdaptiveDownsamplingSelector.downsample(singleVarDataList, bucketSize);
                uniPoint2Map(uniPoints, allMultiValueMap, mapList);
            } else if (bucketSize == 0 && customDownsamplingRule != 1 && customDownsamplingRule <= singleVarDataList.size() && singleVarDataList.size() > 2) { //如果singleVarDataList数量大的话并且closestRate足够大bucketSize仍然可能为0，所以此时假如closestRate等于64，那么取次一级的32进行降采样
                List<UniPoint> uniPoints = AdaptiveDownsamplingSelector.downsample(singleVarDataList, singleVarDataList.size() / customDownsamplingRule);
                uniPoint2Map(uniPoints, allMultiValueMap, mapList);
            } else if (CollectionUtils.isNotEmpty(singleVarDataList) && bucketSize == 0) {//数量少的话直接返回全量表数据
                uniPoint2Map(lagUniPointList, allMultiValueMap, mapList);
                break;
            }
        }
    }

    private void handleFullTailData(Long reqStartTimestamp, Long reqEndTimestamp, String currentTableName, List<Map<String, String>> mapList, String fieldName,
                                    MultiValueMap allMultiValueMap, int per) throws NoSuchFieldException, IllegalAccessException, InterruptedException, ExecutionException {
        Long beginLeftStartTimestamp = null;
        Long endLeftStartTimestamp = null;
        final Set<Map.Entry<BigDecimal, BigDecimal>> entrySet = allMultiValueMap.entrySet();
        for (Map.Entry<BigDecimal, BigDecimal> entry : entrySet) {
            List valueList = (List) entry.getValue();
            BigDecimal[] startBd = (BigDecimal[]) valueList.get(0);
            /**
             * per=10为例
             * 需要处理的情况:假如是8倍降采样，假如起始点是48833,去降采样表查询发现最开始是48910，因为上一个点是48830，小于起始点，不需要查询，此时48833-48910这块儿数据就没返回，因为48910-10大于起始点48833，所以需要处理
             * 不需要处理的情况:但是需要判断假如前面的处理第一条数据返回117850,起始点是117843，此时小于一个per，不需要处理了
             */
            long threadStartValue = startBd[0].longValue();
            if (threadStartValue > reqStartTimestamp && whetherNeedHandleHeadData(threadStartValue, reqStartTimestamp, per)) {
                beginLeftStartTimestamp = threadStartValue - 1;
            }
            BigDecimal[] endBd = (BigDecimal[]) valueList.get(valueList.size() - 1);
            final long threadLastValue = endBd[0].longValue();
            //最后那条数据如果等于结束值，就不用下面的查询剩余数据了
            if (threadLastValue != reqEndTimestamp) {
                endLeftStartTimestamp = threadLastValue + 1;
            }
            break;
        }
        if (beginLeftStartTimestamp != null) {
            Set<String> queryTable = getQueryTable(reqStartTimestamp, beginLeftStartTimestamp, currentTableName);
            CountDownLatch lagCountDownLatch = new CountDownLatch(queryTable.size());
            List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
            List<UniPoint> lagUniPointList = new ArrayList<>();
            for (String table : queryTable) {
                Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler(table, reqStartTimestamp, beginLeftStartTimestamp, jdbcTemplate, lagCountDownLatch, fieldName, mapList));
                lagResultList.add(future);
            }
            lagCountDownLatch.await();
            for (Future<List<UniPoint>> future : lagResultList) {
                lagUniPointList.addAll(future.get());
            }
            uniPoint2Map(lagUniPointList, allMultiValueMap, mapList);
            lagUniPointList.clear();
        }


        if (endLeftStartTimestamp != null) {
            Set<String> queryTable = getQueryTable(endLeftStartTimestamp, reqEndTimestamp, currentTableName);
            CountDownLatch lagCountDownLatch = new CountDownLatch(queryTable.size());
            List<Future<List<UniPoint>>> lagResultList = new ArrayList<>();
            List<UniPoint> lagUniPointList = new ArrayList<>();
            for (String table : queryTable) {
                Future<List<UniPoint>> future = pool.submit(new LagFullTableHandler(table, endLeftStartTimestamp, reqEndTimestamp, jdbcTemplate, lagCountDownLatch, fieldName, mapList));
                lagResultList.add(future);
            }
            lagCountDownLatch.await();
            for (Future<List<UniPoint>> future : lagResultList) {
                lagUniPointList.addAll(future.get());
            }
            uniPoint2Map(lagUniPointList, allMultiValueMap, mapList);
            lagUniPointList.clear();
        }

    }


    public VsCodeRespVo traceLoadOriginal(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        List<TraceFieldMeta> traceFieldMetaList = new ArrayList<>();
        Long traceId = 0L;
        String loadedPath = "";
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    continue;
                }
                if (VarConst.PATH.equals(key)) {
                    loadedPath = String.valueOf(jsonObject.get(key));
                }
            }
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            String traceConfig = traceTableRelatedInfo.getTraceConfig();
            final String traceStatus = traceTableRelatedInfo.getTraceStatus();
            if (!"traceStop".equalsIgnoreCase(traceStatus)) {
                responseVo.setRet(false);
                throw new RuntimeException("当前trace状态为【" + traceStatus + "】,不是stop状态不能载入文件!,traceId= " + traceId);
            }
            MysqlUtils.load(loadedPath, DATABASE_NAME);

            JSONObject respJson = new JSONObject();
            Map<String, Object> map = new HashMap<>();
            // traceConfig = addChineseStr(traceConfig);
            map.put("traceCfg", traceConfig);
            respJson.put("rData", map);
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceLoad");
            responseVo.setRet(true);
            responseVo.settData(respJson);
        } catch (Exception e) {
            responseVo.setRet(false);
            e.printStackTrace();
            logger.error("trace load 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace load successfully executed");
        return responseVo;
    }


    public VsCodeRespVo traceSave(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        Long traceId = 0L;
        String savePath = "";
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    continue;
                }
                if (VarConst.PATH.equals(key)) {
                    savePath = String.valueOf(jsonObject.get(key));
                }
            }
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            String tableName = traceTableRelatedInfo.getTableName();
            final String traceStatus = traceTableRelatedInfo.getTraceStatus();
            if (!"traceStop".equalsIgnoreCase(traceStatus)) {
                responseVo.setRet(false);
                throw new RuntimeException("当前trace状态为【" + traceStatus + "】,不是stop状态不能保存文件!,traceId= " + traceId);
            }
            String downsamplingTableName = traceTableRelatedInfo.getDownsamplingTableName();
            backUpTableList.add(tableName);
            for (int i = 0; i < shardNum; i++) {
                String tableShardName = tableName.concat("_").concat(String.valueOf(i));
                backUpTableList.add(tableShardName);
            }
            final List<TraceFieldMeta> traceFieldMetaList = traceFieldMetaMapper.getCurrentFieldNames(traceId);
            for (TraceFieldMeta traceFieldMeta : traceFieldMetaList) {
                for (Integer downRate : data) {
                    backUpTableList.add(downsamplingTableName.concat("_").concat(traceFieldMeta.getVarName()).concat("_").concat(String.valueOf(downRate)));
                }
            }
            MysqlUtils.backUpForSaveFile(savePath, DATABASE_NAME, backUpTableList);
            logger.info("保存文件" + savePath + "成功");
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceSave");
            responseVo.setRet(true);
        } catch (Exception e) {
            responseVo.setRet(false);
            logger.error("trace save 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace save successfully executed");
        return responseVo;

    }


    public static int getPercentBucketRate(int size) {
        if (size <= 0) {
            throw new RuntimeException("数据量大小不能小于等于0!");
        }
        if (size <= 10) {
            return 3;
        } else if ((size < 100) || (size > 100 && size < 10000) || size == 100) {
            return 7;
        } else if ((size > 10000 && size < 1000000) || size == 10000) {
            return 70;
        } else if ((size > 1000000 && size < 5000000) || size == 1000000) {
            return 350;
        } else if ((size > 5000000 && size < 10000000) || size == 5000000) {
            return 700;
        }
        return 3;

    }

    private Set<String> getQueryTable(Long reqStartTimestamp, Long reqEndTimestamp, String parentTable) {
        Set<String> set = new TreeSet<>();
        if (reqStartTimestamp > reqEndTimestamp) {
            throw new RuntimeException("开始时间戳不能大于结束时间戳!");

        }
        if (reqStartTimestamp > (long) (totalSize / shardNum) * (shardNum - 1) * getConfigPer()) {
            set.add(parentTable.concat("_").concat(String.valueOf(shardNum - 1)));
        }
        if (reqEndTimestamp < ((long) (totalSize / shardNum) * getConfigPer())) {
            set.add(parentTable.concat("_").concat(String.valueOf(0)));
        }
        int beginSlot = (int) (reqStartTimestamp / ((totalSize / shardNum) * getConfigPer()));
        int endSlot = (int) (reqEndTimestamp / ((totalSize / shardNum) * getConfigPer()));
        //防止endTimestamp请求过大导致实际没有那么多分表
        if (endSlot > shardNum - 1) {
            endSlot = shardNum - 1;
        }
        for (int i = beginSlot; i <= endSlot; i++) {
            set.add(parentTable.concat("_").concat(String.valueOf(i)));
        }
        return set;
    }

    /**
     * eg:timestamp:1200000/1000000落在1号桶
     *
     * @param timestamp
     * @return
     */
    private int chooseBucket(Long timestamp) {
        for (int i = 0; i < shardNum; i++) {
            final long first = (long) i * (totalSize / shardNum) * getConfigPer();
            //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
            if (timestamp >= first && timestamp <= first + (long) (totalSize / shardNum) * getConfigPer()) {
                return i;
            }
        }
        return 0;

    }


    private void generateShardingTable(String tableName, List<TraceFieldMeta> traceFieldMetaList, Long traceId) {
        for (int a = 0; a < shardNum; a++) {
            StringBuilder sql = new StringBuilder();
            // sql.append("DROP TABLE IF EXISTS " + "`").append(tableName.concat("_").concat(String.valueOf(i))).append("`").append(";");
            sql.append(" CREATE TABLE " + "`").append(tableName.concat("_").concat(String.valueOf(a))).append("`").append("(");
            sql.append("`id` bigint NOT NULL,");
            for (int i = 0; i < traceFieldMetaList.size(); i++) {
                traceFieldMetaList.get(i).setTraceId(traceId);
                if (i == traceFieldMetaList.size() - 1) {
                    sql.append("`").append(traceFieldMetaList.get(i).getVarName()).append("`").append("  ").append(traceFieldMetaList.get(i).getMysqlType()).append(" DEFAULT NULL, PRIMARY KEY (`id`))ENGINE=InnoDB ");
                } else {
                    sql.append("`").append(traceFieldMetaList.get(i).getVarName()).append("`").append("  ").append(traceFieldMetaList.get(i).getMysqlType()).append(" DEFAULT NULL, ");
                }
            }
            String sqlStr = sql.toString();
            jdbcTemplate.execute(sqlStr);
        }
    }

    public Consumer<String> rollbackShardingTable() {
        Consumer<String> consumer = (s) -> {
            for (int a = 0; a < shardNum; a++) {
                String sql = "drop table " + s.concat("_").concat(String.valueOf(a));
                jdbcTemplate.update(sql);
            }
        };
        return consumer;
    }

    private Long getNearestRegion(Long timestamp) {
        try {
            for (int i = 0; i < shardNum; i++) {
                final long first = (long) i * (totalSize / shardNum) * getConfigPer();
                //eg:if (timestamp>0 && timestamp<=1000000)  if (timestamp>1000000 && timestamp<=2000000)
                if (timestamp >= first && timestamp <= first + (long) (totalSize / shardNum) * getConfigPer()) {
                    return ((long) i * (totalSize / shardNum) * getConfigPer() + (totalSize / shardNum) * getConfigPer());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return -1L;
    }

    public VsCodeRespVo traceInitializeGc(VsCodeReqParam vsCodeReqParam) {
        VsCodeRespVo vsCodeRespVo = new VsCodeRespVo();
        Long requestId = vsCodeReqParam.getRequestId();
        try {
            long start = System.currentTimeMillis();
            //删除主表和降采样表
            final List<TraceTableRelatedInfo> traceTableRelatedInfoList = traceTableRelatedInfoMapper.selectAll();
            for (TraceTableRelatedInfo traceTableRelatedInfo : traceTableRelatedInfoList) {
                String tableName = traceTableRelatedInfo.getTableName();
                Long traceId = traceTableRelatedInfo.getTraceId();
                String downsamplingTableName = traceTableRelatedInfo.getDownsamplingTableName();
                if (StringUtils.isNotEmpty(tableName)) {
                    String sql = "drop table " + tableName;
                    jdbcTemplate.update(sql);
                    rollbackShardingTable().accept(tableName);
                }
                final List<TraceFieldMeta> traceFieldMetaList = traceFieldMetaMapper.getCurrentFieldNames(traceId);
                if (CollectionUtils.isNotEmpty(traceFieldMetaList) && StringUtils.isNotEmpty(downsamplingTableName)) {
                    for (TraceFieldMeta traceFieldMeta : traceFieldMetaList) {
                        for (Integer downRate : data) {
                            String downsamplingSql = "drop table " + downsamplingTableName.concat("_").concat(traceFieldMeta.getVarName()).concat("_").concat(String.valueOf(downRate));
                            jdbcTemplate.update(downsamplingSql);
                        }
                    }
                }
            }
            String traceTableRelatedInfoDeleteSql = "DELETE FROM trace_table_related_info";
            jdbcTemplate.update(traceTableRelatedInfoDeleteSql);
            String traceStatisticsDeleteSql = "DELETE FROM trace_timestamp_statistics";
            jdbcTemplate.update(traceStatisticsDeleteSql);
            String traceFieldMetaDeleteSql = "DELETE FROM trace_field_meta";
            jdbcTemplate.update(traceFieldMetaDeleteSql);
            String traceNumInfoDeleteSql = "DELETE FROM table_num_info";
            jdbcTemplate.update(traceNumInfoDeleteSql);
            long end = System.currentTimeMillis();
            System.out.println("消耗了" + (end - start) + "ms");
        } catch (Exception e) {
            vsCodeRespVo.setRet(false);
            logger.error("trace gc 异常,报错信息为: " + e);
            return vsCodeRespVo;
        }
        logger.info("trace gc successfully executed");
        vsCodeRespVo.setRet(true);
        vsCodeRespVo.setType("ackForTraceGc");
        vsCodeRespVo.setResponseId(requestId);
        return vsCodeRespVo;
    }

    int loopNum = 0;

    public void analyzeTraceLiveData2(VsCodeReqParam vsCodeReqParam) throws Exception {
//        synchronized (lock) {
        reentrantLock.lock();
        try (Connection connection = dataSource.getConnection()) {
            //加锁防止降采样表不是从最开始的时间戳写入的
            final long start = System.currentTimeMillis();
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            //待批量插入数据库的数据
            long traceId = 0L;
            List<String> originalFieldNameList = new ArrayList<>();
            //  originalFieldNameList.add(VarConst.ID);
            TraceTableRelatedInfo traceTableRelatedInfo;
            List<Object[]> objects = new LinkedList<>();
            for (String varName : keySet) {
                if (VarConst.ID.equals(varName)) {
                    traceId = Long.parseLong(String.valueOf(jsonObject.get(varName)));
                    continue;
                }
                if ("varNames".equals(varName)) {
                    String varNameArr = String.valueOf(jsonObject.get(varName));
                    varNameArr = varNameArr.replaceAll(" +", "");
                    varNameArr = varNameArr.substring(1, varNameArr.length() - 1);
                    originalFieldNameList = Arrays.asList(varNameArr.split(","));
                    continue;
                }
                if ("dataList".equals(varName)) {
                    Object o = jsonObject.get(varName);
                    if (o instanceof List<?>) {
                        List list = (List) o;
                        for (Object innerData : list) {
                            List innerList = (List) innerData;
                            Object[] obj = innerList.toArray();
                            objects.add(obj);
                        }
                    }
                }
            }
            List<String> fieldNameList = new ArrayList<>();
            for (String varName : originalFieldNameList) {
                String filterVarName = erasePoint(varName);
                fieldNameList.add(filterVarName);
            }
            fieldNameList.add(0, VarConst.ID);
            final long middle = System.currentTimeMillis();
            System.out.println("解析花费了: " + (middle - start));
            traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            if (!"traceStart".equalsIgnoreCase(traceTableRelatedInfo.getTraceStatus())) {
                logger.warn("current trace status is {}, ignore this request", traceTableRelatedInfo.getTraceStatus());
                return;
            }
            // objects.sort((o1, o2) -> (((BigDecimal) o1[0]).intValue() - ((BigDecimal) o2[0]).intValue()));
            final Object[] firstObj = objects.get(0);
            final Object firstObjTimestamp = firstObj[0];
            final Object[] lastObj = objects.get(objects.size() - 1);
            final Object lastObjTimestamp = lastObj[0];
            long firstTimestamp = Long.parseLong(firstObjTimestamp.toString());
            long lastTimestamp = Long.parseLong(lastObjTimestamp.toString());
//            if (firstTimestamp > (long) (totalSize / shardNum) * getConfigPer()) {
//                writeTimestampToD4("发送的超出1000万数据!!", firstTimestamp, lastTimestamp);
//            }
            int bucket = 0;
            String fieldName = StringUtils.join(fieldNameList, ",");
            List<String> questionMarkList = new ArrayList<>();
            //加1是因为还有id列(固定列)
            for (int i = 0; i < fieldNameList.size(); i++) {
                questionMarkList.add("?");
            }
            if ((lastTimestamp <= (long) (totalSize / shardNum) * getConfigPer() && firstTimestamp < (long) (totalSize / shardNum) * getConfigPer())) {
                bucket = chooseBucket(firstTimestamp);
                String fullTable = traceTableRelatedInfo.getTableName().concat("_").concat(String.valueOf(bucket));
                String prefixSql = "INSERT INTO " + fullTable + "(" + fieldName + ")" + "VALUES";
                BaseUtils.executeFullTableBatchUpdate(connection, prefixSql, questionMarkList, objects);
            } else if (lastTimestamp / firstTimestamp == 1) {
                //firstTimestamp:9990000  lastTimestamp:1000020  or      firstTimestamp:29990000  lastTimestamp:3000020这种情况就需要需要分别插入到2个shard表
                List<Object[]> firstBatchObjects = new ArrayList<>();
                //已经排序好的List
                Long nearestRegion = 0L;
                for (int i = 0; i < objects.size(); i++) {
                    long firstStamp;
                    long otherStamp;
                    final Object[] o = objects.get(i);
                    if (i == 0) {
                        //这一批数据第一条数据的Timestamp
                        firstStamp = Long.parseLong(o[0].toString());
                        bucket = chooseBucket(firstStamp);
                        //假设第一条数据是997855，最邻近的nearestRegion就是1000000
                        nearestRegion = getNearestRegion(firstStamp);
                    }
                    otherStamp = Long.parseLong(o[0].toString());
                    if (otherStamp <= nearestRegion) {
                        firstBatchObjects.add(o);
                    } else {
                        break;
                    }
                }
                final String fullTable = traceTableRelatedInfo.getTableName().concat("_").concat(String.valueOf(bucket));
                String prefixSql = "INSERT INTO " + fullTable + "(" + fieldName + ")" + "VALUES";
                BaseUtils.executeFullTableBatchUpdate(connection, prefixSql, questionMarkList, firstBatchObjects);
                List<Object[]> otherBatchObjects = objects.stream().filter(item -> !firstBatchObjects.contains(item)).toList();
                loopNum = loopNum + 1;
//                if (CollectionUtils.isNotEmpty(firstBatchObjects)) {
//                    writeTimestampToD(String.valueOf(firstTimestamp), "firstBatchObjects333", String.valueOf(bucket), String.valueOf(loopNum));
//                }
                //假如每次传30条数据，10201 10230和19991 20021的入库规则不同(目前只适用于shardNum=10)19001 19002 19003 ...20000
                if (CollectionUtils.isNotEmpty(otherBatchObjects)) {
                    //writeTimestampToD(String.valueOf(lastTimestamp),"otherBatchObjects333",String.valueOf(bucket),String.valueOf(loopNum));
                    final String otherFullTable = traceTableRelatedInfo.getTableName().concat("_").concat(String.valueOf(bucket + 1));
                    String otherPrefixSql = "INSERT INTO " + otherFullTable + "(" + fieldName + ")" + "VALUES";
                    BaseUtils.executeFullTableBatchUpdate(connection, otherPrefixSql, questionMarkList, otherBatchObjects);
                }
            }
            List<String> filterList = fieldNameList.stream().filter(item -> !VarConst.ID.equals(item)).toList();
            handleWasteTimeService.insertDownsamplingData(traceId, jdbcTemplate, shardNum, filterList, getConfigPer());
            final long end = System.currentTimeMillis();
            //logger.info("总共花费了" + (end - start));
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            reentrantLock.unlock();
        }
    }

    public static Pair<List<UniPoint>, Integer> handleBigDownsampling(List<UniPoint> downsamplingDataList, String varName, float gap, Connection connection, int parentDownsamplingRate, String parentDownsamplingTableName) throws ClassNotFoundException, SQLException {
        int bucketSize = (int) (downsamplingDataList.size() / gap);
        String downsamplingTableName = parentDownsamplingTableName.concat("_").concat(varName).concat("_").concat(String.valueOf(gap * parentDownsamplingRate));
        downsamplingTableName = BaseUtils.earseLastPoint(downsamplingTableName);
        if (bucketSize > 0) {
            downsamplingDataList = AdaptiveDownsamplingSelector.downsample(downsamplingDataList, bucketSize);
        }
        List<Object[]> dataObjArr = convertPojoList2ObjListArr(downsamplingDataList, 2);
        BaseUtils.executeDownsamplingBatchUpdate(connection, downsamplingTableName, dataObjArr);
        return Pair.of(downsamplingDataList, (int) (gap * parentDownsamplingRate));
    }

    public MultiValueMap getSingleTimestampFileHandleExecutor(VsCodeReqParam vsCodeReqParam) throws Exception {
        System.out.println("getSingleTimestampFileHandleExecutor=" + Thread.currentThread().getName());
        List<String> originalVarList = vsCodeReqParam.getVarList();
        List<String> filterVarList = new ArrayList<>();
        List<Map<String, String>> mapList = new ArrayList<>();
        for (String varName : originalVarList) {
            String filterVarName = erasePoint(varName);
            Map<String, String> innerMap = new HashMap<>();
            innerMap.put(filterVarName, varName);
            mapList.add(innerMap);
            filterVarList.add(filterVarName);
        }
        String fieldName = StringUtils.join(filterVarList, ",");
        String allFieldName = VarConst.ID.concat(",").concat(fieldName);
        final Long traceId = vsCodeReqParam.getTraceId();
        List<Long> reqTimestampList = vsCodeReqParam.getReqTimestamp();
        final TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
        if (traceTableRelatedInfo == null) {
            throw new RuntimeException("当前traceId查询表失败");
        }
        final String currentTraceTableName = traceTableRelatedInfo.getTableName();
        MultiValueMap allMultiValueMap = new MultiValueMap();

        for (Long reqTimestamp : reqTimestampList) {
            reqTimestamp = BaseUtils.getCircleStamp(reqTimestamp, getConfigPer());
            int bucket = chooseBucket(reqTimestamp);
            Object[] regionParam = new Object[]{reqTimestamp};
            String sql = "select " + allFieldName + "  from " + currentTraceTableName.concat("_").concat(String.valueOf(bucket)) + " where id=?";
            List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, regionParam);
            MultiValueMap multiValueMap = convertList2MultiMap(list, mapList);
            allMultiValueMap.putAll(multiValueMap);
        }
        return allMultiValueMap;
    }

    public VsCodeRespVo traceLoad(VsCodeReqParam vsCodeReqParam) {
        Long requestId = vsCodeReqParam.getRequestId();
        VsCodeRespVo responseVo = new VsCodeRespVo();
        Long traceId = 0L;
        String loadedPath = "";
        try {
            JSONObject jsonObject = vsCodeReqParam.gettData();
            Set<String> keySet = jsonObject.keySet();
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    traceId = Long.valueOf(String.valueOf(jsonObject.get(key)));
                    continue;
                }
                if (VarConst.PATH.equals(key)) {
                    loadedPath = String.valueOf(jsonObject.get(key));
                }
            }
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            String traceConfig = traceTableRelatedInfo.getTraceConfig();
            final String traceStatus = traceTableRelatedInfo.getTraceStatus();
            if (!"traceStop".equalsIgnoreCase(traceStatus)) {
                responseVo.setRet(false);
                throw new RuntimeException("当前trace状态为【" + traceStatus + "】,不是stop状态不能载入文件!,traceId= " + traceId);
            }
            MysqlUtils.loadNio(loadedPath, DATABASE_NAME);
            JSONObject respJson = new JSONObject();
            Map<String, Object> map = new HashMap<>();
            map.put("traceCfg", traceConfig);
            respJson.put("rData", map);
            responseVo.setResponseId(requestId);
            responseVo.setType("ackForTraceLoad");
            responseVo.setRet(true);
            responseVo.settData(respJson);
        } catch (Exception e) {
            responseVo.setRet(false);
            e.printStackTrace();
            logger.error("trace load 异常,报错信息为: " + e);
            return responseVo;
        }
        logger.info("trace load successfully executed");
        return responseVo;
    }


    private Integer getConfigPer() {
        Integer per = perMap.get("per");
        if (per == null) {
            per = defaultPer;
        }
        return per;
    }
}
