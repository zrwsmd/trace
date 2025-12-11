# 动态降采样优化方案说明文档

## 1. 背景与问题

项目在使用 LTTB (Largest-Triangle-Three-Buckets) 算法对大规模时间序列数据进行降采样时，遇到了一个性能瓶颈和视觉效果问题。

当处理的信号是**高频、强周期性**的（例如正弦波），LTTB 算法的降采样结果无法很好地还原信号的包络（envelope），导致在前端图表上出现视觉失真（Aliasing），无法形成理想中“被填满的矩形”效果。其根本原因在于 LTTB 旨在保留曲线的**形态**，而不是**范围**。

为解决此问题，我们引入了一种动态的、基于数据特征的降采样策略。

## 2. 核心理念：“折腾指数” (Volatility Index)

为了让系统能自动识别数据是“趋势性”的还是“振荡性”的，我们引入了一个自定义的量化指标——“折腾指数”。

**“折腾指数”** 的核心思想是：**比较一条曲线在垂直方向上走过的总路程与其自身的垂直高度。**

-   **计算公式**:
    ```
    折腾指数 = 总垂直距离 / 垂直范围
    ```

-   **指标定义**:
    -   **总垂直距离 (Total Vertical Distance)**: 数据点 `Y` 值与前一个点 `Y` 值之差的绝对值的累加和。`Σ|y_i - y_{i-1}|`。这代表了曲线上下“折腾”的总幅度。
    -   **垂直范围 (Vertical Range)**: 数据点中最大的 `Y` 值与最小的 `Y` 值之差。`max(Y) - min(Y)`。这代表了曲线的整体高度。

-   **判断逻辑**:
    -   **高折腾指数 ( > 2.0 )**: 表明曲线在有限的垂直空间内频繁地上下移动，即“在原地做俯卧撑”。这是典型的高频振荡信号特征。
    -   **低折腾指数 ( <= 2.0 )**: 表明曲线的垂直移动总路程与其高度相近，说明其趋势性强，即“在赶路”。

## 3. 实现逻辑

### 3.1. 算法动态选择

我们设定 **2.0** 为“折腾指数”的阈值，在每次对一批数据（约4096个点）进行降采样前，执行以下判断：

-   **如果 `折腾指数 > 2.0`**:
    -   **判定**: 数据为高频振荡信号。
    -   **措施**: 调用 **Min-Max 降采样算法** (`MinMaxDownsampler.java`)。此算法通过在每个数据桶中保留最大值和最小值，完美地保留了信号的视觉包络，解决了视觉失真问题。

-   **如果 `折腾指数 <= 2.0`**:
    -   **判定**: 数据为趋势性信号。
    -   **措施**: 继续使用原有的 **LTTB 降采样算法** (`LTThreeBuckets.sorted()`)。此算法能最好地保留曲线的原始形态，适用于非高频振荡数据。

### 3.2. 性能考量

“折腾指数”的计算仅涉及对当前批次数据（4096点）的一次内存遍历，计算成本极低（微秒级），与数据库I/O和降采样本身的计算相比几乎可以忽略不计。同时，整个降采样过程是异步执行的，因此该新增计算不会对主线程和系统性能造成任何影响。

## 4. 代码改动详情

### 4.1. 主要修改文件

-   `src/main/java/com/yt/server/service/HandleWasteTimeService.java`

### 4.2. 修改位置

为了确保数据在写入降采样表的源头就被正确处理，我们在后台异步降采样的核心入口进行了修改。具体修改了以下两个方法中的 `for` 循环，以保证首次和后续追加数据的处理逻辑一致：

1.  `insertDownsamplingData` 方法
2.  `handleDownData` 方法

### 4.3. 伪代码逻辑

```java
// 在 HandleWasteTimeService.java 的 for (String varName : varNames) 循环内部

// 1. 准备当前变量的原始数据批次
List<UniPoint> originalFilterVarDataList = ... // (获取约4096个点)

// 2. 计算该批次数据的“折腾指数”
BigDecimal volatilityIndex = calculateVolatilityIndex(originalFilterVarDataList);

// 3. 设定阈值并进行判断
final BigDecimal THRESHOLD = new BigDecimal("2");
int bucketSize = originalFilterVarDataList.size() / downSamplingRate;

// 4. 根据指数动态选择降采样算法
if (volatilityIndex.compareTo(THRESHOLD) > 0) {
    // 使用 Min-Max 算法
    log.info("检测到高频信号 (指数={})，使用 Min-Max", volatilityIndex);
    singleVarDataList = MinMaxDownsampler.downsample(originalFilterVarDataList, bucketSize * 2);
} else {
    // 维持 LTTB 算法
    log.info("检测到趋势信号 (指数={})，使用 LTTB", volatilityIndex);
    singleVarDataList = LTThreeBuckets.sorted(originalFilterVarDataList, bucketSize);
}

// 5. 将选择算法后的降采样结果用于后续处理
// (包括存入8倍降采样表，以及构建更高层级的数据金字塔)
saveToDatabase(singleVarDataList, ...);
buildDownsamplingPyramid(singleVarDataList, ...);
```

## 5. 单元测试

为确保 `MinMaxDownsampler` 工具类的健壮性和正确性，我们创建了对应的单元测试：

-   **测试文件**: `src/test/java/com/yt/server/util/MinMaxDownsamplerTest.java`

-   **核心测试用例**:
    -   `testDownsampleHighFrequencyData`: 验证算法能否在高频数据下降采样后保留其最大/最小值包络。
    -   `testDataSmallerThanThreshold`: 验证当输入数据量小于目标点数时，返回原始数据。
    -   `testEmptyAndNullInput`: 验证对空或null输入的处理。
    -   `testSinglePointInBucket`: 验证在极端分桶情况下的行为。

## 6. 总结

通过本次优化，系统现在具备了根据数据自身特征动态选择最合适降采样算法的“智能”。这不仅解决了高频周期信号的视觉失真问题，也保证了趋势性信号的形态得以保留，全面提升了大规模时间序列数据的可视化质量和用户体验。
