package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自适应降采样算法选择器
 * 根据信号特征自动选择最优降采样算法
 *
 * @author 赵瑞文
 * @version 2.0
 */
public class AdaptiveDownsamplingSelector {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveDownsamplingSelector.class);

    // ==================== 配置参数 ====================
    private static final int WINDOW_SIZE = 512;
    private static final int MIN_POINTS_FOR_ANALYSIS = 10;

    // 信号特征阈值
    private static final double FLATNESS_THRESHOLD = 0.01;      // 平坦度阈值
    private static final double LINEARITY_THRESHOLD = 0.95;     // 线性度阈值 (R²)
    private static final double PERIODICITY_THRESHOLD = 0.7;    // 周期性阈值
    private static final double STEP_THRESHOLD = 0.3;           // 阶跃检测阈值
    private static final double NOISE_RATIO_THRESHOLD = 0.5;    // 噪声比例阈值

    /**
     * 主入口：自适应降采样
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount) {
        if (CollectionUtils.isEmpty(dataPoints) || dataPoints.size() <= targetCount || targetCount <= 0) {
            return dataPoints;
        }

        // 小数据集直接使用LTTB
        if (dataPoints.size() < MIN_POINTS_FOR_ANALYSIS) {
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }

        // 分窗口处理（大数据集）
        if (dataPoints.size() > WINDOW_SIZE * 2 && targetCount >= WINDOW_SIZE) {
            return windowBasedDownsampling(dataPoints, targetCount);
        }

        // 单窗口处理
        return selectAndApplyAlgorithm(dataPoints, targetCount);
    }

    /**
     * 基于窗口的降采样（大数据集）
     */
    private static List<UniPoint> windowBasedDownsampling(List<UniPoint> dataPoints, int targetCount) {
        int totalPoints = dataPoints.size();
        int numWindows = (int) Math.ceil((double) totalPoints / WINDOW_SIZE);

        // 第一阶段：分析所有窗口，计算权重
        double[] weights = new double[numWindows];
        SignalType[] signalTypes = new SignalType[numWindows];
        SignalFeatures[] allFeatures = new SignalFeatures[numWindows];
        double totalWeightedSize = 0;

        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            SignalFeatures features = extractFeatures(windowData);
            SignalType type = classifySignal(features);

            double weight = calculateWindowWeight(type, features);

            allFeatures[i] = features;
            signalTypes[i] = type;
            weights[i] = weight;
            totalWeightedSize += weight * windowData.size();
        }

        // 第二阶段：分发点数并执行算法
        List<UniPoint> result = new ArrayList<>(targetCount);
        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            // 基于权重的点数分配
            int windowTargetCount = (int) Math.round(targetCount * (weights[i] * windowData.size()) / totalWeightedSize);

            // 安全保底逻辑
            windowTargetCount = applySafetyConstraints(windowTargetCount, signalTypes[i], allFeatures[i], windowData.size());

            // 应用算法
            DownsamplingAlgorithm algorithm = selectAlgorithm(signalTypes[i], allFeatures[i], windowData.size(), windowTargetCount);
            List<UniPoint> windowResult = applyAlgorithm(algorithm, windowData, windowTargetCount, allFeatures[i]);

            // 避免边界重复 (使用高效的 subList)
            if (!result.isEmpty() && !windowResult.isEmpty()) {
                UniPoint lastOfResult = result.get(result.size() - 1);
                UniPoint firstOfWindow = windowResult.get(0);
                if (pointsEqual(lastOfResult, firstOfWindow)) {
                    if (windowResult.size() > 1) {
                        windowResult = windowResult.subList(1, windowResult.size());
                    } else {
                        windowResult = Collections.emptyList();
                    }
                }
            }

            result.addAll(windowResult);
        }

        return result;
    }

    /**
     * 计算窗口权重：劫富济贫的核心逻辑
     */
    private static double calculateWindowWeight(SignalType type, SignalFeatures features) {
        switch (type) {
            case FLAT:
                return 0.05; // 平稳信号几乎不占点
            case LINEAR:
                return 0.2;  // 线性趋势占点较少
            case PERIODIC:
                // 周期信号需求随波动率指数级增长
                return Math.pow(features.volatility, 3.0);
            case STEP:
            case PULSE:
                return 2.0;  // 阶跃和脉冲需要额外关注边缘特征
            case NOISE:
            case TREND_NOISE:
            case COMPLEX:
                return Math.max(1.0, features.volatility);
            default:
                return 1.0;
        }
    }

    /**
     * 安全保底：确保关键信号不因为总配额少而消失
     */
    private static int applySafetyConstraints(int count, SignalType type, SignalFeatures features, int windowSize) {
        if (type == SignalType.FLAT) return Math.max(2, count);

        int minCount;
        if (type == SignalType.PERIODIC || type == SignalType.COMPLEX) {
            // 震荡信号保底密度：256个点至少给25个点，512个点至少给50个点
            minCount = Math.max(15, windowSize / 10);
        } else if (type == SignalType.STEP || type == SignalType.PULSE) {
            minCount = 10;
        } else {
            minCount = 2;
        }
        return Math.max(minCount, count);
    }

    /**
     * 选择并应用算法（核心逻辑）
     */
    private static List<UniPoint> selectAndApplyAlgorithm(List<UniPoint> dataPoints, int targetCount) {
        try {
            // 1. 提取信号特征
            SignalFeatures features = extractFeatures(dataPoints);

            // 2. 识别信号类型
            SignalType signalType = classifySignal(features);

            // 3. 根据信号类型选择算法
            DownsamplingAlgorithm algorithm = selectAlgorithm(signalType, features, dataPoints.size(), targetCount);

            // 4. 应用算法
            List<UniPoint> result = applyAlgorithm(algorithm, dataPoints, targetCount, features);

            // 5. 日志记录
            if (logger.isDebugEnabled()) {
                logger.debug("Variable: {}, SignalType: {}, Algorithm: {}, Input: {}, Output: {}, Volatility: {:.2f}, Periodicity: {:.2f}",
                        dataPoints.get(0).getVarName(), signalType, algorithm, dataPoints.size(), result.size(),
                        features.volatility, features.periodicity);
            }

            return result;
        } catch (Exception e) {
            // 出错时回退到LTTB
            logger.warn("Adaptive downsampling failed, fallback to LTTB: {}", e.getMessage());
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }
    }

    // ==================== 信号特征提取 ====================

    /**
     * 信号特征结构
     */
    static class SignalFeatures {
        double mean;                    // 均值
        double stdDev;                  // 标准差
        double range;                   // 值域范围
        double volatility;              // 波动指数
        double flatness;                // 平坦度
        double linearity;               // 线性度 (R²)
        double periodicity;             // 周期性
        double autocorrelation;         // 自相关系数
        int stepCount;                  // 阶跃次数
        double trendSlope;              // 趋势斜率
        double noiseRatio;              // 噪声比例
        int zeroCrossings;              // 过零次数
        double maxAbsDerivative;        // 最大导数绝对值
    }

    /**
     * 提取信号特征
     */
    private static SignalFeatures extractFeatures(List<UniPoint> data) {
        SignalFeatures features = new SignalFeatures();
        int n = data.size();

        // 基础统计
        double sum = 0, sumSquare = 0;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;

        for (UniPoint point : data) {
            double y = point.getY().doubleValue();
            sum += y;
            sumSquare += y * y;
            min = Math.min(min, y);
            max = Math.max(max, y);
        }

        features.mean = sum / n;
        features.stdDev = Math.sqrt(Math.max(0, sumSquare / n - features.mean * features.mean));
        features.range = max - min;

        // 波动指数
        features.volatility = calculateVolatility(data, features.range);

        // 平坦度
        features.flatness = features.range < 1e-6 ? 0.0 : features.stdDev / features.range;

        // 线性度
        features.linearity = calculateLinearity(data);

        // 周期性
        features.periodicity = calculatePeriodicity(data);

        // 自相关
        features.autocorrelation = calculateAutocorrelation(data, Math.max(1, n / 4));

        // 阶跃检测
        features.stepCount = detectSteps(data);

        // 趋势斜率
        features.trendSlope = calculateTrendSlope(data);

        // 噪声比例
        features.noiseRatio = calculateNoiseRatio(data);

        // 过零次数
        features.zeroCrossings = countZeroCrossings(data, features.mean);

        // 最大导数
        features.maxAbsDerivative = calculateMaxAbsDerivative(data);

        return features;
    }

    // 波动指数
    private static double calculateVolatility(List<UniPoint> data, double range) {
        if (range < 1e-6) return 0.0;

        double totalDistance = 0;
        for (int i = 1; i < data.size(); i++) {
            double diff = Math.abs(data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue());
            totalDistance += diff;
        }

        return totalDistance / range;
    }

    // 线性度（R²拟合优度）
    private static double calculateLinearity(List<UniPoint> data) {
        int n = data.size();
        if (n < 3) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = data.get(i).getY().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double meanY = sumY / n;
        double denominator = n * sumX2 - sumX * sumX;

        if (Math.abs(denominator) < 1e-6) return 0.0;

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = meanY - slope * (n - 1) / 2.0;

        // 计算R²
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < n; i++) {
            double y = data.get(i).getY().doubleValue();
            double yPred = slope * i + intercept;
            ssRes += Math.pow(y - yPred, 2);
            ssTot += Math.pow(y - meanY, 2);
        }

        return ssTot < 1e-6 ? 1.0 : Math.max(0, 1 - (ssRes / ssTot));
    }

    // 周期性检测
    private static double calculatePeriodicity(List<UniPoint> data) {
        int n = data.size();
        if (n < 10) return 0.0;

        double maxCorr = 0;

        // 检测 n/10 到 n/3 长度的滞后
        int minLag = Math.max(2, n / 10);
        int maxLag = n / 3;

        for (int lag = minLag; lag < maxLag; lag += Math.max(1, (maxLag - minLag) / 20)) {
            double corr = calculateAutocorrelation(data, lag);
            maxCorr = Math.max(maxCorr, corr);
        }

        return maxCorr;
    }

    // 自相关系数
    private static double calculateAutocorrelation(List<UniPoint> data, int lag) {
        int n = data.size();
        if (lag >= n || lag <= 0) return 0.0;

        double mean = data.stream().mapToDouble(p -> p.getY().doubleValue()).average().orElse(0);

        double numerator = 0, denominator = 0;
        for (int i = 0; i < n - lag; i++) {
            double y1 = data.get(i).getY().doubleValue() - mean;
            double y2 = data.get(i + lag).getY().doubleValue() - mean;
            numerator += y1 * y2;
        }

        for (int i = 0; i < n; i++) {
            double y = data.get(i).getY().doubleValue() - mean;
            denominator += y * y;
        }

        return denominator < 1e-6 ? 0.0 : numerator / denominator;
    }

    // 阶跃检测
    private static int detectSteps(List<UniPoint> data) {
        if (data.size() < 3) return 0;

        int stepCount = 0;
        List<Double> derivatives = new ArrayList<>(data.size() - 1);

        // 计算一阶导数
        for (int i = 0; i < data.size() - 1; i++) {
            double deriv = Math.abs(data.get(i + 1).getY().doubleValue() - data.get(i).getY().doubleValue());
            derivatives.add(deriv);
        }

        // 计算均值和标准差
        double mean = derivatives.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = derivatives.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        // 检测异常大的导数
        double threshold = mean + 3 * stdDev;
        for (double d : derivatives) {
            if (d > threshold && d > 0.01) { // 添加绝对阈值避免噪声
                stepCount++;
            }
        }

        return stepCount;
    }

    // 趋势斜率
    private static double calculateTrendSlope(List<UniPoint> data) {
        int n = data.size();
        if (n < 2) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = data.get(i).getY().doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = n * sumX2 - sumX * sumX;
        return Math.abs(denominator) < 1e-6 ? 0.0 : (n * sumXY - sumX * sumY) / denominator;
    }

    // 噪声比例
    private static double calculateNoiseRatio(List<UniPoint> data) {
        if (data.size() < 3) return 0.0;

        double totalChange = 0;
        double smoothChange = 0;

        for (int i = 1; i < data.size() - 1; i++) {
            double y0 = data.get(i - 1).getY().doubleValue();
            double y1 = data.get(i).getY().doubleValue();
            double y2 = data.get(i + 1).getY().doubleValue();

            double acceleration = Math.abs(y2 - 2 * y1 + y0);
            totalChange += Math.abs(y2 - y0);
            smoothChange += acceleration;
        }

        return totalChange < 1e-6 ? 0.0 : smoothChange / totalChange;
    }

    // 过零次数
    private static int countZeroCrossings(List<UniPoint> data, double baseline) {
        if (data.size() < 2) return 0;

        int count = 0;
        boolean above = data.get(0).getY().doubleValue() > baseline;

        for (int i = 1; i < data.size(); i++) {
            boolean currentAbove = data.get(i).getY().doubleValue() > baseline;
            if (currentAbove != above) {
                count++;
                above = currentAbove;
            }
        }

        return count;
    }

    // 最大导数绝对值
    private static double calculateMaxAbsDerivative(List<UniPoint> data) {
        double max = 0;
        for (int i = 1; i < data.size(); i++) {
            double diff = Math.abs(data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue());
            max = Math.max(max, diff);
        }
        return max;
    }

    // ==================== 信号分类 ====================

    /**
     * 信号类型枚举
     */
    enum SignalType {
        FLAT,           // 平稳信号
        LINEAR,         // 线性信号
        PERIODIC,       // 周期信号
        STEP,           // 阶跃信号
        NOISE,          // 噪声信号
        PULSE,          // 脉冲信号
        TREND_NOISE,    // 趋势+噪声
        COMPLEX         // 复杂信号
    }

    /**
     * 信号分类
     */
    private static SignalType classifySignal(SignalFeatures features) {
        // 1. 平稳信号
        if (features.flatness < FLATNESS_THRESHOLD) {
            return SignalType.FLAT;
        }

        // 2. 线性信号
        if (features.linearity > LINEARITY_THRESHOLD && features.noiseRatio < 0.2) {
            return SignalType.LINEAR;
        }

        // 3. 周期信号（优先级高）
        if (features.periodicity > PERIODICITY_THRESHOLD) {
            return SignalType.PERIODIC;
        }

        // 4. 阶跃信号
        if (features.stepCount > 0 && features.maxAbsDerivative > features.range * STEP_THRESHOLD) {
            return SignalType.STEP;
        }

        // 5. 脉冲信号
        if (features.stepCount > 0 && features.stepCount < 5 && features.volatility > 5) {
            return SignalType.PULSE;
        }

        // 6. 噪声信号
        if (features.volatility > 10 && features.noiseRatio > NOISE_RATIO_THRESHOLD) {
            return SignalType.NOISE;
        }

        // 7. 趋势+噪声
        if (Math.abs(features.trendSlope) > 0.01 && features.noiseRatio > 0.3) {
            return SignalType.TREND_NOISE;
        }

        // 8. 复杂信号
        return SignalType.COMPLEX;
    }

    // ==================== 算法选择 ====================

    /**
     * 降采样算法枚举
     */
    enum DownsamplingAlgorithm {
        KEEP_FIRST_LAST,    // 只保留首尾
        LTTB,               // Largest Triangle Three Buckets
        MIN_MAX,            // Min-Max
        UNIFORM,            // 均匀采样
        PEAK_DETECTION,     // 峰值检测
        ADAPTIVE_LTTB       // 自适应LTTB
    }

    /**
     * 选择降采样算法
     */
    private static DownsamplingAlgorithm selectAlgorithm(SignalType signalType, SignalFeatures features,
                                                         int inputSize, int targetSize) {
        switch (signalType) {
            case FLAT:
                return DownsamplingAlgorithm.KEEP_FIRST_LAST;

            case LINEAR:
                return targetSize < 3 ? DownsamplingAlgorithm.KEEP_FIRST_LAST : DownsamplingAlgorithm.LTTB;

            case PERIODIC:
                return DownsamplingAlgorithm.LTTB;

            case STEP:
                return DownsamplingAlgorithm.PEAK_DETECTION;

            case PULSE:
                return DownsamplingAlgorithm.PEAK_DETECTION;

            case NOISE:
                return DownsamplingAlgorithm.MIN_MAX;

            case TREND_NOISE:
                double compressionRatio = (double) inputSize / targetSize;
                return compressionRatio > 10 ? DownsamplingAlgorithm.MIN_MAX : DownsamplingAlgorithm.LTTB;

            case COMPLEX:
            default:
                return DownsamplingAlgorithm.ADAPTIVE_LTTB;
        }
    }

    // ==================== 算法实现 ====================

    /**
     * 应用选定的算法
     */
    private static List<UniPoint> applyAlgorithm(DownsamplingAlgorithm algorithm, List<UniPoint> data,
                                                 int targetCount, SignalFeatures features) {
        switch (algorithm) {
            case KEEP_FIRST_LAST:
                return keepFirstLast(data);

            case LTTB:
                return LTThreeBuckets.sorted(data, targetCount);

            case MIN_MAX:
                return MinMaxDownsampler.downsample(data, targetCount);

            case UNIFORM:
                return uniformDownsampling(data, targetCount);

            case PEAK_DETECTION:
                return peakDetectionDownsampling(data, targetCount);

            case ADAPTIVE_LTTB:
                return adaptiveLTTB(data, targetCount);

            default:
                return LTThreeBuckets.sorted(data, targetCount);
        }
    }

    // 只保留首尾
    private static List<UniPoint> keepFirstLast(List<UniPoint> data) {
        if (data.size() <= 2) return data;
        List<UniPoint> result = new ArrayList<>(2);
        result.add(data.get(0));
        result.add(data.get(data.size() - 1));
        return result;
    }

    // 均匀采样
    private static List<UniPoint> uniformDownsampling(List<UniPoint> data, int targetCount) {
        List<UniPoint> result = new ArrayList<>(targetCount);
        double step = (double) (data.size() - 1) / (targetCount - 1);

        for (int i = 0; i < targetCount; i++) {
            int index = (int) Math.round(i * step);
            if (index >= data.size()) index = data.size() - 1;
            result.add(data.get(index));
        }

        return result;
    }

    // 峰值检测降采样
    private static List<UniPoint> peakDetectionDownsampling(List<UniPoint> data, int targetCount) {
        if (data.size() <= targetCount) return data;

        // 计算所有点的重要性
        List<PointImportance> importances = new ArrayList<>();
        importances.add(new PointImportance(0, Double.MAX_VALUE)); // 首点必选

        for (int i = 1; i < data.size() - 1; i++) {
            double prev = data.get(i - 1).getY().doubleValue();
            double curr = data.get(i).getY().doubleValue();
            double next = data.get(i + 1).getY().doubleValue();

            // 二阶导数的绝对值
            double importance = Math.abs(next - 2 * curr + prev);
            importances.add(new PointImportance(i, importance));
        }

        importances.add(new PointImportance(data.size() - 1, Double.MAX_VALUE)); // 尾点必选

        // 按重要性排序
        importances.sort((a, b) -> Double.compare(b.importance, a.importance));

        // 选择top-N
        Set<Integer> selectedIndices = new HashSet<>();
        for (int i = 0; i < Math.min(targetCount, importances.size()); i++) {
            selectedIndices.add(importances.get(i).index);
        }

        // 按原始顺序返回
        List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
        Collections.sort(sortedIndices);

        List<UniPoint> result = new ArrayList<>(sortedIndices.size());
        for (int idx : sortedIndices) {
            result.add(data.get(idx));
        }

        return result;
    }

    // 自适应LTTB
    private static List<UniPoint> adaptiveLTTB(List<UniPoint> data, int targetCount) {
        int n = data.size();
        int numSegments = Math.min(10, n / 10);

        if (numSegments < 2) {
            return LTThreeBuckets.sorted(data, targetCount);
        }

        int segmentSize = n / numSegments;
        List<Double> segmentComplexity = new ArrayList<>();
        double totalComplexity = 0;

        // 计算每段复杂度
        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            List<UniPoint> segment = data.subList(start, end);

            double complexity = calculateSegmentComplexity(segment);
            segmentComplexity.add(complexity);
            totalComplexity += complexity;
        }

        // 根据复杂度分配采样点
        List<UniPoint> result = new ArrayList<>();
        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            List<UniPoint> segment = data.subList(start, end);

            int segmentTarget = (int) Math.round(targetCount * segmentComplexity.get(i) / totalComplexity);
            segmentTarget = Math.max(2, segmentTarget);

            List<UniPoint> segmentResult = LTThreeBuckets.sorted(segment, segmentTarget);

            // 避免重复
            if (!result.isEmpty() && !segmentResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), segmentResult.get(0))) {
                    segmentResult.remove(0);
                }
            }

            result.addAll(segmentResult);
        }

        return result;
    }

    // 计算分段复杂度
    private static double calculateSegmentComplexity(List<UniPoint> segment) {
        if (segment.size() < 2) return 1.0;

        double totalChange = 0;
        for (int i = 1; i < segment.size(); i++) {
            totalChange += Math.abs(segment.get(i).getY().doubleValue() - segment.get(i - 1).getY().doubleValue());
        }

        return totalChange + 1.0;
    }

    // ==================== 辅助类和方法 ====================

    static class PointImportance {
        int index;
        double importance;

        PointImportance(int index, double importance) {
            this.index = index;
            this.importance = importance;
        }
    }

    private static boolean pointsEqual(UniPoint p1, UniPoint p2) {
        return p1.getX().compareTo(p2.getX()) == 0 && p1.getY().compareTo(p2.getY()) == 0;
    }
}
