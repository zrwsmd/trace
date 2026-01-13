# AdaptiveDownsamplingSelector#peakDetectionDownsampling 方法详解

`com.yt.server.util.AdaptiveDownsamplingSelector#peakDetectionDownsampling` 是一个基于**特征重要性**排序的降采样算法。

它的核心思想是：**优先保留那些“弯曲程度”最大（即二阶导数绝对值最大）的点**，因为这些点通常对应着波峰、波谷或信号突变的位置，而丢弃那些在直线上或变化平缓的点。

## 核心逻辑步骤

1. **强制保留首尾**：将第一个点和最后一个点的重要性设为最大值（`Double.MAX_VALUE`），确保它们一定被选中，维持数据的时间跨度。
2. **计算重要性（曲率）**：对于中间的每一个点，计算其二阶差分（近似二阶导数）：
   $$ \text{Importance} = | \text{Next}_y - 2 \times \text{Curr}_y + \text{Prev}_y | $$
    * 物理意义：这个值反映了当前点偏离由前后两点构成的直线的程度。如果三点共线，值为0；如果是个尖峰，值会很大。
3. **排序与筛选**：将所有点按重要性从大到小排序，选取前 `targetCount` 个最重要的点。
4. **按序重组**：将选中的点按原始索引（时间顺序）重新排序，输出结果。

## 举例说明

假设我们有 5 个数据点，代表一个平稳信号中间突然出现一个脉冲，我们要将其降采样为 **3个点**。

**原始数据 (Index: Y值)**：

* **Idx 0**: `10` (起点)
* **Idx 1**: `10` (平稳)
* **Idx 2**: `50` (**突变峰值**)
* **Idx 3**: `10` (平稳)
* **Idx 4**: `10` (终点)

**目标点数**：3

### 1. 计算重要性

* **Idx 0**: 首点 $\rightarrow$ **MAX**
* **Idx 4**: 尾点 $\rightarrow$ **MAX**
* **Idx 1**: $|10(\text{Idx0}) - 2\times10(\text{Idx1}) + 50(\text{Idx2})| = |10 - 20 + 50| = |40| = $ **40**
* **Idx 2**: $|10(\text{Idx1}) - 2\times50(\text{Idx2}) + 10(\text{Idx3})| = |10 - 100 + 10| = |-80| = $ **80** (
  弯曲度最大)
* **Idx 3**: $|50(\text{Idx2}) - 2\times10(\text{Idx3}) + 10(\text{Idx4})| = |50 - 20 + 10| = |40| = $ **40**

### 2. 排序结果

1. **Idx 0** (MAX)
2. **Idx 4** (MAX)
3. **Idx 2** (Score: 80)
4. Idx 1 (Score: 40)
5. Idx 3 (Score: 40)

### 3. 筛选 Top 3

选中的索引集合为：`{0, 4, 2}`

### 4. 重组输出

按索引排序后：`0 -> 2 -> 4`
**最终结果**：保留了起点、峰值点、终点。
`[10, 50, 10]`

## 代码对应

```java
// 1. 初始化首点重要性为无穷大
importances.add(new PointImportance(0, Double.MAX_VALUE));

// 2. 遍历中间点计算二阶差分
        for(
int i = 1; i <data.

size() -1;i++){
double prev = data.get(i - 1).getY().doubleValue();
double curr = data.get(i).getY().doubleValue();
double next = data.get(i + 1).getY().doubleValue();
// 对应公式 |next - 2*curr + prev|
    importances.

add(new PointImportance(i, Math.abs(next-2*curr+prev)));
        }

// 3. 初始化尾点重要性为无穷大
        importances.

add(new PointImportance(data.size() -1,Double.MAX_VALUE));

// 4. 按重要性倒序排序
        importances.

sort((a, b) ->Double.

compare(b.importance, a.importance));

// 5. 取前 targetCount 个点的索引
Set<Integer> selectedIndices = new HashSet<>();
for(
int i = 0; i <Math.

min(targetCount, importances.size());i++){
        selectedIndices.

add(importances.get(i).index);
        }

// 6. 按原始索引顺序重组数据
List<Integer> sortedIndices = new ArrayList<>(selectedIndices);
Collections.

sort(sortedIndices);
```

## 适用场景

这种方法非常适合 **Step（阶跃）** 或 **Pulse（脉冲）** 类型的信号，因为它能极其精准地抓住信号发生突变的关键位置，而不会像普通均匀采样那样可能“漏掉”尖峰。
