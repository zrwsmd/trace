package com.yt.server.mapper;

import com.yt.server.entity.TraceTableRelatedInfo;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TraceTableRelatedInfoMapper {

    int deleteByPrimaryKey(Long traceId);

    int insert(TraceTableRelatedInfo record);

    int insertSelective(TraceTableRelatedInfo record);

    TraceTableRelatedInfo selectByPrimaryKey(Long traceId);

    int updateByPrimaryKeySelective(TraceTableRelatedInfo record);

    int updateByPrimaryKey(TraceTableRelatedInfo record);

    List<TraceTableRelatedInfo> selectAll();

    List<TraceTableRelatedInfo>disSelect(List<Long>traceIds);
}