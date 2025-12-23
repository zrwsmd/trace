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
    private static final double THRESHOLD = 2.0;
    private static final int WINDOW_SIZE = 512;

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

        // If data size is small enough, or target count is very small, use single algorithm mode
        // to avoid overhead of splitting.
        if (dataPoints.size() <= WINDOW_SIZE || targetCount < (dataPoints.size() / WINDOW_SIZE) * 2) {
            return executeSingleAlgorithm(dataPoints, targetCount);
        }

        List<UniPoint> finalResult = new ArrayList<>(targetCount);
        int totalPoints = dataPoints.size();

        // Split data into windows
        int numWindows = (int) Math.ceil((double) totalPoints / WINDOW_SIZE);

        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            /**
             * 根据窗口的数据量占比，按比例分配降采样后的点数配额。
             *   假设：
             *   - 总数据量 (totalPoints): 4096 个点
             *   - 总目标点数 (targetCount): 256 个点（我们希望最终降采样成 256 个点）
             *   - 窗口大小: 假设我们切分的一个窗口里有 512 个点（windowData.size() = 512）
             *   我们想知道：这个窗口里的 512 个原始点，应该变成多少个结果点？
             *   计算过程：
             *   1. 计算占比:
             *   这个窗口占总数据量的比例是：
             *   512 / 4096 = 0.125 (也就是 1/8)
             *   2. 分配配额:
             *   既然原始数据占总量的 1/8，那么它产生的结果点数也应该占总目标点数的 1/8，这样时间轴的密度才是均匀的。
             *   256 * 0.125 = 32 个点
             *
             *   代码翻译：
             *
             *   // Math.round(...) 是四舍五入取整
             *   int windowTargetCount = (int) Math.round(
             *       (double) targetCount  // 256
             *       * windowData.size()   // * 512
             *       / totalPoints         // / 4096
             *   );
             *   // 结果 = 32
             *
             *   如果不这样做会怎样？
             *   如果我们简单粗暴地给每个窗口固定分配点数，或者分配不均，会导致最后生成的图表有的地方很稀疏（点很少），有的地方很密集（点很多），时间轴看起来就会忽快忽慢，
             *   视觉效果很差。按比例分配能保证数据密度的一致性。
             */
            int windowTargetCount = (int) Math.round((double) targetCount * windowData.size() / totalPoints);
            // Ensure at least 2 points (start and end) unless quota is extremely small
            if (windowTargetCount < 2) windowTargetCount = 2;

            // Execute downsampling for this window
            List<UniPoint> windowResult = executeSingleAlgorithm(windowData, windowTargetCount);

            // Add to final result
            // Note: Simple addAll might duplicate boundary points between windows.
            // However, LTTB/MinMax usually preserve exact start/end points.
            // To be safe and clean, we could check for duplicates, but standard addAll is acceptable
            // as downstream usually handles time-ordered points well.
            // For strictness: if finalResult not empty and last point equals new first point, remove one.
            if (!finalResult.isEmpty() && !windowResult.isEmpty()) {
                 UniPoint lastOfFinal = finalResult.get(finalResult.size() - 1);
                 UniPoint firstOfWindow = windowResult.get(0);
                 if (lastOfFinal.getX().compareTo(firstOfWindow.getX()) == 0 &&
                     lastOfFinal.getY().compareTo(firstOfWindow.getY()) == 0) {
                     windowResult.remove(0); // Avoid duplicate boundary point
                 }
            }
            finalResult.addAll(windowResult);
        }

        return finalResult;
    }

    private static List<UniPoint> executeSingleAlgorithm(List<UniPoint> dataPoints, int targetCount) {
        double volatilityIndex = calculateVolatilityIndex(dataPoints);

        // Optimization 4: Fast path for horizontal lines
        /**
         - 判断条件: 如果一个数据窗口的波动指数为 0（且至少包含 2 个点），说明该窗口内所有点的 Y 值完全一致，是一条水平直线。
         - 优化操作: 直接返回该窗口的第一个点和最后一个点，完全跳过后续复杂的 LTTB 或 Min-Max 算法计算。
         - 收益: 对于包含大量平稳段（水平线）的数据集，该优化能显著降低 CPU 消耗并提升处理速度。
         */
        if (volatilityIndex == 0.0 && dataPoints.size() >= 2) {
            List<UniPoint> result = new ArrayList<>(2);
            result.add(dataPoints.get(0));
            result.add(dataPoints.get(dataPoints.size() - 1));
            return result;
        }

        if (volatilityIndex > THRESHOLD) {
            // logger.debug("Volatility index ({}) > threshold ({}). Using Min-Max downsampling.", volatilityIndex, THRESHOLD);
            return MinMaxDownsampler.downsample(dataPoints, targetCount);
        } else {
            // logger.debug("Volatility index ({}) <= threshold ({}). Using LTTB downsampling.", volatilityIndex, THRESHOLD);
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
