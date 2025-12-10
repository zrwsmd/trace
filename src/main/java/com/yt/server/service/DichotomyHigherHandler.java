package com.yt.server.service;

import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yt.server.util.BaseUtils.*;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/11 9:40
 * @version:1.0
 */
public class DichotomyHigherHandler implements Callable<MultiValueMap> {

    private static final Logger logger = LoggerFactory.getLogger(DichotomyHigherHandler.class);

    private final List<UniPoint> uniPointList;
    private final List<Map<String, String>> mapList;
    private final CountDownLatch countDownLatch;
    private final Integer big;
    private final Integer small;
    private final Integer totalExpectedNum;
    private final Integer notMissDataExpectedNum;

    public DichotomyHigherHandler(List<UniPoint> uniPointList, List<Map<String, String>> mapList, CountDownLatch countDownLatch, Integer big, Integer small, Integer totalExpectedNum, Integer notMissDataExpectedNum) {
        this.uniPointList = uniPointList;
        this.mapList = mapList;
        this.countDownLatch = countDownLatch;
        this.big = big;
        this.small = small;
        this.totalExpectedNum = totalExpectedNum;
        this.notMissDataExpectedNum = notMissDataExpectedNum;
    }

    @Override
    public MultiValueMap call() throws Exception {
        MultiValueMap allMultiValueMap=new MultiValueMap();
        try {
            final Pair<List<UniPoint>, List<UniPoint>> pair = multiDown(uniPointList, big, small, totalExpectedNum);
            final List<UniPoint> notMissDatauniPointList = dichotomyDownNotMissData(uniPointList, notMissDataExpectedNum);
            MultiValueMap leftMultiValueMap = uniPoint2Map(pair.getLeft(), mapList);
            allMultiValueMap.putAll(leftMultiValueMap);
            MultiValueMap rightMultiValueMap = uniPoint2Map(pair.getRight(), mapList);
            allMultiValueMap.putAll(rightMultiValueMap);
            MultiValueMap otherMultiValueMap = uniPoint2Map(notMissDatauniPointList, mapList);
            allMultiValueMap.putAll(otherMultiValueMap);
            countDownLatch.countDown();
        } catch (Exception e) {
            logger.error(DichotomyHigherHandler.class.getName(), e);
        }
        return allMultiValueMap;
    }
}
