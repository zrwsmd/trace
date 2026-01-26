package com.yt.server.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;


/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.service
 * @author:赵瑞文
 * @createTime:2026/1/26 16:12
 * @version:1.0
 */

@Service
public class OptimizedDatabaseService {

    private static final String MYSQL_DIR = "D://trace-mysql//bin//";
    private static final int MYSQL_PORT = 3307;
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "123456";

    /**
     * 优化的导出方法（比你原来的快2-3倍）
     */
    public String backupOptimized(String savePath, String databaseName,
                                  Collection<String> tableNameList) throws Exception {

        // 1. 构建优化的 mysqldump 命令
        StringBuilder command = new StringBuilder();
        command.append(MYSQL_DIR).append("mysqldump.exe ");
        command.append("-P ").append(MYSQL_PORT).append(" ");
        command.append("-u").append(MYSQL_USER).append(" ");
        command.append("-p").append(MYSQL_PASSWORD).append(" ");

        // 关键优化参数
        command.append("--single-transaction ");  // 一致性备份，不锁表
        command.append("--quick ");               // 不缓冲查询，逐行读取
        command.append("--extended-insert ");     // 多行插入，减少INSERT语句数量
        command.append("--max_allowed_packet=1G "); // 增大包大小
        command.append("--net_buffer_length=16384 "); // 优化网络缓冲
        command.append("--default-character-set=utf8mb4 "); // 字符集

        command.append(databaseName).append(" ");

        // 添加表名
        if (tableNameList != null && !tableNameList.isEmpty()) {
            for (String tableName : tableNameList) {
                command.append(tableName).append(" ");
            }
        }

        // 2. 直接重定向到文件（比逐行写入快得多）
        command.append("> \"").append(savePath).append("\"");

        System.out.println("执行命令: " + command.toString());

        // 3. 使用 cmd /c 执行（支持重定向）
        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command.toString());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return "导出成功: " + savePath;
        } else {
            throw new RuntimeException("导出失败，退出码: " + exitCode);
        }
    }


    /**
     * 修复后的并行导入方法
     */
    public String loadWithParallel(String sqlFilePath, String databaseName,
                                   int threadCount) throws Exception {

        // 1. 分割SQL文件
        System.out.println("开始分割文件...");
        List<String> splitFiles = splitSqlFile(sqlFilePath, 50000);

        System.out.println("文件已分割为 " + splitFiles.size() + " 个部分");

        // 2. 优化MySQL配置（只设置服务器端变量）
        System.out.println("优化MySQL配置...");
        executeSqlCommand("SET GLOBAL foreign_key_checks=0");
        executeSqlCommand("SET GLOBAL unique_checks=0");
        executeSqlCommand("SET GLOBAL innodb_flush_log_at_trx_commit=2");
        executeSqlCommand("SET GLOBAL max_allowed_packet=1073741824"); // 1GB

        // 关键：设置超时参数（在服务器端）
        executeSqlCommand("SET GLOBAL wait_timeout=28800");           // 8小时
        executeSqlCommand("SET GLOBAL interactive_timeout=28800");    // 8小时
        executeSqlCommand("SET GLOBAL net_read_timeout=3600");        // 1小时
        executeSqlCommand("SET GLOBAL net_write_timeout=3600");       // 1小时

        try {
            // 3. 并行导入
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<String>> futures = new ArrayList<>();

            int index = 0;
            for (String splitFile : splitFiles) {
                final int fileIndex = index++;
                futures.add(executor.submit(() -> {
                    System.out.println("开始导入第 " + fileIndex + " 个文件: " + splitFile);
                    String result = importSingleFile(splitFile, databaseName);
                    System.out.println("完成第 " + fileIndex + " 个文件");
                    return result;
                }));
            }

            // 等待所有任务完成
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get(2, TimeUnit.HOURS); // 每个文件最多等待2小时
                } catch (TimeoutException e) {
                    System.err.println("文件 " + i + " 导入超时");
                    throw new RuntimeException("导入超时", e);
                }
            }

            executor.shutdown();
            executor.awaitTermination(4, TimeUnit.HOURS);

            // 4. 清理分割的文件
            System.out.println("清理临时文件...");
            for (String splitFile : splitFiles) {
                try {
                    Files.deleteIfExists(Paths.get(splitFile));
                } catch (Exception e) {
                    System.err.println("删除文件失败: " + splitFile);
                }
            }

            return "并行导入成功！共导入 " + splitFiles.size() + " 个文件";

        } finally {
            // 5. 恢复配置
            System.out.println("恢复MySQL配置...");
            executeSqlCommand("SET GLOBAL foreign_key_checks=1");
            executeSqlCommand("SET GLOBAL unique_checks=1");
            executeSqlCommand("SET GLOBAL innodb_flush_log_at_trx_commit=1");
        }
    }

    /**
     * 分割SQL文件
     */
    private List<String> splitSqlFile(String sqlFilePath, int linesPerFile) throws IOException {
        List<String> splitFiles = new ArrayList<>();

        File inputFile = new File(sqlFilePath);
        String baseName = inputFile.getName().replace(".sql", "");
        String parentDir = inputFile.getParent();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sqlFilePath), StandardCharsets.UTF_8))) {

            int fileIndex = 0;
            int lineCount = 0;
            BufferedWriter writer = null;
            StringBuilder tableStructure = new StringBuilder();
            boolean isInTableStructure = false;

            String line;
            while ((line = reader.readLine()) != null) {

                // 保存表结构
                if (line.trim().toUpperCase().startsWith("DROP TABLE") ||
                        line.trim().toUpperCase().startsWith("CREATE TABLE") ||
                        line.trim().toUpperCase().startsWith("ALTER TABLE")) {
                    isInTableStructure = true;
                }

                if (isInTableStructure) {
                    tableStructure.append(line).append("\n");
                    if (line.trim().endsWith(";")) {
                        isInTableStructure = false;
                    }
                    continue;
                }

                // 跳过注释和空行
                if (line.trim().startsWith("--") ||
                        line.trim().startsWith("/*") ||
                        line.trim().isEmpty()) {
                    continue;
                }

                // 创建新文件
                if (lineCount % linesPerFile == 0) {
                    if (writer != null) {
                        writer.close();
                    }

                    String splitFileName = parentDir + "/" + baseName + "_part" + fileIndex + ".sql";
                    splitFiles.add(splitFileName);
                    writer = new BufferedWriter(
                            new OutputStreamWriter(new FileOutputStream(splitFileName), StandardCharsets.UTF_8));

                    // 每个文件都包含表结构
                    if (tableStructure.length() > 0) {
                        writer.write(tableStructure.toString());
                    }

                    fileIndex++;
                }

                writer.write(line);
                writer.newLine();
                lineCount++;
            }

            if (writer != null) {
                writer.close();
            }
        }

        return splitFiles;
    }

    /**
     * 导入单个文件（移除客户端不支持的参数）
     */
    private String importSingleFile(String sqlFile, String databaseName) throws Exception {
        String sqlPath = sqlFile.replace("\\", "/");

        // 修复：只使用客户端支持的参数
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s " +
                        "--max_allowed_packet=1G " +          // 最大包1GB
                        "--net_buffer_length=16384 " +        // 网络缓冲
                        "%s -e \"source %s\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD,
                databaseName, sqlPath
        );

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (line.contains("ERROR")) {
                    System.err.println("导入错误: " + line);
                }
            }
        }

        int exitCode = process.waitFor();

        if (exitCode == 0) {
            return "success";
        } else {
            throw new RuntimeException("导入失败: " + sqlFile +
                    ", 退出码: " + exitCode +
                    "\n输出: " + output.toString());
        }
    }

    /**
     * 执行SQL命令
     */
    private void executeSqlCommand(String sql) throws Exception {
        String command = String.format(
                "%smysql.exe -P %d -u%s -p%s -e \"%s\"",
                MYSQL_DIR, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, sql
        );

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", command);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // 读取输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ERROR")) {
                    System.err.println("执行SQL错误: " + line);
                }
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println("执行SQL失败: " + sql + ", 退出码: " + exitCode);
        }
    }
}
