package com.yt.server.mapper;

import com.yt.server.entity.TraceFieldMeta;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TraceFieldMetaMapper {

    int deleteByPrimaryKey(Long id);

    int insert(TraceFieldMeta record);

    int insertSelective(TraceFieldMeta record);

    List<TraceFieldMeta> selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TraceFieldMeta record);

    int updateByPrimaryKey(TraceFieldMeta record);

    void insertBatch(List<TraceFieldMeta> list);

    List<TraceFieldMeta> getCurrentFieldNames(Long traceId);

    int deleteByIds(List<Long> idList);
}
