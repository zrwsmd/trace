package com.yt.server.controller;


import com.yt.server.entity.Hero;
import com.yt.server.service.HeroService;
import com.yt.server.service.QueryFullTableHandler;
import org.apache.commons.collections.map.MultiValueMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.yt.server.service.IoComposeServiceDatabase.DOMAIN_PREFIX;


@RestController
@RequestMapping("/basic")
public class BasicController {

    @Autowired
    private HeroService heroService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Integer CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public ThreadPoolExecutor pool = new ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE * 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));



    @RequestMapping("/selectHeroById")
    public Hero selectHeroById(Integer id) {
        throw new RuntimeException("请勿调用此接口!");
        //return heroService.selectByPrimaryKey(id);
    }



    @RequestMapping("/b")
    public String b(String reqStartTimestamp, String reqEndTimestamp) throws InterruptedException, NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        final Class<?> clazz = Class.forName(DOMAIN_PREFIX.concat("Trace1"));
        Object[] regionParam = new Object[]{reqStartTimestamp, reqEndTimestamp};
        String originalRegionSql = "select * from " + "trace1" + " where id between ? and ? ";
        long start = System.currentTimeMillis();
        List list = jdbcTemplate.query(originalRegionSql, regionParam, new BeanPropertyRowMapper<>(clazz));
        MultiValueMap multiValueMap = null;
//        System.out.println(list);
        long end=System.currentTimeMillis();
        System.out.println("花费了"+(end-start)+"ms");//21ms
        return "ok";
    }


    public static List<String> getQueryTable(Long reqStartTimestamp, Long reqEndTimestamp, String parentTable) {
        List<String> list = new ArrayList<>();
        if (reqStartTimestamp > reqEndTimestamp) {
            throw new RuntimeException("开始时间戳不能大于结束时间戳!");
        }
        if (reqStartTimestamp > 9000000) {
            list.add(parentTable.concat("_").concat(String.valueOf(9)));
        }
        if (reqEndTimestamp < 1000000) {
            list.add(parentTable.concat("_").concat(String.valueOf(0)));
        }
        int beginSlot = (int) (reqStartTimestamp / 1000000);
        int endSlot = (int) (reqEndTimestamp / 1000000);
        for (int i = beginSlot; i <= endSlot; i++) {
            list.add(parentTable.concat("_").concat(String.valueOf(i)));
        }
        return list;
    }


}
