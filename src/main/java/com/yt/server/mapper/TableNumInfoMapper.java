package com.yt.server.mapper;

import com.yt.server.entity.TableNumInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TableNumInfoMapper {

    int deleteByPrimaryKey(Integer id);

    int insert(TableNumInfo record);

    int insertSelective(TableNumInfo record);

    TableNumInfo selectByPrimaryKey(Integer id);

    int updateByPrimaryKeySelective(TableNumInfo record);

    int updateByPrimaryKey(TableNumInfo record);
}