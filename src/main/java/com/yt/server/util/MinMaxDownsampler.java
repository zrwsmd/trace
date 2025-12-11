package com.yt.server.util;

import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A specialized downsampler for high-frequency periodic data.
 * Instead of LTTB (which preserves shape but can cause aliasing on dense periodic signals),
 * this uses a Min-Max approach to preserve the envelope of the signal.
 */
public class MinMaxDownsampler {

    /**
     * Downsamples data by dividing it into buckets and keeping the Min and Max values of each bucket.
     * This ensures the visual "envelope" of the signal is preserved, creating a filled effect for dense periodic data.
     *
     * @param data      The original list of points
     * @param threshold The target number of points to return (approximate)
     * @return A downsampled list of points containing roughly `threshold` points
     */
    public static List<UniPoint> downsample(List<UniPoint> data, int threshold) {
        if (CollectionUtils.isEmpty(data) || threshold >= data.size()) {
            return data;
        }
        // We want to return 'threshold' points.
        // Since we select 2 points (min and max) per bucket, we need threshold/2 buckets.
        int bucketCount = Math.max(1, threshold / 2);
        // Calculate bucket size
        int dataSize = data.size();
        int step = dataSize / bucketCount;
        if (step < 1) step = 1;
        List<UniPoint> sampledPoints = new ArrayList<>(bucketCount * 2);
        //String varName = data.get(0).getVarName(); // Assuming all points have same varName
        for (int i = 0; i < bucketCount; i++) {
            int start = i * step;
            int end = Math.min((i + 1) * step, dataSize);
            if (start >= end) break;
            UniPoint minPoint = null;
            UniPoint maxPoint = null;
            // Find min and max Y in this bucket
            for (int j = start; j < end; j++) {
                UniPoint current = data.get(j);
                if (minPoint == null || current.getY().compareTo(minPoint.getY()) < 0) {
                    minPoint = current;
                }
                if (maxPoint == null || current.getY().compareTo(maxPoint.getY()) > 0) {
                    maxPoint = current;
                }
            }
            if (minPoint != null) {
                // Determine order by X to keep time sequence valid
                /**
                 * 保证插入的顺序是按照时间戳(x值)从小到大插入到sampledPoints里面的
                 * [ (4, 9), (5, 4), (6, 7), (7, 3) ]
                 * minPoint 是 (7, 3)。maxPoint 是 (4, 9)。
                 * 比较X值： minPoint.getX() (7) > maxPoint.getX() (4)。
                 * 先把 maxPoint 存进去，再存 minPoint。
                 *
                 */
                if (minPoint.getX().compareTo(maxPoint.getX()) <= 0) {
                    sampledPoints.add(minPoint);
                    if (minPoint != maxPoint) {
                        sampledPoints.add(maxPoint);
                    }
                } else {
                    sampledPoints.add(maxPoint);
                    sampledPoints.add(minPoint);
                }
            }
        }
        // Ensure the very first and last points are included if critical for continuity,
        // though MinMax usually covers range well.
        // LTTB enforces first and last, we can optionally check them here but MinMax naturally captures extremes.

        // Sort by X to ensure strict time ordering, although bucket processing usually guarantees this
        // except when min/max swap within a bucket (handled above).
        // Sorting generally not needed if buckets are processed sequentially and points added in X order.
        return sampledPoints;
    }
}
