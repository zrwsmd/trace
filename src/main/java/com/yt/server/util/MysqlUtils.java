package com.yt.server.util;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.commons.compress.parallel.InputStreamSupplier;
import org.apache.commons.io.input.NullInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import static com.yt.server.service.IoComposeServiceDatabase.DATABASE_NAME;

/**
 * @description:
 * @projectName:native-test
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/2/22 13:24
 * @version:1.0
 */

public class MysqlUtils {

    private static final String driverClassName;

    private static final String username;

    private static final String password;

    private static final Properties properties = new Properties();

    static {
        try {
            properties.load(MysqlUtils.class.getClassLoader().getResourceAsStream("application.properties"));
            driverClassName = properties.getProperty("spring.datasource.driverClassName");
            username = properties.getProperty("spring.datasource.username");
            password = properties.getProperty("spring.datasource.password");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static final Logger logger = LoggerFactory.getLogger(MysqlUtils.class);

    public static String getMysqlInstallDir(String databaseName) throws ClassNotFoundException, SQLException {
        Class.forName(driverClassName);
        String selfUrl = "jdbc:mysql://localhost:3306/" + databaseName + "?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true";
        Connection connection = DriverManager.getConnection(selfUrl, username, password);
        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery("select @@basedir");
        String mysqlPath = "";

        while (res.next()) {
            mysqlPath = res.getString(1);
        }
        mysqlPath = mysqlPath.concat("bin");
        mysqlPath = mysqlPath.replace("\\", "/");
        mysqlPath = mysqlPath.concat("/");
        // logger.info("Mysql path is :" + mysqlPath);
        return mysqlPath;
    }

    public static String getMysqlDataDir(String databaseName) throws ClassNotFoundException, SQLException {
        Class.forName(driverClassName);
        String selfUrl = "jdbc:mysql://localhost:3306/" + databaseName + "?characterEncoding=utf-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true";
        Connection connection = DriverManager.getConnection(selfUrl, username, password);
        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery("select @@datadir");
        String mysqlPath = "";

        while (res.next()) {
            mysqlPath = res.getString(1);
        }
        mysqlPath = mysqlPath.replace("\\", "/");
//        mysqlPath = mysqlPath.concat("/");
        // logger.info("Mysql path is :" + mysqlPath);
        return mysqlPath;
    }


    //保存为文件
    public static void backUpForSaveFile(String savePath, String databaseName, Collection<String> tableNameList) throws SQLException, ClassNotFoundException, IOException {
        InputStream in = null;
        InputStreamReader inputStreamReader = null;
        BufferedReader br = null;
        OutputStreamWriter writer = null;
        FileOutputStream fout = null;
        try {
            //String mysqlInstallDir = getMysqlInstallDir(databaseName);
            String mysqlInstallDir = "D://trace-mysql//bin//";
            Runtime rt = Runtime.getRuntime();
            // rt.exec("cmd");
            StringBuilder stringBuilder = new StringBuilder();
            //-t参数只备份数据，不包含表结构
            stringBuilder.append(mysqlInstallDir).append("mysqldump.exe -P 3307 -uroot -p123456   ").append(databaseName);
            stringBuilder.append(" ");
            for (String tableName : tableNameList) {
                stringBuilder.append(tableName);
                stringBuilder.append(" ");
            }
            String command = stringBuilder.toString();
            //System.out.println(command);
            // final Process process = rt.exec(mysqlInstallDir + "mysqldump.exe -uroot -p123456" + databaseName + " trace_field_meta trace_downsampling");
            Process process = rt.exec(command);
            in = process.getInputStream();// 控制台的输出信息作为输入流
            inputStreamReader = new InputStreamReader(in, StandardCharsets.UTF_8);// 设置输出流编码为utf8。这里必须是utf8，否则从流中读入的是乱码

            String inStr;
            String outStr = "";
            // 组合控制台输出信息字符串
            br = new BufferedReader(inputStreamReader);
            while ((inStr = br.readLine()) != null) {
                outStr = inStr.concat("\r\n");
                // outStr = sb.toString();//备份出来的内容是一个字符串

                // 要用来做导入用的sql目标文件：
                fout = new FileOutputStream(savePath, true);
                writer = new OutputStreamWriter(fout, StandardCharsets.UTF_8);
                writer.write(outStr);//写文件
                // 注：这里如果用缓冲方式写入文件的话，会导致中文乱码，用flush()方法则可以避免
                writer.flush();

                // 别忘记关闭输入输出流

            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (inputStreamReader != null) {
                inputStreamReader.close();
            }
            if (br != null) {
                br.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (fout != null) {
                fout.close();
            }
        }

        // rt.exec(mysqlInstallDir + "/mysqldump.exe -uroot -p123456 test  trace_field_meta trace_downsampling >e:cc.sql ");


    }

    //数据库表的恢复(加载文件)
    public static String load(String savePath, String databaseName) throws IOException {
        OutputStreamWriter writer = null;
        OutputStream out = null;
        BufferedReader br = null;
        try {
            // String fPath = "e:/dd.sql";
            Runtime rt = Runtime.getRuntime();
            StringBuilder stringBuilder = new StringBuilder();
            String mysqlInstallDir = getMysqlInstallDir(DATABASE_NAME);
            stringBuilder.append(mysqlInstallDir).append("mysql.exe -uroot -p123456  ").append(databaseName);
            String command = stringBuilder.toString();
            // System.out.println(command);
            Process process = rt.exec(command);
            //  Process child1 =rt.exec("C:/Program Files/MySQL/MySQL Server 5.0/bin/mysql.exe -uroot -p8095longchun project");
            out = process.getOutputStream();//控制台的输入信息作为输出流
            String inStr;
            String outStr;

            //fPath=savePath
            StringBuilder sb = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(new FileInputStream(savePath), StandardCharsets.UTF_8));
            while ((inStr = br.readLine()) != null) {
                //   sb.append(inStr).append("\r\n");
                outStr = inStr.concat("\r\n");
                if (!outStr.contains("UNLOCK TABLES;")) {
                    sb.append(outStr);
                } else {
                    sb.append("UNLOCK TABLES;");
                    writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                    writer.write(sb.toString());
                    writer.flush();
                    sb = new StringBuilder();
                }
            }
            // outStr = sb.toString();


            // 注：这里如果用缓冲方式写入文件的话，会导致中文乱码，用flush()方法则可以避免


        } catch (Exception e) {
            e.printStackTrace();
            return "load related table failed";
        } finally {
            if (out != null) {
                out.close();
            }
            if (br != null) {
                br.close();
            }
            if (writer != null) {
                writer.close();
            }
        }
        return "success";


    }

    /**
     * 批量压缩文件
     * new way
     *
     * @param fileNameList 需要压缩的文件名称列表(包含相对路径)
     * @param zipOutName   压缩后的文件名称
     **/
    public static void backUpForCompress(String zipOutName, Collection<String> fileNameList, String databaseName) throws IOException, ExecutionException, InterruptedException, SQLException, ClassNotFoundException {
        ThreadFactory factory = new ThreadFactoryBuilder().build();
        ExecutorService executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2, 100, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200), factory);
        ParallelScatterZipCreator parallelScatterZipCreator = new ParallelScatterZipCreator(executor);
        OutputStream outputStream = new FileOutputStream(zipOutName);
        ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
        zipArchiveOutputStream.setEncoding("UTF-8");

        // String mysqlInstallDir = "C:\\ProgramData\\MySQL\\MySQL Server 8.0\\Data\\";
        //String mysqlInstallDir = getMysqlDataDir(DATABASE_NAME);
        String dataStoreDir = getMysqlDataDir(DATABASE_NAME);
        File file = new File(dataStoreDir);
        final Set<String> directory = getDirectory(file);
        // String data = dataStoreDir.concat("/").concat(tableName);
        List<String> fileList = new ArrayList<>(directory);
        for (String fileName : fileList) {
            File inFile = new File(fileName);
            final InputStreamSupplier inputStreamSupplier = () -> {
                try {
                    return new FileInputStream(inFile);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    return new NullInputStream(0);
                }
            };
            ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(inFile.getName());
            zipArchiveEntry.setMethod(ZipArchiveEntry.DEFLATED);
            zipArchiveEntry.setSize(inFile.length());
            zipArchiveEntry.setUnixMode(UnixStat.FILE_FLAG | 436);
            parallelScatterZipCreator.addArchiveEntry(zipArchiveEntry, inputStreamSupplier);
        }
        parallelScatterZipCreator.writeTo(zipArchiveOutputStream);
        zipArchiveOutputStream.close();
        outputStream.close();
        // log.info("ParallelCompressUtil->ParallelCompressUtil-> info:{}", JSONObject.toJSONString(parallelScatterZipCreator.getStatisticsMessage()));
    }

    /**
     * 把zip文件解压到指定的文件夹
     *
     * @param zipFilePath zip文件路径, 如 "D:/test/aa.trace"
     * @param saveFileDir 解压后的文件存放路径, 如"D:/test/"
     */
    public static void loadDecompress(String zipFilePath, String saveFileDir) throws IOException {
        // if(isEndsWithZip(zipFilePath)) {
        File file = new File(zipFilePath);
        if (file.exists()) {
            InputStream is = null;
            //can read Zip archives
            ZipArchiveInputStream zais = null;
            try {
                is = new FileInputStream(file);
                zais = new ZipArchiveInputStream(is);
                ArchiveEntry archiveEntry;
                //把zip包中的每个文件读取出来
                //然后把文件写到指定的文件夹
                while ((archiveEntry = zais.getNextEntry()) != null) {
                    //获取文件名
                    String entryFileName = archiveEntry.getName();
                    //构造解压出来的文件存放路径
                    String entryFilePath = saveFileDir + entryFileName;
                    final int size = (int) archiveEntry.getSize();
                    byte[] content = new byte[size];
                    zais.read(content);
                    OutputStream os = null;
                    try {
                        //把解压出来的文件写到指定路径
                        File entryFile = new File(entryFilePath);
                        os = new BufferedOutputStream(new FileOutputStream(entryFile));
                        os.write(content);
                    } catch (IOException e) {
                        throw new IOException(e);
                    } finally {
                        if (os != null) {
                            os.flush();
                            os.close();
                        }
                    }

                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                if (zais != null) {
                    zais.close();
                }
                if (is != null) {
                    is.close();
                }

            }
        } else {
            throw new FileNotFoundException("文件路径 " + zipFilePath + "不存在!");
        }
        //   }
    }

    /**
     * 解压
     *
     * @param zipFilePath
     * @param destDir
     */
    public static void decompress(String zipFilePath, String destDir) {
        File zipFile = new File(zipFilePath);
        File pathFile = new File(destDir);
        if (!pathFile.exists()) {
            pathFile.mkdirs();
        }
        logger.info("{}  开始解压...", zipFilePath);
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                // 遍历获取压缩文件内全部条目，包括子条目中的条目
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = entry.getName();
                try (InputStream in = zip.getInputStream(entry)) {
                    String outPath = (destDir + "/" + entryName);
                    // 判断路径是否存在，不存在则创建文件路径
                    File file = new File(outPath.substring(0, outPath.lastIndexOf('/')));
                    if (!file.exists()) {
                        file.mkdirs();
                    }                // 判断文件全路径是否为文件夹,如果是上面已经创建,不需要解压
                    if (new File(outPath).isDirectory()) {
                        continue;
                    }
                    try (OutputStream out = new FileOutputStream(outPath)) {
                        byte[] buf = new byte[4 * 1024];
                        int len = 0;
                        while ((len = in.read(buf)) >= 0) {
                            out.write(buf, 0, len);
                        }
                    } catch (IOException e) {
                        logger.error("解压失败", e);
                    }
                }
            }
            logger.info("{}  解压完毕，解压到了 {}", zipFilePath, destDir);
        } catch (Exception e) {
            logger.error("解压失败", e);
        }
    }


    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException, ExecutionException, InterruptedException {
        List<String> list = new ArrayList<>();
        list.add("trace_table_related_info");
        list.add("table_num_info");
        list.add("trace_field_meta");
        list.add("trace_timestamp_statistics");
//        list.add("trace8");
//        list.add("trace8_0");
//        list.add("trace8_1");
//        list.add("trace8_2");
//        list.add("trace8_3");
//        list.add("trace8_4");
//        list.add("trace8_5");
//        list.add("trace8_6");
//        list.add("trace8_7");
//        list.add("trace8_8");
//        list.add("trace8_9");
//        String prefix = "trace8_downsampling_app_00pou_00var_0";
//        Integer[] data = new Integer[]{8};
//        for (int i = 0; i < 10; i++) {
//            for (Integer rate : data) {
//                list.add(prefix.concat(String.valueOf(i)).concat("_").concat(String.valueOf(rate)));
//            }
//        }
        final long start = System.currentTimeMillis();
        //     backUpForCompress("e://0726.zip", list, "aa");//13562ms  52382ms
        //    String mysqlDataDir = MysqlUtils.getMysqlDataDir(DATABASE_NAME);
//        System.out.println(mysqlDataDir);
//        mysqlDataDir = mysqlDataDir.concat("aa").concat("/");


        // String mysqlDataDir = getMysqlDataDir(DATABASE_NAME);
//        File file=new File(mysqlDataDir);
//        final Set<String> directory = getDirectory(file);
//        System.out.println(directory.size());
//        final File[] files = file.listFiles();
//        for (File innerFile : files) {
//            System.out.println(innerFile);
//        }
//        mysqlDataDir = mysqlDataDir.concat(DATABASE_NAME).concat("/");;
//        System.out.println(mysqlDataDir);

        // backUpForSaveFile("e://meta.sql", "trace", list);//16868ms
        //  loadNio("e:/0615.sql", "aa");//173263ms


//         ZipUtil.zip("D:\\0\\MySQLData","e://0726-hutool.zip");
//        zipOriginal("D:\\0\\MySQLData","e://0726-original.zip");
        //final String mysqlInstallDir = getMysqlDataDir(DATABASE_NAME);
        decompress("e://0726-th3.trace", "D:/0/MySQLData/");//6459ms 61516ms
        // bomb  ZipUtil.unzip("e://0726-th.zip","e:\\ccc");
//
//        System.out.println(mysqlInstallDir);
        final long end = System.currentTimeMillis();
        System.out.println("花费了" + (end - start));//5113ms  1000000        51037ms  10000000    121609ms  20000000
    }

    static Set<String> fileList = new HashSet<>();

    // 递归遍历
    private static Set<String> getDirectory(File file) {
        File flist[] = file.listFiles();

        if (flist == null || flist.length == 0) {
            return fileList;
        }
        for (File f : flist) {
            if (f.isDirectory()) {
                fileList.add(f.getAbsolutePath());
                //这里将列出所有的文件夹
                // System.out.println("Dir==>" + f.getAbsolutePath());
                // getDirectory(f);
            } else {
                fileList.add(f.getAbsolutePath());
                //这里将列出所有的文件
                // System.out.println("file==>" + f.getAbsolutePath());
            }
        }
        //  System.out.println(fileList.size());
        return fileList;
    }


    public static String loadNio(String savePath, String databaseName) throws IOException {
        OutputStream out = null;
        ReadableByteChannel inChannel = null;
        WritableByteChannel outChannel = null;
        try {
            // String fPath = "e:/dd.sql";
            Runtime rt = Runtime.getRuntime();
            StringBuilder stringBuilder = new StringBuilder();
            String mysqlInstallDir = getMysqlInstallDir(DATABASE_NAME);
            stringBuilder.append(mysqlInstallDir).append("mysql.exe -uroot -p123456  ").append(databaseName);
            String command = stringBuilder.toString();
            // System.out.println(command);
            Process process = rt.exec(command);
            //  Process child1 =rt.exec("C:/Program Files/MySQL/MySQL Server 5.0/bin/mysql.exe -uroot -p8095longchun project");
            out = process.getOutputStream();//控制台的输入信息作为输出流
            FileInputStream inputStream = new FileInputStream(savePath);
            inChannel = Channels.newChannel(inputStream);
            outChannel = Channels.newChannel(out);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (inChannel.read(buffer) != -1) {
                buffer.flip();
                outChannel.write(buffer);
                buffer.clear();
            }

        } catch (SQLException | ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (out != null) {
                out.close();
            }
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }
//            while ((inStr = br.readLine()) != null) {
//                //   sb.append(inStr).append("\r\n");
//                outStr = inStr.concat("\r\n");
//                writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
//                writer.write(outStr);
//                writer.flush();
//            }


        // 注：这里如果用缓冲方式写入文件的话，会导致中文乱码，用flush()方法则可以避免


        return "success";
    }

    private static byte[] byteBuffer;

    public static synchronized int read(MappedByteBuffer mappedByteBuffers, Integer byteBufferSize) {
        int limit = mappedByteBuffers.limit();
        int position = mappedByteBuffers.position();

        int realSize = 0;
        try {
            realSize = byteBufferSize;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (limit - position < byteBufferSize) {
            realSize = limit - position;
        }
        byteBuffer = new byte[realSize];
        mappedByteBuffers.get(byteBuffer);
        return realSize;
    }

    public static ByteBuffer byte2Byffer(byte[] byteArray) {

        //初始化一个和byte长度一样的buffer
        ByteBuffer buffer = ByteBuffer.allocate(byteArray.length);
        // 数组放到buffer中
        buffer.put(byteArray);
        //重置 limit 和postion 值 否则 buffer 读取数据不对
        buffer.flip();
        return buffer;
    }

    public static synchronized byte[] getCurrentBytes() {
        return byteBuffer;
    }

    public static String loadMmap(String savePath, String databaseName) throws IOException {
        OutputStream out = null;
        FileChannel inChannel = null;
        WritableByteChannel outChannel = null;
        try {
            // String fPath = "e:/dd.sql";
            Runtime rt = Runtime.getRuntime();
            StringBuilder stringBuilder = new StringBuilder();
            String mysqlInstallDir = getMysqlInstallDir(DATABASE_NAME);
            stringBuilder.append(mysqlInstallDir).append("mysql.exe -uroot -p123456  ").append(databaseName);
            String command = stringBuilder.toString();
            // System.out.println(command);
            Process process = rt.exec(command);
            //  Process child1 =rt.exec("C:/Program Files/MySQL/MySQL Server 5.0/bin/mysql.exe -uroot -p8095longchun project");
            out = process.getOutputStream();//控制台的输入信息作为输出流
            RandomAccessFile aFile = new RandomAccessFile(savePath, "rw");
            inChannel = aFile.getChannel();
            // inChannel = Channels.newChannel(inputStream);
            long fileSize = inChannel.size();
            int FETCH_SIZE = 300 * 1024 * 1024;
            long preLength = 0;
            int loopNum = (int) Math.ceil((double) fileSize / FETCH_SIZE);
            MappedByteBuffer mappedByteBuffers = null;
            for (int j = 0; j < loopNum; j++) {
                if (j < loopNum - 1) {
                    mappedByteBuffers = inChannel.map(FileChannel.MapMode.READ_ONLY, preLength, FETCH_SIZE);
                } else {
                    mappedByteBuffers = inChannel.map(FileChannel.MapMode.READ_ONLY, preLength, fileSize - preLength);
                }
                preLength += FETCH_SIZE;
                outChannel = Channels.newChannel(out);
                while (read(mappedByteBuffers, 4096) != 0) {
                    outChannel.write(byte2Byffer(getCurrentBytes()));
                }
            }
            //final MappedByteBuffer mappedByteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, FETCH_SIZE);

        } catch (SQLException | ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (out != null) {
                out.close();
            }
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }
        return "success";
    }


    public static String loadTransfer(String savePath, String databaseName) throws IOException {
        OutputStream out = null;
        FileChannel inChannel = null;
        WritableByteChannel outChannel = null;
        try {
            // String fPath = "e:/dd.sql";
            Runtime rt = Runtime.getRuntime();
            StringBuilder stringBuilder = new StringBuilder();
            String mysqlInstallDir = getMysqlInstallDir(DATABASE_NAME);
            stringBuilder.append(mysqlInstallDir).append("mysql.exe -uroot -p123456  ").append(databaseName);
            String command = stringBuilder.toString();
            // System.out.println(command);
            Process process = rt.exec(command);
            //  Process child1 =rt.exec("C:/Program Files/MySQL/MySQL Server 5.0/bin/mysql.exe -uroot -p8095longchun project");
            out = process.getOutputStream();//控制台的输入信息作为输出流
            // FileInputStream inputStream = new FileInputStream(savePath);
            RandomAccessFile aFile = new RandomAccessFile(savePath, "rw");
            inChannel = aFile.getChannel();
            //inChannel = Channels.newChannel(inputStream);
            outChannel = Channels.newChannel(out);
            // outChannel.
            inChannel.transferTo(0, inChannel.size(), outChannel);
            //final MappedByteBuffer mappedByteBuffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, FETCH_SIZE);
        } catch (SQLException | ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (out != null) {
                out.close();
            }
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }
        return "success";
    }

    public static void traceSave(String directoryPath, String zipPath, int processors) {
        new ZipArchiveUtils().createZip(directoryPath, zipPath, processors);
    }

    public static void terminateMysql(String mysqlInstallDir) throws SQLException, ClassNotFoundException, IOException {
        Runtime runtime = Runtime.getRuntime();
        String command = mysqlInstallDir + "mysqladmin -uroot -p123456  shutdown";
        runtime.exec(command);
        logger.info("mysql successfully terminated");
    }

    public static void restartMysql(String path) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        runtime.exec(path + " elevate net start mysql80");
        logger.info("mysql successfully restarted");
    }
}
