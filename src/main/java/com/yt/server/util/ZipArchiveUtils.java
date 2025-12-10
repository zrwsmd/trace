package com.yt.server.util;

import com.yt.server.service.compress.ZipArchiveInputStreamSupplier;
import com.yt.server.service.compress.ZipArchiveScatterOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.ZipEntry;
/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/7/27 9:06
 * @version:1.0
 */
public class ZipArchiveUtils {

    /**
     * 限制最大使用线程
     */
    private static final int MAX_THREADS = 4;
    /**
     * 默认使用线程数百分比：67%
     */
    private static final double DEFAULT_THREADS_RATIO = 0.67;

    /**
     * 创建压缩文件
     *
     * @param directoryPath 需压缩文件夹/文件
     * @param zipPath       压缩包路径 + 文件名
     */
    public void createZip(String directoryPath, String zipPath) {
        this.createZip(directoryPath, zipPath, ZipEntry.DEFLATED, getAvailableThreads(DEFAULT_THREADS_RATIO));
    }

    /**
     * 创建压缩文件
     *
     * @param directoryPath 需压缩文件夹/文件
     * @param zipPath       压缩包路径 + 文件名
     * @param nThreads      线程数
     */
    public void createZip(String directoryPath, String zipPath, int nThreads) {
        this.createZip(directoryPath, zipPath, ZipEntry.DEFLATED, nThreads);
    }

    /**
     * 创建压缩文件
     *
     * @param directoryPath         需压缩文件夹/文件
     * @param zipPath               压缩包路径 + 文件名
     * @param availableThreadsRatio 可用线程比例
     */
    public void createZip(String directoryPath, String zipPath, double availableThreadsRatio) {
        this.createZip(directoryPath, zipPath, ZipEntry.DEFLATED, getAvailableThreads(availableThreadsRatio));
    }

    /**
     * 创建压缩文件
     *
     * @param directoryPath 需压缩文件夹/文件
     * @param zipPath       压缩包路径 + 文件名
     * @param method        压缩方式：ZipEntry.DEFLATED: 压缩/ZipEntry.STORED:不压缩
     * @param nThreads      线程数
     */
    public void createZip(String directoryPath, String zipPath, int method, int nThreads) {
        try {
            File zipFile = new File(zipPath);
            File dstFolder = new File(zipFile.getParent());
            if (!dstFolder.isDirectory()) {
                dstFolder.mkdirs();
            }
            File rootDir = new File(directoryPath);
            ZipArchiveScatterOutputStream scatterOutput = new ZipArchiveScatterOutputStream(rootDir.getAbsolutePath(), nThreads);
            compress(rootDir, scatterOutput, "", method);
            ZipArchiveOutputStream archiveOutput = new ZipArchiveOutputStream(zipFile);
            scatterOutput.writeTo(archiveOutput);
            archiveOutput.close();
        } catch (Exception e) {
            System.err.println("压缩异常");
            e.printStackTrace();
        }
    }

    /**
     * 压缩文件
     *
     * @param dir           压缩文件
     * @param output        ZipArchive多线程输出流
     * @param zipName       压缩包名称
     * @param method        压缩方式：ZipEntry.DEFLATED: 压缩/ZipEntry.STORED:不压缩
     * @throws IOException  流异常
     */
    private void compress(File dir, ZipArchiveScatterOutputStream output, String zipName, int method) throws IOException {
        if (dir == null) {
            return;
        }
        if (dir.isFile()) {
            addEntry(zipName, dir, output, method);
            return;
        }
        if (Objects.requireNonNull(dir.listFiles()).length == 0) {
            String fileName = zipName + dir.getAbsolutePath().replace(output.getDirectoryPath(), "") + File.separator;
            addEntry(fileName, dir, output, method);
            return;
        }
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                compress(file, output, zipName, method);
            } else {
                String fileName = zipName + file.getParent().replace(output.getDirectoryPath(), "") + File.separator + file.getName();
                addEntry(fileName, file, output, method);
            }
        }
    }

    /**
     * 添加目录/文件
     *
     * @param filePath      压缩文件路径
     * @param file          压缩文件
     * @param output        ZipArchive多线程输出流
     * @param method        压缩方式：ZipEntry.DEFLATED: 压缩/ZipEntry.STORED:不压缩
     * @throws IOException  流异常
     */
    private void addEntry(String filePath, File file, ZipArchiveScatterOutputStream output, int method) throws IOException {
        ZipArchiveEntry archiveEntry = new ZipArchiveEntry(filePath);
        archiveEntry.setMethod(method);
        InputStreamSupplier supplier = new ZipArchiveInputStreamSupplier(file);
        output.addEntry(archiveEntry, supplier);
    }

    /**
     * 获取无后缀文件名
     *
     * @param fileName  文件名
     * @return          无后缀文件名
     */
    private String getFileName(String fileName) {
        if (fileName == null || fileName.length() <= 1 || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    /**
     * 计算可用线程
     *
     * @param ratio  使用线程比率
     * @return       可用线程
     */
    private int getAvailableThreads(double ratio) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int nThreads = (int) (availableProcessors * ratio);
        if (nThreads <= 0) {
            return  1;
        } else if (nThreads > MAX_THREADS) {
            return Math.min(MAX_THREADS, availableProcessors);
        }
        return Math.min(nThreads, availableProcessors);
    }
}
