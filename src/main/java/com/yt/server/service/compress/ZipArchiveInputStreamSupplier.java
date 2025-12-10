package com.yt.server.service.compress;

import org.apache.commons.compress.parallel.InputStreamSupplier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/7/27 9:03
 * @version:1.0
 */
public class ZipArchiveInputStreamSupplier implements InputStreamSupplier {

    private final File file;

    public ZipArchiveInputStreamSupplier(File file) {
        this.file = file;
    }

    @Override
    public InputStream get() {
        try {
            return file.isDirectory() ? new ZipArchiveNullInputStream(0) : new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
