package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自适应降采样算法选择器 v3.0
 * 核心改进：对振幅不敏感的周期检测，确保周期信号全局采样密度一致
 *
 * @author 赵瑞文
 * @version 3.0
 */
public class AdaptiveDownsamplingSelector {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveDownsamplingSelector.class);

    // ==================== 配置参数 ====================
    private static final int WINDOW_SIZE = 256;  // 🔥 从512减半，更细粒度
    private static final int MIN_POINTS_FOR_ANALYSIS = 10;

    // 信号特征阈值
    private static final double FLATNESS_THRESHOLD = 0.01;
    private static final double LINEARITY_THRESHOLD = 0.95;
    private static final double PERIODICITY_THRESHOLD = 0.55;  // 🔥 再降低，更容易识别
    private static final double STEP_THRESHOLD = 0.3;
    private static final double NOISE_RATIO_THRESHOLD = 0.5;

    // 🔥 周期信号特殊处理：每个周期至少保证的采样点数
    private static final int MIN_SAMPLES_PER_CYCLE = 16;  // 从12提升到16

    // 🔥 全局最小密度保护
    private static final double MIN_DENSITY_RATIO = 0.01;  // 回归到1%，通用保底

    /**
     * 主入口：自适应降采样
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount) {
        if (CollectionUtils.isEmpty(dataPoints) || dataPoints.size() <= targetCount || targetCount <= 0) {
            return dataPoints;
        }

        if (dataPoints.size() < MIN_POINTS_FOR_ANALYSIS) {
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }

        // 分窗口处理
        if (dataPoints.size() > WINDOW_SIZE * 2 && targetCount >= WINDOW_SIZE) {
            return windowBasedDownsampling(dataPoints, targetCount);
        }

        return selectAndApplyAlgorithm(dataPoints, targetCount);
    }

    /**
     * 基于窗口的降采样（核心改进）
     */
    private static List<UniPoint> windowBasedDownsampling(List<UniPoint> dataPoints, int targetCount) {
        int totalPoints = dataPoints.size();
        int numWindows = (int) Math.ceil((double) totalPoints / WINDOW_SIZE);

        // 第一阶段：分析所有窗口
        double[] normalizedWeights = new double[numWindows];
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

            // 🔥 核心改进：使用归一化权重
            double weight = calculateNormalizedWeight(type, features);

            allFeatures[i] = features;
            signalTypes[i] = type;
            normalizedWeights[i] = weight;
            totalWeightedSize += weight * windowData.size();
        }

        // 第二阶段：分发点数并执行算法
        List<UniPoint> result = new ArrayList<>(targetCount);

        for (int i = 0; i < numWindows; i++) {
            int start = i * WINDOW_SIZE;
            int end = Math.min(start + WINDOW_SIZE, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            // 基于归一化权重的点数分配
            int windowTargetCount = (int) Math.round(
                    targetCount * (normalizedWeights[i] * windowData.size()) / totalWeightedSize
            );

            // 🔥 周期信号特殊处理：保证最小采样密度
            if (signalTypes[i] == SignalType.PERIODIC || signalTypes[i] == SignalType.AMPLITUDE_MODULATED) {
                int estimatedCycles = estimateCycleCount(allFeatures[i], windowData.size());
                int minRequired = Math.max(MIN_SAMPLES_PER_CYCLE * estimatedCycles, 30);
                windowTargetCount = Math.max(windowTargetCount, minRequired);
            }

            // 🔥 v3.1新增：全局最小密度保护（防止空白区域）
            int globalMinCount = (int) Math.ceil(windowData.size() * MIN_DENSITY_RATIO);
            windowTargetCount = Math.max(windowTargetCount, globalMinCount);

            // 其他信号的安全保底
            windowTargetCount = applySafetyConstraints(
                    windowTargetCount, signalTypes[i], allFeatures[i], windowData.size()
            );

            // 应用算法
            DownsamplingAlgorithm algorithm = selectAlgorithm(
                    signalTypes[i], allFeatures[i], windowData.size(), windowTargetCount
            );
            List<UniPoint> windowResult = applyAlgorithm(
                    algorithm, windowData, windowTargetCount, allFeatures[i]
            );

            // 去重边界点
            if (!result.isEmpty() && !windowResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), windowResult.get(0))) {
                    windowResult = windowResult.size() > 1 ?
                            windowResult.subList(1, windowResult.size()) : Collections.emptyList();
                }
            }

            result.addAll(windowResult);
        }

        return result;
    }

    /**
     * 🔥 归一化权重计算 (v5.0 通用版)
     * 不再依赖单一分类，而是基于综合特征评分
     */
    private static double calculateNormalizedWeight(SignalType type, SignalFeatures features) {
        // 1. 基础重要性：波动越大，信息熵越高
        double importance = features.normalizedVolatility * 1.5;

        // 2. 形状复杂度加成：非线性的、非平坦的信号需要更多点
        double complexityBonus = (1.0 - features.linearity) * 0.5 + (1.0 - features.flatness) * 0.3;

        // 3. 突变加成：检测到阶跃或脉冲时，大幅提高优先级以保护边缘
        double spikeBonus = (type == SignalType.STEP || type == SignalType.PULSE) ? 1.5 : 0.0;

        // 4. 周期性偏置：如果是周期信号，给予一个稳定的基础权重，确保波形连续
        double periodicityBonus = (type == SignalType.PERIODIC || type == SignalType.AMPLITUDE_MODULATED)
                ? features.periodicity * 0.8
                : 0.0;

        // 综合得分，最低不低于 0.1 (FLAT)，最高不封顶
        return Math.max(0.1, importance + complexityBonus + spikeBonus + periodicityBonus);
    }

    /**
     * 🔥 估算周期数（用于保证采样密度）
     */
    private static int estimateCycleCount(SignalFeatures features, int dataSize) {
        if (features.estimatedPeriod <= 0) {
            return Math.max(1, dataSize / 50); // 保守估计
        }
        return Math.max(1, (int) Math.ceil((double) dataSize / features.estimatedPeriod));
    }

    /**
     * 🔥 安全保底约束（v3.1强化版）
     */
    private static int applySafetyConstraints(
            int count, SignalType type, SignalFeatures features, int windowSize
    ) {
        if (type == SignalType.FLAT) {
            return Math.max(2, count);
        }

        int minCount;
        if (type == SignalType.PERIODIC || type == SignalType.AMPLITUDE_MODULATED || type == SignalType.COMPLEX) {
            // 🔥 周期信号：至少 windowSize / 5（从1/8提升到1/5）
            minCount = Math.max(30, windowSize / 5);
        } else if (type == SignalType.STEP || type == SignalType.PULSE) {
            minCount = 15;  // 从10提升到15
        } else {
            minCount = 5;  // 从2提升到5
        }

        return Math.max(minCount, count);
    }

    /**
     * 选择并应用算法
     */
    private static List<UniPoint> selectAndApplyAlgorithm(
            List<UniPoint> dataPoints, int targetCount
    ) {
        try {
            SignalFeatures features = extractFeatures(dataPoints);
            SignalType signalType = classifySignal(features);
            DownsamplingAlgorithm algorithm = selectAlgorithm(
                    signalType, features, dataPoints.size(), targetCount
            );

            List<UniPoint> result = applyAlgorithm(algorithm, dataPoints, targetCount, features);

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "🔍 Var: {}, Type: {}, Algo: {}, In: {}, Out: {}, NormVol: {:.3f}, Period: {:.0f}, Periodicity: {:.2f}",
                        dataPoints.get(0).getVarName(), signalType, algorithm,
                        dataPoints.size(), result.size(),
                        features.normalizedVolatility, features.estimatedPeriod, features.periodicity
                );
            }

            return result;
        } catch (Exception e) {
            logger.warn("Adaptive downsampling failed, fallback to LTTB: {}", e.getMessage());
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }
    }

    // ==================== 信号特征提取 ====================

    /**
     * 🔥 增强的信号特征结构
     */
    static class SignalFeatures {
        double mean;
        double stdDev;
        double range;
        double volatility;              // 绝对波动率
        double normalizedVolatility;    // 🔥 归一化波动率（新增）
        double flatness;
        double linearity;
        double periodicity;
        double autocorrelation;
        int stepCount;
        double trendSlope;
        double noiseRatio;
        int zeroCrossings;
        double maxAbsDerivative;
        double estimatedPeriod;         // 🔥 估计的周期长度（新增）
        double residualStdDev;
        double detrendedRange;
        double trendStrength;
        double envelopeGrowthRatio;
    }

    static class TrendInfo {
        double slope;
        double intercept;
        List<Double> residuals = Collections.emptyList();
        double residualRange;
        double residualStdDev;
    }

    /**
     * 🔥 核心改进：特征提取
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

        TrendInfo trendInfo = calculateTrendInfo(data);

        // 🔥 核心改进：分别计算绝对和归一化波动率
        features.volatility = calculateVolatility(data, features.range);
        features.normalizedVolatility = calculateNormalizedVolatility(trendInfo.residuals);

        features.flatness = features.range < 1e-6 ? 0.0 : features.stdDev / features.range;
        features.linearity = calculateLinearity(data);

        // 🔥 核心改进：周期性检测返回周期长度
        PeriodInfo periodInfo = detectPeriodicity(trendInfo.residuals);
        features.periodicity = periodInfo.strength;
        features.estimatedPeriod = periodInfo.period;

        features.autocorrelation = calculateAutocorrelation(data, Math.max(1, n / 4));
        features.stepCount = detectSteps(data);
        features.trendSlope = trendInfo.slope;
        features.noiseRatio = calculateNoiseRatio(data);
        features.zeroCrossings = countZeroCrossings(data, features.mean);
        features.maxAbsDerivative = calculateMaxAbsDerivative(data);
        features.residualStdDev = trendInfo.residualStdDev;
        features.detrendedRange = trendInfo.residualRange;
        features.trendStrength = features.range < 1e-6 ? 0.0 :
                Math.min(1.0, Math.abs(trendInfo.slope) * n / (features.range + 1e-6));
        features.envelopeGrowthRatio = trendInfo.residualRange < 1e-6 ? 0.0 :
                Math.min(10.0, features.range / (trendInfo.residualRange + 1e-6));

        return features;
    }

    /**
     * 🔥 新增：归一化波动率（对振幅不敏感）
     */
    private static double calculateNormalizedVolatility(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }

        // 计算归一化一阶差分
        List<Double> normalizedDiffs = new ArrayList<>();

        for (int i = 1; i < values.size(); i++) {
            double y0 = values.get(i - 1);
            double y1 = values.get(i);

            // 避免除零
            double avg = (Math.abs(y0) + Math.abs(y1)) / 2.0;
            if (avg < 1e-6) avg = 1.0;

            double normalizedDiff = Math.abs(y1 - y0) / avg;
            normalizedDiffs.add(normalizedDiff);
        }

        // 返回归一化差分的均值
        return normalizedDiffs.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    /**
     * 原有的绝对波动率（保留用于其他判断）
     */
    private static double calculateVolatility(List<UniPoint> data, double range) {
        if (range < 1e-6) return 0.0;

        double totalDistance = 0;
        for (int i = 1; i < data.size(); i++) {
            double diff = Math.abs(
                    data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue()
            );
            totalDistance += diff;
        }

        return totalDistance / range;
    }

    private static TrendInfo calculateTrendInfo(List<UniPoint> data) {
        TrendInfo info = new TrendInfo();
        int n = data.size();
        if (n == 0) {
            info.residuals = Collections.emptyList();
            return info;
        }
        if (n == 1) {
            info.slope = 0.0;
            info.intercept = data.get(0).getY().doubleValue();
            info.residuals = Collections.singletonList(0.0);
            info.residualRange = 0.0;
            info.residualStdDev = 0.0;
            return info;
        }

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
        if (Math.abs(denominator) < 1e-6) {
            info.slope = 0.0;
            info.intercept = sumY / n;
        } else {
            info.slope = (n * sumXY - sumX * sumY) / denominator;
            info.intercept = (sumY - info.slope * sumX) / n;
        }

        List<Double> residuals = new ArrayList<>(n);
        double minResidual = Double.POSITIVE_INFINITY;
        double maxResidual = Double.NEGATIVE_INFINITY;
        double residualSum = 0.0;
        double residualSumSquare = 0.0;
        for (int i = 0; i < n; i++) {
            double fitted = info.slope * i + info.intercept;
            double residual = data.get(i).getY().doubleValue() - fitted;
            residuals.add(residual);
            minResidual = Math.min(minResidual, residual);
            maxResidual = Math.max(maxResidual, residual);
            residualSum += residual;
            residualSumSquare += residual * residual;
        }
        info.residuals = residuals;
        if (minResidual == Double.POSITIVE_INFINITY) {
            info.residualRange = 0.0;
        } else {
            info.residualRange = maxResidual - minResidual;
        }
        double meanResidual = residualSum / n;
        info.residualStdDev = Math.sqrt(Math.max(0, residualSumSquare / n - meanResidual * meanResidual));
        return info;
    }

    /**
     * 🔥 周期信息结构
     */
    static class PeriodInfo {
        double strength;    // 周期性强度 [0, 1]
        double period;      // 估计的周期长度
    }

    /**
     * 🔥 核心改进：增强的周期性检测（v3.1优化）
     * 先归一化，再做自相关，增加鲁棒性
     */
    private static PeriodInfo detectPeriodicity(List<Double> values) {
        PeriodInfo info = new PeriodInfo();
        int n = values.size();

        if (n < 10) {
            info.strength = 0.0;
            info.period = 0;
            return info;
        }

        // 🔥 归一化数据（去除振幅影响）
        List<Double> normalized = normalizeSignal(values);

        double maxCorr = 0;
        int bestLag = 0;

        int minLag = Math.max(2, n / 10);
        int maxLag = n / 3;
        int step = Math.max(1, (maxLag - minLag) / 40);  // 🔥 从30改为40，更精细

        for (int lag = minLag; lag < maxLag; lag += step) {
            double corr = calculateAutocorrelationNormalized(normalized, lag);
            if (corr > maxCorr) {
                maxCorr = corr;
                bestLag = lag;
            }
        }

        // 🔥 v3.1：放宽周期性判断
        // 即使自相关不是很高，只要有一定的周期性就认可
        if (maxCorr > 0.3) {  // 从隐式的更高阈值降低到0.3
            // 精细搜索最佳lag附近
            int refinedLag = refinePerio(normalized, bestLag, maxCorr);
            info.strength = maxCorr;
            info.period = refinedLag;
        } else {
            info.strength = 0.0;
            info.period = 0;
        }

        return info;
    }

    /**
     * 🔥 信号归一化（v3.1增强：更鲁棒的处理）
     */
    private static List<Double> normalizeSignal(List<Double> values) {
        double mean = values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);

        double stdDev = Math.sqrt(variance);

        if (stdDev < 1e-6) {
            List<Double> normalized = new ArrayList<>(values.size());
            for (Double value : values) {
                normalized.add(value - mean);
            }
            return normalized;
        }

        List<Double> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            double normValue = (value - mean) / stdDev;
            normValue = Math.max(-10.0, Math.min(10.0, normValue));
            normalized.add(normValue);
        }

        return normalized;
    }


    /**
     * 归一化数据的自相关
     */
    private static double calculateAutocorrelationNormalized(List<Double> normalized, int lag) {
        int n = normalized.size();
        if (lag >= n || lag <= 0) return 0.0;

        double sum = 0;
        for (int i = 0; i < n - lag; i++) {
            sum += normalized.get(i) * normalized.get(i + lag);
        }

        return sum / (n - lag);
    }

    /**
     * 精细调整周期估计
     */
    private static int refinePerio(List<Double> normalized, int initialLag, double initialCorr) {
        int bestLag = initialLag;
        double bestCorr = initialCorr;

        // 在±5范围内精细搜索
        for (int delta = -5; delta <= 5; delta++) {
            int lag = initialLag + delta;
            if (lag < 2 || lag >= normalized.size() / 2) continue;

            double corr = calculateAutocorrelationNormalized(normalized, lag);
            if (corr > bestCorr) {
                bestCorr = corr;
                bestLag = lag;
            }
        }

        return bestLag;
    }

    // 线性度（保持不变）
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

        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < n; i++) {
            double y = data.get(i).getY().doubleValue();
            double yPred = slope * i + intercept;
            ssRes += Math.pow(y - yPred, 2);
            ssTot += Math.pow(y - meanY, 2);
        }

        return ssTot < 1e-6 ? 1.0 : Math.max(0, 1 - (ssRes / ssTot));
    }

    // 自相关（原版本）
    private static double calculateAutocorrelation(List<UniPoint> data, int lag) {
        int n = data.size();
        if (lag >= n || lag <= 0) return 0.0;

        double mean = data.stream()
                .mapToDouble(p -> p.getY().doubleValue())
                .average()
                .orElse(0);

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

    // 阶跃检测（保持不变）
    private static int detectSteps(List<UniPoint> data) {
        if (data.size() < 3) return 0;

        int stepCount = 0;
        List<Double> derivatives = new ArrayList<>(data.size() - 1);

        for (int i = 0; i < data.size() - 1; i++) {
            double deriv = Math.abs(
                    data.get(i + 1).getY().doubleValue() - data.get(i).getY().doubleValue()
            );
            derivatives.add(deriv);
        }

        double mean = derivatives.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = derivatives.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);

        double threshold = mean + 3 * stdDev;
        for (double d : derivatives) {
            if (d > threshold && d > 0.01) {
                stepCount++;
            }
        }

        return stepCount;
    }

    // 噪声比例（保持不变）
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

    // 过零次数（保持不变）
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

    // 最大导数（保持不变）
    private static double calculateMaxAbsDerivative(List<UniPoint> data) {
        double max = 0;
        for (int i = 1; i < data.size(); i++) {
            double diff = Math.abs(
                    data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue()
            );
            max = Math.max(max, diff);
        }
        return max;
    }

    // ==================== 信号分类 ====================

    enum SignalType {
        FLAT, LINEAR, PERIODIC, AMPLITUDE_MODULATED, STEP, NOISE, PULSE, TREND_NOISE, COMPLEX
    }

    /**
     * 信号分类（保持不变）
     */
    private static SignalType classifySignal(SignalFeatures features) {
        if (features.flatness < FLATNESS_THRESHOLD) {
            return SignalType.FLAT;
        }

        if (features.linearity > LINEARITY_THRESHOLD && features.noiseRatio < 0.2) {
            return SignalType.LINEAR;
        }

        // 🔥 周期性判断优先级提高
        if (features.periodicity > PERIODICITY_THRESHOLD) {
            if (features.envelopeGrowthRatio > 1.5 && Math.abs(features.trendSlope) > 0.01) {
                return SignalType.AMPLITUDE_MODULATED;
            }
            return SignalType.PERIODIC;
        }

        if (features.stepCount > 0 &&
                features.maxAbsDerivative > features.range * STEP_THRESHOLD) {
            return SignalType.STEP;
        }

        if (features.stepCount > 0 && features.stepCount < 5 && features.volatility > 5) {
            return SignalType.PULSE;
        }

        if (features.volatility > 10 && features.noiseRatio > NOISE_RATIO_THRESHOLD) {
            return SignalType.NOISE;
        }

        if (Math.abs(features.trendSlope) > 0.01 && features.noiseRatio > 0.3) {
            return SignalType.TREND_NOISE;
        }

        return SignalType.COMPLEX;
    }

    // ==================== 算法选择与应用 ====================

    enum DownsamplingAlgorithm {
        KEEP_FIRST_LAST, LTTB, MIN_MAX, UNIFORM, PEAK_DETECTION, ADAPTIVE_LTTB
    }

    private static DownsamplingAlgorithm selectAlgorithm(
            SignalType signalType, SignalFeatures features, int inputSize, int targetSize
    ) {
        double compression = (double) inputSize / targetSize;

        // 通用策略：基于压缩比和信号复杂度决策
        if (features.flatness < FLATNESS_THRESHOLD) {
            return DownsamplingAlgorithm.KEEP_FIRST_LAST;
        }

        // 高压缩比场景 (>10)
        if (compression > 10.0) {
            // 只要不是纯线性的，都优先保证包络 (MIN_MAX)
            return (features.linearity > 0.99) ? DownsamplingAlgorithm.LTTB : DownsamplingAlgorithm.MIN_MAX;
        }

        // 中低压缩比场景
        switch (signalType) {
            case PERIODIC:
                return DownsamplingAlgorithm.ADAPTIVE_LTTB;
            case AMPLITUDE_MODULATED:
                return DownsamplingAlgorithm.MIN_MAX;
            case COMPLEX:
            case TREND_NOISE:
                // 复杂信号使用 ADAPTIVE_LTTB (它会在内部做二次分段加权)
                return DownsamplingAlgorithm.ADAPTIVE_LTTB;
            case STEP:
            case PULSE:
                return DownsamplingAlgorithm.PEAK_DETECTION;
            case LINEAR:
            default:
                return DownsamplingAlgorithm.LTTB;
        }
    }

    private static List<UniPoint> applyAlgorithm(
            DownsamplingAlgorithm algorithm, List<UniPoint> data,
            int targetCount, SignalFeatures features
    ) {
        int size = data.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        if (targetCount >= size || targetCount <= 0) {
            return new ArrayList<>(data);
        }
        if (targetCount < 2) {
            targetCount = 2;
        }
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

    // ==================== 具体算法实现（保持不变）====================

    private static List<UniPoint> keepFirstLast(List<UniPoint> data) {
        if (data.size() <= 2) return data;
        List<UniPoint> result = new ArrayList<>(2);
        result.add(data.get(0));
        result.add(data.get(data.size() - 1));
        return result;
    }

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

    private static List<UniPoint> peakDetectionDownsampling(
            List<UniPoint> data, int targetCount
    ) {
        if (data.size() <= targetCount) return data;

        List<PointImportance> importances = new ArrayList<>();
        importances.add(new PointImportance(0, Double.MAX_VALUE));

        for (int i = 1; i < data.size() - 1; i++) {
            double prev = data.get(i - 1).getY().doubleValue();
            double curr = data.get(i).getY().doubleValue();
            double next = data.get(i + 1).getY().doubleValue();

            double importance = Math.abs(next - 2 * curr + prev);
            importances.add(new PointImportance(i, importance));
        }

        importances.add(new PointImportance(data.size() - 1, Double.MAX_VALUE));

        importances.sort((a, b) -> Double.compare(b.importance, a.importance));

        Set<Integer> selectedIndices = new HashSet<>();
        for (int i = 0; i < Math.min(targetCount, importances.size()); i++) {
            selectedIndices.add(importances.get(i).index);
        }

        List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
        Collections.sort(sortedIndices);

        List<UniPoint> result = new ArrayList<>(sortedIndices.size());
        for (int idx : sortedIndices) {
            result.add(data.get(idx));
        }

        return result;
    }

    private static List<UniPoint> adaptiveLTTB(List<UniPoint> data, int targetCount) {
        int n = data.size();
        int numSegments = Math.min(10, n / 10);

        if (numSegments < 2) {
            return LTThreeBuckets.sorted(data, targetCount);
        }

        int segmentSize = n / numSegments;
        List<Double> segmentComplexity = new ArrayList<>();
        double totalComplexity = 0;

        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            List<UniPoint> segment = data.subList(start, end);

            double complexity = calculateSegmentComplexity(segment);
            segmentComplexity.add(complexity);
            totalComplexity += complexity;
        }

        List<UniPoint> result = new ArrayList<>();
        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            List<UniPoint> segment = data.subList(start, end);

            int segmentTarget = (int) Math.round(
                    targetCount * segmentComplexity.get(i) / totalComplexity
            );
            segmentTarget = Math.max(2, segmentTarget);

            List<UniPoint> segmentResult = LTThreeBuckets.sorted(segment, segmentTarget);

            if (!result.isEmpty() && !segmentResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), segmentResult.get(0))) {
                    segmentResult = segmentResult.size() > 1 ?
                            segmentResult.subList(1, segmentResult.size()) :
                            Collections.emptyList();
                }
            }

            result.addAll(segmentResult);
        }

        return result;
    }

    private static double calculateSegmentComplexity(List<UniPoint> segment) {
        if (segment.size() < 2) return 1.0;

        double totalChange = 0;
        for (int i = 1; i < segment.size(); i++) {
            totalChange += Math.abs(
                    segment.get(i).getY().doubleValue() -
                            segment.get(i - 1).getY().doubleValue()
            );
        }

        return totalChange + 1.0;
    }

    // ==================== 辅助类 ====================

    static class PointImportance {
        int index;
        double importance;

        PointImportance(int index, double importance) {
            this.index = index;
            this.importance = importance;
        }
    }

    private static boolean pointsEqual(UniPoint p1, UniPoint p2) {
        return p1.getX().compareTo(p2.getX()) == 0 &&
                p1.getY().compareTo(p2.getY()) == 0;
    }
}
