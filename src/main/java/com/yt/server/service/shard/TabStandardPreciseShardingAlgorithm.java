//package com.yt.server.service.shard;
//
//
//import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm;
//import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue;
//
//import java.util.Collection;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server.service.shard
// * @author:赵瑞文
// * @createTime:2023/2/27 14:56
// * @version:1.0
// */
//public class TabStandardPreciseShardingAlgorithm implements PreciseShardingAlgorithm<Long> {
//
//
//    public TabStandardPreciseShardingAlgorithm(){
//
//    }
//
//
////    @Override
////    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
////        System.out.println("分片参数信息：" + shardingValue);
////        Long value = shardingValue.getValue();
////
////        if (value < 100L) {
////            return "trace1_0";
////        } else if (value < 200L) {
////            return "trace1_1";
////        } else {
////            return "";
////        }
////    }
////
////    @Override
////    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> shardingValue) {
////        return null;
////    }
////
////    @Override
////    public void init() {
////
////    }
////
////    @Override
////    public String getType() {
////        return null;
////    }
//
//    @Override
//    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> shardingValue) {
//                System.out.println("分片参数信息：" + shardingValue);
//        Long value = shardingValue.getValue();
//
//        if (value < 1000000L) {
//            return "trace1_0";
//        } else if (value < 2000000L) {
//            return "trace1_1";
//        }else if (value < 3000000L) {
//            return "trace1_2";
//        }else if (value < 4000000L) {
//            return "trace1_3";
//        }else if (value < 5000000L) {
//            return "trace1_4";
//        }else if (value < 6000000L) {
//            return "trace1_5";
//        }else if (value < 7000000L) {
//            return "trace1_6";
//        }else if (value < 8000000L) {
//            return "trace1_7";
//        }else if (value < 9000000L) {
//            return "trace1_8";
//        }else if (value <= 10000000L) {
//            return "trace1_9";
//        } else {
//            return "";
//        }
//    }
//}
