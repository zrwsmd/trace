package com.yt.server.service;

import com.alibaba.fastjson.JSONObject;
import com.yt.server.entity.UniPoint;
import com.yt.server.util.BaseUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.yt.server.util.BaseUtils.getBigDecimal;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/7/20 11:05
 * @version:1.0
 */
public class DispatchVarDataHandler implements Callable<Set<UniPoint>> {

    private final JSONObject jsonObject;
    private final String varName;
    private final CountDownLatch countDownLatch;

    public DispatchVarDataHandler(JSONObject jsonObject, String varName, CountDownLatch countDownLatch) {
        this.jsonObject = jsonObject;
        this.varName = varName;
        this.countDownLatch = countDownLatch;
    }


    @Override
    public Set<UniPoint> call() throws Exception {
        Set<UniPoint> dataSet;
        try {
            dataSet = new HashSet<>();
            Object o = jsonObject.get(varName);
            if (o instanceof List<?>) {
                List list = (List) o;
                for (Object innerObj : list) {
                    List innerList = (List) innerObj;
                    final Object x = innerList.get(0);
                    final Object y = innerList.get(1);
                    String filterVarName = BaseUtils.erasePoint(varName);
                    UniPoint uniPoint = new UniPoint(getBigDecimal(x), getBigDecimal(y), filterVarName);
                    dataSet.add(uniPoint);
                }
            }
            countDownLatch.countDown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return dataSet;
    }
}
