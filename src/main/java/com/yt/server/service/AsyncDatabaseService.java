package com.yt.server.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2026/1/26 16:45
 * @version:1.0
 */
@Service
public class AsyncDatabaseService {

    private static final String MYSQL_DIR = "D://trace-mysql//bin//";
    private static final int MYSQL_PORT = 3307;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "123456";

    // 存储任务状态
    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AsyncDatabaseService.class);


    /**
     * 异步导入（立即返回任务ID）
     */
    @Async
    public CompletableFuture<String> loadAsync(String taskId, String sqlFilePath, String databaseName) {

        try {
            // 更新状态：进行中
            updateTaskStatus(taskId, "running", 0, "开始导入...");

            logger.debug("开始导入: " + sqlFilePath);

            // 1. 优化配置
            updateTaskStatus(taskId, "running", 5, "优化MySQL配置...");
            executeSqlCommand("SET GLOBAL foreign_key_checks=0");
            executeSqlCommand("SET GLOBAL unique_checks=0");
            executeSqlCommand("SET GLOBAL innodb_flush_log_at_trx_commit=2");
            executeSqlCommand("SET GLOBAL max_allowed_packet=1073741824");
            executeSqlCommand("SET GLOBAL wait_timeout=28800");
            executeSqlCommand("SET GLOBAL interactive_timeout=28800");
            executeSqlCommand("SET GLOBAL net_read_timeout=3600");
            executeSqlCommand("SET GLOBAL net_write_timeout=3600");

            try {
                // 2. 导入数据
                updateTaskStatus(taskId, "running", 10, "开始导入数据...");

                String sqlPath = sqlFilePath.replace("\\", "/");

                String command = String.format(
                        "%smysql.exe -P %d -u%s -p%s " +
                                "--max_allowed_packet=1G " +
                                "--net_buffer_length=16384 " +
                                "%s -e \"source %s\"",
                        MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD,
                        databaseName, sqlPath
                );

                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // 读取输出并更新进度
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    int progress = 10;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);

                        // 简单的进度估算（可以根据实际情况优化）
                        if (progress < 90) {
                            progress += 1;
                            updateTaskStatus(taskId, "running", progress, "导入中: " + line.substring(0, Math.min(50, line.length())));
                        }

                        if (line.contains("ERROR")) {
                            updateTaskStatus(taskId, "error", progress, "错误: " + line);
                        }
                    }
                }

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    updateTaskStatus(taskId, "error", 0, "导入失败，退出码: " + exitCode);
                    throw new RuntimeException("导入失败，退出码: " + exitCode);
                }

                updateTaskStatus(taskId, "running", 95, "恢复MySQL配置...");

            } finally {
                // 3. 恢复配置
                executeSqlCommand("SET GLOBAL foreign_key_checks=1");
                executeSqlCommand("SET GLOBAL unique_checks=1");
                executeSqlCommand("SET GLOBAL innodb_flush_log_at_trx_commit=1");
            }

            updateTaskStatus(taskId, "success", 100, "导入完成！");
            return CompletableFuture.completedFuture("success");

        } catch (Exception e) {
            e.printStackTrace();
            updateTaskStatus(taskId, "error", 0, "导入失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, String status, int progress, String message) {
        TaskStatus taskStatus = taskStatusMap.computeIfAbsent(taskId, k -> new TaskStatus());
        taskStatus.setStatus(status);
        taskStatus.setProgress(progress);
        taskStatus.setMessage(message);
        taskStatus.setUpdateTime(System.currentTimeMillis());

        System.out.println("任务 " + taskId + ": " + status + " - " + progress + "% - " + message);
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    /**
     * 执行SQL命令
     */
    private void executeSqlCommand(String sql) throws Exception {
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -e \"%s\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, sql
        );

        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }

    // 任务状态类
    public static class TaskStatus {
        private String status;  // running, success, error
        private int progress;   // 0-100
        private String message;
        private long updateTime;

        // Getters and Setters
        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public long getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(long updateTime) {
            this.updateTime = updateTime;
        }
    }
}
