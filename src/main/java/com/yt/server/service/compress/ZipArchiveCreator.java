package com.yt.server.service.compress;

import org.apache.commons.compress.archivers.zip.ParallelScatterZipCreator;
import org.apache.commons.compress.parallel.ScatterGatherBackingStoreSupplier;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server.util
 * @author:赵瑞文
 * @createTime:2023/7/27 9:02
 * @version:1.0
 */
public class ZipArchiveCreator extends ParallelScatterZipCreator {

    public ZipArchiveCreator() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ZipArchiveCreator(int nThreads) {
        this(Executors.newFixedThreadPool(nThreads));
    }

    public ZipArchiveCreator(ExecutorService executorService) {
        super(executorService);
    }

    public ZipArchiveCreator(ExecutorService executorService, ScatterGatherBackingStoreSupplier backingStoreSupplier) {
        super(executorService, backingStoreSupplier);
    }
}
