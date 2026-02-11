package com.yt.server.service;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2023/5/9 10:03
 * @version:1.0
 */
public class BackGroundDownSamplingTask implements Runnable {

    private final String varName;
    private final JdbcTemplate jdbcTemplate;
    private final String downsamplingTableName;
    private final CountDownLatch countDownLatch;

    public BackGroundDownSamplingTask(String varName, JdbcTemplate jdbcTemplate, String downsamplingTableName,
                                      CountDownLatch countDownLatch) {
        this.varName = varName;
        this.jdbcTemplate = jdbcTemplate;
        this.downsamplingTableName = downsamplingTableName;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        try {
            for (Integer downRate : IoComposeServiceDatabase.data) {
                String eachVarNameDownsamplingTableName = downsamplingTableName.concat("_").concat(varName).concat("_")
                        .concat(String.valueOf(downRate));
                String delSqlString = " DROP TABLE IF EXISTS " + "`" + eachVarNameDownsamplingTableName + "`" + ";";
                jdbcTemplate.execute(delSqlString);
                // 创建对应的降采样表
                StringBuilder samplingSql = new StringBuilder();
                samplingSql.append("CREATE TABLE " + "`").append(eachVarNameDownsamplingTableName).append("`")
                        .append("(");
                samplingSql.append("`id` bigint NOT NULL AUTO_INCREMENT, ");
                samplingSql.append(" `timestamp` bigint DEFAULT NULL, ");
                samplingSql.append(" `value` decimal(20,8) DEFAULT NULL, ");
                samplingSql.append(" PRIMARY KEY (`id`), ");
                samplingSql.append(" UNIQUE KEY `trace_downsampling_un` (`timestamp`,`value`) ");
                samplingSql.append(
                        " ) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;");
                String samplingSqlStr = samplingSql.toString();
                jdbcTemplate.execute(samplingSqlStr);
            }
        } finally {
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        }
    }
}
