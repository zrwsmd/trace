package com.yt.server.controller;

import com.yt.server.entity.RequestParameter;
import com.yt.server.entity.VsCodeReqParam;
import com.yt.server.entity.VsCodeRespVo;
import com.yt.server.mapper.TraceFieldMetaMapper;
import com.yt.server.service.IoComposeServiceDatabase;
import org.apache.commons.collections.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

;


@RestController
@RequestMapping("/io")
public class IoController {

    private final Logger logger = LoggerFactory.getLogger(IoController.class);


    @Autowired
    private IoComposeServiceDatabase ioComposeServiceDatabase;

    /**
     * 处理实时数据并且实时返回
     *
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/getFileHandleExecutor")
    public MultiValueMap getFileHandleExecutor(RequestParameter RequestParameter) throws Exception {
        return ioComposeServiceDatabase.getFileHandleExecutor(RequestParameter);

    }

    /**
     * type:'traceVarsData',
     * tData:{
     * id:"",
     * var0:[[0,222],[20,333],[40,444]],//变量var0采样数据
     * var1:[[0,1222],[20,1333],[40,1444]],//变量var1采样数据
     * var2:[[0,2222],[20,2333],[40,2444]],//变量var2采样数据
     * }
     * //* @param requestParameter
     *
     * @return
     * @throws Exception
     */
//    @PostMapping("/analyzeTraceLiveData")
//    public void analyzeTraceLiveData(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
//        ioComposeServiceDatabase.analyzeTraceLiveData(vsCodeReqParam);
//
//    }

    @PostMapping("/traceCreate")
    public VsCodeRespVo traceCreate(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceCreate(vsCodeReqParam);

    }

    @PostMapping("/traceStart")
    public VsCodeRespVo traceStart(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceStart(vsCodeReqParam);

    }

    @PostMapping("/traceStop")
    public VsCodeRespVo traceStop(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceStop(vsCodeReqParam);

    }

    @PostMapping("/traceDestroy")
    public VsCodeRespVo traceDestroy(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceDestroy(vsCodeReqParam);

    }

    @PostMapping("/traceGc")
    public VsCodeRespVo traceGc(@RequestBody VsCodeReqParam vsCodeReqParam) {
         return ioComposeServiceDatabase.traceInitializeGc(vsCodeReqParam);
    }

    @PostMapping("/analyzeTraceLiveData2")
    public void analyzeTraceLiveData2(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        ioComposeServiceDatabase.analyzeTraceLiveData2(vsCodeReqParam);

    }

    @PostMapping(value = "/getSingleTimestampFileHandleExecutor")
    public MultiValueMap getSingleTimestampFileHandleExecutor(VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.getSingleTimestampFileHandleExecutor(vsCodeReqParam);
    }

    @PostMapping("/traceSave")
    public VsCodeRespVo traceSave(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceSave(vsCodeReqParam);

    }

    @PostMapping("/traceLoad")
    public VsCodeRespVo traceLoad(@RequestBody VsCodeReqParam vsCodeReqParam) throws Exception {
        return ioComposeServiceDatabase.traceLoad(vsCodeReqParam);

    }

}
