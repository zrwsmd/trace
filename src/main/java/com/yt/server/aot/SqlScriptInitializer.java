//package com.yt.server.aot;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Conditional;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.io.Resource;
//import org.springframework.jdbc.datasource.init.DataSourceInitializer;
//import org.springframework.jdbc.datasource.init.DatabasePopulator;
//import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
//
//import javax.sql.DataSource;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server.aot
// * @author:赵瑞文
// * @createTime:2023/3/14 15:45
// * @version:1.0
// */
//@Configuration
//public class SqlScriptInitializer {
//
//    private static final Logger logger = LoggerFactory.getLogger(SqlScriptInitializer.class);
//
//    @Autowired
//    private DataSource dataSource;
//
//    @Value("classpath:sql/baseTable.sql")
//    private Resource sqlScript;
//
//    @Bean
//    @Conditional(DbCondition.class)
//    public DataSourceInitializer dataSourceInitializer(){
//        DataSourceInitializer dataSourceInitializer = new DataSourceInitializer();
//        dataSourceInitializer.setDataSource(dataSource);
//        dataSourceInitializer.setDatabasePopulator(databasePopulator());
//        return dataSourceInitializer;
//    }
//
//    private DatabasePopulator databasePopulator(){
//        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
//        populator.addScript(sqlScript);
////        populator.setSeparator("$$$"); // 分隔符，默认为;
//        return populator;
//    }
//
//}
