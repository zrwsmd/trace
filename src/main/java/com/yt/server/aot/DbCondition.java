package com.yt.server.aot;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static com.yt.server.service.IoComposeServiceDatabase.DATABASE_NAME;
import static com.yt.server.service.IoComposeServiceDatabase.DEFAULT_TABLE;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.aot
 * @author:赵瑞文
 * @createTime:2023/3/14 16:32
 * @version:1.0
 */

public class DbCondition implements Condition {

    private static final String driverClassName;

    private static final String url;

    private static final String username;

    private static final String password;

    private static final Properties properties = new Properties();

    static {
        try {
            properties.load(DbCondition.class.getClassLoader().getResourceAsStream("application.properties"));
            driverClassName = properties.getProperty("spring.datasource.driverClassName");
            url = properties.getProperty("spring.datasource.url");
            username = properties.getProperty("spring.datasource.username");
            password = properties.getProperty("spring.datasource.password");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {

        try {
            return isDbExist(DATABASE_NAME) && isTableExist(Arrays.asList(DEFAULT_TABLE));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isDbExist(String dbName) throws SQLException {
        Connection connection;
        Statement statement = null;
        String defaultDb = "jdbc:mysql://127.0.0.1:3306/sys?characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        try {
            Class.forName(driverClassName);
            // 创建链接
            connection = DriverManager.getConnection(defaultDb, username, password);
            statement = connection.createStatement();
            String sql = "USE " + dbName;
            statement.execute(sql);
            return false;
        } catch (Exception e) {
            connection = DriverManager.getConnection(defaultDb, username, password);
            statement = connection.createStatement();
            String sql = "CREATE DATABASE " + dbName;
            statement.execute(sql);
            return true;
        }

    }

    private boolean isTableExist(List<String> tableNames) {
        Connection conn = null;
        ResultSet rs = null;
        try {
            Class.forName(driverClassName);
            // 创建链接
            conn = DriverManager.getConnection(url, username, password);
            DatabaseMetaData data = conn.getMetaData();
            String[] types = {"TABLE"};
            for (String tableName : tableNames) {
                /**
                 * mysql8.0的驱动，在5.5之前nullCatalogMeansCurrent属性默认为true,8.0中默认为false，所以导致DatabaseMetaData.getTables()加载了全部的无关表,rs.next()即使表不存在也返回了true。
                 * 在jdbc 创建连接的url后加上&nullCatalogMeansCurrent=true
                 */
                rs = data.getTables(null, null, tableName, types);
                if (!rs.next()) {
                    return true;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }


}
