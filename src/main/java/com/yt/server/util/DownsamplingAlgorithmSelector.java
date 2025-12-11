package com.yt.server.util;

import com.ggalmazor.ltdownsampling.LTThreeBuckets;
import com.yt.server.entity.UniPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A utility class to dynamically select the appropriate downsampling algorithm
 * based on the data's characteristics, using the "Volatility Index".
 */
public class DownsamplingAlgorithmSelector {

    private static final Logger logger = LoggerFactory.getLogger(DownsamplingAlgorithmSelector.class);
    private static final BigDecimal THRESHOLD = new BigDecimal("2");

    /**
     * Downsamples a list of points by dynamically choosing between LTTB and Min-Max algorithms.
     *
     * @param dataPoints   The original list of data points.
     * @param targetCount The desired number of points after downsampling.
     * @return A new list of downsampled points.
     */
    public static List<UniPoint> downsample(List<UniPoint> dataPoints, int targetCount) {
        if (dataPoints == null || dataPoints.size() <= targetCount || targetCount <= 0) {
            return dataPoints;
        }

        BigDecimal volatilityIndex = calculateVolatilityIndex(dataPoints);

        if (volatilityIndex.compareTo(THRESHOLD) > 0) {
            logger.debug("Volatility index ({}) > threshold ({}). Using Min-Max downsampling.", volatilityIndex, THRESHOLD);
            // For Min-Max, we want targetCount/2 buckets, each producing 2 points.
            // So the 'threshold' parameter for MinMaxDownsampler should be the targetCount itself.
            return MinMaxDownsampler.downsample(dataPoints, targetCount);
        } else {
            logger.debug("Volatility index ({}) <= threshold ({}). Using LTTB downsampling.", volatilityIndex, THRESHOLD);
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
    public static BigDecimal calculateVolatilityIndex(List<UniPoint> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal minY = dataPoints.get(0).getY();
        BigDecimal maxY = dataPoints.get(0).getY();
        BigDecimal totalVerticalDistance = BigDecimal.ZERO;

        for (int i = 1; i < dataPoints.size(); i++) {
            UniPoint currentPoint = dataPoints.get(i);
            UniPoint previousPoint = dataPoints.get(i - 1);
            BigDecimal currentY = currentPoint.getY();

            if (currentY.compareTo(minY) < 0) {
                minY = currentY;
            }
            if (currentY.compareTo(maxY) > 0) {
                maxY = currentY;
            }

            totalVerticalDistance = totalVerticalDistance.add(currentY.subtract(previousPoint.getY()).abs());
        }

        BigDecimal verticalRange = maxY.subtract(minY);

        if (verticalRange.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // Horizontal line, no volatility.
        }

        return totalVerticalDistance.divide(verticalRange, 2, RoundingMode.HALF_UP);
    }
}
