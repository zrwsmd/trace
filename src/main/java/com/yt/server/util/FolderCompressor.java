package com.yt.server.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.*;

import org.apache.commons.compress.utils.IOUtils;

/**
 * 单线程
 */
public class FolderCompressor {

    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) {
        String sourceFolder = "D:\\0\\MySQLData";
        String zipFile = "e://0726-th.zip";
        final long start = System.currentTimeMillis();
        compressFolder(sourceFolder, zipFile);
        System.out.println(System.currentTimeMillis()-start);
    }

    private static void compressFolder(String sourceFolderPath, String destinationZipFilePath) {
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(destinationZipFilePath)))) {
            File sourceFolder = new File(sourceFolderPath);
            compressFilesRecursively(zipOut, sourceFolder, "");
            zipOut.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void compressFilesRecursively(ZipArchiveOutputStream zipOut, File sourceFile, String parentPath) throws IOException {
        String entryName = parentPath + sourceFile.getName();
        ArchiveEntry entry = zipOut.createArchiveEntry(sourceFile, entryName);
        zipOut.putArchiveEntry(entry);

        if (sourceFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(sourceFile);BufferedInputStream bis=new BufferedInputStream(fis);) {
                IOUtils.copy(bis, zipOut);
            }
            zipOut.closeArchiveEntry();
        } else if (sourceFile.isDirectory()) {
            zipOut.closeArchiveEntry();
            File[] children = sourceFile.listFiles();
            if (children != null) {
                for (File child : children) {
                    compressFilesRecursively(zipOut, child, entryName + "/");
                }
            }
        }
    }
}