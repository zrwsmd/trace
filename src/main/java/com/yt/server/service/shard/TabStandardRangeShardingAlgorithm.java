//package com.yt.server.service.shard;
//
//
//import com.google.common.collect.Range;
//import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm;
//import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue;
//import org.apache.shardingsphere.api.sharding.standard.RangeShardingAlgorithm;
//import org.apache.shardingsphere.api.sharding.standard.RangeShardingValue;
////import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
////import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
////import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;
//
//import java.util.*;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server.service.shard
// * @author:赵瑞文
// * @createTime:2023/2/27 14:56
// * @version:1.0
// */
//public class TabStandardRangeShardingAlgorithm implements RangeShardingAlgorithm<Long> {
//
//
//    public TabStandardRangeShardingAlgorithm(){
//
//    }
//
//    @Override
//    public Collection<String> doSharding(Collection<String> tableNames, RangeShardingValue<Long> shardingValue) {
//        final Long reqStartTimestamp = shardingValue.getValueRange().lowerEndpoint();
//        final Long reqEndTimestamp = shardingValue.getValueRange().upperEndpoint();
//        Set<String> set = new HashSet<>();
//        final Object[] tableNameArr = tableNames.toArray();
//        final String tableName = tableNames.iterator().next();
//        final String[] split = tableName.split("_");
//        String prefixTableName=split[0];
//        int beginSlot = (int) (reqStartTimestamp / 1000000);
//        int endSlot = (int) (reqEndTimestamp / 1000000);
//        for (int i = beginSlot; i <= endSlot; i++) {
//            set.add(prefixTableName.concat("_").concat(String.valueOf(i)));
//        }
//        if (reqStartTimestamp > 9000000) {
//            set.add((String) tableNameArr[tableNameArr.length - 1]);
//        }
//        if (reqEndTimestamp < 1000000) {
//            set.add(tableNames.iterator().next());
//        }
//        return set;
//    }
//
//}
