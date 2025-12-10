//package com.yt.server.service;
//
//import com.yt.server.entity.UniPoint;
//import org.apache.commons.collections.map.MultiValueMap;
//import org.apache.commons.lang3.tuple.Pair;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.*;
//
//import static com.yt.server.util.BaseUtils.*;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server.service
// * @author:赵瑞文
// * @createTime:2023/7/11 9:40
// * @version:1.0
// */
//public class DichotomyHandler implements Callable<MultiValueMap> {
//
//    private static final Logger logger = LoggerFactory.getLogger(DichotomyHandler.class);
//
//    private final List<UniPoint> uniPointList;
//    private final List<Map<String, String>> mapList;
//    private final CountDownLatch countDownLatch;
//    private final Integer big;
//    private final Integer small;
//    private final Integer expectedNum;
//
//    public DichotomyHandler(List<UniPoint> uniPointList, List<Map<String, String>> mapList, CountDownLatch countDownLatch, Integer big, Integer small, Integer expectedNum) {
//        this.uniPointList = uniPointList;
//        this.mapList = mapList;
//        this.countDownLatch = countDownLatch;
//        this.big = big;
//        this.small = small;
//        this.expectedNum = expectedNum;
//    }
//
//    @Override
//    public MultiValueMap call() throws Exception {
//        MultiValueMap allMultiValueMap=new MultiValueMap();
//        try {
//            Pair<List<UniPoint>, List<UniPoint>> pair = multiDown(uniPointList, big, small, expectedNum);
//             MultiValueMap multiValueMap = uniPoint2Map(pair.getLeft(), mapList);
//            MultiValueMap otherMultiValueMap = uniPoint2Map(pair.getRight(), mapList);
//            allMultiValueMap.putAll(multiValueMap);
//            allMultiValueMap.putAll(otherMultiValueMap);
//            countDownLatch.countDown();
//        } catch (Exception e) {
//            logger.error(DichotomyHandler.class.getName(), e);
//        }
//        return allMultiValueMap;
//    }
//}
