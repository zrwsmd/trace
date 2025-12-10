package com.yt.server.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/2/13 16:36
 * @version:1.0
 */
public class ConnectionManager {
    //使用ThreadLocal保存Connection变量
    private static  ThreadLocal<Connection> connectionHolder = new ThreadLocal<Connection>();

    private static final String username;

    private static final String password;

    private static final Properties properties = new Properties();

    static {
        try {
            properties.load(ConnectionManager.class.getClassLoader().getResourceAsStream("application.properties"));
            username = properties.getProperty("spring.datasource.username");
            password = properties.getProperty("spring.datasource.password");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 连接Connection
     *
     * @return
     */
    public static Connection getConnection(String databaseName) throws ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        //ThreadLocal取得当前线程的connection
        Connection conn = connectionHolder.get();
        //如果ThreadLocal没有绑定相应的Connection，创建一个新的Connection，
        //并将其保存到本地线程变量中。
        if (conn == null) {
            try {
                conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/" + databaseName + "?useUnicode=true&characterEncoding=utf-8&useSSL=false&nullCatalogMeansCurrent=true", username, password);
                //将当前线程的Connection设置到ThreadLocal
                connectionHolder.set(conn);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return conn;

    }

    /**
     * 关闭Connection，清除集合中的Connection
     */
    public static void closeConnection() {
        //ThreadLocal取得当前线程的connection
        Connection conn = connectionHolder.get();
        //当前线程的connection不为空时，关闭connection.
        if (conn != null) {
            try {
                conn.close();
                //connection关闭之后，要从ThreadLocal的集合中清除Connection
                connectionHolder.remove();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }

    }

}
