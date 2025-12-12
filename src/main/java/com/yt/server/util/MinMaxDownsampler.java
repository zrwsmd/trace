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
        if (CollectionUtils.isEmpty(data) || threshold >= data.size() || threshold < 2) {
            return data;
        }

        List<UniPoint> sampledPoints = new ArrayList<>(threshold);
        UniPoint firstPoint = data.get(0);
        UniPoint lastPoint = data.get(data.size() - 1);

        // 1. Always add the first point
        sampledPoints.add(firstPoint);

        // 2. Downsample the middle part of the data
        List<UniPoint> middleData = data.subList(1, data.size() - 1);
        int targetMiddleCount = threshold - 2;

        if (targetMiddleCount > 0 && !middleData.isEmpty()) {
            int bucketCount = Math.max(1, targetMiddleCount / 2);
            int dataSize = middleData.size();
            int step = dataSize / bucketCount;
            if (step < 1) step = 1;

            for (int i = 0; i < bucketCount; i++) {
                int start = i * step;
                int end = Math.min((i + 1) * step, dataSize);
                if (start >= end) break;

                UniPoint minPoint = null;
                UniPoint maxPoint = null;

                for (int j = start; j < end; j++) {
                    UniPoint current = middleData.get(j);
                    if (minPoint == null || current.getY().compareTo(minPoint.getY()) < 0) {
                        minPoint = current;
                    }
                    if (maxPoint == null || current.getY().compareTo(maxPoint.getY()) > 0) {
                        maxPoint = current;
                    }
                }

                if (minPoint != null) {
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
        }

        // 3. Always add the last point, avoid duplicates
        if (!sampledPoints.get(sampledPoints.size() - 1).equals(lastPoint)) {
            sampledPoints.add(lastPoint);
        }

        return sampledPoints;
    }
}
