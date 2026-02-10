package com.yt.server.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ZipUtil;
import com.yt.server.entity.TraceTableRelatedInfo;
import com.yt.server.mapper.TraceTableRelatedInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Enumeration;

@Service
public class AsyncDatabaseMultiThreadService {

    private static final int MYSQL_PORT = 3307;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "123456";

    private static final String ENCRYPTION_KEY = "youtak%_trace";
    private static final String ENCRYPTED_FILE_EXTENSION = ".trace";
    private static final Set<String> GLOBAL_TRACE_TABLES = new HashSet<>(Arrays.asList(
            "table_num_info",
            "trace_field_meta",
            "trace_table_related_info",
            "trace_timestamp_statistics"));

    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(AsyncDatabaseMultiThreadService.class);

    @Autowired
    private TraceTableRelatedInfoMapper traceTableRelatedInfoMapper;

    @Async
    public CompletableFuture<String> loadAsync(String taskId, String sqlFilePath, String databaseName, String binPath,
            boolean autoFinish, Long traceId) {
        ExecutorService executor = null;
        try {
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            // 只导入trace158的表，当前的originalTableName就是trace158
            String originalTableName = traceTableRelatedInfo.getTableName();

            updateTaskStatus(taskId, "running", 0, "初始化导入任务...");
            logger.debug("开始导入: " + sqlFilePath + ", 只导入表前缀: " + originalTableName);

            File sourceFile = new File(sqlFilePath);
            List<File> sqlFiles = new ArrayList<>();

            if (sourceFile.isDirectory()) {
                File[] files = sourceFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
                if (files != null && files.length > 0) {
                    // 过滤只保留符合 originalTableName 前缀的文件
                    for (File file : files) {
                        String fileName = file.getName().replace(".sql", "");
                        // 检查文件名是否以 originalTableName 开头
                        // 例如: trace158.sql, trace158_0.sql, trace158_downsampling_xxx.sql 都会被保留
                        // 而 trace1.sql, trace27_3.sql 会被过滤掉
                        if (fileName.equals(originalTableName) ||
                                fileName.startsWith(originalTableName + "_") ||
                                GLOBAL_TRACE_TABLES.contains(fileName)) {
                            sqlFiles.add(file);
                        }
                    }
                    logger.info("总文件数: {}, 过滤后匹配 '{}' 前缀的文件数: {}(包含4个固定文件的导入)",
                            files.length, originalTableName, sqlFiles.size());
                }
            } else if (sourceFile.isFile() && sourceFile.getName().toLowerCase().endsWith(".sql")) {
                // 单文件模式也需要检查前缀
                String fileName = sourceFile.getName().replace(".sql", "");
                if (fileName.equals(originalTableName) ||
                        fileName.startsWith(originalTableName + "_") ||
                        GLOBAL_TRACE_TABLES.contains(fileName)) {
                    sqlFiles.add(sourceFile);
                } else {
                    updateTaskStatus(taskId, "error", 0,
                            "文件 " + sourceFile.getName() + " 不匹配表前缀 " + originalTableName);
                    return CompletableFuture.completedFuture("error: File prefix mismatch");
                }
            }

            if (sqlFiles.isEmpty()) {
                updateTaskStatus(taskId, "error", 0,
                        "没有找到匹配表前缀 '" + originalTableName + "' 的SQL文件: " + sqlFilePath);
                return CompletableFuture.completedFuture("error: No matching SQL files found");
            }

            int totalFiles = sqlFiles.size();
            updateTaskStatus(taskId, "running", 5,
                    "准备导入 " + totalFiles + " 个匹配 '" + originalTableName + "' 的文件...");

            int threadCount = Math.min(totalFiles, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalFiles);

            updateTaskStatus(taskId, "running", 10, "并发导入中 (线程数: " + threadCount + ")...");

            for (File sqlFile : sqlFiles) {
                executor.submit(() -> {
                    try {
                        logger.info("正在处理表: {}", sqlFile.getName().replace(".sql", ""));
                        importTable(databaseName, sqlFile.getAbsolutePath(), binPath);
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

            latch.await();

            if (errorCount.get() > 0) {
                String errorMsg = "导入完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                if (autoFinish) {
                    updateTaskStatus(taskId, "warning", 100, errorMsg);
                }
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                if (autoFinish) {
                    updateTaskStatus(taskId, "success", 100,
                            "导入完成！共 " + totalFiles + " 个 '" + originalTableName + "' 相关表");
                }
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

    private void importTable(String databaseName, String sqlPath, String binPath) throws Exception {
        String normalizedSqlPath = sqlPath.replace("\\", "/");

        String sqlCommand = String.format(
                "SET FOREIGN_KEY_CHECKS=0; " +
                        "SET UNIQUE_CHECKS=0; " +
                        "SET SQL_LOG_BIN=0; " +
                        "SOURCE %s;",
                normalizedSqlPath);

        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s " +
                        "--default-character-set=utf8mb4 " +
                        "%s -e \"%s\"",
                binPath, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD,
                databaseName, sqlCommand);

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

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

    @Async
    public CompletableFuture<String> loadEncryptedAsync(String taskId, String encryptedFilePath,
            String databaseName, String binPath, boolean autoFinish, Long traceId) {
        ExecutorService executor = null;
        File tempDir = null;
        try {
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            // 只导入trace158的表，当前的originalTableName就是trace158
            String originalTableName = traceTableRelatedInfo.getTableName();

            updateTaskStatus(taskId, "running", 0, "初始化加密导入任务...");
            logger.debug("开始解密并导入: " + encryptedFilePath + ", 只导入表前缀: " + originalTableName);

            File sourceFile = new File(encryptedFilePath);
            List<File> encryptedFiles = new ArrayList<>();

            if (sourceFile.isDirectory()) {
                File[] files = sourceFile
                        .listFiles((dir, name) -> name.toLowerCase().endsWith(ENCRYPTED_FILE_EXTENSION));
                if (files != null && files.length > 0) {
                    // 过滤只保留符合 originalTableName 前缀的文件
                    for (File file : files) {
                        String fileName = file.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                        // 检查文件名是否以 originalTableName 开头
                        // 例如: trace158.trace, trace158_0.trace, trace158_downsampling_xxx.trace 都会被保留
                        // 而 trace1.trace, trace27_3.trace 会被过滤掉
                        if (fileName.equals(originalTableName) ||
                                fileName.startsWith(originalTableName + "_") ||
                                GLOBAL_TRACE_TABLES.contains(fileName)) {
                            encryptedFiles.add(file);
                        }
                    }
                    logger.info("总文件数: {}, 过滤后匹配 '{}' 前缀的文件数: {}(包含4个固定文件的导入)",
                            files.length, originalTableName, encryptedFiles.size());
                }
            } else if (sourceFile.isFile() && sourceFile.getName().toLowerCase().endsWith(ENCRYPTED_FILE_EXTENSION)) {
                // 单文件模式也需要检查前缀
                String fileName = sourceFile.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                if (fileName.equals(originalTableName) ||
                        fileName.startsWith(originalTableName + "_") ||
                        GLOBAL_TRACE_TABLES.contains(fileName)) {
                    encryptedFiles.add(sourceFile);
                } else {
                    updateTaskStatus(taskId, "error", 0,
                            "文件 " + sourceFile.getName() + " 不匹配表前缀 " + originalTableName);
                    return CompletableFuture.completedFuture("error: File prefix mismatch");
                }
            }
            logger.info("encryptedFiles={}", encryptedFiles);

            if (encryptedFiles.isEmpty()) {
                updateTaskStatus(taskId, "error", 0,
                        "没有找到匹配表前缀 '" + originalTableName + "' 的加密文件: " + encryptedFilePath);
                return CompletableFuture.completedFuture("error: No matching encrypted files found");
            }

            int totalFiles = encryptedFiles.size();
            updateTaskStatus(taskId, "running", 5,
                    "准备解密并导入 " + totalFiles + " 个匹配 '" + originalTableName + "' 的文件...");

            tempDir = new File(System.getProperty("java.io.tmpdir"), "mysql_import_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                throw new RuntimeException("无法创建临时目录: " + tempDir.getAbsolutePath());
            }
            final File finalTempDir = tempDir;

            int threadCount = Math.min(totalFiles, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalFiles);
            List<File> tempSqlFiles = Collections.synchronizedList(new ArrayList<>());

            updateTaskStatus(taskId, "running", 10, "并发解密导入中 (线程数: " + threadCount + ")...");

            for (File encryptedFile : encryptedFiles) {
                executor.submit(() -> {
                    File tempSqlFile = null;
                    try {
                        String tableName = encryptedFile.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                        tempSqlFile = new File(finalTempDir, tableName + ".sql");
                        tempSqlFiles.add(tempSqlFile);

                        logger.debug("正在处理表: {}", tableName);
                        decryptFile(encryptedFile.getAbsolutePath(), tempSqlFile.getAbsolutePath());
                        importTable(databaseName, tempSqlFile.getAbsolutePath(), binPath);

                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(encryptedFile.getName() + ": " + e.getMessage());
                        logger.error("解密导入文件 " + encryptedFile.getName() + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();
                        int progress = 10 + (int) ((completed / (double) totalFiles) * 85);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("解密导入进度: %d/%d (失败: %d)", completed, totalFiles, errorCount.get()));
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // 清理临时文件
            for (File tempFile : tempSqlFiles) {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
            if (tempDir.exists()) {
                tempDir.delete();
            }

            if (errorCount.get() > 0) {
                String errorMsg = "解密导入完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                if (autoFinish) {
                    updateTaskStatus(taskId, "warning", 100, errorMsg);
                }
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                if (autoFinish) {
                    updateTaskStatus(taskId, "success", 100,
                            "解密导入完成！共 " + totalFiles + " 个 '" + originalTableName + "' 相关表");
                }
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("解密导入任务异常", e);
            updateTaskStatus(taskId, "error", 0, "解密导入失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    @Async
    public CompletableFuture<String> backupAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList, String binPath) {
        ExecutorService executor = null;
        try {
            updateTaskStatus(taskId, "running", 0, "初始化导出任务...");
            logger.debug("开始导出数据库: " + databaseName + " 到目录: " + savePath);

            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                if (!saveDir.mkdirs()) {
                    throw new RuntimeException("无法创建导出目录: " + savePath);
                }
            } else if (!saveDir.isDirectory()) {
                throw new RuntimeException("导出路径必须是目录: " + savePath);
            }

            List<String> tablesToExport;
            if (tableNameList != null && !tableNameList.isEmpty()) {
                tablesToExport = new ArrayList<>(tableNameList);
            } else {
                updateTaskStatus(taskId, "running", 2, "获取表列表...");
                tablesToExport = getAllTables(databaseName, binPath);
            }

            if (tablesToExport.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到需要导出的表");
                return CompletableFuture.completedFuture("error: No tables found");
            }

            int totalTables = tablesToExport.size();
            updateTaskStatus(taskId, "running", 5, "准备导出 " + totalTables + " 张表...");

            int threadCount = Math.min(totalTables, Math.max(2, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalTables);

            for (String tableName : tablesToExport) {
                executor.submit(() -> {
                    try {
                        exportTable(databaseName, tableName, saveDir.getAbsolutePath(), binPath);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(tableName + ": " + e.getMessage());
                        logger.error("导出表 " + tableName + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();
                        int progress = 5 + (int) ((completed / (double) totalTables) * 90);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("导出进度: %d/%d (失败: %d)", completed, totalTables, errorCount.get()));
                        latch.countDown();
                    }
                });
            }

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

    @Async
    public CompletableFuture<String> backupEncryptedAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList, String binPath) {
        ExecutorService executor = null;
        try {
            updateTaskStatus(taskId, "running", 0, "初始化加密导出任务...");
            logger.debug("开始加密导出数据库: " + databaseName + " 到目录: " + savePath);

            File saveDir = new File(savePath);
            if (!saveDir.exists()) {
                if (!saveDir.mkdirs()) {
                    throw new RuntimeException("无法创建导出目录: " + savePath);
                }
            }

            List<String> tablesToExport;
            if (tableNameList != null && !tableNameList.isEmpty()) {
                tablesToExport = new ArrayList<>(tableNameList);
            } else {
                updateTaskStatus(taskId, "running", 2, "获取表列表...");
                tablesToExport = getAllTables(databaseName, binPath);
            }

            if (tablesToExport.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到需要导出的表");
                return CompletableFuture.completedFuture("error: No tables found");
            }

            int totalTables = tablesToExport.size();
            updateTaskStatus(taskId, "running", 5, "准备加密导出 " + totalTables + " 张表...");

            int threadCount = Math.min(totalTables, Math.max(2, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalTables);

            for (String tableName : tablesToExport) {
                executor.submit(() -> {
                    try {
                        exportTableEncrypted(databaseName, tableName, saveDir.getAbsolutePath(), binPath);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(tableName + ": " + e.getMessage());
                        logger.error("加密导出表 " + tableName + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();
                        int progress = 5 + (int) ((completed / (double) totalTables) * 90);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("加密导出进度: %d/%d (失败: %d)", completed, totalTables, errorCount.get()));
                        latch.countDown();
                    }
                });
            }

            latch.await();

            if (errorCount.get() > 0) {
                String errorMsg = "加密导出完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                updateTaskStatus(taskId, "warning", 100, errorMsg);
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                updateTaskStatus(taskId, "success", 100, "加密导出完成！共 " + totalTables + " 张表");
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("加密导出任务异常", e);
            updateTaskStatus(taskId, "error", 0, "加密导出失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    private void exportTable(String databaseName, String tableName, String saveDir, String binPath) throws Exception {
        String fileName = tableName + ".sql";
        File outputFile = new File(saveDir, fileName);

        StringBuilder command = new StringBuilder();
        command.append(binPath).append("mysqldump.exe ");
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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("mysqldump exit code: " + exitCode);
        }
    }

    private void exportTableEncrypted(String databaseName, String tableName, String saveDir, String binPath)
            throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"),
                "mysql_export_" + System.currentTimeMillis() + "_" + tableName);
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("无法创建临时目录");
        }

        try {
            String tempSqlPath = new File(tempDir, tableName + ".sql").getAbsolutePath();

            StringBuilder command = new StringBuilder();
            command.append(binPath).append("mysqldump.exe ");
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
            command.append("> \"").append(tempSqlPath).append("\"");

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("mysqldump exit code: " + exitCode);
            }

            String encryptedPath = new File(saveDir, tableName + ENCRYPTED_FILE_EXTENSION).getAbsolutePath();
            encryptFile(tempSqlPath, encryptedPath);

        } finally {
            File[] tempFiles = tempDir.listFiles();
            if (tempFiles != null) {
                for (File f : tempFiles) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }

    private void encryptFile(String inputFile, String outputFile) throws Exception {
        byte[] keyBytes = get32ByteKey(ENCRYPTION_KEY);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "ChaCha20");
        ChaCha20ParameterSpec chaChaSpec = new ChaCha20ParameterSpec(new byte[12], 1);

        Cipher cipher = Cipher.getInstance("ChaCha20");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, chaChaSpec);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile), 1024 * 1024);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile), 1024 * 1024);
                CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {

            byte[] buffer = new byte[1024 * 1024]; // 1MB缓冲区
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
            }
        }
    }

    private void decryptFile(String inputFile, String outputFile) throws Exception {
        byte[] keyBytes = get32ByteKey(ENCRYPTION_KEY);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "ChaCha20");
        ChaCha20ParameterSpec chaChaSpec = new ChaCha20ParameterSpec(new byte[12], 1);

        Cipher cipher = Cipher.getInstance("ChaCha20");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, chaChaSpec);

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile), 1024 * 1024);
                CipherInputStream cis = new CipherInputStream(bis, cipher);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile), 1024 * 1024)) {

            byte[] buffer = new byte[1024 * 1024]; // 1MB缓冲区
            int bytesRead;
            while ((bytesRead = cis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
        }
    }

    private byte[] get32ByteKey(String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
        return keyBytes;
    }

    public void updateTaskStatus(String taskId, String status, int progress, String message) {
        TaskStatus taskStatus = taskStatusMap.computeIfAbsent(taskId, k -> new TaskStatus());
        taskStatus.setStatus(status);
        taskStatus.setProgress(progress);
        taskStatus.setMessage(message);
        taskStatus.setUpdateTime(System.currentTimeMillis());
        logger.debug("任务 " + taskId + ": " + status + " - " + progress + "% - " + message);
    }

    public void updateTaskResult(String taskId, long startTime, long endTime) {
        TaskStatus taskStatus = taskStatusMap.get(taskId);
        if (taskStatus != null) {
            taskStatus.setStartTime(startTime);
            taskStatus.setEndTime(endTime);
        }
    }

    public TaskStatus getTaskStatus(String taskId) {
        return taskStatusMap.get(taskId);
    }

    public void clearTaskStatus(String taskId) {
        taskStatusMap.remove(taskId);
    }

    public Map<String, TaskStatus> getAllTaskStatus() {
        return new HashMap<>(taskStatusMap);
    }

    private List<String> getAllTables(String databaseName, String binPath) throws Exception {
        List<String> tables = new ArrayList<>();
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -D %s -e \"SHOW TABLES;\"",
                binPath, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, databaseName);

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
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

    private void executeSqlCommand(String sql, String binPath) throws Exception {
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -e \"%s\"",
                binPath, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, sql);
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor();
    }

    @Async
    public CompletableFuture<String> loadEncryptedUnzipAsync(String taskId, String encryptedFilePath,
            String databaseName, String binPath, boolean autoFinish, Long traceId) {
        ExecutorService executor = null;
        File tempDir = null;
        File tempExtractDir = null;
        try {
            TraceTableRelatedInfo traceTableRelatedInfo = traceTableRelatedInfoMapper.selectByPrimaryKey(traceId);
            String originalTableName = traceTableRelatedInfo.getTableName();

            updateTaskStatus(taskId, "running", 0, "初始化加密导入任务...");
            logger.debug("开始解密并导入: " + encryptedFilePath + ", 只导入表前缀: " + originalTableName);

            File sourceFile = new File(encryptedFilePath);
            List<File> encryptedFiles = new ArrayList<>();

            // 1. 处理Zip解压
            if (sourceFile.isFile() && sourceFile.getName().toLowerCase().endsWith(".zip")) {
                // 校验Zip包内容格式
                try (ZipFile zipFile = new ZipFile(sourceFile)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.isDirectory()) {
                            if (!entry.getName().toLowerCase().endsWith(ENCRYPTED_FILE_EXTENSION)) {
                                updateTaskStatus(taskId, "error", 0,
                                        "非法的数据格式:压缩包内包含非.trace文件(" + entry.getName() + ")");
                                return CompletableFuture.completedFuture("error: Illegal data format");
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("ZIP文件校验失败: " + sourceFile.getName(), e);
                    updateTaskStatus(taskId, "error", 0, "ZIP文件校验失败: " + e.getMessage());
                    return CompletableFuture.completedFuture("error: Zip validation failed");
                }

                updateTaskStatus(taskId, "running", 2, "正在解压文件: " + sourceFile.getName());
                tempExtractDir = new File(System.getProperty("java.io.tmpdir"),
                        "mysql_import_zip_enc_" + System.currentTimeMillis());
                if (!tempExtractDir.mkdirs()) {
                    throw new RuntimeException("无法创建临时解压目录");
                }
                ZipUtil.unzip(sourceFile, tempExtractDir);
                sourceFile = tempExtractDir; // 指向解压后的目录
                logger.debug("ZIP文件已解压至: " + tempExtractDir.getAbsolutePath());
            }

            // 2. 扫描文件 (支持目录递归或单个文件)
            if (sourceFile.isDirectory()) {
                List<File> dirsToScan = new ArrayList<>();
                dirsToScan.add(sourceFile);

                // 检查目录下是否还有zip文件需要解压
                File[] nestedZipFiles = sourceFile.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
                if (nestedZipFiles != null && nestedZipFiles.length > 0) {
                    if (tempExtractDir == null) {
                        tempExtractDir = new File(System.getProperty("java.io.tmpdir"),
                                "mysql_import_zip_enc_" + System.currentTimeMillis());
                        if (!tempExtractDir.mkdirs()) {
                            throw new RuntimeException("无法创建临时解压目录");
                        }
                    }
                    updateTaskStatus(taskId, "running", 2, "正在解压目录下的嵌套ZIP文件...");
                    for (File zip : nestedZipFiles) {
                        try {
                            ZipUtil.unzip(zip, tempExtractDir);
                        } catch (Exception e) {
                            logger.error("解压嵌套文件失败: " + zip.getName(), e);
                        }
                    }
                    dirsToScan.add(tempExtractDir);
                }

                for (File dirToScan : dirsToScan) {
                    File[] files = dirToScan
                            .listFiles((dir, name) -> name.toLowerCase().endsWith(ENCRYPTED_FILE_EXTENSION));
                    if (files != null) {
                        for (File file : files) {
                            String fileName = file.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                            if (fileName.equals(originalTableName) ||
                                    fileName.startsWith(originalTableName + "_") ||
                                    GLOBAL_TRACE_TABLES.contains(fileName)) {
                                encryptedFiles.add(file);
                            }
                        }
                    }
                }
                logger.info("扫描完成, 过滤后匹配 '{}' 前缀的文件数: {}(包含4个固定文件的导入)",
                        originalTableName, encryptedFiles.size());

            } else if (sourceFile.isFile() && sourceFile.getName().toLowerCase().endsWith(ENCRYPTED_FILE_EXTENSION)) {
                String fileName = sourceFile.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                if (fileName.equals(originalTableName) ||
                        fileName.startsWith(originalTableName + "_") ||
                        GLOBAL_TRACE_TABLES.contains(fileName)) {
                    encryptedFiles.add(sourceFile);
                } else {
                    updateTaskStatus(taskId, "error", 0, "文件不匹配表前缀 " + originalTableName);
                    return CompletableFuture.completedFuture("error: File prefix mismatch");
                }
            }

            if (encryptedFiles.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到匹配表前缀 '" + originalTableName + "' 的加密文件");
                return CompletableFuture.completedFuture("error: No matching encrypted files found");
            }

            // 3. 执行解密和导入
            int totalFiles = encryptedFiles.size();
            updateTaskStatus(taskId, "running", 5, "准备解密并导入 " + totalFiles + " 个文件...");

            // 专门用于存放解密后的.sql文件的临时目录
            tempDir = new File(System.getProperty("java.io.tmpdir"),
                    "mysql_import_decrypted_sqls_" + System.currentTimeMillis());
            if (!tempDir.mkdirs()) {
                throw new RuntimeException("无法创建临时解密目录: " + tempDir.getAbsolutePath());
            }
            final File finalTempDir = tempDir;

            int threadCount = Math.min(totalFiles, Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalFiles);
            List<File> tempSqlFiles = Collections.synchronizedList(new ArrayList<>());

            updateTaskStatus(taskId, "running", 10, "并发解密导入中 (线程数: " + threadCount + ")...");

            for (File encryptedFile : encryptedFiles) {
                executor.submit(() -> {
                    File tempSqlFile = null;
                    try {
                        String tableName = encryptedFile.getName().replace(ENCRYPTED_FILE_EXTENSION, "");
                        tempSqlFile = new File(finalTempDir, tableName + ".sql");

                        logger.debug("正在处理表: {}", tableName);
                        decryptFile(encryptedFile.getAbsolutePath(), tempSqlFile.getAbsolutePath());

                        // 立即删除加密源文件，节省空间
                        if (encryptedFile.exists()) {
                            encryptedFile.delete();
                            logger.debug("已删除加密源文件: {}", encryptedFile.getName());
                        }

                        importTable(databaseName, tempSqlFile.getAbsolutePath(), binPath);

                        // 立即删除导入后的SQL文件
                        if (tempSqlFile.exists()) {
                            tempSqlFile.delete();
                            logger.debug("已删除临时SQL文件: {}", tempSqlFile.getName());
                        }

                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(encryptedFile.getName() + ": " + e.getMessage());
                        logger.error("解密导入文件 " + encryptedFile.getName() + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();
                        int progress = 10 + (int) ((completed / (double) totalFiles) * 85);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("解密导入进度: %d/%d (失败: %d)", completed, totalFiles, errorCount.get()));
                        latch.countDown();
                    }
                });
            }

            latch.await();

            // 最终兜底清理临时解密目录
            if (tempDir.exists()) {
                FileUtil.del(tempDir);
            }

            if (errorCount.get() > 0) {
                String errorMsg = "解密导入完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                if (autoFinish)
                    updateTaskStatus(taskId, "warning", 100, errorMsg);
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                if (autoFinish)
                    updateTaskStatus(taskId, "success", 100, "解密导入完成！共 " + totalFiles + " 个文件");
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("解密导入任务异常", e);
            updateTaskStatus(taskId, "error", 0, "解密导入失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null)
                executor.shutdown();
            // 清理Zip解压临时目录
            if (tempExtractDir != null)
                FileUtil.del(tempExtractDir);
        }
    }

    @Async
    public CompletableFuture<String> backupZipEncryptedAsync(String taskId, String savePath,
            String databaseName, Collection<String> tableNameList, String binPath, Long traceId) {
        ExecutorService executor = null;
        File tempExportDir = null;
        try {
            updateTaskStatus(taskId, "running", 0, "初始化加密导出任务...");
            logger.debug("开始加密导出数据库: " + databaseName + " 到: " + savePath);

            // 优化：如果要保存的路径是目录且由于重试等原因已经存在文件，先清空，避免生成多个zip包
            File saveTargetToCheck = new File(savePath);
            if (saveTargetToCheck.exists() && saveTargetToCheck.isDirectory()) {
                FileUtil.clean(saveTargetToCheck);
                logger.info("保存目录存在其他内容,已清空目标目录: " + savePath);
            }

            // 创建临时导出目录
            tempExportDir = new File(System.getProperty("java.io.tmpdir"),
                    "mysql_export_backup_enc_" + System.currentTimeMillis());
            if (!tempExportDir.mkdirs()) {
                throw new RuntimeException("无法创建临时导出目录: " + tempExportDir.getAbsolutePath());
            }
            final File finalTempExportDir = tempExportDir;

            List<String> tablesToExport;
            if (tableNameList != null && !tableNameList.isEmpty()) {
                tablesToExport = new ArrayList<>(tableNameList);
            } else {
                updateTaskStatus(taskId, "running", 2, "获取表列表...");
                tablesToExport = getAllTables(databaseName, binPath);
            }

            if (tablesToExport.isEmpty()) {
                updateTaskStatus(taskId, "error", 0, "没有找到需要导出的表");
                return CompletableFuture.completedFuture("error: No tables found");
            }

            int totalTables = tablesToExport.size();
            updateTaskStatus(taskId, "running", 5, "准备加密导出 " + totalTables + " 张表...");

            int threadCount = Math.min(totalTables, Math.max(2, Runtime.getRuntime().availableProcessors()));
            executor = Executors.newFixedThreadPool(threadCount);

            AtomicInteger completedCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<String> errors = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(totalTables);

            for (String tableName : tablesToExport) {
                executor.submit(() -> {
                    try {
                        exportTableEncrypted(databaseName, tableName, finalTempExportDir.getAbsolutePath(), binPath);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add(tableName + ": " + e.getMessage());
                        logger.error("加密导出表 " + tableName + " 失败", e);
                    } finally {
                        int completed = completedCount.incrementAndGet();
                        int progress = 5 + (int) ((completed / (double) totalTables) * 90);
                        updateTaskStatus(taskId, "running", progress,
                                String.format("加密导出进度: %d/%d (失败: %d)", completed, totalTables, errorCount.get()));
                        latch.countDown();
                    }
                });
            }

            latch.await();

            if (errorCount.get() > 0) {
                String errorMsg = "加密导出完成但有 " + errorCount.get() + " 个错误: " + String.join("; ", errors);
                updateTaskStatus(taskId, "warning", 100, errorMsg);
                return CompletableFuture.completedFuture("warning: " + errorMsg);
            } else {
                // 压缩打包
                updateTaskStatus(taskId, "running", 95, "正在压缩打包...");
                File saveTarget = new File(savePath);
                File zipFile;

                if (saveTarget.isDirectory()) {
                    String zipName = String.format("backup_%s.zip", traceId);
                    zipFile = new File(saveTarget, zipName);
                } else if (savePath.toLowerCase().endsWith(".zip")) {
                    zipFile = saveTarget;
                    File parent = zipFile.getParentFile();
                    if (parent != null && !parent.exists())
                        parent.mkdirs();
                } else {
                    if (!saveTarget.exists())
                        saveTarget.mkdirs();
                    String zipName = String.format("backup_%s.zip", traceId);
                    zipFile = new File(saveTarget, zipName);
                }

                ZipUtil.zip(tempExportDir.getAbsolutePath(), zipFile.getAbsolutePath());

                updateTaskStatus(taskId, "success", 100, "加密导出完成！已保存为: " + zipFile.getName());
                return CompletableFuture.completedFuture("success");
            }

        } catch (Exception e) {
            logger.error("加密导出任务异常", e);
            updateTaskStatus(taskId, "error", 0, "加密导出失败: " + e.getMessage());
            return CompletableFuture.completedFuture("error: " + e.getMessage());
        } finally {
            if (executor != null)
                executor.shutdown();
            if (tempExportDir != null)
                FileUtil.del(tempExportDir);
        }
    }

    public static class TaskStatus {
        private String status;
        private int progress;
        private String message;
        private long updateTime;
        private long startTime;
        private Long EndTime;

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

        public Long getEndTime() {
            return EndTime;
        }

        public void setEndTime(Long endTime) {
            EndTime = endTime;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }
    }
}
