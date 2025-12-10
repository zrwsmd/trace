//package com.yt.server.aot;
//
//import com.yt.server.service.shard.TabStandardRangeShardingAlgorithm;
//import com.zaxxer.hikari.HikariDataSource;
//import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
//import org.apache.shardingsphere.infra.config.RuleConfiguration;
//import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration;
//import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
//import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
//import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//import javax.sql.DataSource;
//import java.sql.SQLException;
//import java.util.*;
//
///**
// * @description:
// * @projectName:native-test
// * @see:com.yt.server.aot
// * @author:赵瑞文
// * @createTime:2023/2/27 13:35
// * @version:1.0
// */
//@Configuration
//public class ShardingConfig2 {
//
//    @Autowired
//    private DataSource dataSource;
//    @Bean
//    public DataSource dataSource() throws SQLException {
//        //数据源Map
//        Map<String, DataSource> dsMap = new HashMap<>();
////        // Druid连接池可以根据需要自己配置
////        HikariDataSource hikariDataSource = new HikariDataSource();
////        hikariDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
////        hikariDataSource.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/test?userUnicode=true&characterEncoding=UTF-8");
////        hikariDataSource.setUsername("root");
////        hikariDataSource.setPassword("123456");
//        //配置主库
//        dsMap.put("test", dataSource);
//        //配置分片规则
//        List<RuleConfiguration> ruleConfigs = new ArrayList<>();
//        // user表分表规则规则
//        ruleConfigs.add(getShardingRuleConfiguration());
//        //创建DS
//        // 可选参数
//        Properties prop = new Properties();
//        // 打印sql
//        prop.setProperty("sql-show", "true");
//        // 创建sharding数据源
//        DataSource source = ShardingSphereDataSourceFactory.createDataSource(dsMap, ruleConfigs, prop);
//        // 获取sharding上下文
//    //    ComputeNodeInstance instance = ((ShardingSphereDataSource) source).getContextManager().getInstanceContext().getInstance();
////        // 获取workId及设置workId
////        Long workerId = InitWorkerId.getInstance(redisTemplate);
////        instance.setWorkerId(workerId);
//        return source;
//    }
//
////    @Bean
////    JdbcTemplate jdbcTemplate() throws SQLException {
////        return new JdbcTemplate(dataSource());
////    }
//
//    @Bean
//    ShardingRuleConfiguration getShardingRuleConfiguration() {
//        ShardingRuleConfiguration shardingRuleConfig = new ShardingRuleConfiguration();
//        //配置各个表的分库分表策略
//        shardingRuleConfig.getTables().add(getTableRuleConfiguration("",""));
//
//
////        // 分库
////        Properties dbShardingAlgorithmrProps = new Properties();
////        dbShardingAlgorithmrProps.setProperty(
////                "algorithm-expression",
////                "trace1_${id % 2}"
////        );
////        shardingRuleConfig.getShardingAlgorithms().put(
////                "dbShardingAlgorithm",
////                new ShardingSphereAlgorithmConfiguration("INLINE", dbShardingAlgorithmrProps));
//
//        //分表
//        Properties tableShardingAlgorithmrProps = new Properties();
//        tableShardingAlgorithmrProps.setProperty("strategy","STANDARD");
//        tableShardingAlgorithmrProps.setProperty(
//                "algorithmClassName",
//                TabStandardRangeShardingAlgorithm.class.getName());
////
//
//      //  shardingRuleConfig.setDefaultTableShardingStrategy(new StandardShardingStrategyConfiguration("id", TabStandardRangeShardingAlgorithm.class.getName()));
//
//        shardingRuleConfig.getShardingAlgorithms().put(
//                "zrwShardingAlgorithm", new ShardingSphereAlgorithmConfiguration("CLASS_BASED", tableShardingAlgorithmrProps));
//
//        //配置默认分表规则
//     //   shardingRuleConfig.setDefaultTableShardingStrategy(new NoneShardingStrategyConfiguration());
//        return shardingRuleConfig;
//    }
//
//    /**
//     * 具体分库分表规则配置
//     * @return
//     */
//    ShardingTableRuleConfiguration getTableRuleConfiguration(String logicalTableName,String dataNodes) {
//        ShardingTableRuleConfiguration result = new ShardingTableRuleConfiguration("trace1","test.trace1_${0..1}");
//        //配置分库策略
//        // result.setDatabaseShardingStrategy(new StandardShardingStrategyConfiguration("id","dbShardingAlgorithm"));
//        //配置分表策略
//        result.setTableShardingStrategy(new StandardShardingStrategyConfiguration("id","zrwShardingAlgorithm"));
//        return result;
//    }
//
//
//
//
//
//
//
//
//}
