package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自适应降采样算法选择器 v4.0
 * 核心优化：
 * 1. 改进点数分配策略，确保全局分布均匀
 * 2. 增强包络保护，避免丢失关键边界点
 * 3. 优化窗口划分，使用自适应窗口大小
 * 4. 改进权重计算，避免过度稀疏
 *
 * @author 赵瑞文
 * @version 4.0
 */
public class AdaptiveDownsamplingSelector {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveDownsamplingSelector.class);

    // ==================== 配置参数（优化后）====================
    private static final int BASE_WINDOW_SIZE = 200;  // 🔥 基础窗口大小
    private static final int MIN_POINTS_FOR_ANALYSIS = 10;

    // 信号特征阈值
    private static final double FLATNESS_THRESHOLD = 0.01;
    private static final double LINEARITY_THRESHOLD = 0.95;
    private static final double PERIODICITY_THRESHOLD = 0.55;
    private static final double STEP_THRESHOLD = 0.3;
    private static final double NOISE_RATIO_THRESHOLD = 0.5;

    // 🔥 v4.0 新增：更严格的最小密度保护
    private static final double MIN_DENSITY_RATIO = 0.02;  // 从1%提升到2%
    private static final double MAX_WINDOW_SPARSITY = 0.5;  // 窗口最大稀疏度50%

    /**
     * 主入口：自适应降采样
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount) {
        if (CollectionUtils.isEmpty(dataPoints)) {
            return Collections.emptyList();
        }
        if (targetCount <= 0) {
            return Collections.emptyList();
        }

        List<UniPoint> rawResult;

        if (dataPoints.size() <= targetCount + 2) {
            rawResult = new ArrayList<>(dataPoints);
        } else if (dataPoints.size() < MIN_POINTS_FOR_ANALYSIS) {
            rawResult = LTThreeBuckets.sorted(dataPoints, targetCount);
        } else if (dataPoints.size() > BASE_WINDOW_SIZE * 2 && targetCount >= BASE_WINDOW_SIZE / 2) {
            rawResult = windowBasedDownsamplingV4(dataPoints, targetCount);
        } else {
            rawResult = selectAndApplyAlgorithm(dataPoints, targetCount);
        }

        return normalizeToTargetV4(rawResult, dataPoints, targetCount);
    }

    /**
     * 🔥 v4.0 优化的窗口降采样
     * 核心改进：
     * 1. 自适应窗口大小
     * 2. 改进的权重计算（避免过度稀疏）
     * 3. 强制最小点数保护
     */
    private static List<UniPoint> windowBasedDownsamplingV4(List<UniPoint> dataPoints, int targetCount) {
        int totalPoints = dataPoints.size();

        // 🔥 自适应窗口大小：根据数据量和目标点数动态调整
        int adaptiveWindowSize = calculateAdaptiveWindowSize(totalPoints, targetCount);
        int numWindows = (int) Math.ceil((double) totalPoints / adaptiveWindowSize);

        // 第一阶段：分析所有窗口
        double[] weights = new double[numWindows];
        SignalType[] signalTypes = new SignalType[numWindows];
        SignalFeatures[] allFeatures = new SignalFeatures[numWindows];
        int[] windowSizes = new int[numWindows];

        double totalWeightedSize = 0;

        for (int i = 0; i < numWindows; i++) {
            int start = i * adaptiveWindowSize;
            int end = Math.min(start + adaptiveWindowSize, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            windowSizes[i] = windowData.size();
            SignalFeatures features = extractFeatures(windowData);
            SignalType type = classifySignal(features);

            // 🔥 v4.0 改进的权重计算
            double weight = calculateBalancedWeight(type, features);

            allFeatures[i] = features;
            signalTypes[i] = type;
            weights[i] = weight;
            totalWeightedSize += weight * windowData.size();
        }

        // 🔥 v4.0 第二阶段：改进的点数分配策略
        int[] windowTargets = allocatePointsV4(
                weights, windowSizes, numWindows, targetCount, totalWeightedSize
        );

        // 第三阶段：执行降采样
        List<UniPoint> result = new ArrayList<>(targetCount);

        for (int i = 0; i < numWindows; i++) {
            int start = i * adaptiveWindowSize;
            int end = Math.min(start + adaptiveWindowSize, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty()) continue;

            int windowTargetCount = windowTargets[i];

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
     * 🔥 v4.0 新增：计算自适应窗口大小
     */
    private static int calculateAdaptiveWindowSize(int totalPoints, int targetCount) {
        // 基于压缩比动态调整窗口大小
        double compressionRatio = (double) totalPoints / targetCount;

        int windowSize;
        if (compressionRatio < 5) {
            windowSize = BASE_WINDOW_SIZE / 2;  // 低压缩：小窗口
        } else if (compressionRatio < 20) {
            windowSize = BASE_WINDOW_SIZE;      // 中压缩：标准窗口
        } else {
            windowSize = BASE_WINDOW_SIZE * 2;  // 高压缩：大窗口
        }

        // 确保至少有2个窗口
        windowSize = Math.min(windowSize, totalPoints / 2);
        return Math.max(50, windowSize);
    }

    /**
     * 🔥 v4.0 改进的权重计算（避免过度稀疏）
     */
    private static double calculateBalancedWeight(SignalType type, SignalFeatures features) {
        // 基础权重：从归一化波动率开始
        double baseWeight = features.normalizedVolatility * 1.2;

        // 🔥 关键改进：设置权重下限，避免任何窗口被过度压缩
        baseWeight = Math.max(0.3, baseWeight);  // 最低30%的重要性

        // 复杂度加成
        double complexityBonus = (1.0 - features.linearity) * 0.3 + (1.0 - features.flatness) * 0.2;

        // 突变加成
        double spikeBonus = (type == SignalType.STEP || type == SignalType.PULSE) ? 1.0 : 0.0;

        // 周期性加成
        double periodicityBonus = (type == SignalType.PERIODIC || type == SignalType.AMPLITUDE_MODULATED)
                ? features.periodicity * 0.5
                : 0.0;

        // 🔥 综合权重，确保合理范围
        double finalWeight = baseWeight + complexityBonus + spikeBonus + periodicityBonus;
        return Math.max(0.3, Math.min(3.0, finalWeight));  // 限制在0.3-3.0之间
    }

    /**
     * 🔥 v4.0 新增：改进的点数分配算法
     * 核心改进：
     * 1. 设置每个窗口的最小点数（基于全局密度）
     * 2. 防止过度稀疏的窗口
     * 3. 多轮分配，确保公平性
     */
    private static int[] allocatePointsV4(
            double[] weights, int[] windowSizes, int numWindows,
            int targetCount, double totalWeightedSize
    ) {
        int[] targets = new int[numWindows];
        int totalAllocated = 0;

        // 🔥 第一轮：基于权重的基础分配
        for (int i = 0; i < numWindows; i++) {
            if (windowSizes[i] == 0) continue;

            int baseAllocation = (int) Math.round(
                    targetCount * (weights[i] * windowSizes[i]) / totalWeightedSize
            );

            targets[i] = baseAllocation;
            totalAllocated += baseAllocation;
        }

        // 🔥 第二轮：强制最小密度保护
        for (int i = 0; i < numWindows; i++) {
            if (windowSizes[i] == 0) continue;

            // 每个窗口至少保证2%的点
            int minPoints = Math.max(3, (int) Math.ceil(windowSizes[i] * MIN_DENSITY_RATIO));

            // 🔥 防止稀疏度过高：如果窗口很大，增加最小点数
            if (windowSizes[i] > 100) {
                minPoints = Math.max(minPoints, windowSizes[i] / 50);
            }

            if (targets[i] < minPoints) {
                int deficit = minPoints - targets[i];
                targets[i] = minPoints;
                totalAllocated += deficit;
            }
        }

        // 🔥 第三轮：如果超出目标，按比例缩减（保护最小值）
        if (totalAllocated > targetCount) {
            int excess = totalAllocated - targetCount;
            // 从点数较多的窗口中减少
            for (int i = 0; i < numWindows && excess > 0; i++) {
                int minPoints = Math.max(3, (int) Math.ceil(windowSizes[i] * MIN_DENSITY_RATIO));
                if (targets[i] > minPoints) {
                    int canReduce = targets[i] - minPoints;
                    int reduction = Math.min(canReduce, excess);
                    targets[i] -= reduction;
                    excess -= reduction;
                }
            }
        }

        // 🔥 第四轮：如果不足目标，补充到权重高的窗口
        if (totalAllocated < targetCount) {
            int deficit = targetCount - totalAllocated;
            // 按权重排序，优先补充到重要的窗口
            Integer[] indices = new Integer[numWindows];
            for (int i = 0; i < numWindows; i++) indices[i] = i;
            Arrays.sort(indices, (a, b) -> Double.compare(weights[b], weights[a]));

            for (int idx : indices) {
                if (deficit <= 0) break;
                if (windowSizes[idx] > 0 && targets[idx] < windowSizes[idx]) {
                    targets[idx]++;
                    deficit--;
                }
            }
        }

        return targets;
    }

    /**
     * 🔥 v4.0 改进的结果归一化（确保目标点数）
     */
    private static List<UniPoint> normalizeToTargetV4(
            List<UniPoint> candidate, List<UniPoint> original, int targetCount
    ) {
        if (targetCount <= 0) {
            return Collections.emptyList();
        }

        List<UniPoint> safeOriginal = CollectionUtils.isEmpty(original)
                ? Collections.emptyList()
                : original;
        List<UniPoint> safeCandidate = candidate == null ? Collections.emptyList() : candidate;

        // 如果已经达到目标，直接返回
        if (safeCandidate.size() == targetCount || safeOriginal.isEmpty()) {
            return safeCandidate;
        }

        // 如果超出目标，均匀裁剪
        if (safeCandidate.size() > targetCount) {
            return balancedUniformTrim(safeCandidate, targetCount);
        }

        // 🔥 如果不足目标，智能补充
        int missing = targetCount - safeCandidate.size();
        LinkedHashSet<UniPoint> merged = new LinkedHashSet<>(safeCandidate.size() + missing);
        merged.addAll(safeCandidate);

        if (missing > 0 && !safeOriginal.isEmpty()) {
            // 🔥 改进：优先从候选点的"空白区域"补充
            List<UniPoint> filler = fillGaps(safeCandidate, safeOriginal, missing);

            for (UniPoint point : filler) {
                merged.add(point);
                if (merged.size() >= targetCount) {
                    break;
                }
            }
        }

        // 如果还不够，均匀补充
        if (merged.size() < targetCount) {
            for (UniPoint point : safeOriginal) {
                if (merged.add(point) && merged.size() >= targetCount) {
                    break;
                }
            }
        }

        List<UniPoint> normalized = new ArrayList<>(merged);
        normalized.sort(Comparator.comparing(UniPoint::getX));

        if (normalized.size() > targetCount) {
            return balancedUniformTrim(normalized, targetCount);
        }
        return normalized;
    }

    /**
     * 🔥 v4.0 新增：填充空白区域
     * 识别候选点中间隔较大的区域，从原始数据中补充点
     */
    private static List<UniPoint> fillGaps(
            List<UniPoint> candidate, List<UniPoint> original, int count
    ) {
        if (candidate.size() < 2 || original.isEmpty()) {
            return uniformDownsampling(original, count);
        }

        // 排序候选点
        List<UniPoint> sortedCandidate = new ArrayList<>(candidate);
        sortedCandidate.sort(Comparator.comparing(UniPoint::getX));

        // 找出最大的gaps
        List<Gap> gaps = new ArrayList<>();
        for (int i = 0; i < sortedCandidate.size() - 1; i++) {
            double x1 = sortedCandidate.get(i).getX().doubleValue();
            double x2 = sortedCandidate.get(i + 1).getX().doubleValue();
            double gapSize = x2 - x1;
            gaps.add(new Gap(i, gapSize, x1, x2));
        }

        // 按gap大小排序
        gaps.sort((a, b) -> Double.compare(b.size, a.size));

        // 从最大的gaps中填充
        List<UniPoint> filler = new ArrayList<>();
        Set<UniPoint> candidateSet = new HashSet<>(candidate);

        for (Gap gap : gaps) {
            if (filler.size() >= count) break;

            // 在这个gap区间内，从原始数据中选择点
            for (UniPoint point : original) {
                double x = point.getX().doubleValue();
                if (x > gap.x1 && x < gap.x2 && !candidateSet.contains(point)) {
                    filler.add(point);
                    candidateSet.add(point);
                    if (filler.size() >= count) break;
                }
            }
        }

        // 如果还不够，均匀补充
        if (filler.size() < count) {
            for (UniPoint point : original) {
                if (!candidateSet.contains(point)) {
                    filler.add(point);
                    if (filler.size() >= count) break;
                }
            }
        }

        return filler;
    }

    static class Gap {
        int index;
        double size;
        double x1, x2;

        Gap(int index, double size, double x1, double x2) {
            this.index = index;
            this.size = size;
            this.x1 = x1;
            this.x2 = x2;
        }
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
                        "🔍 Var: {}, Type: {}, Algo: {}, In: {}, Out: {}, NormVol: {:.3f}",
                        dataPoints.get(0).getVarName(), signalType, algorithm,
                        dataPoints.size(), result.size(), features.normalizedVolatility
                );
            }

            return result;
        } catch (Exception e) {
            logger.warn("Adaptive downsampling failed, fallback to LTTB: {}", e.getMessage());
            return LTThreeBuckets.sorted(dataPoints, targetCount);
        }
    }

    // ==================== 信号特征提取（保持不变）====================

    static class SignalFeatures {
        double mean;
        double stdDev;
        double range;
        double volatility;
        double normalizedVolatility;
        double flatness;
        double linearity;
        double periodicity;
        double autocorrelation;
        int stepCount;
        double trendSlope;
        double noiseRatio;
        int zeroCrossings;
        double maxAbsDerivative;
        double estimatedPeriod;
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

    private static SignalFeatures extractFeatures(List<UniPoint> data) {
        SignalFeatures features = new SignalFeatures();
        int n = data.size();

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

        features.volatility = calculateVolatility(data, features.range);
        features.normalizedVolatility = calculateNormalizedVolatility(trendInfo.residuals);
        features.flatness = features.range < 1e-6 ? 0.0 : features.stdDev / features.range;
        features.linearity = calculateLinearity(data);

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

    private static double calculateNormalizedVolatility(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;

        List<Double> normalizedDiffs = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            double y0 = values.get(i - 1);
            double y1 = values.get(i);
            double avg = (Math.abs(y0) + Math.abs(y1)) / 2.0;
            if (avg < 1e-6) avg = 1.0;
            normalizedDiffs.add(Math.abs(y1 - y0) / avg);
        }

        return normalizedDiffs.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private static double calculateVolatility(List<UniPoint> data, double range) {
        if (range < 1e-6) return 0.0;
        double totalDistance = 0;
        for (int i = 1; i < data.size(); i++) {
            totalDistance += Math.abs(
                    data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue()
            );
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
            return info;
        }

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double y = data.get(i).getY().doubleValue();
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumX2 += i * i;
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
        double minR = Double.POSITIVE_INFINITY, maxR = Double.NEGATIVE_INFINITY;
        double rSum = 0.0, rSumSq = 0.0;

        for (int i = 0; i < n; i++) {
            double fitted = info.slope * i + info.intercept;
            double residual = data.get(i).getY().doubleValue() - fitted;
            residuals.add(residual);
            minR = Math.min(minR, residual);
            maxR = Math.max(maxR, residual);
            rSum += residual;
            rSumSq += residual * residual;
        }

        info.residuals = residuals;
        info.residualRange = minR == Double.POSITIVE_INFINITY ? 0.0 : maxR - minR;
        double meanR = rSum / n;
        info.residualStdDev = Math.sqrt(Math.max(0, rSumSq / n - meanR * meanR));

        return info;
    }

    static class PeriodInfo {
        double strength;
        double period;
    }

    private static PeriodInfo detectPeriodicity(List<Double> values) {
        PeriodInfo info = new PeriodInfo();
        if (values.size() < 10) {
            info.strength = 0.0;
            info.period = 0;
            return info;
        }

        List<Double> normalized = normalizeSignal(values);
        double maxCorr = 0;
        int bestLag = 0;
        int n = values.size();
        int minLag = Math.max(2, n / 10);
        int maxLag = n / 3;
        int step = Math.max(1, (maxLag - minLag) / 40);

        for (int lag = minLag; lag < maxLag; lag += step) {
            double corr = calculateAutocorrelationNormalized(normalized, lag);
            if (corr > maxCorr) {
                maxCorr = corr;
                bestLag = lag;
            }
        }

        if (maxCorr > 0.3) {
            info.strength = maxCorr;
            info.period = bestLag;
        } else {
            info.strength = 0.0;
            info.period = 0;
        }

        return info;
    }

    private static List<Double> normalizeSignal(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev < 1e-6) {
            List<Double> normalized = new ArrayList<>(values.size());
            for (Double value : values) normalized.add(value - mean);
            return normalized;
        }

        List<Double> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            double normValue = (value - mean) / stdDev;
            normalized.add(Math.max(-10.0, Math.min(10.0, normValue)));
        }
        return normalized;
    }

    private static double calculateAutocorrelationNormalized(List<Double> normalized, int lag) {
        int n = normalized.size();
        if (lag >= n || lag <= 0) return 0.0;
        double sum = 0;
        for (int i = 0; i < n - lag; i++) {
            sum += normalized.get(i) * normalized.get(i + lag);
        }
        return sum / (n - lag);
    }

    private static double calculateLinearity(List<UniPoint> data) {
        int n = data.size();
        if (n < 3) return 0.0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double y = data.get(i).getY().doubleValue();
            sumX += i;
            sumY += y;
            sumXY += i * y;
            sumX2 += i * i;
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

    private static int detectSteps(List<UniPoint> data) {
        if (data.size() < 3) return 0;

        List<Double> derivatives = new ArrayList<>();
        for (int i = 0; i < data.size() - 1; i++) {
            derivatives.add(Math.abs(
                    data.get(i + 1).getY().doubleValue() - data.get(i).getY().doubleValue()
            ));
        }

        double mean = derivatives.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = derivatives.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double threshold = mean + 3 * stdDev;

        int count = 0;
        for (double d : derivatives) {
            if (d > threshold && d > 0.01) count++;
        }
        return count;
    }

    private static double calculateNoiseRatio(List<UniPoint> data) {
        if (data.size() < 3) return 0.0;

        double totalChange = 0, smoothChange = 0;
        for (int i = 1; i < data.size() - 1; i++) {
            double y0 = data.get(i - 1).getY().doubleValue();
            double y1 = data.get(i).getY().doubleValue();
            double y2 = data.get(i + 1).getY().doubleValue();
            smoothChange += Math.abs(y2 - 2 * y1 + y0);
            totalChange += Math.abs(y2 - y0);
        }

        return totalChange < 1e-6 ? 0.0 : smoothChange / totalChange;
    }

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

    private static SignalType classifySignal(SignalFeatures features) {
        if (features.flatness < FLATNESS_THRESHOLD) return SignalType.FLAT;
        if (features.linearity > LINEARITY_THRESHOLD && features.noiseRatio < 0.2) return SignalType.LINEAR;
        if (features.periodicity > PERIODICITY_THRESHOLD) {
            if (features.envelopeGrowthRatio > 1.5 && Math.abs(features.trendSlope) > 0.01) {
                return SignalType.AMPLITUDE_MODULATED;
            }
            return SignalType.PERIODIC;
        }
        if (features.stepCount > 0 && features.maxAbsDerivative > features.range * STEP_THRESHOLD) {
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
        KEEP_FIRST_LAST, LTTB, MIN_MAX, UNIFORM, PEAK_DETECTION, ADAPTIVE_LTTB, HYBRID_ENVELOPE
    }

    private static DownsamplingAlgorithm selectAlgorithm(
            SignalType signalType, SignalFeatures features, int inputSize, int targetSize
    ) {
        double compression = (double) inputSize / targetSize;

        if (features.flatness < FLATNESS_THRESHOLD) {
            return DownsamplingAlgorithm.KEEP_FIRST_LAST;
        }

        if (compression > 10.0) {
            if (signalType == SignalType.PERIODIC || signalType == SignalType.AMPLITUDE_MODULATED) {
                return DownsamplingAlgorithm.HYBRID_ENVELOPE;
            }
            return (features.linearity > 0.99) ? DownsamplingAlgorithm.LTTB : DownsamplingAlgorithm.MIN_MAX;
        }

        switch (signalType) {
            case PERIODIC:
            case AMPLITUDE_MODULATED:
                return DownsamplingAlgorithm.HYBRID_ENVELOPE;
            case COMPLEX:
            case TREND_NOISE:
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
        if (data.isEmpty()) return Collections.emptyList();
        if (data.size() <= targetCount + 2) return new ArrayList<>(data);
        if (targetCount < 2) targetCount = 2;

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
            case HYBRID_ENVELOPE:
                return hybridEnvelopeDownsampling(data, targetCount, features);
            default:
                return LTThreeBuckets.sorted(data, targetCount);
        }
    }

    private static List<UniPoint> hybridEnvelopeDownsampling(
            List<UniPoint> data, int targetCount, SignalFeatures features
    ) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0) return data;

        int safeTarget = Math.min(Math.max(targetCount, 2), data.size());
        if (safeTarget <= 5) return MinMaxDownsampler.downsample(data, safeTarget);

        // 🔥 v4.0 改进：增加包络点的配额
        int envelopeQuota = Math.max(4, (int) Math.round(safeTarget * 0.4));  // 从35%提升到40%
        int centerQuota = Math.max(2, (int) Math.round(safeTarget * 0.3));    // 从35%降低到30%
        int fillerQuota = Math.max(0, safeTarget - envelopeQuota - centerQuota);

        List<UniPoint> envelope = MinMaxDownsampler.downsample(data, envelopeQuota);
        if (CollectionUtils.isEmpty(envelope)) return LTThreeBuckets.sorted(data, safeTarget);

        List<UniPoint> centralBand = sampleCentralBand(data, centerQuota);
        List<UniPoint> filler = Collections.emptyList();

        int remaining = safeTarget - envelope.size() - centralBand.size();
        if (remaining > 0) {
            boolean noisy = features != null && features.noiseRatio > NOISE_RATIO_THRESHOLD;
            filler = noisy ?
                    LTThreeBuckets.sorted(data, Math.max(remaining, 2)) :
                    uniformDownsampling(data, Math.max(remaining, 2));
        }

        LinkedHashSet<UniPoint> merged = new LinkedHashSet<>(safeTarget);
        merged.addAll(envelope);
        merged.addAll(centralBand);
        for (UniPoint point : filler) {
            if (merged.size() >= safeTarget) break;
            merged.add(point);
        }

        if (merged.size() < safeTarget) {
            for (UniPoint point : data) {
                if (merged.add(point) && merged.size() >= safeTarget) break;
            }
        }

        List<UniPoint> mergedList = new ArrayList<>(merged);
        mergedList.sort(Comparator.comparing(UniPoint::getX));

        return mergedList.size() > safeTarget ?
                balancedUniformTrim(mergedList, safeTarget) : mergedList;
    }

    private static List<UniPoint> sampleCentralBand(List<UniPoint> data, int quota) {
        if (quota <= 0 || CollectionUtils.isEmpty(data)) return Collections.emptyList();

        int bucketCount = Math.min(Math.max(1, quota * 2), data.size());
        double bucketWidth = (double) data.size() / bucketCount;

        List<UniPoint> selected = new ArrayList<>(quota);
        for (int i = 0; i < bucketCount && selected.size() < quota; i++) {
            int start = (int) Math.floor(i * bucketWidth);
            int end = (int) Math.min(data.size(), Math.round((i + 1) * bucketWidth));
            if (start >= end) continue;

            double sum = 0;
            for (int j = start; j < end; j++) sum += data.get(j).getY().doubleValue();
            double baseline = sum / (end - start);

            UniPoint closest = null;
            double bestDiff = Double.MAX_VALUE;
            for (int j = start; j < end; j++) {
                double diff = Math.abs(data.get(j).getY().doubleValue() - baseline);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    closest = data.get(j);
                }
            }

            if (closest != null) selected.add(closest);
        }

        if (selected.isEmpty()) return uniformDownsampling(data, quota);
        selected.sort(Comparator.comparing(UniPoint::getX));
        return selected.size() > quota ? balancedUniformTrim(selected, quota) : selected;
    }

    private static List<UniPoint> balancedUniformTrim(List<UniPoint> data, int targetCount) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0 || data.size() <= targetCount) {
            return data;
        }
        if (targetCount == 1) return Collections.singletonList(data.get(0));

        List<UniPoint> trimmed = new ArrayList<>(targetCount);
        trimmed.add(data.get(0));
        double step = (double) (data.size() - 1) / (targetCount - 1);
        double cursor = step;

        for (int i = 1; i < targetCount - 1; i++) {
            int index = (int) Math.round(cursor);
            if (index >= data.size() - 1) index = data.size() - 2;
            trimmed.add(data.get(index));
            cursor += step;
        }
        trimmed.add(data.get(data.size() - 1));
        return trimmed;
    }

    private static List<UniPoint> keepFirstLast(List<UniPoint> data) {
        if (data.size() <= 2) return data;
        List<UniPoint> result = new ArrayList<>(2);
        result.add(data.get(0));
        result.add(data.get(data.size() - 1));
        return result;
    }

    private static List<UniPoint> uniformDownsampling(List<UniPoint> data, int targetCount) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0) return Collections.emptyList();
        if (targetCount >= data.size()) return new ArrayList<>(data);
        if (targetCount == 1) return Collections.singletonList(data.get(data.size() / 2));

        List<UniPoint> result = new ArrayList<>(targetCount);
        double step = (double) (data.size() - 1) / (targetCount - 1);

        for (int i = 0; i < targetCount; i++) {
            int index = (int) Math.round(i * step);
            if (index >= data.size()) index = data.size() - 1;
            result.add(data.get(index));
        }
        return result;
    }

    private static List<UniPoint> peakDetectionDownsampling(List<UniPoint> data, int targetCount) {
        if (data.size() <= targetCount) return data;

        List<PointImportance> importances = new ArrayList<>();
        importances.add(new PointImportance(0, Double.MAX_VALUE));

        for (int i = 1; i < data.size() - 1; i++) {
            double prev = data.get(i - 1).getY().doubleValue();
            double curr = data.get(i).getY().doubleValue();
            double next = data.get(i + 1).getY().doubleValue();
            importances.add(new PointImportance(i, Math.abs(next - 2 * curr + prev)));
        }

        importances.add(new PointImportance(data.size() - 1, Double.MAX_VALUE));
        importances.sort((a, b) -> Double.compare(b.importance, a.importance));

        Set<Integer> selectedIndices = new HashSet<>();
        for (int i = 0; i < Math.min(targetCount, importances.size()); i++) {
            selectedIndices.add(importances.get(i).index);
        }

        List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
        Collections.sort(sortedIndices);

        List<UniPoint> result = new ArrayList<>();
        for (int idx : sortedIndices) result.add(data.get(idx));
        return result;
    }

    private static List<UniPoint> adaptiveLTTB(List<UniPoint> data, int targetCount) {
        int n = data.size();
        int numSegments = Math.min(10, n / 10);
        if (numSegments < 2) return LTThreeBuckets.sorted(data, targetCount);

        int segmentSize = n / numSegments;
        List<Double> segmentComplexity = new ArrayList<>();
        double totalComplexity = 0;

        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            double complexity = calculateSegmentComplexity(data.subList(start, end));
            segmentComplexity.add(complexity);
            totalComplexity += complexity;
        }

        List<UniPoint> result = new ArrayList<>();
        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? n : (i + 1) * segmentSize;
            List<UniPoint> segment = data.subList(start, end);

            int segmentTarget = (int) Math.round(targetCount * segmentComplexity.get(i) / totalComplexity);
            segmentTarget = Math.max(2, segmentTarget);

            List<UniPoint> segmentResult = segment.size() <= segmentTarget + 2 ?
                    new ArrayList<>(segment) : LTThreeBuckets.sorted(segment, segmentTarget);

            if (!result.isEmpty() && !segmentResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), segmentResult.get(0))) {
                    segmentResult = segmentResult.size() > 1 ?
                            segmentResult.subList(1, segmentResult.size()) : Collections.emptyList();
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
                    segment.get(i).getY().doubleValue() - segment.get(i - 1).getY().doubleValue()
            );
        }
        return totalChange + 1.0;
    }

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
