package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.TraceDownsampling;
import com.yt.server.entity.UniPoint;
import com.yt.server.service.IoComposeServiceDatabase;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.yt.server.service.IoComposeServiceDatabase.totalSize;

/**
 * @description:
 * @projectName:native-test
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/2/15 14:31
 * @version:1.0
 */
public class BaseUtils {

    private static final Logger logger = LoggerFactory.getLogger(BaseUtils.class);

    public synchronized static long generateTraceId() {
        return Long.parseLong(String.valueOf(System.currentTimeMillis()).substring(1, 13));
    }

    public static List<String> acquireAllField(Class clazz) {
        final Field[] fields = clazz.getDeclaredFields();
        List<String> fieldList = new ArrayList<>();
        for (Field field : fields) {
            if (!"id".equals(field.getName())) {
                fieldList.add(field.getName());
            }
        }
        return fieldList;

    }

    public static int getClosestRate(int gap) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Integer datum : IoComposeServiceDatabase.data) {
            int abs = Math.abs(gap - datum);
            if (map.size() == 0) {
                map.put(abs, datum);
            }
            final Integer currentMinValue = map.keySet().iterator().next();
            if (abs < currentMinValue) {
                map.clear();
                map.put(abs, datum);
            }
        }
        Object[] objects = map.values().toArray();
        return (int) objects[0];
    }

    public static int getSpecificClosestRate(int gap) {
        Map<Integer, Integer> map = new HashMap<>();
        Integer[] data = new Integer[]{2, 3, 4};
//        Integer[] dataExtra = new Integer[]{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
//        Integer[] filterArr = size > 50000 ? dataExtra : data;
        for (Integer datum : data) {
            int abs = Math.abs(gap - datum);
            if (map.size() == 0) {
                map.put(abs, datum);
            }
            final Integer currentMinValue = map.keySet().iterator().next();
            if (abs <= currentMinValue) {
                map.clear();
                map.put(abs, datum);
            }
        }
        Object[] objects = map.values().toArray();
        return (int) objects[0];
    }


    public static Set<Integer> getCurrentDownsamplingRate(Long eachBucketPointNum) {
        Set<Integer> set = new TreeSet<>();
        for (Integer datum : IoComposeServiceDatabase.data) {
            if (datum < eachBucketPointNum) {
                set.add(datum);
            }
        }
        return set;
    }


    public static Set<Integer> getCurrentDownsamplingRate(List<UniPoint> singleVarDataList) {
        Set<Integer> set = new TreeSet<>();
        int size = singleVarDataList.size();
        for (Integer datum : IoComposeServiceDatabase.data) {
            if (datum < size) {
                set.add(datum);
            }
        }
        return set;
    }

    /**
     * @param list /**
     *             "id": [
     *             [
     *             1,
     *             1
     *             ],
     *             ]
     *             <p>
     *             "earn": [
     *             [
     *             1,
     *             2000
     *             ],
     *             ]
     *             "age": [
     *             [
     *             1,
     *             24
     *             ],
     *             ]
     *             "pwd": [
     *             [
     *             1,
     *             123456
     *             ],
     *             ]
     *             适用于全量数据表(比如trace1)
     */
    public static MultiValueMap convertList2MultiMap(List<Map<String, Object>> list, List<Map<String, String>> mapList) throws NoSuchFieldException, IllegalAccessException {
        MultiValueMap multiValueMap = new MultiValueMap();
        for (Map<String, Object> map : list) {
            final Set<String> keySet = map.keySet();
            Iterator<String> iterator = keySet.iterator();
            Long timestamp = -1L;
            while (iterator.hasNext()) {
                String fieldName = iterator.next();
                final Object obj = map.get(fieldName);
                if (VarConst.ID.equals(fieldName)) {
                    timestamp = (Long) map.get(fieldName);
                    continue;
                }
                if (timestamp != -1L) {
                    BigDecimal[] data;
                    if (obj instanceof Integer) {
                        int value = (int) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof Long) {
                        long value = (long) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof Float) {
                        float value = (float) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof Double) {
                        double value = (double) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof Short) {
                        short value = (short) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof Byte) {
                        byte value = (byte) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else if (obj instanceof BigDecimal) {
                        BigDecimal value = (BigDecimal) obj;
                        value = value.stripTrailingZeros();
                        String valueStr = value.toPlainString();
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(valueStr)};
                    } else if (obj instanceof String) {
                        String value = (String) obj;
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), new BigDecimal(value)};
                    } else {
                        data = new BigDecimal[]{BigDecimal.valueOf(timestamp), null};
                    }
                    multiValueMap.put(parseOriginalVarName(fieldName, mapList), data);
                }

            }
        }
        return multiValueMap;
    }

    public static MultiValueMap convert2MultiMapForTraceDownSampling(List<TraceDownsampling> output, String varName, List<Map<String, String>> mapList) {
        MultiValueMap multiValueMap = new MultiValueMap();
        //返回前端容易解析的数据
        for (TraceDownsampling resp : output) {
            BigDecimal value = resp.getValue();
//            String str = value.toString();
//            value = new BigDecimal(str);
            value = value.stripTrailingZeros();
            String valueStr = value.toPlainString();
            BigDecimal[] data = new BigDecimal[]{BigDecimal.valueOf(resp.getTimestamp()), new BigDecimal(valueStr)};
            multiValueMap.put(parseOriginalVarName(varName, mapList), data);
        }
        return multiValueMap;
    }

    public static BigDecimal getBigDecimal(Object value) {
        BigDecimal ret = null;
        if (value != null) {
            if (value instanceof Long) {
                ret = BigDecimal.valueOf(((Number) value).longValue());
            } else if (value instanceof Integer) {
                ret = BigDecimal.valueOf(((Number) value).intValue());
            } else if (value instanceof Double) {
                ret = BigDecimal.valueOf(((Number) value).doubleValue());
            } else if (value instanceof Float) {
                ret = BigDecimal.valueOf(((Number) value).floatValue());
            } else if (value instanceof Short) {
                ret = BigDecimal.valueOf(((Number) value).shortValue());
            } else if (value instanceof Byte) {
                ret = BigDecimal.valueOf(((Number) value).byteValue());
            } else if (value instanceof BigDecimal) {
                ret = (BigDecimal) value;
                ret = ret.stripTrailingZeros();
            } else if (value instanceof String) {
                ret = new BigDecimal((String) value);
            } else if (value instanceof BigInteger) {
                ret = new BigDecimal((BigInteger) value);
            } else {
                throw new ClassCastException("Not possible to coerce [" + value + "] from class " + value.getClass() + " into a BigDecimal.");
            }
        }
        return ret;
    }

    public static String windowsType2DatabaseType(String windowsType) {
        String dataBaseType = null;
        if ("DWORD".equalsIgnoreCase(windowsType) || "DINT".equalsIgnoreCase(windowsType) || "UDINT".equalsIgnoreCase(windowsType) || "WORD".equalsIgnoreCase(windowsType)) {
            dataBaseType = "int";
        } else if ("LWORD".equalsIgnoreCase(windowsType) || "LINT".equalsIgnoreCase(windowsType)) {
            dataBaseType = "bigint";
        } else if ("SINT".equalsIgnoreCase(windowsType) || "USINT".equalsIgnoreCase(windowsType) || "BYTE".equalsIgnoreCase(windowsType) || "BOOL".equalsIgnoreCase(windowsType)) {
            dataBaseType = "tinyint";
        } else if ("INT".equalsIgnoreCase(windowsType) || "UINT".equalsIgnoreCase(windowsType)) {
            dataBaseType = "smallint";
        } else if ("REAL".equalsIgnoreCase(windowsType)) {
            dataBaseType = "float";
        } else if ("LREAL".equalsIgnoreCase(windowsType)) {
            dataBaseType = "double";
        }
        return dataBaseType;
    }


    public static void executeDownsamplingBatchUpdate(Connection conn, String downsamplingTableName, List<Object[]> batchArgs) throws SQLException {
        if (batchArgs.size() == 0) {
            return;
        }
        // sql前缀
        //String prefix = "INSERT INTO trace2 (id, earn, age, pwd) VALUES";
        String prefix = "INSERT IGNORE INTO " + downsamplingTableName + " (`timestamp`, value) VALUES";
        final int piece = batchArgs.size() / 100;
        int remainder = batchArgs.size() % 100;
        try {
            // 保存sql后缀
            StringBuilder suffix = new StringBuilder();
            // 设置事务为非自动提交
            conn.setAutoCommit(false);
            // 比起st，pst会更好些
            PreparedStatement pst = conn.prepareStatement(" ");//准备执行语句
            // 外层循环，总提交事务次数
            if (piece > 0) {
                for (int i = 0; i < 100; i++) {
                    suffix = new StringBuilder();
                    // 第j次提交步长
                    for (int j = 0; j < piece; j++) {
                        // 构建SQL后缀
                        suffix.append("(");
                        suffix.append("'").append(batchArgs.get(i * piece + j)[0]).append("'").append(",");
                        suffix.append(batchArgs.get(i * piece + j)[1]);
                        suffix.append("),");
                    }
                    // 构建完整SQL
                    String sql = prefix + suffix.substring(0, suffix.length() - 1);
                    // 添加执行SQL
                    pst.addBatch(sql);
                    // 执行操作
                    pst.executeBatch();
                    // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                    conn.commit();
                    // 清空上一次添加的数据
                    suffix = new StringBuilder();
                }
            } else {
                for (Object[] batchArg : batchArgs) {
                    // 构建SQL后缀
                    suffix.append("(");
                    suffix.append("'").append(batchArg[0]).append("'").append(",");
                    suffix.append(batchArg[1]);
                    suffix.append("),");
                }
                // 构建完整SQL
                String sql = prefix + suffix.substring(0, suffix.length() - 1);
                // 添加执行SQL
                pst.addBatch(sql);
                // 执行操作
                pst.executeBatch();
                // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                conn.commit();
            }
            //不能整除
            if (remainder != 0) {
                for (int k = piece * 100; k < batchArgs.size(); k++) {
                    suffix = new StringBuilder();
                    suffix.append("(");
                    suffix.append("'").append(batchArgs.get(k)[0]).append("'").append(",");
                    suffix.append(batchArgs.get(k)[1]);
                    suffix.append("),");
                    // 构建完整SQL
                    String sql = prefix + suffix.substring(0, suffix.length() - 1);
                    // 添加执行SQL
                    pst.addBatch(sql);
                    // 执行操作
                    pst.executeBatch();
                    // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                    conn.commit();
                }
            }
            // 头等连接
//            pst.close();
//            conn.close();
        } catch (SQLException e) {
            conn.rollback();

//            throw new RuntimeException(e);
        }
        // 耗时
//        logger.info("{}条数据插入花费时间{} : ", batchArgs.size(), (end - begin) / 1000 + " s");//107s
//        logger.info("插入完成");
    }


    public static void executeFullTableBatchUpdate(Connection conn, String prefixSql, List<String> questionMarkList, List<Object[]> batchArgs) throws SQLException {
        if (batchArgs.size() == 0) {
            return;
        }
        // sql前缀
        final int piece = batchArgs.size() / 100;
        try {
            // 保存sql后缀
            StringBuilder suffix = new StringBuilder();
            // 设置事务为非自动提交
            conn.setAutoCommit(false);
            // 比起st，pst会更好些
            PreparedStatement pst = conn.prepareStatement(" ");//准备执行语句
            // 外层循环，总提交事务次数
            if (piece > 0) {
                for (int i = 0; i < 100; i++) {
                    suffix = new StringBuilder();
                    // 第j次提交步长
                    for (int j = 0; j < piece; j++) {
                        // 构建SQL后缀
                        suffix.append("(");
                        for (int k = 0; k < questionMarkList.size(); k++) {
                            if (k == questionMarkList.size() - 1) {
                                suffix.append(batchArgs.get(i * piece + j)[k]);
                            } else {
                                suffix.append(batchArgs.get(i * piece + j)[k]).append(",");
                            }
                        }
                        suffix.append("),");
                    }
                    // 构建完整SQL
                    String sql = prefixSql + suffix.substring(0, suffix.length() - 1);
                    // 添加执行SQL
                    pst.addBatch(sql);
                    // 执行操作
                    pst.executeBatch();
                    // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                    conn.commit();
                    // 清空上一次添加的数据
                    suffix = new StringBuilder();
                }
            } else {
                for (Object[] batchArg : batchArgs) {
                    // 构建SQL后缀
                    suffix.append("(");
                    for (int k = 0; k < questionMarkList.size(); k++) {
                        if (k == questionMarkList.size() - 1) {
                            suffix.append(batchArg[k]);
                        } else {
                            suffix.append(batchArg[k]).append(",");
                        }
                    }
                    suffix.append("),");
                }
                // 构建完整SQL
                String sql = prefixSql + suffix.substring(0, suffix.length() - 1);
                // 添加执行SQL
                pst.addBatch(sql);
                // 执行操作
                pst.executeBatch();
                // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                conn.commit();
            }
            if (batchArgs.size() % 100 != 0 && piece > 0) {
                for (int m = piece * 100; m < batchArgs.size(); m++) {
                    suffix = new StringBuilder();
                    suffix.append("(");
                    for (int k = 0; k < questionMarkList.size(); k++) {
                        if (k == questionMarkList.size() - 1) {
                            suffix.append(batchArgs.get(m)[k]);
                        } else {
                            suffix.append(batchArgs.get(m)[k]).append(",");
                        }
                    }
                    suffix.append("),");
                    // 构建完整SQL
                    String sql = prefixSql + suffix.substring(0, suffix.length() - 1);
                    // 添加执行SQL
                    pst.addBatch(sql);
                    // 执行操作
                    pst.executeBatch();
                    // 提交事务  使用insert批量插入，每次插入10万条数据就提交一次事务，节省了大量时间
                    conn.commit();
                }
            }
            // 头等连接
//            pst.close();
//            conn.close();
        } catch (SQLException e) {
            conn.rollback();
        }
        // 耗时
//        logger.info("{}条数据插入花费时间{} : ", batchArgs.size(), (end - begin) / 1000 + " s");//107s
//        logger.info("插入完成");
    }


    public static String getShardTable(String downsamplingTableName, Long timestamp, Integer shardNum) {
        final String[] str = downsamplingTableName.split("_");
        String prefixTableName = str[0];
        int piece = totalSize / shardNum;
        long slot = timestamp / piece;
        if (slot > shardNum - 1) {
            slot = shardNum - 1;
        }
        return prefixTableName.concat("_").concat(String.valueOf(slot));
    }


    public static String erasePoint(String varName) {
        if (StringUtils.isNotEmpty(varName)) {
            varName = varName.replace(".", "");
        }
        return varName;
    }


    public static String parseOriginalVarName(String filterVarName, List<Map<String, String>> mapList) {
        for (Map<String, String> map : mapList) {
            if (map.get(filterVarName) != null) {
                return map.get(filterVarName);
            }
        }
        return null;

    }

    public static Long getMinValue(Long reqStartTimestamp, int sampleInterval) {
        final long startNearestValue = reqStartTimestamp / sampleInterval;
        return startNearestValue * sampleInterval;

    }

    public static Pair<Long, Long> getLastCanDownSamplingValue(Long reqEndTimestamp, int sampleInterval) {
        final long gap = reqEndTimestamp / sampleInterval;
        final long left = reqEndTimestamp % sampleInterval;
        if (left != 0) {
            Long max = (gap - 1) * sampleInterval;
            return Pair.of(max + 1, reqEndTimestamp);
        } else {
            Long max = gap * sampleInterval;
            return Pair.of(max, reqEndTimestamp);
        }

    }

    public static List<UniPoint> convertList2Uni(List<Map<String, Object>> list) {
        List<UniPoint> uniPointList = new ArrayList<>();
        for (Map<String, Object> map : list) {
            final Set<String> keySet = map.keySet();
            Long id = null;
            for (String key : keySet) {
                if (VarConst.ID.equals(key)) {
                    id = (Long) map.get(key);
                } else {
                    UniPoint uniPoint = new UniPoint(new BigDecimal(id), getBigDecimal(map.get(key)), key);
                    uniPointList.add(uniPoint);
                }
            }
        }
        return uniPointList;
    }

    public static boolean whetherNeedHandleHeadData(Long beginLeftStartTimestamp, Long reqStartTimestamp, int sampleInterval) {
        long headData = beginLeftStartTimestamp - sampleInterval;
        if (headData > reqStartTimestamp) {
            return true;
        }
        return false;
    }

    public static MultiValueMap uniPoint2Map(List<UniPoint> uniPointList, MultiValueMap baseMultiMap, List<Map<String, String>> mapList) {
        BigDecimal[] data;
        for (UniPoint uniPoint : uniPointList) {
            BigDecimal bg = uniPoint.getY().stripTrailingZeros();
            String value = bg.toPlainString();
            data = new BigDecimal[]{uniPoint.getX(), new BigDecimal(value)};
            baseMultiMap.put(parseOriginalVarName(uniPoint.getVarName(), mapList), data);
        }
        return baseMultiMap;
    }

    public static Integer customDownsamplingRule(int closestRate, int size) {
        Integer[] data = new Integer[]{4, 8, 16, 32, 64, 128, 256, 512};
        int currentMinDownRate = 1;
        for (Integer datum : data) {
            if (datum < closestRate) {
                if (datum <= size) {
                    currentMinDownRate = datum;
                }
            } else {
                return currentMinDownRate;
            }
        }
        return currentMinDownRate;
    }

    public static List<UniPoint> convertTraceDownsampling2UniPoint(List<TraceDownsampling> traceDownsamplingList, String varName) {
        List<UniPoint> uniPointList = new ArrayList<>();
        for (TraceDownsampling traceDownsampling : traceDownsamplingList) {
            UniPoint uniPoint = new UniPoint(new BigDecimal(traceDownsampling.getTimestamp()), traceDownsampling.getValue(), varName);
            uniPointList.add(uniPoint);
        }
        return uniPointList;
    }

    public static MultiValueMap uniPoint2Map(List<UniPoint> uniPointList, List<Map<String, String>> mapList) {
        MultiValueMap baseMultiMap = new MultiValueMap();
        BigDecimal[] data;
        for (UniPoint uniPoint : uniPointList) {
            BigDecimal bg = uniPoint.getY().stripTrailingZeros();
            String value = bg.toPlainString();
            data = new BigDecimal[]{uniPoint.getX(), new BigDecimal(value)};
            baseMultiMap.put(parseOriginalVarName(uniPoint.getVarName(), mapList), data);
        }
        return baseMultiMap;
    }

    public static Long getCircleStamp(Long stamp, int per) {
        long left = stamp % per;
        if (left != 0) {
            long piece = stamp / per;
            long leftAbs = Math.abs(stamp - piece * per);
            long rightAbs = Math.abs(stamp - (piece + 1) * per);
            if (leftAbs < rightAbs) {
                return piece * per;
            } else {
                return (piece + 1) * per;
            }
        }
        return stamp;
    }

    public static String earseLastPoint(String downsamplingTableName) {
        downsamplingTableName = downsamplingTableName.replace(".0", "");
        return downsamplingTableName;
        //return downsamplingTableName.substring(0,downsamplingTableName.length()-3);
    }

    public static List<UniPoint> dichotomyDown(List<UniPoint> uniPointList, int expectedNum) {
        do {
            List<UniPoint> otherFirstFilterList = LTThreeBuckets.sorted(uniPointList, uniPointList.size() / 2);
            uniPointList = removeAllQuickly(uniPointList, otherFirstFilterList);
        } while (uniPointList.size() > expectedNum * 8);
        return uniPointList;
    }

    public static List<UniPoint> dichotomyDownNotMissData(List<UniPoint> uniPointList, int expectedNum) {
        do {
            uniPointList = LTThreeBuckets.sorted(uniPointList, uniPointList.size() / 2);
            // uniPointList = removeAllQuickly(uniPointList, otherFirstFilterList);
        } while (uniPointList.size() > expectedNum);
        return uniPointList;
    }


    public static Pair<List<UniPoint>, List<UniPoint>> multiDown(List<UniPoint> list, int big, int small, int expectedNum) {
        List<UniPoint> firstFilterList;
        do {
            firstFilterList = LTThreeBuckets.sorted(list, big);
            //  removeAllQuickly(list, firstFilterList);
            list = dichotomyDown(list, small);
        } while (list.size() + firstFilterList.size() > expectedNum);
        return Pair.of(firstFilterList, list);
    }


    public static List<UniPoint> removeAllQuickly(List<UniPoint> a, List<UniPoint> b) {

        List<UniPoint> c = new LinkedList<>(a);//大集合用LinkedList

        HashSet<UniPoint> s = new HashSet<>(b);//小集合用HashSet

        c.removeIf(s::contains);

        return c;

    }

    public static String getNextDownRate(String preDownTableSuffix) {
        final Integer[] data = IoComposeServiceDatabase.data;
        for (Integer datum : data) {
            if (datum > Integer.parseInt(preDownTableSuffix)) {
                return String.valueOf(datum);
            }
        }
        return preDownTableSuffix;
    }

    public static byte[] parseTimestampToBCD(String timestamp) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date date = dateFormat.parse(timestamp);
            SimpleDateFormat bcdFormat = new SimpleDateFormat("yyyyMMddHHmmss");
            String bcdString = bcdFormat.format(date);
            byte[] bcd = new byte[7];
            for (int i = 0; i < bcdString.length(); i += 2) {
                bcd[i / 2] = (byte) ((Character.digit(bcdString.charAt(i), 10) << 4) + Character.digit(bcdString.charAt(i + 1), 10));
            }
            reverseByteArray(bcd);
            return bcd;
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[7];
        }
    }

    public static void reverseByteArray(byte[] array) {
        int left = 0; // 开始指针
        int right = array.length - 1; // 结束指针

        while (left < right) {
            // 交换left和right指向的元素
            byte temp = array[left];
            array[left] = array[right];
            array[right] = temp;

            // 移动指针
            left++;
            right--;
        }
    }

    public static void main(String[] args) {
        byte[] bytes = parseTimestampToBCD("2024-07-19 16:58:55");
        System.out.printf(Arrays.toString(bytes));
    }


}
