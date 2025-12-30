package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class to dynamically select the appropriate downsampling algorithm
 * based on the data's characteristics, using the "Volatility Index".
 */
public class DownsamplingAlgorithmSelector {

    private static final Logger logger = LoggerFactory.getLogger(DownsamplingAlgorithmSelector.class);
    private static final double THRESHOLD = 0.5; // 大幅降低阈值，捕获更多微小波动
    private static final int WINDOW_SIZE = 256; // 缩减窗口大小，提高对局部特征的敏感度

    /**
     * Downsamples a list of points by dynamically choosing between LTTB and Min-Max algorithms.
     * Uses a hybrid approach: data is split into windows, and the algorithm is selected independently for each window.
     *
     * @param dataPoints   The original list of data points.
     * @param targetCount The desired number of points after downsampling.
     * @return A new list of downsampled points.
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount) {
        if (dataPoints == null || dataPoints.size() <= targetCount || targetCount <= 0) {
            return dataPoints;
        }

        if (dataPoints.size() <= WINDOW_SIZE) {
            return executeSingleAlgorithm(dataPoints, targetCount);
        }

        int totalPoints = dataPoints.size();
        int numWindows = (int) Math.ceil((double) totalPoints / WINDOW_SIZE);

        // First pass: Calculate volatility and weight for each window
        double[] volatilities = new double[numWindows];
        double[] weights = new double[numWindows];
        double totalWeightedSize = 0;

        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            double v = calculateVolatilityIndex(windowData);
            volatilities[i] = v;

            // Calculate weight based on complexity
            double weight;
            if (v == 0) {
                weight = 0.05; // 水平线只需要极少的点 (2个点)
            } else if (v <= 0.3) {
                weight = 0.1;  // 极平缓趋势
            } else if (v <= THRESHOLD) {
                weight = 1.0;  // 普通趋势数据 (LTTB)
            } else {
                // 使用 4 次方权重增长，极大幅度地向高频震荡区域倾斜配额。
                // 不再设置硬性上限，让震荡区域能够真正“抢到”足够的点数。
                weight = Math.pow(v, 4.0);
            }
            weights[i] = weight;
            totalWeightedSize += weight * windowData.size();
        }

        // Second pass: Distribute targetCount based on weights and execute
        List<UniPoint> finalResult = new ArrayList<>(targetCount);
        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            // Calculate weighted quota for this window
            int windowTargetCount = (int) Math.round(targetCount * (weights[i] * windowData.size()) / totalWeightedSize);

            // Safety constraints (强力保底策略)
            if (volatilities[i] > THRESHOLD) {
                // 震荡数据必须保证采样密度。
                // 至少给 windowData.size() / 8 的采样率（例如 256个点至少采 32 个点）。
                int minQuota = Math.max(20, windowData.size() / 8);
                if (windowTargetCount < minQuota) windowTargetCount = minQuota;

                // 如果极度波动 (v > 4.0)，采样密度要求更高。
                if (volatilities[i] > 4.0) {
                    int extremeQuota = Math.max(40, windowData.size() / 4);
                    if (windowTargetCount < extremeQuota) windowTargetCount = extremeQuota;
                }
            } else if (volatilities[i] > 0) {
                if (windowTargetCount < 2) windowTargetCount = 2;
            }

            List<UniPoint> windowResult = executeSingleAlgorithm(windowData, windowTargetCount, volatilities[i]);

            // Merge results
            if (!finalResult.isEmpty() && !windowResult.isEmpty()) {
                UniPoint lastOfFinal = finalResult.get(finalResult.size() - 1);
                UniPoint firstOfWindow = windowResult.get(0);
                if (lastOfFinal.getX().compareTo(firstOfWindow.getX()) == 0 &&
                        lastOfFinal.getY().compareTo(firstOfWindow.getY()) == 0) {
                    windowResult = windowResult.subList(1, windowResult.size());
                }
            }
            finalResult.addAll(windowResult);
        }

        return finalResult;
    }

    private static List<UniPoint> executeSingleAlgorithm(List<UniPoint> dataPoints, int targetCount) {
        return executeSingleAlgorithm(dataPoints, targetCount, calculateVolatilityIndex(dataPoints));
    }

    private static List<UniPoint> executeSingleAlgorithm(List<UniPoint> dataPoints, int targetCount, double volatilityIndex) {
        // Fast path for horizontal lines
        if (volatilityIndex == 0.0 && dataPoints.size() >= 2) {
            List<UniPoint> result = new ArrayList<>(2);
            result.add(dataPoints.get(0));
            result.add(dataPoints.get(dataPoints.size() - 1));
            return result;
        }

        if (volatilityIndex > THRESHOLD) {
            return MinMaxDownsampler.downsample(dataPoints, targetCount);
        } else {
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }
    }

    /**
     * Calculates the "Volatility Index" for a given list of points.
     * Volatility Index = Total Vertical Distance / Vertical Range
     *
     * @param dataPoints The list of points.
     * @return The calculated volatility index.
     */
    public static double calculateVolatilityIndex(List<UniPoint> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return 0.0;
        }
        double minY = dataPoints.get(0).getY().doubleValue();
        double maxY = dataPoints.get(0).getY().doubleValue();
        double totalVerticalDistance = 0.0;
        for (int i = 1; i < dataPoints.size(); i++) {
            double currentY = dataPoints.get(i).getY().doubleValue();
            double previousY = dataPoints.get(i - 1).getY().doubleValue();
            if (currentY < minY) {
                minY = currentY;
            }
            if (currentY > maxY) {
                maxY = currentY;
            }
            totalVerticalDistance += Math.abs(currentY - previousY);
        }
        double verticalRange = maxY - minY;
        if (verticalRange == 0.0) {
            return 0.0; // Horizontal line, no volatility.
        }
        return totalVerticalDistance / verticalRange;
    }
}
