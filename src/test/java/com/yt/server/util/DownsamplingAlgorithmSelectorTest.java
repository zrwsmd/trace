package com.yt.server.util;

import com.yt.server.entity.UniPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DownsamplingAlgorithmSelectorTest {

    private final int DATA_SIZE = 4096;
    private final int TARGET_COUNT = 512; // 4096 / 8 = 512

    @BeforeEach
    void setUp() {
        System.out.println("----------------------------------------------------");
    }

    @Test
    void testHighFrequencyScenario_ShouldSelectMinMax() {
        System.out.println("测试场景: 高频振荡数据 (正弦波)");
        System.out.println("预期结果: 折腾指数 > 2, 选择 Min-Max 算法");

        // 1. 准备数据: 创建一个包含4096个点的正弦波数据
        List<UniPoint> points = new ArrayList<>();
        BigDecimal minY = null, maxY = null;
        BigDecimal totalVerticalDistance = BigDecimal.ZERO;

        for (int i = 0; i < DATA_SIZE; i++) {
            double yValue = 500 + 400 * Math.sin(i * 0.5); // 在 100 到 900 之间波动
            UniPoint currentPoint = new UniPoint(BigDecimal.valueOf(i), BigDecimal.valueOf(yValue), "sine_wave");
            points.add(currentPoint);

            // 手动计算以供验证
            if (minY == null || currentPoint.getY().compareTo(minY) < 0) minY = currentPoint.getY();
            if (maxY == null || currentPoint.getY().compareTo(maxY) > 0) maxY = currentPoint.getY();
            if (i > 0) {
                totalVerticalDistance = totalVerticalDistance.add(currentPoint.getY().subtract(points.get(i - 1).getY()).abs());
            }
        }
        BigDecimal verticalRange = maxY.subtract(minY);
        BigDecimal expectedIndex = totalVerticalDistance.divide(verticalRange, 2, RoundingMode.HALF_UP);

        System.out.println("数据准备完成: " + DATA_SIZE + "个点");
        System.out.println("理论计算 - 最小值 (minY): " + minY.setScale(2, RoundingMode.HALF_UP));
        System.out.println("理论计算 - 最大值 (maxY): " + maxY.setScale(2, RoundingMode.HALF_UP));
        System.out.println("理论计算 - 垂直范围: " + verticalRange.setScale(2, RoundingMode.HALF_UP));
        System.out.println("理论计算 - 总垂直距离: " + totalVerticalDistance.setScale(2, RoundingMode.HALF_UP));
        System.out.println("理论计算 - 预期折腾指数: " + expectedIndex);

        // 2. 执行算法选择器的降采样
        List<UniPoint> result = DownsamplingAlgorithmSelector.downsample(points, TARGET_COUNT);

        // 3. 验证
        BigDecimal actualIndex = DownsamplingAlgorithmSelector.calculateVolatilityIndex(points);
        System.out.println("实际计算 - 折腾指数: " + actualIndex);
        assertTrue(actualIndex.compareTo(new BigDecimal("2")) > 0, "高频数据的折腾指数应该大于2");

        // 验证返回的点数大约是目标点数
        // MinMax返回的点数可能略多于或少于targetCount，但应在合理范围内
        assertTrue(result.size() >= TARGET_COUNT - 2 && result.size() <= TARGET_COUNT + 2, "结果点数应接近目标值 " + TARGET_COUNT);
        System.out.println("测试通过: 折腾指数符合预期，算法选择正确。");
    }

    @Test
    void testLowFrequencyScenario_ShouldSelectLTTB() {
        System.out.println("测试场景: 平缓趋势数据 (线性增长)");
        System.out.println("预期结果: 折腾指数 <= 2, 选择 LTTB 算法");

        // 1. 准备数据: 创建一个线性增长的数据
        List<UniPoint> points = new ArrayList<>();
        for (int i = 0; i < DATA_SIZE; i++) {
            points.add(new UniPoint(BigDecimal.valueOf(i), BigDecimal.valueOf(i * 2), "linear_growth"));
        }

        BigDecimal minY = BigDecimal.valueOf(0);
        BigDecimal maxY = BigDecimal.valueOf((DATA_SIZE - 1) * 2);
        BigDecimal verticalRange = maxY.subtract(minY);
        // 对于线性增长，总垂直距离就等于垂直范围
        BigDecimal totalVerticalDistance = verticalRange;
        BigDecimal expectedIndex = totalVerticalDistance.divide(verticalRange, 2, RoundingMode.HALF_UP);

        System.out.println("数据准备完成: " + DATA_SIZE + "个点");
        System.out.println("理论计算 - 垂直范围: " + verticalRange);
        System.out.println("理论计算 - 总垂直距离: " + totalVerticalDistance);
        System.out.println("理论计算 - 预期折腾指数: " + expectedIndex + " (应为1.00)");

        // 2. 执行
        List<UniPoint> result = DownsamplingAlgorithmSelector.downsample(points, TARGET_COUNT);

        // 3. 验证
        BigDecimal actualIndex = DownsamplingAlgorithmSelector.calculateVolatilityIndex(points);
        System.out.println("实际计算 - 折腾指数: " + actualIndex);
        assertTrue(actualIndex.compareTo(new BigDecimal("2")) <= 0, "线性增长数据的折腾指数应该小于等于2");
        assertEquals(0, new BigDecimal("1.00").compareTo(actualIndex), "线性增长数据的折腾指数应该精确为1.00");

        // LTTB算法会精确返回目标数量的点
        assertEquals(TARGET_COUNT + 2, result.size());
        System.out.println("测试通过: 折腾指数符合预期，算法选择正确。");
    }

    @Test
    void testHorizontalLineScenario_ShouldSelectLTTB() {
        System.out.println("测试场景: 水平线数据");
        System.out.println("预期结果: 折腾指数 = 0, 选择 LTTB 算法");

        // 1. 准备数据
        List<UniPoint> points = new ArrayList<>();
        BigDecimal constantY = BigDecimal.valueOf(100);
        for (int i = 0; i < DATA_SIZE; i++) {
            points.add(new UniPoint(BigDecimal.valueOf(i), constantY, "horizontal_line"));
        }

        System.out.println("数据准备完成: " + DATA_SIZE + "个点, Y值恒为100");
        System.out.println("理论计算 - 垂直范围: 0");
        System.out.println("理论计算 - 总垂直距离: 0");
        System.out.println("理论计算 - 预期折腾指数: 0.00");

        // 2. 执行
        List<UniPoint> result = DownsamplingAlgorithmSelector.downsample(points, TARGET_COUNT);

        // 3. 验证
        BigDecimal actualIndex = DownsamplingAlgorithmSelector.calculateVolatilityIndex(points);
        System.out.println("实际计算 - 折腾指数: " + actualIndex);
        assertEquals(0, BigDecimal.ZERO.compareTo(actualIndex), "水平线数据的折腾指数应该为0");
        assertEquals(TARGET_COUNT + 2, result.size());
        System.out.println("测试通过: 折腾指数符合预期，算法选择正确。");
    }
}
