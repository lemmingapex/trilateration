package com.lemmingapex.trilateration;

import org.hipparchus.linear.Array2DRowRealMatrix;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.SingularValueDecomposition;

/**
 * Closed-form estimate used as the optimizer's starting point.
 *
 * <p>Squaring the range equations {@code ||x - p_i||^2 = r_i^2} and subtracting their weighted mean
 * eliminates the quadratic term {@code ||x||^2}, leaving the linear system
 *
 * <pre>
 *   2 (p_i - c) . y = ||p_i - c||^2 - mean(||p_j - c||^2) - r_i^2 + mean(r_j^2),   y = x - c
 * </pre>
 *
 * where {@code c} is the weighted centroid of the anchors and the means are weighted the same way.
 * Using the mean as the reference avoids favouring any single (possibly noisy) anchor, and cancels
 * the constant bias that noise adds to every {@code r_i^2}. Each row is scaled by
 * {@code sqrt(w_i)} (with {@code w_i = 1 / sigma_i^2}), so trusted measurements dominate. The
 * system is solved in the least squares, minimum norm sense with an SVD, which also reveals the
 * rank of the anchor geometry and its flattest direction.
 *
 * <p>When the anchors span fewer than {@code n} dimensions (for example, collinear anchors in 2D)
 * the linear system only determines the component of {@code x} inside the anchors' affine hull.
 * The remaining offset {@code h} perpendicular to the hull follows from Pythagoras,
 * {@code h^2 = r_i^2 - ||y - (p_i - c)||^2}, averaged over all anchors. The point is offset by
 * {@code h} along a null-space direction, which selects one of the mirror-image solutions instead
 * of leaving the optimizer stuck on a saddle point inside the hull.
 */
final class LinearEstimator {

    /** Singular values below this fraction of the largest one are treated as zero. */
    static final double RANK_TOLERANCE = 1e-9;

    private LinearEstimator() {
    }

    /**
     * Result of the linear estimate.
     *
     * @param point             the estimated point
     * @param rank              numerical rank of the anchor geometry, between 0 and n
     * @param centroid          weighted centroid of the anchors
     * @param flattestDirection unit vector along which the anchors are least spread out (a null
     *                          direction when rank &lt; n)
     */
    record Estimate(double[] point, int rank, double[] centroid, double[] flattestDirection) {
    }

    /**
     * @param positions anchor positions
     * @param distances measured distances
     * @param weights   strictly positive weight per measurement, {@code 1 / sigma^2}
     * @return the estimate
     */
    static Estimate estimate(double[][] positions, double[] distances, double[] weights) {
        int m = positions.length;
        int n = positions[0].length;

        double weightSum = 0;
        double[] centroid = new double[n];
        for (int i = 0; i < m; i++) {
            weightSum += weights[i];
            for (int j = 0; j < n; j++) {
                centroid[j] += weights[i] * positions[i][j];
            }
        }
        for (int j = 0; j < n; j++) {
            centroid[j] /= weightSum;
        }

        double[][] q = new double[m][n];
        double[] q2 = new double[m];
        double[] r2 = new double[m];
        double meanQ2 = 0;
        double meanR2 = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                q[i][j] = positions[i][j] - centroid[j];
                q2[i] += q[i][j] * q[i][j];
            }
            r2[i] = distances[i] * distances[i];
            meanQ2 += weights[i] * q2[i];
            meanR2 += weights[i] * r2[i];
        }
        meanQ2 /= weightSum;
        meanR2 /= weightSum;

        // Pad with zero rows so the SVD always yields a full n x n V (zero rows change nothing).
        int rows = Math.max(m, n);
        double[][] a = new double[rows][n];
        double[] b = new double[rows];
        for (int i = 0; i < m; i++) {
            double rowScale = Math.sqrt(weights[i]);
            for (int j = 0; j < n; j++) {
                a[i][j] = rowScale * 2 * q[i][j];
            }
            b[i] = rowScale * (q2[i] - meanQ2 - r2[i] + meanR2);
        }

        SingularValueDecomposition svd = new SingularValueDecomposition(new Array2DRowRealMatrix(a, false));
        double[] s = svd.getSingularValues();
        RealMatrix u = svd.getU();
        RealMatrix v = svd.getV();

        int rank = 0;
        double tolerance = s[0] * RANK_TOLERANCE;
        for (double singularValue : s) {
            if (singularValue > tolerance && singularValue > 0) {
                rank++;
            }
        }

        // y = V * S^+ * U^T * b, restricted to the numerical rank.
        double[] y = new double[n];
        for (int k = 0; k < rank; k++) {
            double coefficient = 0;
            for (int i = 0; i < rows; i++) {
                coefficient += u.getEntry(i, k) * b[i];
            }
            coefficient /= s[k];
            for (int j = 0; j < n; j++) {
                y[j] += coefficient * v.getEntry(j, k);
            }
        }

        if (rank < n) {
            double h2 = 0;
            for (int i = 0; i < m; i++) {
                double d2 = 0;
                for (int j = 0; j < n; j++) {
                    double d = y[j] - q[i][j];
                    d2 += d * d;
                }
                h2 += weights[i] * (r2[i] - d2);
            }
            double h = Math.sqrt(Math.max(0, h2 / weightSum));
            for (int j = 0; j < n; j++) {
                y[j] += h * v.getEntry(j, rank);
            }
        }

        double[] point = new double[n];
        for (int j = 0; j < n; j++) {
            point[j] = centroid[j] + y[j];
        }
        return new Estimate(point, rank, centroid, v.getColumn(n - 1));
    }
}
