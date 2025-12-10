package com.yt.server.mapper;

import com.yt.server.entity.TraceTimestampStatistics;
import com.yt.server.entity.TraceTimestampStatisticsExample;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface TraceTimestampStatisticsMapper {
    long countByExample(TraceTimestampStatisticsExample example);

    int deleteByPrimaryKey(Long traceId);

    int insert(TraceTimestampStatistics record);

    int insertSelective(TraceTimestampStatistics record);

    TraceTimestampStatistics selectByPrimaryKey(Long traceId);

    int updateByPrimaryKeySelective(TraceTimestampStatistics record);

    int updateByPrimaryKey(TraceTimestampStatistics record);
}
