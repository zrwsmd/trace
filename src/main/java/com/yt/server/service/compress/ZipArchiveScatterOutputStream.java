package com.yt.server.service.compress;

import org.apache.commons.compress.archivers.zip.ScatterZipOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryRequest;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/7/27 9:05
 * @version:1.0
 */
public class ZipArchiveScatterOutputStream {

    /**
     * 需要 压缩文件/文件夹 路径
     */
    private final String directoryPath;
    /**
     *
     */
    private final ZipArchiveCreator creator;
    /**
     * 多线程输出流
     */
    private final ScatterZipOutputStream output;

    public ZipArchiveScatterOutputStream(String directoryPath) throws IOException {
        this(directoryPath, Runtime.getRuntime().availableProcessors());
    }

    public ZipArchiveScatterOutputStream(String directoryPath, int nThreads) throws IOException {
        this.directoryPath = directoryPath;
        this.creator = new ZipArchiveCreator(nThreads);
        this.output = ScatterZipOutputStream.fileBased(File.createTempFile("whatever-preffix", ".whatever"));
    }

    public void addEntry(ZipArchiveEntry entry, InputStreamSupplier supplier) throws IOException {
        if (entry.isDirectory() && !entry.isUnixSymlink()) {
            output.addArchiveEntry(ZipArchiveEntryRequest.createZipArchiveEntryRequest(entry, supplier));
        } else {
            creator.addArchiveEntry(entry, supplier);
        }
    }

    public void writeTo(ZipArchiveOutputStream archiveOutput) throws IOException, ExecutionException, InterruptedException {
        output.writeTo(archiveOutput);
        output.close();
        creator.writeTo(archiveOutput);
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    public ZipArchiveCreator getCreator() {
        return creator;
    }

    public ScatterZipOutputStream getOutput() {
        return output;
    }
}
