package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自适应降采样算法选择器 v5.0
 * 核心优化：
 * 1. 改进点数分配策略，确保全局分布均匀
 * 2. 增强包络保护，避免丢失关键边界点
 * 3. 优化窗口划分，使用自适应窗口大小
 * 4. 改进权重计算，避免过度稀疏
 * 5. 🔥 v5.0 新增：UNIFORM_WITH_EXTREMES 算法，确保极值点不丢失的同时保证均匀分布
 * 6. 🔥 v5.0 改进：增强全局极值保护机制
 *
 * @author 赵瑞文
 * @version 5.0
 */
public class AdaptiveDownsamplingSelector {

    public enum ExecType {
        SYNC_TYPE("sync"),
        ASYNC_TYPE("async"),
        HANDLE_DOWNDATA("handleDownData"),
        HANDLE_BIGDOWNSAMPLING("handleBigDownsampling");

        private final String code;

        ExecType(String code) {
            this.code = code;
        }

        /**
         * 获取执行类型编码
         *
         * @return 编码字符串
         */
        public String getCode() {
            return code;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveDownsamplingSelector.class);

    // ==================== 配置参数（优化后）====================
    private static final int BASE_WINDOW_SIZE = 200; // 🔥 基础窗口大小
    private static final int MIN_POINTS_FOR_ANALYSIS = 10;

    // 信号特征阈值
    private static final double FLATNESS_THRESHOLD = 0.01;
    private static final double LINEARITY_THRESHOLD = 0.95;
    private static final double PERIODICITY_THRESHOLD = 0.55;
    private static final double STEP_THRESHOLD = 0.3;
    private static final double NOISE_RATIO_THRESHOLD = 0.5;

    // 🔥 v4.0 新增：更严格的最小密度保护
    private static final double MIN_DENSITY_RATIO = 0.02; // 从1%提升到2%
    private static final double MAX_WINDOW_SPARSITY = 0.5; // 窗口最大稀疏度50%

    /**
     * 主入口：自适应降采样核心逻辑
     * <p>
     * 根据数据规模和预期点数，自动选择最优处理路径：
     * 1. 规模极小：直通返回
     * 2. 规模较小（不足分析阈值）：全局算法选择
     * 3. 规模较大且压缩比适中：基于窗口的精细化降采样（V4版本）
     * 4. 其他：全局特征识别后应用最匹配算法
     *
     * @param dataPoints  原始数据点列表
     * @param targetCount 目标点数
     * @param type        执行类型（同步/异步）
     * @return 降采样后的点列表
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount, ExecType type) {
        logger.info("Downsample {} data points {}: ", type.getCode(), dataPoints.size());
        logger.info("Downsample {} target count {}: ", type.getCode(), targetCount);
        if (CollectionUtils.isEmpty(dataPoints)) {
            return Collections.emptyList();
        }
        if (targetCount <= 0) {
            return Collections.emptyList();
        }
        // 只有一条数据直接返回就行，不用走下面的逻辑
        if (dataPoints.size() == 1 || targetCount == 1) {
            return dataPoints;
        }
        List<UniPoint> rawResult;

        if (dataPoints.size() <= targetCount + 2) {
            rawResult = new ArrayList<>(dataPoints);
        } else if (dataPoints.size() < MIN_POINTS_FOR_ANALYSIS) {
            rawResult = selectAndApplyAlgorithm(dataPoints, targetCount);
        } else if (dataPoints.size() > BASE_WINDOW_SIZE * 2 && targetCount >= BASE_WINDOW_SIZE / 2) {
            rawResult = windowBasedDownsamplingV4(dataPoints, targetCount);
        } else {
            rawResult = selectAndApplyAlgorithm(dataPoints, targetCount);
        }

        return normalizeToTargetV4(rawResult, dataPoints, targetCount);
    }

    /**
     * 基于窗口的自适应降采样（V4版本）
     * <p>
     * 核心流程：
     * 1. 计算自适应窗口大小
     * 2. 对每个窗口进行局部特征提取与信号分类
     * 3. 根据窗口复杂度（权重）动态分配采样配额
     * 4. 对每个窗口应用最匹配的局部降采样算法
     * 5. 聚合结果并确保最终点数达标
     *
     * @param dataPoints  原始数据点列表
     * @param targetCount 约束后的目标总点数
     * @return 精确符合目标点数的降采样结果
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

            if (windowData.isEmpty())
                continue;

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
                weights, windowSizes, numWindows, targetCount, totalWeightedSize);

        // 第三阶段：执行降采样
        List<UniPoint> result = new ArrayList<>(targetCount);

        for (int i = 0; i < numWindows; i++) {
            int start = i * adaptiveWindowSize;
            int end = Math.min(start + adaptiveWindowSize, totalPoints);
            List<UniPoint> windowData = dataPoints.subList(start, end);

            if (windowData.isEmpty())
                continue;

            int windowTargetCount = windowTargets[i];

            // 应用算法
            DownsamplingAlgorithm algorithm = selectAlgorithm(
                    signalTypes[i], allFeatures[i], windowData.size(), windowTargetCount);

            List<UniPoint> windowResult = applyAlgorithm(
                    algorithm, windowData, windowTargetCount, allFeatures[i]);

            // 去重边界点
            if (!result.isEmpty() && !windowResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), windowResult.get(0))) {
                    windowResult = windowResult.size() > 1 ? windowResult.subList(1, windowResult.size())
                            : Collections.emptyList();
                }
            }

            result.addAll(windowResult);
        }

        return result;
    }

    /**
     * 计算自适应窗口大小
     * <p>
     * 根据整体压缩比动态决定处理窗口的粒度。压缩倍率越高，建议窗口越大以平衡性能与效果。
     *
     * @param totalPoints 数据点总数
     * @param targetCount 目标压缩点数
     * @return 建议的窗口步长（点数）
     */
    private static int calculateAdaptiveWindowSize(int totalPoints, int targetCount) {
        // 基于压缩比动态调整窗口大小
        double compressionRatio = (double) totalPoints / targetCount;

        int windowSize;
        if (compressionRatio < 5) {
            windowSize = BASE_WINDOW_SIZE / 2; // 低压缩：小窗口
        } else if (compressionRatio < 20) {
            windowSize = BASE_WINDOW_SIZE; // 中压缩：标准窗口
        } else {
            windowSize = BASE_WINDOW_SIZE * 2; // 高压缩：大窗口
        }

        // 确保至少有2个窗口
        windowSize = Math.min(windowSize, totalPoints / 2);
        return Math.max(50, windowSize);
    }

    /**
     * 计算窗口重要性权重
     * <p>
     * 权重计算综合考虑以下指标：
     * 1. 波动性权重（基准权重）
     * 2. 复杂度补偿（非线性和非平坦度）
     * 3. 信号类型加成（阶跃、脉冲等特殊信号大幅加码）
     * 4. 周期性奖励（保留循环特征）
     *
     * @param type     窗口数据分类
     * @param features 窗口提取的统计特征
     * @return 该窗口的相对重要性权重 (0.3 ~ 3.0+)
     */
    private static double calculateBalancedWeight(SignalType type, SignalFeatures features) {
        // 基础权重：从归一化波动率开始
        double baseWeight = features.normalizedVolatility * 1.2;

        // 🔥 关键改进：设置权重下限，避免任何窗口被过度压缩
        baseWeight = Math.max(0.3, baseWeight); // 最低30%的重要性

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
        return Math.max(0.3, Math.min(3.0, finalWeight)); // 限制在0.3-3.0之间
    }

    /**
     * 点数资源配额分配（V4版本）
     * <p>
     * 核心逻辑：
     * 1. 首轮：基于窗口加权尺寸比例进行基础分配
     * 2. 二轮：执行最小密度保护（MIN_DENSITY_RATIO = 2%），防止任何区域被掏空
     * 3. 三轮：溢出处理，如果分配总数超出限制，在保证最小值基础上按规模裁剪
     * 4. 四轮：赤字处理，如果点数不足，将剩余名额补充给高权重的关键区域
     *
     * @param weights           权重数组
     * @param windowSizes       窗口实际点数数组
     * @param numWindows        窗口总数
     * @param targetCount       期望总点数
     * @param totalWeightedSize 所有窗口加权总尺寸
     * @return 计算后的每个窗口的目标采样配额
     */
    private static int[] allocatePointsV4(
            double[] weights, int[] windowSizes, int numWindows,
            int targetCount, double totalWeightedSize) {
        int[] targets = new int[numWindows];
        int totalAllocated = 0;

        // 🔥 第一轮：基于权重的基础分配
        for (int i = 0; i < numWindows; i++) {
            if (windowSizes[i] == 0)
                continue;

            int baseAllocation = (int) Math.round(
                    targetCount * (weights[i] * windowSizes[i]) / totalWeightedSize);

            targets[i] = baseAllocation;
            totalAllocated += baseAllocation;
        }

        // 🔥 第二轮：强制最小密度保护
        for (int i = 0; i < numWindows; i++) {
            if (windowSizes[i] == 0)
                continue;

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
            for (int i = 0; i < numWindows; i++)
                indices[i] = i;
            Arrays.sort(indices, (a, b) -> Double.compare(weights[b], weights[a]));

            for (int idx : indices) {
                if (deficit <= 0)
                    break;
                if (windowSizes[idx] > 0 && targets[idx] < windowSizes[idx]) {
                    targets[idx]++;
                    deficit--;
                }
            }
        }

        return targets;
    }

    /**
     * 结果点数归一化（精确对齐目标）
     * <p>
     * 无论中间算法产生多少点，最终通过此方法确保输出规模绝对等于 targetCount：
     * 1. candidate.size == targetCount: 无需处理
     * 2. candidate.size > targetCount: 执行二次均匀裁剪
     * 3. candidate.size < targetCount: 调用智能填充策略补足缺口
     *
     * @param candidate   降采样算法生成的候选点集
     * @param original    完整的原始点集（用于补点参考）
     * @param targetCount 最终期望输出的点数规模
     * @return 长度完全符合预期的降采样结果集
     */
    private static List<UniPoint> normalizeToTargetV4(
            List<UniPoint> candidate, List<UniPoint> original, int targetCount) {
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
     * 智能数据空隙填充逻辑（V5版本）
     * <p>
     * 核心改进点（V5）：
     * 1. 空间优先：识别 X 轴上最大的时间差（Gap）
     * 2. 分布优先：在 Gap 中优先由于偏爱首尾而引入数据，改为优先采用【中点】附近的原始数据
     * 3. 均匀扩展：对于特大 Gap，支持 1/4 和 3/4 分位点的二次采样，提升视觉饱满度
     *
     * @param candidate 现有已选中的点集
     * @param original  全量备选点集
     * @param count     还需要追加补充的点数名额
     * @return 选中的填充点列表
     */
    private static List<UniPoint> fillGaps(
            List<UniPoint> candidate, List<UniPoint> original, int count) {
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

        // 🔥 v5.0 改进：在每个 gap 中选择中点位置的点
        List<UniPoint> filler = new ArrayList<>();
        Set<UniPoint> candidateSet = new HashSet<>(candidate);

        for (Gap gap : gaps) {
            if (filler.size() >= count)
                break;

            // 计算 gap 的中点位置
            double midX = (gap.x1 + gap.x2) / 2.0;

            // 在这个 gap 区间内，找到最接近中点的点
            UniPoint closestToMid = null;
            double minDistToMid = Double.MAX_VALUE;

            List<UniPoint> pointsInGap = new ArrayList<>();
            for (UniPoint point : original) {
                double x = point.getX().doubleValue();
                if (x > gap.x1 && x < gap.x2 && !candidateSet.contains(point)) {
                    pointsInGap.add(point);
                    double dist = Math.abs(x - midX);
                    if (dist < minDistToMid) {
                        minDistToMid = dist;
                        closestToMid = point;
                    }
                }
            }

            // 优先添加中点附近的点
            if (closestToMid != null) {
                filler.add(closestToMid);
                candidateSet.add(closestToMid);
            }

            // 如果 gap 很大且还有配额，可以在 gap 中均匀添加更多点
            if (filler.size() < count && pointsInGap.size() > 1) {
                // 在 gap 中均匀选择额外的点（除了已添加的中点）
                pointsInGap.remove(closestToMid);
                pointsInGap.sort(Comparator.comparing(UniPoint::getX));

                // 每个 gap 最多补充 2 个额外点（1/4 和 3/4 位置）
                int extraQuota = Math.min(2, count - filler.size());
                if (extraQuota > 0 && pointsInGap.size() >= 2) {
                    // 选择 1/4 位置
                    int idx1 = pointsInGap.size() / 4;
                    if (!candidateSet.contains(pointsInGap.get(idx1))) {
                        filler.add(pointsInGap.get(idx1));
                        candidateSet.add(pointsInGap.get(idx1));
                        extraQuota--;
                    }
                    // 选择 3/4 位置
                    if (extraQuota > 0) {
                        int idx2 = 3 * pointsInGap.size() / 4;
                        if (idx2 != idx1 && !candidateSet.contains(pointsInGap.get(idx2))) {
                            filler.add(pointsInGap.get(idx2));
                            candidateSet.add(pointsInGap.get(idx2));
                        }
                    }
                }
            }
        }

        // 如果还不够，均匀补充（从原始数据中均匀采样）
        if (filler.size() < count) {
            List<UniPoint> remaining = new ArrayList<>();
            for (UniPoint point : original) {
                if (!candidateSet.contains(point)) {
                    remaining.add(point);
                }
            }
            if (!remaining.isEmpty()) {
                // 🔥 均匀采样而不是顺序添加
                int needed = count - filler.size();
                if (remaining.size() <= needed) {
                    filler.addAll(remaining);
                } else {
                    double step = (double) remaining.size() / needed;
                    for (int i = 0; i < needed; i++) {
                        int idx = (int) Math.round(i * step);
                        if (idx >= remaining.size())
                            idx = remaining.size() - 1;
                        filler.add(remaining.get(idx));
                    }
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
     * 全局决策引擎：选择最匹配算法并应用
     * <p>
     * 处理中等规模数据或分窗处理的首选方案。
     * 读取信号类型（SignalType），并映射到预定义的专业降采样算法。
     */
    private static List<UniPoint> selectAndApplyAlgorithm(
            List<UniPoint> dataPoints, int targetCount) {
        try {
            SignalFeatures features = extractFeatures(dataPoints);
            SignalType signalType = classifySignal(features);
            DownsamplingAlgorithm algorithm = selectAlgorithm(
                    signalType, features, dataPoints.size(), targetCount);

            List<UniPoint> result = applyAlgorithm(algorithm, dataPoints, targetCount, features);

            if (logger.isDebugEnabled()) {
                logger.debug(
                        "🔍 Var: {}, Type: {}, Algo: {}, In: {}, Out: {}, NormVol: {}",
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

    /**
     * 核心特征提取引擎
     * <p>
     * 对输入段落进行全方位的数学特征画像，包括：
     * - 基础统计量：均值、标准差、极值范围
     * - 波动宏观特征：绝对波动率、归一化波动率（核心指纹）
     * - 几何形状特征：线性度（R²拟合）、趋势斜率、平坦度
     * - 信号规律识别：周期性强度、预估周期（自相关分析）
     * - 噪声/平滑分析：噪声占比（二阶导数分析）
     *
     * @param data 待分析的数据段
     * @return 封装好的 SignalFeatures 对象
     */
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
        features.trendStrength = features.range < 1e-6 ? 0.0
                : Math.min(1.0, Math.abs(trendInfo.slope) * n / (features.range + 1e-6));
        features.envelopeGrowthRatio = trendInfo.residualRange < 1e-6 ? 0.0
                : Math.min(10.0, features.range / (trendInfo.residualRange + 1e-6));

        return features;
    }

    /**
     * 计算归一化波动率（残差抖动）
     * <p>
     * 排除宏观趋势后，计算相邻点变化的平均相对振幅。
     * 该指标能有效识别"高频噪声"与"低频信号"。
     *
     * @param values 残差序列（去趋势后的序列）
     * @return 归一化波动系数
     */
    private static double calculateNormalizedVolatility(List<Double> values) {
        if (values == null || values.size() < 2)
            return 0.0;

        List<Double> normalizedDiffs = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            double y0 = values.get(i - 1);
            double y1 = values.get(i);
            double avg = (Math.abs(y0) + Math.abs(y1)) / 2.0;
            if (avg < 1e-6)
                avg = 1.0;
            normalizedDiffs.add(Math.abs(y1 - y0) / avg);
        }

        return normalizedDiffs.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    /**
     * 计算绝对路径波动率
     * <p>
     * 定义：Σ|y_i - y_{i-1}| / range
     * 反应了信号在给定时空内的"总位移"与"有效跨度"的比值。
     *
     * @param data  局部数据
     * @param range 垂直方向总跨度
     * @return 绝对波动率系数
     */
    private static double calculateVolatility(List<UniPoint> data, double range) {
        if (range < 1e-6)
            return 0.0;
        double totalDistance = 0;
        for (int i = 1; i < data.size(); i++) {
            totalDistance += Math.abs(
                    data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue());
        }
        return totalDistance / range;
    }

    /**
     * 计算线性趋势信息
     * <p>
     * 使用最小二乘法拟合 y = ax + b。
     * 提取出的趋势斜率（slope）用于判定信号漂移，残差（residuals）用于后续的微观特征分析。
     *
     * @param data 原始数据段
     * @return 包含斜率、截距及残差统计的 TrendInfo 对象
     */
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

    /**
     * 信号周期性捕捉引擎
     * <p>
     * 通过扫描不同时长（Lag）下的自相关系数来推断信号是否有重复模式。
     * 广泛应用于识别正弦波、方波等周期性物理量。
     *
     * @param values 分析序列
     * @return 包含周期强度和最短周期的 PeriodInfo
     */
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

    /**
     * 信号标准化预处理
     * <p>
     * 将数据转换为零均值（Zero-mean）和单位方差。
     * 这是执行高精度统计关联分析（如周期性检测）的必要前置步骤。
     */
    private static List<Double> normalizeSignal(List<Double> values) {
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev < 1e-6) {
            List<Double> normalized = new ArrayList<>(values.size());
            for (Double value : values)
                normalized.add(value - mean);
            return normalized;
        }

        List<Double> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            double normValue = (value - mean) / stdDev;
            normalized.add(Math.max(-10.0, Math.min(10.0, normValue)));
        }
        return normalized;
    }

    /**
     * 计算基于标准化的自相关系数（Lag correlation）
     */
    private static double calculateAutocorrelationNormalized(List<Double> normalized, int lag) {
        int n = normalized.size();
        if (lag >= n || lag <= 0)
            return 0.0;
        double sum = 0;
        for (int i = 0; i < n - lag; i++) {
            sum += normalized.get(i) * normalized.get(i + lag);
        }
        return sum / (n - lag);
    }

    /**
     * 计算信号线性度（R²）
     * <p>
     * 判定该段数据是否更符合一条笔直的斜线。
     *
     * @return 决定系数 R² (0~1)
     */
    private static double calculateLinearity(List<UniPoint> data) {
        int n = data.size();
        if (n < 3)
            return 0.0;

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
        if (Math.abs(denominator) < 1e-6)
            return 0.0;

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

    /**
     * 全局自相关分析
     */
    private static double calculateAutocorrelation(List<UniPoint> data, int lag) {
        int n = data.size();
        if (lag >= n || lag <= 0)
            return 0.0;

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

    /**
     * 阶跃检测
     * <p>
     * 通过寻找超出统计阈值的局部一阶导数（变化量），识别信号中的突变点。
     * 常用于捕捉开关量变化或传感器故障。
     */
    private static int detectSteps(List<UniPoint> data) {
        if (data.size() < 3)
            return 0;

        List<Double> derivatives = new ArrayList<>();
        for (int i = 0; i < data.size() - 1; i++) {
            derivatives.add(Math.abs(
                    data.get(i + 1).getY().doubleValue() - data.get(i).getY().doubleValue()));
        }

        double mean = derivatives.stream().mapToDouble(d -> d).average().orElse(0);
        double variance = derivatives.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double threshold = mean + 3 * stdDev;

        int count = 0;
        for (double d : derivatives) {
            if (d > threshold && d > 0.01)
                count++;
        }
        return count;
    }

    /**
     * 计算噪声占比分析
     * <p>
     * 基于二阶导数（曲率）与总变化的比例。
     * 比例越高，说明信号的随机抖动成分越重，物理规律越不明显。
     */
    private static double calculateNoiseRatio(List<UniPoint> data) {
        if (data.size() < 3)
            return 0.0;

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

    /**
     * 计算过零率（围绕均值的交越频率）
     * <p>
     * 反应信号的中心频率特性。如果是振荡信号，过零率会显著高于漂移信号。
     */
    private static int countZeroCrossings(List<UniPoint> data, double baseline) {
        if (data.size() < 2)
            return 0;

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

    /**
     * 计算最大瞬时变化率
     */
    private static double calculateMaxAbsDerivative(List<UniPoint> data) {
        double max = 0;
        for (int i = 1; i < data.size(); i++) {
            double diff = Math.abs(
                    data.get(i).getY().doubleValue() - data.get(i - 1).getY().doubleValue());
            max = Math.max(max, diff);
        }
        return max;
    }

    // ==================== 信号分类 ====================

    enum SignalType {
        FLAT, LINEAR, PERIODIC, AMPLITUDE_MODULATED, STEP, NOISE, PULSE, TREND_NOISE, COMPLEX
    }

    /**
     * 信号语义分类逻辑
     * <p>
     * 基于提取的多维特征，将信号映射到具体的物理/逻辑类别：
     * - FLAT: 平坦信号（逻辑值或死区数据）
     * - LINEAR: 线性变换（恒定斜率）
     * - PERIODIC: 周期振荡（正弦、方波等）
     * - STEP/PULSE: 规则突变
     * - NOISE: 纯随机抖动
     * - COMPLEX/TREND_NOISE: 复杂复合信号
     */
    private static SignalType classifySignal(SignalFeatures features) {
        if (features.flatness < FLATNESS_THRESHOLD)
            return SignalType.FLAT;
        if (features.linearity > LINEARITY_THRESHOLD && features.noiseRatio < 0.2)
            return SignalType.LINEAR;
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
        KEEP_FIRST_LAST, LTTB, MIN_MAX, UNIFORM, PEAK_DETECTION, ADAPTIVE_LTTB, HYBRID_ENVELOPE, UNIFORM_WITH_EXTREMES
    }

    /**
     * 算法选择路由矩阵
     * <p>
     * 逻辑分层：
     * 1. 极致压缩保护（FLAT）：首尾保留
     * 2. 高压缩比场景（Compression > 10）：优先包络保护算法
     * 3. 低压缩比场景：优先视觉几何保真算法
     *
     * @param signalType 识别出的信号分类
     * @param features   详细统计特征
     * @param inputSize  输入规模
     * @param targetSize 目标规模
     * @return 最优降采样算法类型
     */
    private static DownsamplingAlgorithm selectAlgorithm(
            SignalType signalType, SignalFeatures features, int inputSize, int targetSize) {
        double compression = (double) inputSize / targetSize;

        if (features.flatness < FLATNESS_THRESHOLD) {
            return DownsamplingAlgorithm.KEEP_FIRST_LAST;
        }

        // 🔥 v5.0 改进：高压缩比情况下的算法选择
        if (compression > 10.0) {
            if (signalType == SignalType.PERIODIC || signalType == SignalType.AMPLITUDE_MODULATED) {
                return DownsamplingAlgorithm.HYBRID_ENVELOPE;
            }
            // 🔥 v5.0：高压缩比下的噪声/复杂数据使用 UNIFORM_WITH_EXTREMES 确保均匀分布
            if (signalType == SignalType.NOISE || signalType == SignalType.TREND_NOISE
                    || signalType == SignalType.COMPLEX) {
                return DownsamplingAlgorithm.UNIFORM_WITH_EXTREMES;
            }
            return (features.linearity > 0.99) ? DownsamplingAlgorithm.LTTB : DownsamplingAlgorithm.MIN_MAX;
        }

        switch (signalType) {
            case PERIODIC:
            case AMPLITUDE_MODULATED:
                return DownsamplingAlgorithm.HYBRID_ENVELOPE;
            case NOISE:
            case TREND_NOISE:
                // 🔥 v5.0：噪声型数据使用 UNIFORM_WITH_EXTREMES，确保极值不丢失且分布均匀
                return DownsamplingAlgorithm.UNIFORM_WITH_EXTREMES;
            case COMPLEX:
                return DownsamplingAlgorithm.ADAPTIVE_LTTB;
            case STEP:
            case PULSE:
                return DownsamplingAlgorithm.PEAK_DETECTION;
            case LINEAR:
            default:
                return DownsamplingAlgorithm.LTTB;
        }
    }

    /**
     * 算法执行调度器
     * <p>
     * 根据选择的算法类型，分发执行相应的具体实现函数。
     *
     * @param algorithm   目标算法
     * @param data        原始数据
     * @param targetCount 采样配额
     * @param features    预先提取的特征（供混合算法参考）
     * @return 局部降采样结果
     */
    private static List<UniPoint> applyAlgorithm(
            DownsamplingAlgorithm algorithm, List<UniPoint> data,
            int targetCount, SignalFeatures features) {
        if (data.isEmpty())
            return Collections.emptyList();
        if (data.size() <= targetCount + 2)
            return new ArrayList<>(data);
        if (targetCount < 2)
            targetCount = 2;

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
            case UNIFORM_WITH_EXTREMES:
                // 🔥 v5.0 新增：极值点保护 + 均匀分布算法
                return uniformWithExtremesDownsampling(data, targetCount);
            default:
                return LTThreeBuckets.sorted(data, targetCount);
        }
    }

    /**
     * 混合包络降采样算法
     * <p>
     * 专门针对【高压缩比】下的【周期性/振荡】信号设计。
     * 策略：
     * 1. 分配 40% 配额给 MinMax 包络（保留显示屏上的上下波动面）。
     * 2. 分配 30% 配额给中心带采样（保留信号的平均平衡态）。
     * 3. 剩余 30% 配额用于填充（LTTB 或均匀采样）。
     */
    private static List<UniPoint> hybridEnvelopeDownsampling(
            List<UniPoint> data, int targetCount, SignalFeatures features) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0)
            return data;

        int safeTarget = Math.min(Math.max(targetCount, 2), data.size());
        if (safeTarget <= 5)
            return MinMaxDownsampler.downsample(data, safeTarget);

        // 🔥 v4.0 改进：增加包络点的配额
        int envelopeQuota = Math.max(4, (int) Math.round(safeTarget * 0.4)); // 从35%提升到40%
        int centerQuota = Math.max(2, (int) Math.round(safeTarget * 0.3)); // 从35%降低到30%
        int fillerQuota = Math.max(0, safeTarget - envelopeQuota - centerQuota);

        List<UniPoint> envelope = MinMaxDownsampler.downsample(data, envelopeQuota);
        if (CollectionUtils.isEmpty(envelope))
            return LTThreeBuckets.sorted(data, safeTarget);

        List<UniPoint> centralBand = sampleCentralBand(data, centerQuota);
        List<UniPoint> filler = Collections.emptyList();

        int remaining = safeTarget - envelope.size() - centralBand.size();
        if (remaining > 0) {
            boolean noisy = features != null && features.noiseRatio > NOISE_RATIO_THRESHOLD;
            filler = noisy ? LTThreeBuckets.sorted(data, Math.max(remaining, 2))
                    : uniformDownsampling(data, Math.max(remaining, 2));
        }

        LinkedHashSet<UniPoint> merged = new LinkedHashSet<>(safeTarget);
        merged.addAll(envelope);
        merged.addAll(centralBand);
        for (UniPoint point : filler) {
            if (merged.size() >= safeTarget)
                break;
            merged.add(point);
        }

        if (merged.size() < safeTarget) {
            for (UniPoint point : data) {
                if (merged.add(point) && merged.size() >= safeTarget)
                    break;
            }
        }

        List<UniPoint> mergedList = new ArrayList<>(merged);
        mergedList.sort(Comparator.comparing(UniPoint::getX));

        return mergedList.size() > safeTarget ? balancedUniformTrim(mergedList, safeTarget) : mergedList;
    }

    /**
     * 中心带采样
     * <p>
     * 在 bucket 内寻找最接近均值的点，用于刻画信号的"骨干"部分。
     */
    private static List<UniPoint> sampleCentralBand(List<UniPoint> data, int quota) {
        if (quota <= 0 || CollectionUtils.isEmpty(data))
            return Collections.emptyList();

        int bucketCount = Math.min(Math.max(1, quota * 2), data.size());
        double bucketWidth = (double) data.size() / bucketCount;

        List<UniPoint> selected = new ArrayList<>(quota);
        for (int i = 0; i < bucketCount && selected.size() < quota; i++) {
            int start = (int) Math.floor(i * bucketWidth);
            int end = (int) Math.min(data.size(), Math.round((i + 1) * bucketWidth));
            if (start >= end)
                continue;

            double sum = 0;
            for (int j = start; j < end; j++)
                sum += data.get(j).getY().doubleValue();
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

            if (closest != null)
                selected.add(closest);
        }

        if (selected.isEmpty())
            return uniformDownsampling(data, quota);
        selected.sort(Comparator.comparing(UniPoint::getX));
        return selected.size() > quota ? balancedUniformTrim(selected, quota) : selected;
    }

    /**
     * 均衡均匀裁剪
     * <p>
     * 当已有候选集点数超出预期时，通过采样方式均匀删减。
     */
    private static List<UniPoint> balancedUniformTrim(List<UniPoint> data, int targetCount) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0 || data.size() <= targetCount) {
            return data;
        }
        if (targetCount == 1)
            return Collections.singletonList(data.get(0));

        List<UniPoint> trimmed = new ArrayList<>(targetCount);
        trimmed.add(data.get(0));
        double step = (double) (data.size() - 1) / (targetCount - 1);
        double cursor = step;

        for (int i = 1; i < targetCount - 1; i++) {
            int index = (int) Math.round(cursor);
            if (index >= data.size() - 1)
                index = data.size() - 2;
            trimmed.add(data.get(index));
            cursor += step;
        }
        trimmed.add(data.get(data.size() - 1));
        return trimmed;
    }

    /**
     * * 🔥 v5.0 新增：极值点保护 + 均匀分布算法
     * 解决 NOISE/TREND_NOISE 场景下 LTTB 容易产生点聚合的问题。
     * * 核心思路：
     * * 1. 首先识别并保护全局极值点（全局最大值、全局最小值）
     * * 2. 识别局部极值点（局部峰值和谷值）
     * * 3. 剩余配额均匀分布采样
     * * 4. 合并去重并按时间排序
     * * <p>
     * * 这确保了：
     * * - 极值点（特征明显的点）永远不会丢失
     * * - 采样点在时间轴上分布均匀
     */
    private static List<UniPoint> uniformWithExtremesDownsampling(List<UniPoint> data, int targetCount) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0) {
            return Collections.emptyList();
        }
        if (data.size() <= targetCount) {
            return new ArrayList<>(data);
        }
        if (targetCount < 2) {
            return Collections.singletonList(data.get(0));
        }

        // ========== 第一步：识别全局极值点 ==========
        UniPoint globalMin = data.get(0);
        UniPoint globalMax = data.get(0);
        int globalMinIdx = 0;
        int globalMaxIdx = 0;

        for (int i = 1; i < data.size(); i++) {
            double y = data.get(i).getY().doubleValue();
            if (y < globalMin.getY().doubleValue()) {
                globalMin = data.get(i);
                globalMinIdx = i;
            }
            if (y > globalMax.getY().doubleValue()) {
                globalMax = data.get(i);
                globalMaxIdx = i;
            }
        }

        // ========== 第二步：识别局部极值点 ==========
        // 根据目标点数动态调整局部极值的配额（约占15%）
        int localExtremeQuota = Math.max(2, (int) Math.round(targetCount * 0.15));
        List<PointImportance> localExtremes = new ArrayList<>();

        for (int i = 1; i < data.size() - 1; i++) {
            double prev = data.get(i - 1).getY().doubleValue();
            double curr = data.get(i).getY().doubleValue();
            double next = data.get(i + 1).getY().doubleValue();

            // 局部极大值
            if (curr > prev && curr > next) {
                double prominence = Math.min(curr - prev, curr - next);
                localExtremes.add(new PointImportance(i, prominence));
            }
            // 局部极小值
            else if (curr < prev && curr < next) {
                double prominence = Math.min(prev - curr, next - curr);
                localExtremes.add(new PointImportance(i, prominence));
            }
        }

        // 按显著性排序，取最重要的局部极值
        localExtremes.sort((a, b) -> Double.compare(b.importance, a.importance));

        // ========== 第三步：构建必须保留的点集 ==========
        Set<Integer> mustKeepIndices = new LinkedHashSet<>();

        // 始终保留首尾点
        mustKeepIndices.add(0);
        mustKeepIndices.add(data.size() - 1);

        // 保留全局极值
        mustKeepIndices.add(globalMinIdx);
        mustKeepIndices.add(globalMaxIdx);

        // 保留最重要的局部极值（不超过配额）
        int localAdded = 0;
        for (PointImportance extreme : localExtremes) {
            if (localAdded >= localExtremeQuota)
                break;
            if (!mustKeepIndices.contains(extreme.index)) {
                mustKeepIndices.add(extreme.index);
                localAdded++;
            }
        }

        // ========== 第四步：均匀填充剩余配额 ==========
        int uniformQuota = targetCount - mustKeepIndices.size();

        if (uniformQuota > 0) {
            // 计算均匀采样的步长
            double step = (double) (data.size() - 1) / (uniformQuota + 1);

            for (int i = 1; i <= uniformQuota; i++) {
                int index = (int) Math.round(i * step);
                if (index >= data.size())
                    index = data.size() - 1;
                if (index < 0)
                    index = 0;

                // 如果这个位置已经被极值占用，尝试找附近的点
                if (mustKeepIndices.contains(index)) {
                    // 向两边搜索最近的未占用位置
                    int left = index - 1;
                    int right = index + 1;
                    while (left >= 0 || right < data.size()) {
                        if (left >= 0 && !mustKeepIndices.contains(left)) {
                            index = left;
                            break;
                        }
                        if (right < data.size() && !mustKeepIndices.contains(right)) {
                            index = right;
                            break;
                        }
                        left--;
                        right++;
                    }
                }

                mustKeepIndices.add(index);

                // 如果已经达到目标数量，停止添加
                if (mustKeepIndices.size() >= targetCount)
                    break;
            }
        }

        // ========== 第五步：构建结果并排序 ==========
        List<UniPoint> result = new ArrayList<>(mustKeepIndices.size());
        List<Integer> sortedIndices = new ArrayList<>(mustKeepIndices);
        Collections.sort(sortedIndices);

        for (int idx : sortedIndices) {
            result.add(data.get(idx));
        }

        // 如果结果过多（由于极值点较多），按均匀方式裁剪
        if (result.size() > targetCount) {
            return balancedUniformTrim(result, targetCount);
        }

        return result;
    }

    /**
     * 极致压缩：仅保留首尾
     */
    private static List<UniPoint> keepFirstLast(List<UniPoint> data) {
        if (data.size() <= 2)
            return data;
        List<UniPoint> result = new ArrayList<>(2);
        result.add(data.get(0));
        result.add(data.get(data.size() - 1));
        return result;
    }

    /**
     * 标准均匀降采样
     */
    private static List<UniPoint> uniformDownsampling(List<UniPoint> data, int targetCount) {
        if (CollectionUtils.isEmpty(data) || targetCount <= 0)
            return Collections.emptyList();
        if (targetCount >= data.size())
            return new ArrayList<>(data);
        if (targetCount == 1)
            return Collections.singletonList(data.get(data.size() / 2));

        List<UniPoint> result = new ArrayList<>(targetCount);
        double step = (double) (data.size() - 1) / (targetCount - 1);

        for (int i = 0; i < targetCount; i++) {
            int index = (int) Math.round(i * step);
            if (index >= data.size())
                index = data.size() - 1;
            result.add(data.get(index));
        }
        return result;
    }

    /**
     * 基于重要性排序的峰值检测降采样
     * <p>
     * 计算点的曲率（二阶导数）作为重要性权重，优先保留波动剧烈的点。
     */
    private static List<UniPoint> peakDetectionDownsampling(List<UniPoint> data, int targetCount) {
        if (data.size() <= targetCount)
            return data;

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
        for (int idx : sortedIndices)
            result.add(data.get(idx));
        return result;
    }

    /**
     * 自适应 LTTB 算法
     * <p>
     * 先分段计算复杂度，复杂度高的段落分配更多的 LTTB 桶（Buckets）。
     */
    private static List<UniPoint> adaptiveLTTB(List<UniPoint> data, int targetCount) {
        int n = data.size();
        int numSegments = Math.min(10, n / 10);
        if (numSegments < 2)
            return LTThreeBuckets.sorted(data, targetCount);

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

            List<UniPoint> segmentResult = segment.size() <= segmentTarget + 2 ? new ArrayList<>(segment)
                    : LTThreeBuckets.sorted(segment, segmentTarget);

            if (!result.isEmpty() && !segmentResult.isEmpty()) {
                if (pointsEqual(result.get(result.size() - 1), segmentResult.get(0))) {
                    segmentResult = segmentResult.size() > 1 ? segmentResult.subList(1, segmentResult.size())
                            : Collections.emptyList();
                }
            }

            result.addAll(segmentResult);
        }

        return result;
    }

    /**
     * 计算数据段复杂度
     */
    private static double calculateSegmentComplexity(List<UniPoint> segment) {
        if (segment.size() < 2)
            return 1.0;
        double totalChange = 0;
        for (int i = 1; i < segment.size(); i++) {
            totalChange += Math.abs(
                    segment.get(i).getY().doubleValue() - segment.get(i - 1).getY().doubleValue());
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

    /**
     * 点相等性判断（坐标值完全一致）
     */
    private static boolean pointsEqual(UniPoint p1, UniPoint p2) {
        return p1.getX().compareTo(p2.getX()) == 0 && p1.getY().compareTo(p2.getY()) == 0;
    }
}
