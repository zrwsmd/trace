package com.yt.server.entity;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections.map.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static com.yt.server.util.BaseUtils.getBigDecimal;

/**
 * @description:
 * @projectName:native-test
 * @see:com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/2/23 13:30
 * @version:1.0
 */
public class VsCodeReqParam extends RequestParameter {

    private Long requestId;

    private String type;

    private JSONObject tData;

    private List<Long> reqTimestamp=new ArrayList<>();

    private List<Long>traceIdList=new LinkedList<>();

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public JSONObject gettData() {
        return tData;
    }

    public void settData(JSONObject tData) {
        this.tData = tData;
    }

    public List<Long> getReqTimestamp() {
        return reqTimestamp;
    }

    public void setReqTimestamp(List<Long> reqTimestamp) {
        this.reqTimestamp = reqTimestamp;
    }

    public void setTraceIdList(List<Long> traceIdList) {
        this.traceIdList = traceIdList;
    }

    public List<Long> getTraceIdList() {
        return traceIdList;
    }
}
