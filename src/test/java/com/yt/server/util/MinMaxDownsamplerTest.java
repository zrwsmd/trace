package com.yt.server.util;

import com.yt.server.entity.UniPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MinMaxDownsamplerTest {

    @Test
    void testDownsampleHighFrequencyData() {
        // 1. 准备数据: 创建一个类似正弦波的高频振荡数据
        List<UniPoint> points = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            // Y值在 0 到 100 之间波动
            double y = 50 + 50 * Math.sin(i * 0.5);
            points.add(new UniPoint(BigDecimal.valueOf(i), BigDecimal.valueOf(y), "sine_wave"));
        }

        // 2. 执行降采样: 目标是降到100个点
        int threshold = 100;
        List<UniPoint> result = MinMaxDownsampler.downsample(points, threshold);

        // 3. 验证结果
        // 验证返回的点数大约是100 (因为每个桶2个点，所以应该是正好100)
        assertEquals(100, result.size());

        // 验证结果保留了原始数据的最大值和最小值包络
        BigDecimal originalMaxY = points.stream().map(UniPoint::getY).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal downsampledMaxY = result.stream().map(UniPoint::getY).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        assertEquals(0, originalMaxY.compareTo(downsampledMaxY), "最大值应该被保留");

        BigDecimal originalMinY = points.stream().map(UniPoint::getY).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal downsampledMinY = result.stream().map(UniPoint::getY).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        assertEquals(0, originalMinY.compareTo(downsampledMinY), "最小值应该被保留");

        // 验证时序正确性 (X值是递增的)
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).getX().compareTo(result.get(i + 1).getX()) <= 0, "X值（时间戳）应该是升序的");
        }
    }

    @Test
    void testDataSmallerThanThreshold() {
        // 准备数据: 只有10个点
        List<UniPoint> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(new UniPoint(BigDecimal.valueOf(i), BigDecimal.valueOf(i), "linear"));
        }

        // 执行降采样: 目标是100个点，远大于数据量
        int threshold = 100;
        List<UniPoint> result = MinMaxDownsampler.downsample(points, threshold);

        // 验证结果: 应该返回原始列表
        assertEquals(10, result.size());
        assertEquals(points, result, "当数据量小于阈值时，应返回原始列表");
    }

    @Test
    void testEmptyAndNullInput() {
        // 验证空列表输入
        List<UniPoint> emptyList = new ArrayList<>();
        List<UniPoint> resultFromEmpty = MinMaxDownsampler.downsample(emptyList, 100);
        assertNotNull(resultFromEmpty);
        assertTrue(resultFromEmpty.isEmpty(), "输入为空列表时，应返回空列表");

        // 验证null输入 (当前实现会抛出NPE，这是可接受的，但更好的实践是在方法开始时检查null)
        // 这里我们期望它返回一个空列表或本身，基于您当前的代码
        List<UniPoint> resultFromNull = MinMaxDownsampler.downsample(null, 100);
        assertNotNull(resultFromNull);
        assertTrue(resultFromNull.isEmpty(), "输入为null时，应返回空列表");
    }

    @Test
    void testSinglePointInBucket() {
        // 准备数据，使得每个桶只有一个点
        List<UniPoint> points = new ArrayList<>();
        points.add(new UniPoint(BigDecimal.valueOf(1), BigDecimal.valueOf(10), "p1"));
        points.add(new UniPoint(BigDecimal.valueOf(2), BigDecimal.valueOf(20), "p1"));
        points.add(new UniPoint(BigDecimal.valueOf(3), BigDecimal.valueOf(30), "p1"));

        // 目标点数为6，每个桶产生2个点，所以有3个桶，每个桶1个点
        int threshold = 6;
        List<UniPoint> result = MinMaxDownsampler.downsample(points, threshold);

        // 在每个桶只有一个点的情况下，min和max是同一点，所以结果应该只包含这3个点
        // (因为您的代码有 `if (minPoint != maxPoint)` 判断)
        assertEquals(3, result.size());
        assertEquals(BigDecimal.valueOf(10), result.get(0).getY());
        assertEquals(BigDecimal.valueOf(20), result.get(1).getY());
        assertEquals(BigDecimal.valueOf(30), result.get(2).getY());
    }
}
