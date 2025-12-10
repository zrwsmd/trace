package com.yt.server.mapper;


import com.yt.server.entity.TraceFieldMeta;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TraceFieldMetaMapper {

    int deleteByPrimaryKey(Long id);

    int insert(TraceFieldMeta record);

    int insertSelective(TraceFieldMeta record);

    TraceFieldMeta selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TraceFieldMeta record);

    int updateByPrimaryKey(TraceFieldMeta record);

    void insertBatch(List<TraceFieldMeta> list);

    List<TraceFieldMeta>getCurrentFieldNames(Long traceId);

    int deleteByIds(List<Long>idList);
}