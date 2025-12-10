package com.yt.server.mapper;



import com.yt.server.entity.Hero;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;


@Mapper
public interface HeroMapper {


    Hero selectByPrimaryKey(Integer id);


}