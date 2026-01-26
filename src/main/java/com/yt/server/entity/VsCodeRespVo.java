package com.yt.server.entity;

import com.alibaba.fastjson.JSONObject;

/**
 * @description:
 * @projectName:native-test
 * @see:com.yt.server.entity
 * @author:赵瑞文
 * @createTime:2023/2/23 15:43
 * @version:1.0
 */
public class VsCodeRespVo {

    private Long responseId;

    private boolean ret;

    private String type;

    private JSONObject tData;

    private String taskId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getResponseId() {
        return responseId;
    }

    public void setResponseId(Long responseId) {
        this.responseId = responseId;
    }

    public boolean isRet() {
        return ret;
    }

    public void setRet(boolean ret) {
        this.ret = ret;
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
}
