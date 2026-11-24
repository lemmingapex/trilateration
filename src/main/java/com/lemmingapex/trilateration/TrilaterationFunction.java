package com.lemmingapex.trilateration;

import org.hipparchus.linear.Array2DRowRealMatrix;
import org.hipparchus.linear.ArrayRealVector;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.RealVector;
import org.hipparchus.optim.nonlinear.vector.leastsquares.MultivariateJacobianFunction;
import org.hipparchus.util.Pair;

/**
 * The trilateration model for the least squares optimizer.
 *
 * <p>Residual {@code i} is the geometric range error {@code ||x - p_i|| - r_i}, so the optimizer
 * minimizes the (weighted) sum of squared distance errors. This is the maximum likelihood estimate
 * when range errors are independent and Gaussian, and it keeps the residuals, RMS and covariance in
 * the same unit as the input distances.
 *
 * <p>The Jacobian row is the unit vector {@code (x - p_i) / ||x - p_i||}. It is undefined at the
 * anchor itself; a zero row is used there.
 */
final class TrilaterationFunction implements MultivariateJacobianFunction {

    private final double[][] positions;
    private final double[] distances;

    TrilaterationFunction(double[][] positions, double[] distances) {
        this.positions = positions;
        this.distances = distances;
    }

    /**
     * @param point the candidate solution
     * @return the geometric residual {@code ||point - p_i|| - r_i} for each anchor
     */
    double[] residuals(double[] point) {
        double[] residuals = new double[positions.length];
        for (int i = 0; i < positions.length; i++) {
            residuals[i] = distance(point, positions[i]) - distances[i];
        }
        return residuals;
    }

    /**
     * @param point the candidate solution
     * @return the unweighted Jacobian of the residuals at {@code point}, a new {@code m x n} array
     */
    double[][] jacobian(double[] point) {
        double[][] jacobian = new double[positions.length][point.length];
        for (int i = 0; i < positions.length; i++) {
            double range = distance(point, positions[i]);
            if (range > 0) {
                for (int j = 0; j < point.length; j++) {
                    jacobian[i][j] = (point[j] - positions[i][j]) / range;
                }
            }
        }
        return jacobian;
    }

    @Override
    public Pair<RealVector, RealMatrix> value(RealVector point) {
        double[] x = point.toArray();
        double[] residuals = new double[positions.length];
        double[][] jacobian = new double[positions.length][x.length];

        for (int i = 0; i < positions.length; i++) {
            double range = distance(x, positions[i]);
            residuals[i] = range - distances[i];
            if (range > 0) {
                for (int j = 0; j < x.length; j++) {
                    jacobian[i][j] = (x[j] - positions[i][j]) / range;
                }
            }
        }

        return new Pair<>(new ArrayRealVector(residuals, false), new Array2DRowRealMatrix(jacobian, false));
    }

    static double distance(double[] a, double[] b) {
        double sum = 0;
        for (int j = 0; j < a.length; j++) {
            double d = a[j] - b[j];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
