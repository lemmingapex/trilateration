package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinearEstimatorTest {

    @Test
    void exactForWellPosedProblem() {
        double[][] positions = {{0, 0}, {10, 0}, {0, 10}, {10, 10}};
        double[] truth = {3, 7};

        LinearEstimator.Estimate estimate = estimate(positions, distances(positions, truth));

        assertEquals(2, estimate.rank);
        assertArrayEquals(truth, estimate.point, 1e-9);
    }

    @Test
    void doesNotDependOnAnchorOrder() {
        double[][] positions = {{0, 0}, {10, 0}, {0, 10}, {10, 10}};
        double[][] reversed = {{10, 10}, {0, 10}, {10, 0}, {0, 0}};
        double[] noisy = {7.4, 9.9, 3.1, 7.7};
        double[] noisyReversed = {7.7, 3.1, 9.9, 7.4};

        assertArrayEquals(estimate(positions, noisy).point,
                estimate(reversed, noisyReversed).point, 1e-12);
    }

    @Test
    void rankRevealsCollinearAnchorsAndOffsetsOffTheLine() {
        double[][] positions = {{0, 0}, {5, 0}, {10, 0}};
        double[] truth = {3, 4};

        LinearEstimator.Estimate estimate = estimate(positions, distances(positions, truth));

        assertEquals(1, estimate.rank);
        assertEquals(3, estimate.point[0], 1e-9);
        assertEquals(4, Math.abs(estimate.point[1]), 1e-9);
    }

    @Test
    void identicalAnchorsHaveRankZero() {
        LinearEstimator.Estimate estimate = estimate(new double[][] {{1, 1}, {1, 1}}, new double[] {2, 2});

        assertEquals(0, estimate.rank);
        assertEquals(2, TrilaterationFunction.distance(new double[] {1, 1}, estimate.point), 1e-9);
    }

    @Test
    void weightsSuppressAnUnreliableMeasurement() {
        double[][] positions = {{0, 0}, {10, 0}, {0, 10}, {10, 10}};
        double[] truth = {3, 7};
        double[] distances = distances(positions, truth);
        distances[3] += 4;

        double[] unweighted = estimate(positions, distances).point;
        double[] weighted = LinearEstimator.estimate(positions, distances, new double[] {1, 1, 1, 1e-8}).point;

        assertArrayEquals(truth, weighted, 1e-3);
        assertTrue(TrilaterationFunction.distance(truth, unweighted) > 1);
    }

    @Test
    void reportsCentroidAndFlattestDirection() {
        LinearEstimator.Estimate estimate = estimate(new double[][] {{0, 0}, {50, 0}, {100, 0}}, new double[] {50, 40, 50});

        assertArrayEquals(new double[] {50, 0}, estimate.centroid, 1e-12);
        assertEquals(1, Math.abs(estimate.flattestDirection[1]), 1e-12);
    }

    private static LinearEstimator.Estimate estimate(double[][] positions, double[] distances) {
        double[] weights = new double[positions.length];
        java.util.Arrays.fill(weights, 1.0);
        return LinearEstimator.estimate(positions, distances, weights);
    }

    private static double[] distances(double[][] positions, double[] point) {
        double[] distances = new double[positions.length];
        for (int i = 0; i < positions.length; i++) {
            distances[i] = TrilaterationFunction.distance(positions[i], point);
        }
        return distances;
    }
}
