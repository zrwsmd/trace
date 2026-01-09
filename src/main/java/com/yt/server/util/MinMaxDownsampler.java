package com.yt.server.util;

import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A specialized downsampler for high-frequency periodic data.
 * Instead of LTTB (which preserves shape but can cause aliasing on dense periodic signals),
 * this uses a Min-Max approach to preserve the envelope of the signal.
 */
public class MinMaxDownsampler {

    private static final Logger logger = LoggerFactory.getLogger(MinMaxDownsampler.class);

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

        // If threshold is very low for a large dataset, prioritize global extremes to show the envelope
        if (threshold < 4 && data.size() > 10) {
            List<UniPoint> extremes = new ArrayList<>();
            UniPoint globalMin = data.get(0);
            UniPoint globalMax = data.get(0);
            for (UniPoint p : data) {
                if (p.getY().doubleValue() < globalMin.getY().doubleValue()) globalMin = p;
                if (p.getY().doubleValue() > globalMax.getY().doubleValue()) globalMax = p;
            }
            extremes.add(data.get(0));
            if (globalMin != data.get(0) && globalMin != data.get(data.size() - 1)) extremes.add(globalMin);
            if (globalMax != data.get(0) && globalMax != data.get(data.size() - 1) && globalMax != globalMin)
                extremes.add(globalMax);
            extremes.add(data.get(data.size() - 1));
            extremes.sort((p1, p2) -> p1.getX().compareTo(p2.getX()));
            return extremes;
        }

        if (threshold < 2) return data.subList(0, 1);

        List<UniPoint> sampledPoints = new ArrayList<>(threshold);
        UniPoint firstPoint = data.get(0);
        UniPoint lastPoint = data.get(data.size() - 1);

        // 1. Always add the first point
        sampledPoints.add(firstPoint);

        if (threshold == 2) {
            // 当 threshold 为 2 时，使用 Stream 直接找全局最大最小值点
            UniPoint globalMin = data.stream().min(Comparator.comparingDouble(p -> p.getY().doubleValue())).orElse(data.get(0));
            UniPoint globalMax = data.stream().max(Comparator.comparingDouble(p -> p.getY().doubleValue())).orElse(data.get(0));
            sampledPoints.clear();
            if (globalMin.getX().doubleValue() <= globalMax.getX().doubleValue()) {
                sampledPoints.add(globalMin);
                if (globalMin != globalMax) sampledPoints.add(globalMax);
            } else {
                sampledPoints.add(globalMax);
                sampledPoints.add(globalMin);
            }
            logger.info("get min max value{}", sampledPoints);
            return sampledPoints;
        }

        // 2. Downsample the middle part of the data
        List<UniPoint> middleData = data.subList(1, data.size() - 1);
        int targetMiddleCount = threshold - 2;

        if (targetMiddleCount > 0 && !middleData.isEmpty()) {
            // 充分利用每一个配额点。如果 targetMiddleCount 是 15，我们要尽量采出 15 个点。
            // 传统 Min-Max 是成对采 (Min, Max)，所以我们分 bucketCount = targetMiddleCount / 2 个桶。
            // 如果有余数，我们可以在最后一个桶多采一个点，或者增加一个单点桶。
            int bucketCount = Math.max(1, targetMiddleCount / 2);
            int dataSize = middleData.size();
            double bucketWidth = (double) dataSize / bucketCount;

            for (int i = 0; i < bucketCount; i++) {
                int start = (int) (i * bucketWidth);
                int end = (int) Math.min((i + 1) * bucketWidth, dataSize);
                if (start >= end) continue;

                UniPoint minPoint = null;
                UniPoint maxPoint = null;
                double minY = Double.MAX_VALUE;
                double maxY = -Double.MAX_VALUE;

                for (int j = start; j < end; j++) {
                    UniPoint current = middleData.get(j);
                    double currentY = current.getY().doubleValue();
                    if (minPoint == null || currentY < minY) {
                        minPoint = current;
                        minY = currentY;
                    }
                    if (maxPoint == null || currentY > maxY) {
                        maxPoint = current;
                        maxY = currentY;
                    }
                }

                if (minPoint != null) {
                    // 保持 X 轴有序
                    if (minPoint.getX().doubleValue() <= maxPoint.getX().doubleValue()) {
                        sampledPoints.add(minPoint);
                        if (minPoint != maxPoint && sampledPoints.size() < threshold - 1) {
                            sampledPoints.add(maxPoint);
                        }
                    } else {
                        sampledPoints.add(maxPoint);
                        if (minPoint != maxPoint && sampledPoints.size() < threshold - 1) {
                            sampledPoints.add(minPoint);
                        }
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
