# PLC 变量自适应降采样策略分析

基于您提供的 `PLC_PRG` 逻辑代码以及 `AdaptiveDownsamplingSelector` (v5.0) 的算法能力，以下是针对每个变量类型的详细分析、特征识别及最佳算法推荐。

## 0. 参考 ST 代码 (PLC_PRG)

```iec-st
PROGRAM PLC_PRG
VAR
    // === 1. 原有基础变量 ===
    b0: BOOL := FALSE;          // 【矩形波/开关量】 -> 测试边缘保持
    r0: REAL := 0.0;            // 【调幅信号 (AMPLITUDE_MODULATED)】 -> 测试 HYBRID_ENVELOPE (包络保护)
    r1: REAL := 0.0;            // 【线性增 (LINEAR)】 -> 测试 LTTB
    r2: REAL := 0.0;            // 【线性减 (LINEAR)】 -> 测试 LTTB

    // === 2. 新增测试变量 ===
    r_flat: REAL := 5000.0;       // 【平坦信号 (FLAT)】
    r_periodic: REAL := 0.0;    // 【标准周期 (PERIODIC)】
    r_step: REAL := 0.0;        // 【阶跃信号 (STEP)】
    r_noise: REAL := 0.0;       // 【纯噪声 (NOISE)】 -> 🔥 测试 v5.0 新算法 UNIFORM_WITH_EXTREMES
    r_trend_noise: REAL := 0.0; // 【带趋势噪声 (TREND_NOISE)】 -> 🔥 测试 v5.0 新算法
    r_pulse: REAL := 0.0;       // 【脉冲信号 (PULSE)】
    r_complex: REAL := 0.0;     // 【复杂叠加 (COMPLEX)】

    // 辅助计数器
    counter: INT := 0;
END_VAR

// === 逻辑实现 ===

// 1. 原有逻辑
b0 := NOT(b0);
r0 := SIN(0.1 * r1) * r1; 
r1 := r1 + 5.0; 
r2 := r2 - 5.0;

// 2. FLAT: 平坦信号 -> 应该只采首尾2个点
r_flat := 5000.0; 

// 3. PERIODIC: 标准正弦波 -> 应该使用 HYBRID_ENVELOPE 保持完美波形
r_periodic := SIN(0.1 * r1) * 100.0;

// 4. STEP: 阶梯/爬坡信号 -> 应该精确保留跳变点
counter := counter + 1;
IF (counter MOD 20 = 0) THEN
    r_step := r_step + 30.0;
    IF r_step > 150.0 THEN r_step := 0.0; END_IF
END_IF

// 5. NOISE: 高噪声 (模拟随机抖动) -> 🔥 重点看是否分布均匀且有包络
// 利用两个高频互质正弦叠加模拟随机感
r_noise := SIN(r1 * 13.0) * 240.0 + COS(r1 * 7.0) * 100.0;

// 6. TREND_NOISE: 线性增长 + 噪声 -> 🔥 重点看是否是一条均匀变粗的斜线
r_trend_noise := r1 + (SIN(r1 * 23.0) * 3000.0);

// 7. PULSE: 脉冲/尖峰信号 -> 应该保留住那根“刺”
IF (counter MOD 60 = 0) THEN
    r_pulse := 50000.0; // 突变尖峰
ELSE
    r_pulse := 0.0;
END_IF

// 8. COMPLEX: 复杂叠加信号 (正弦 + 随机高频毛刺)
// 在 r_periodic 基础上叠加高频抖动
r_complex := r_periodic + (SIN(r1 * 50.0) * 1000.0);

END_PROGRAM
```

## 1. 布尔/开关量信号

### `b0`: BOOL (方波)

* **信号特征类型**: `STEP` (阶跃)
* **关键指标**:
    * `stepCount` > 0 (存在阶跃跳变)
    * `maxAbsDerivative` 极高 (边缘处导数无穷大)
    * `linearity` 低
* **推荐算法**: `PEAK_DETECTION`
* **推荐理由**: 峰值检测基于变化率（或二阶导数）。对于 0/1 开关信号，它能确保每一次状态翻转（0->1 或 1->0）都被作为一个"
  关键点"保留下来，防止因平均化或 LTTB 桶采样导致的跳变丢失。

## 2. 模拟量/数学信号

### `r_flat`: REAL (恒定值 5000.0)

* **信号特征类型**: `FLAT`
* **关键指标**:
    * `flatness` < `FLATNESS_THRESHOLD` (0.01)
    * `range` ≈ 0 (极差极小)
    * `stdDev` ≈ 0
* **推荐算法**: `KEEP_FIRST_LAST`
* **推荐理由**: 信号没有任何信息量的变化。仅存储首尾 2 个点就足以完美重构整条直线，实现极高的压缩比。

### `r1` / `r2`: REAL (纯线性增减)

* **信号特征类型**: `LINEAR`
* **关键指标**:
    * `linearity` (R²) > `LINEARITY_THRESHOLD` (0.95)
    * `noiseRatio` < 0.2
* **推荐算法**: `LTTB` (Largest-Triangle-Three-Buckets)
* **推荐理由**: LTTB 在数学上是为保持视觉趋势而生的。对于完美的斜线，它能有效地选取代表点，在视觉上维持斜率恒定，且不引入锯齿假象。

### `r_periodic`: REAL (标准正弦波)

* **信号特征类型**: `PERIODIC`
* **关键指标**:
    * `periodicity` > `PERIODICITY_THRESHOLD` (0.55)
    * 高自相关性 (Autocorrelation)
* **推荐算法**: `HYBRID_ENVELOPE`
* **推荐理由**: 普通采样（如 LTTB）在高频周期信号上容易产生混叠（摩尔纹）现象。`HYBRID_ENVELOPE` 强制保留 40% 的点用于记录
  Min/Max 包络，确保无论缩放级别如何，波形的"粗细"（振幅范围）始终视觉正确。

### `r0`: REAL (调幅正弦波)

* **信号特征类型**: `AMPLITUDE_MODULATED`
* **关键指标**:
    * `periodicity` > 0.55
    * `envelopeGrowthRatio` > 1.5 (振幅随时间变化)
* **推荐算法**: `HYBRID_ENVELOPE`
* **推荐理由**: 与标准周期信号类似，但捕捉*包络*（变化的边界）更为关键。该算法能精准锁定波峰和波谷，描绘出调幅的"葫芦"形状。

## 3. 事件/不规则信号

### `r_step`: REAL (阶梯波/锯齿)

* **信号特征类型**: `STEP`
* **关键指标**:
    * `stepCount` > 0
    * 由"瞬间剧烈变化"和"平坦保持"交替组成。
* **推荐算法**: `PEAK_DETECTION`
* **推荐理由**: 该算法优先保留重要性（重要性 = 变化率）高的点。它能精准钉住台阶的"起跳点"和"落地点"，保留锐利的边缘，而不是将其模糊成一条斜线。

### `r_pulse`: REAL (偶发尖峰)

* **信号特征类型**: `PULSE`
* **关键指标**:
    * `stepCount` 较低 (尖峰很少)
    * `volatility` 相对基线极高（突变剧烈）
* **推荐算法**: `PEAK_DETECTION`
* **推荐理由**: 一个脉冲本质上是两个极速的阶跃（上和下）。峰值检测能确保这一瞬间的极值被采样到。如果用均匀或 LTTB
  采样，若尖峰恰好落在两个采样桶之间，可能会被完全漏掉。

### `counter`: INT (溢出锯齿波)

* **信号特征类型**: `LINEAR` (局部) 或 `STEP` (全局，因溢出突变)
* **关键指标**:
    * 分段具有高 `linearity`。
    * 在溢出点 (32767 -> -32768) 具有巨大的 `maxAbsDerivative`。
* **推荐算法**: `PEAK_DETECTION` 或 `LTTB`
* **推荐理由**: 为了完美捕捉溢出瞬间的垂直跌落，`PEAK_DETECTION` 更为安全。不过由于其主体部分高度线性，`LTTB`
  通常也能表现良好，尽管垂直线可能会稍微倾斜。

## 4. 噪声/复杂信号 (v5.0 重点)

### `r_noise`: REAL (纯高频噪声)

* **信号特征类型**: `NOISE`
* **关键指标**:
    * `volatility` > 10
    * `noiseRatio` > `NOISE_RATIO_THRESHOLD` (0.5)
    * 低 `linearity` 和低 `periodicity` 稳定性。
* **推荐算法**: `UNIFORM_WITH_EXTREMES` (v5.0)
* **推荐理由**: 用 LTTB 处理纯噪声时，算法会试图寻找面积最大的三角形，导致采样点在视觉上聚集成不自然的"团块"。v5.0
  的新算法通过锁死全局极值（保留边界），并在中间进行均匀采样，能最真实地还原噪声的"密度感"和"随机感"。

### `r_trend_noise`: REAL (线性趋势 + 噪声)

* **信号特征类型**: `TREND_NOISE`
* **关键指标**:
    * `linearity` 中等 (因有趋势存在)。
    * `noiseRatio` > 0.3。
    * `trendSlope` 显著。
* **推荐算法**: `UNIFORM_WITH_EXTREMES` (v5.0)
* **推荐理由**: 类似于纯噪声，如果在一条"毛茸茸"的斜线上使用
  LTTB，线条边缘会变得参差不齐。新算法既保留了首尾和极值（确定了线条的粗细范围），又通过均匀采样保持了线性的视觉平滑度。

### `r_complex`: REAL (周期 + 噪声叠加)

* **信号特征类型**: `COMPLEX`
* **关键指标**:
    * 混合特征：有一定周期性，又有高噪声比，可能还有非线性趋势。
* **推荐算法**: `ADAPTIVE_LTTB` 或 `HYBRID_ENVELOPE`
* **推荐理由**:
    * 代码逻辑中 `COMPLEX` 默认走 `ADAPTIVE_LTTB`。它将信号分段，平稳段少采点，因噪声导致复杂度高的段多采点。
    * 如果底层周期性足够强从而被识别为 `SignalType.PERIODIC`，则会使用 `HYBRID_ENVELOPE`，这对保留带噪正弦波的轮廓也非常有效。
