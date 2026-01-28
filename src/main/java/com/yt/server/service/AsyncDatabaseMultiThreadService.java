package com.yt.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2026/1/28 10:41
 * @version:1.0
 */

@Service
public class AsyncDatabaseMultiThreadService {
    private static final String MYSQL_DIR = "D://trace-mysql//bin//";
    private static final int MYSQL_PORT = 3307;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "123456";

    // 存储任务状态
    private final Map<String, AsyncDatabaseService.TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AsyncDatabaseService.class);

    /**
     * 异步导入（立即返回任务ID）
     */
    /**
     * 异步导入（支持并行导入）
     */
    @Async
    public CompletableFuture<String> loadAsync(String taskId, String sqlFilePath, String databaseName) {
        ExecutorService executor = null;
        try {
            updateTaskStatus(taskId, "running", 0, "初始化导入任务...");
            logger.debug("开始导入: " + sqlFilePath);

            File sourceFile = new File(sqlFilePath);
            List<File> sqlFiles = new ArrayList<>();

            if (sourceFile.isDirectory()) {
                File[] files = sourceFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
                if (files != null && files.length > 0) {
                    sqlFiles.addAll(Arrays.asList(files));
                }
            } else if (sourceFile.isFile() && sourceFile.getName().toLowerCase().endsWith(".sql")) {
                sqlFiles.add(sourceFile);
            }

            if (sqlFiles.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到需要导入的SQL文件: " + sqlFilePath);
                return CompletableFuture.completedFuture("error: No SQL files found");
            }

            int totalFiles = sqlFiles.size();
            updateTaskStatus(taskId, "running", 5, "准备导入 " + totalFiles + " 个文件...");

            // 1. 全局配置优化 (尽管每个连接也会设置，但为了安全起见也可以保留全局设置，视权限而定)
            // 注意：多线程导入时，全局设置可能不生效或被覆盖，重点依靠每个session的设置

            // 2. 创建线程池
            int threadCount = Math.min(totalFiles, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalFiles);

            updateTaskStatus(taskId, "running", 10, "并发导入中 (线程数: " + threadCount + ")...");

            // 3. 提交任务
            for (File sqlFile : sqlFiles) {
                executor.submit(() -> {
                    try {
                        importTable(databaseName, sqlFile.getAbsolutePath());
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(sqlFile.getName() + ": " + e.getMessage());
                        logger.error("导入文件 " + sqlFile.getName() + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();

                        int progress = 10 + (int) ((completed / (double) totalFiles) * 85);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("导入进度: %d/%d (失败: %d)", completed, totalFiles, errorCount.get()));

                        latch.countDown();
                    }
                });
            }

            // 4. 等待完成
            latch.await();

            if (errorCount.get() > 0) {
                String errorMsg = "导入完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                updateTaskStatus(taskId, "warning", 100, errorMsg);
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                updateTaskStatus(taskId, "success", 100, "导入完成！共 " + totalFiles + " 个文件");
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("导入任务异常", e);
            updateTaskStatus(taskId, "error", 0, "导入失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    /**
     * 单个文件导入
     */
    private void importTable(String databaseName, String sqlPath) throws Exception {
        // 注意：Windows下路径分隔符替换
        String normalizedSqlPath = sqlPath.replace("\\", "/");

        // 关键：在同一个Session内执行SET配置和SOURCE
        String sqlCommand = String.format(
                "SET FOREIGN_KEY_CHECKS=0; " +
                        "SET UNIQUE_CHECKS=0; " +
                        "SET SQL_LOG_BIN=0; " + // 如果不需要binlog，可以加快速度
                        "SOURCE %s;",
                normalizedSqlPath);

        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s " +
                        "--default-character-set=utf8mb4 " +
                        "%s -e \"%s\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD,
                databaseName, sqlCommand);

        //command:  e.g D://trace-mysql//bin//mysql.exe -P 3307 -uroot -p123456 --default-character-set=utf8mb4 trace -e "SET FOREIGN_KEY_CHECKS=0; SET UNIQUE_CHECKS=0; SET SQL_LOG_BIN=0; SOURCE E:/trace-dump/backup_task_2/trace158_1.sql;"
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 读取输出，避免阻塞
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR")) {
                    throw new RuntimeException(line);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("mysql import exit code: " + exitCode);
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(String taskId, String status, int progress, String message) {
        AsyncDatabaseService.TaskStatus taskStatus = taskStatusMap.computeIfAbsent(taskId,
                k -> new AsyncDatabaseService.TaskStatus());
        taskStatus.setStatus(status);
        taskStatus.setProgress(progress);
        taskStatus.setMessage(message);
        taskStatus.setUpdateTime(System.currentTimeMillis());

        logger.debug("任务 " + taskId + ": " + status + " - " + progress + "% - " + message);
    }

    /**
     * 获取任务状态
     */
    public AsyncDatabaseService.TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    /**
     * 清除已完成的任务状态（可选，避免内存泄漏）
     */
    public void clearTaskStatus(String taskId) {
        taskStatusMap.remove(taskId);
    }

    /**
     * 获取所有任务状态（可选，用于管理页面）
     */
    public Map<String, AsyncDatabaseService.TaskStatus> getAllTaskStatus() {
        return new HashMap<>(taskStatusMap);
    }

    /**
     * 获取数据库所有表名
     */
    private List<String> getAllTables(String databaseName) throws Exception {
        List<String> tables = new ArrayList<>();
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -D %s -e \"SHOW TABLES;\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, databaseName);

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }
                if (!line.trim().isEmpty()) {
                    tables.add(line.trim());
                }
            }
        }
        process.waitFor();
        return tables;
    }

    /**
     * 执行SQL命令
     */
    private void executeSqlCommand(String sql) throws Exception {
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -e \"%s\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, sql);

        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }

    /**
     * 异步导出（立即返回任务ID）
     */
    /**
     * 异步导出（多线程并行导出每张表）
     */
    @Async
    public CompletableFuture<String> backupAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList) {
        ExecutorService executor = null;
        try {
            updateTaskStatus(taskId, "running", 0, "初始化导出任务...");
            logger.debug("开始导出数据库: " + databaseName + " 到目录: " + savePath);

            // 1. 准备目录
            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                if (!saveDir.mkdirs()) {
                    throw new RuntimeException("无法创建导出目录: " + savePath);
                }
            } else if (!saveDir.isDirectory()) {
                throw new RuntimeException("导出路径必须是目录: " + savePath);
            }

            // 2. 获取表列表
            List<String> tablesToExport;
            if (tableNameList != null && !tableNameList.isEmpty()) {
                tablesToExport = new ArrayList<>(tableNameList);
            } else {
                updateTaskStatus(taskId, "running", 2, "获取表列表...");
                tablesToExport = getAllTables(databaseName);
            }

            if (tablesToExport.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到需要导出的表");
                return CompletableFuture.completedFuture("error: No tables found");
            }

            int totalTables = tablesToExport.size();
            updateTaskStatus(taskId, "running", 5, "准备导出 " + totalTables + " 张表...");

            // 3. 创建线程池
            int threadCount = Math.min(totalTables, Math.max(2, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalTables);

            // 4. 提交任务
            for (String tableName : tablesToExport) {
                executor.submit(() -> {
                    try {
                        exportTable(databaseName, tableName, saveDir.getAbsolutePath());
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(tableName + ": " + e.getMessage());
                        logger.error("导出表 " + tableName + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();

                        // 更新进度 (5% - 95%)
                        int progress = 5 + (int) ((completed / (double) totalTables) * 90);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("导出进度: %d/%d (失败: %d)", completed, totalTables, errorCount.get()));

                        latch.countDown();
                    }
                });
            }

            // 5. 等待完成
            latch.await();

            if (errorCount.get() > 0) {
                String errorMsg = "导出完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                updateTaskStatus(taskId, "warning", 100, errorMsg);
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                updateTaskStatus(taskId, "success", 100, "导出完成！共 " + totalTables + " 张表");
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("导出任务异常", e);
            updateTaskStatus(taskId, "error", 0, "导出失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    /**
     * 导出单张表
     */
    private void exportTable(String databaseName, String tableName, String saveDir) throws Exception {
        String fileName = tableName + ".sql";
        File outputFile = new File(saveDir, fileName);

        StringBuilder command = new StringBuilder();
        command.append(MYSQL_DIR).append("mysqldump.exe ");
        command.append("-P ").append(MYSQL_PORT).append(" ");
        command.append("-u").append(MYSQL_USER).append(" ");
        command.append("-p").append(MYSQL_PASSWORD).append(" ");

        command.append("--single-transaction ");
        command.append("--quick ");
        command.append("--extended-insert ");
        command.append("--max_allowed_packet=1G ");
        command.append("--net_buffer_length=16384 ");
        command.append("--default-character-set=utf8mb4 ");

        command.append(databaseName).append(" ");
        command.append(tableName).append(" ");

        command.append("> \"").append(outputFile.getAbsolutePath()).append("\"");

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // 消耗输出流防止阻塞
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Ignore output
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("mysqldump exit code: " + exitCode);
        }
    }

    // 任务状态类
    public static class TaskStatus {
        private String status; // running, success, error
        private int progress; // 0-100
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
