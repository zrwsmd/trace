package com.yt.server.service.impl;


import com.yt.server.entity.Hero;
import com.yt.server.mapper.HeroMapper;
import com.yt.server.service.HeroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HeroServiceImpl implements HeroService {


    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HeroMapper heroMapper;


//    private HeroMapper heroMapper;
//
//    @Autowired
//    public void setHeroMapper(HeroMapper heroMapper) {
//        this.heroMapper = heroMapper;
//    }


    @Override
    public Hero selectByPrimaryKey(Integer id) {
        return jdbcTemplate.queryForObject("select * from hero where id = ?", new BeanPropertyRowMapper<Hero>(Hero.class), id);
       // return heroMapper.selectByPrimaryKey(id);
        //return null;
    }
}
