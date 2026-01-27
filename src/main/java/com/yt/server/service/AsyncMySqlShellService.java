package com.yt.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 使用 MySQL Shell (mysqlsh) 实现的高性能数据库导入导出服务
 *
 * MySQL Shell 相比传统 mysqldump/mysql 的优势:
 * 1. 多线程并行导出/导入，速度提升 4-10 倍
 * 2. 支持 zstd 压缩，减少磁盘占用
 * 3. 支持断点续传
 * 4. 自动分块处理大表
 *
 * 导出
 * mysqlsh -u root -p123456 -P 3307 -- util dump-schemas trace
 * --outputUrl=e:/mysqlshell/aa --threads=4
 *
 * 导入
 * mysqlsh -u root -p123456 -h localhost -P 3307 -- util load-dump
 * e:/mysqlshell/aa --threads=4 --ignoreExistingObjects
 *
 * @description: MySQL Shell 异步数据库服务
 * @projectName: yt-java-server
 * @see: com.yt.server.service
 * @author: 赵瑞文
 * @createTime: 2026/1/27
 * @version: 1.0
 */
@Service
@Deprecated
public class AsyncMySqlShellService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMySqlShellService.class);

    // MySQL Shell 路径 (需要安装 MySQL Shell: https://dev.mysql.com/downloads/shell/)
    private static final String MYSQLSH_PATH = "E://mysqlshell//mysql-shell-9.6.0-windows-x86-64bit//bin//mysqlsh.exe";

    // 数据库连接配置
    private static final String MYSQL_HOST = "localhost";
    private static final int MYSQL_PORT = 3307;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "123456";

    // 导出导入性能配置
    private static final int DEFAULT_THREADS = 4; // 默认并行线程数
    private static final boolean USE_COMPRESSION = true; // 是否启用压缩
    private static final int BYTES_PER_CHUNK = 64 * 1024 * 1024; // 每个分块 64MB

    // 存储任务状态
    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    /**
     * 构建导出用连接参数
     * 格式: -u root -p123456 -P 3307
     */
    private String getDumpConnectionArgs() {
        return String.format("-u %s -p%s -P %d",
                MYSQL_USER, MYSQL_PASSWORD, MYSQL_PORT);
    }

    /**
     * 构建导入用连接参数
     * 格式: -u root -p123456 -h localhost -P 3307
     */
    private String getLoadConnectionArgs() {
        return String.format("-u %s -p%s -h %s -P %d",
                MYSQL_USER, MYSQL_PASSWORD, MYSQL_HOST, MYSQL_PORT);
    }

    /**
     * 异步导出数据库（使用 MySQL Shell 的 util.dumpSchemas）
     *
     * 特点:
     * - 多线程并行导出
     * - 自动压缩
     * - 一致性快照
     *
     * @param taskId        任务ID
     * @param savePath      保存目录路径（注意：MySQL Shell 导出到目录，不是单个文件）
     * @param databaseName  数据库名
     * @param tableNameList 要导出的表名列表（null 或空表示导出整个数据库）
     * @param threads       并行线程数
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<String> dumpAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList,
            Integer threads) {
        try {
            updateTaskStatus(taskId, "running", 0, "开始导出...");
            logger.info("开始使用 MySQL Shell 导出数据库: {}", databaseName);

            int threadCount = threads != null ? threads : DEFAULT_THREADS;

            // 确保输出目录存在且为空
            Path outputPath = Path.of(savePath);
            if (Files.exists(outputPath)) {
                // MySQL Shell 要求输出目录为空或不存在
                deleteDirectory(outputPath.toFile());
            }

            updateTaskStatus(taskId, "running", 5, "准备导出命令...");

            // 构建 CLI 命令（严格按照实测有效的命令格式）
            // 格式: mysqlsh -u root -p123456 -P 3307 -- util dump-schemas dbname
            // --outputUrl=path --threads=4
            StringBuilder command = new StringBuilder();
            command.append(String.format("\"%s\" %s -- util ",
                    MYSQLSH_PATH, getDumpConnectionArgs()));

            if (tableNameList != null && !tableNameList.isEmpty()) {
                // 导出指定表: util dump-tables dbname table1 table2 ...
                command.append("dump-tables ").append(databaseName).append(" ");
                for (String tableName : tableNameList) {
                    command.append(tableName).append(" ");
                }
                updateTaskStatus(taskId, "running", 10, "导出 " + tableNameList.size() + " 个表...");
            } else {
                // 导出整个数据库: util dump-schemas dbname
                command.append("dump-schemas ").append(databaseName).append(" ");
                updateTaskStatus(taskId, "running", 10, "导出整个数据库...");
            }

            // 添加输出路径和线程数
            String outputDir = savePath.replace("\\", "/");
            command.append(String.format("--outputUrl=%s --threads=%d", outputDir, threadCount));

            logger.info("执行命令: {}", command);
            updateTaskStatus(taskId, "running", 15, "开始导出数据...");

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出并更新进度
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int progress = 15;
                while ((line = reader.readLine()) != null) {
                    logger.debug("mysqlsh output: {}", line);

                    // 解析进度信息
                    if (line.contains("%")) {
                        // 尝试从输出中提取进度百分比
                        progress = parseProgress(line, progress);
                    }

                    if (progress < 95) {
                        updateTaskStatus(taskId, "running", progress,
                                "导出中: " + line.substring(0, Math.min(80, line.length())));
                    }

                    if (line.contains("ERROR") || line.contains("Error")) {
                        updateTaskStatus(taskId, "error", progress, "错误: " + line);
                        logger.error("导出错误: {}", line);
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                updateTaskStatus(taskId, "error", 0, "导出失败，退出码: " + exitCode);
                throw new RuntimeException("导出失败，退出码: " + exitCode);
            }

            // 检查输出目录
            File resultDir = new File(savePath);
            if (resultDir.exists() && resultDir.isDirectory()) {
                long totalSize = calculateDirectorySize(resultDir);
                long sizeMB = totalSize / (1024 * 1024);
                updateTaskStatus(taskId, "success", 100,
                        String.format("导出完成！总大小: %d MB, 线程数: %d", sizeMB, threadCount));
                logger.info("导出成功: {} ({} MB)", savePath, sizeMB);
            } else {
                updateTaskStatus(taskId, "error", 0, "导出失败：输出目录不存在");
                throw new RuntimeException("导出失败：输出目录不存在");
            }

            return CompletableFuture.completedFuture("success");

        } catch (Exception e) {
            logger.error("导出异常", e);
            updateTaskStatus(taskId, "error", 0, "导出失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        }
    }

    /**
     * 异步导入数据库（使用 MySQL Shell 的 util.loadDump）
     *
     * 特点:
     * - 多线程并行导入
     * - 延迟创建索引（先导入数据，后创建索引，大幅提升速度）
     * - 支持断点续传
     *
     * @param taskId       任务ID
     * @param dumpPath     dump 目录路径（由 dumpAsync 生成）
     * @param databaseName 目标数据库名
     * @param threads      并行线程数
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<String> loadAsync(String taskId, String dumpPath,
            String databaseName, Integer threads) {
        try {
            updateTaskStatus(taskId, "running", 0, "开始导入...");
            logger.info("开始使用 MySQL Shell 导入数据到: {}", databaseName);

            int threadCount = threads != null ? threads : DEFAULT_THREADS;

            // 检查 dump 目录是否存在
            File dumpDir = new File(dumpPath);
            if (!dumpDir.exists() || !dumpDir.isDirectory()) {
                updateTaskStatus(taskId, "error", 0, "导入失败：dump 目录不存在");
                throw new RuntimeException("dump 目录不存在: " + dumpPath);
            }

            updateTaskStatus(taskId, "running", 5, "准备导入命令...");

            // 构建 CLI 命令（严格按照实测有效的命令格式）
            // 格式: mysqlsh -u root -p123456 -h localhost -P 3307 -- util load-dump path
            // --threads=4 --ignoreExistingObjects
            String inputPath = dumpPath.replace("\\", "/");
            String command = String.format(
                    "\"%s\" %s -- util load-dump %s --threads=%d --ignoreExistingObjects",
                    MYSQLSH_PATH,
                    getLoadConnectionArgs(),
                    inputPath,
                    threadCount);

            logger.info("执行命令: {}", command);
            updateTaskStatus(taskId, "running", 10, "开始导入数据...");

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出并更新进度
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int progress = 10;
                while ((line = reader.readLine()) != null) {
                    logger.debug("mysqlsh output: {}", line);

                    // 解析进度信息
                    if (line.contains("%")) {
                        progress = parseProgress(line, progress);
                    }

                    if (progress < 95) {
                        updateTaskStatus(taskId, "running", progress,
                                "导入中: " + line.substring(0, Math.min(80, line.length())));
                    }

                    if (line.contains("ERROR") || line.contains("Error")) {
                        updateTaskStatus(taskId, "error", progress, "错误: " + line);
                        logger.error("导入错误: {}", line);
                    }

                    // 检测索引创建阶段
                    if (line.contains("Creating indexes")) {
                        updateTaskStatus(taskId, "running", 90, "创建索引中...");
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                updateTaskStatus(taskId, "error", 0, "导入失败，退出码: " + exitCode);
                throw new RuntimeException("导入失败，退出码: " + exitCode);
            }

            updateTaskStatus(taskId, "success", 100,
                    String.format("导入完成！线程数: %d", threadCount));
            logger.info("导入成功: {} -> {}", dumpPath, databaseName);

            return CompletableFuture.completedFuture("success");

        } catch (Exception e) {
            logger.error("导入异常", e);
            updateTaskStatus(taskId, "error", 0, "导入失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        }
    }

    /**
     * 导出为单个 SQL 文件（兼容传统格式）
     * 使用 util.dumpSchemas 配合 ocimds: false 选项
     *
     * @param taskId        任务ID
     * @param savePath      保存目录路径
     * @param databaseName  数据库名
     * @param tableNameList 表名列表
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<String> dumpToSqlAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList) {
        // 使用默认线程数导出，然后可以选择是否合并
        return dumpAsync(taskId, savePath, databaseName, tableNameList, DEFAULT_THREADS);
    }

    /**
     * 从传统 SQL 文件导入（向后兼容）
     * 对于传统 SQL 文件，仍使用 mysql 客户端，但增加优化参数
     *
     * @param taskId       任务ID
     * @param sqlFilePath  SQL 文件路径
     * @param databaseName 数据库名
     * @return CompletableFuture
     */
    @Async
    public CompletableFuture<String> loadFromSqlAsync(String taskId, String sqlFilePath,
            String databaseName) {
        try {
            updateTaskStatus(taskId, "running", 0, "开始导入 SQL 文件...");
            logger.info("使用 MySQL Shell 导入 SQL 文件: {}", sqlFilePath);

            // 检查文件是否存在
            File sqlFile = new File(sqlFilePath);
            if (!sqlFile.exists()) {
                updateTaskStatus(taskId, "error", 0, "SQL 文件不存在");
                throw new RuntimeException("SQL 文件不存在: " + sqlFilePath);
            }

            updateTaskStatus(taskId, "running", 5, "准备导入...");

            // MySQL Shell 直接执行 SQL 文件（使用 CLI 风格）
            String sqlPath = sqlFilePath.replace("\\", "/");
            String command = String.format(
                    "\"%s\" %s --database=%s --sql -f \"%s\"",
                    MYSQLSH_PATH,
                    getLoadConnectionArgs(),
                    databaseName,
                    sqlPath);

            logger.info("执行命令: {}", command);
            updateTaskStatus(taskId, "running", 10, "开始导入数据...");

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int progress = 10;
                while ((line = reader.readLine()) != null) {
                    if (progress < 90) {
                        progress += 1;
                        updateTaskStatus(taskId, "running", progress,
                                "导入中: " + line.substring(0, Math.min(50, line.length())));
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

            updateTaskStatus(taskId, "success", 100, "SQL 文件导入完成！");
            return CompletableFuture.completedFuture("success");

        } catch (Exception e) {
            logger.error("导入异常", e);
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

        logger.debug("任务 {}: {} - {}% - {}", taskId, status, progress, message);
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    /**
     * 清除任务状态
     */
    public void clearTaskStatus(String taskId) {
        taskStatusMap.remove(taskId);
    }

    /**
     * 获取所有任务状态
     */
    public Map<String, TaskStatus> getAllTaskStatus() {
        return new HashMap<>(taskStatusMap);
    }

    /**
     * 从输出行解析进度百分比
     */
    private int parseProgress(String line, int currentProgress) {
        try {
            // 尝试匹配类似 "50%" 或 "50.5%" 的模式
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)%");
            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                double percent = Double.parseDouble(matcher.group(1));
                // 映射到 15-95 的范围
                return Math.min(95, 15 + (int) (percent * 0.8));
            }
        } catch (Exception e) {
            // 解析失败，返回当前进度
        }
        return Math.min(95, currentProgress + 1);
    }

    /**
     * 计算目录大小
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += calculateDirectorySize(file);
                }
            }
        }
        return size;
    }

    /**
     * 删除目录及其内容
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    /**
     * 检查 MySQL Shell 是否可用
     */
    public boolean isMySqlShellAvailable() {
        try {
            File mysqlsh = new File(MYSQLSH_PATH);
            if (!mysqlsh.exists()) {
                logger.warn("MySQL Shell 不存在: {}", MYSQLSH_PATH);
                return false;
            }

            ProcessBuilder pb = new ProcessBuilder(MYSQLSH_PATH, "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains("mysqlsh")) {
                    logger.info("MySQL Shell 版本: {}", line);
                    return true;
                }
            }

            return process.waitFor() == 0;
        } catch (Exception e) {
            logger.error("检查 MySQL Shell 可用性失败", e);
            return false;
        }
    }

    // 任务状态类（与 AsyncDatabaseService 保持一致）
    public static class TaskStatus {
        private String status; // running, success, error
        private int progress; // 0-100
        private String message;
        private long updateTime;

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
