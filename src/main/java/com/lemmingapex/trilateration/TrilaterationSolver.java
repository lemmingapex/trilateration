package com.lemmingapex.trilateration;

import java.util.Arrays;
import java.util.Objects;

import org.hipparchus.exception.MathRuntimeException;
import org.hipparchus.linear.Array2DRowRealMatrix;
import org.hipparchus.linear.DiagonalMatrix;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.SingularValueDecomposition;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresBuilder;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresOptimizer.Optimum;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LeastSquaresProblem;
import org.hipparchus.optim.nonlinear.vector.leastsquares.LevenbergMarquardtOptimizer;

/**
 * Solves {@link TrilaterationProblem}s with a Levenberg-Marquardt nonlinear least squares
 * optimizer.
 *
 * <p>The solver minimizes {@code sum_i w_i (||x - p_i|| - r_i)^2}, where {@code w_i = 1 / sigma_i^2}
 * when standard deviations are given and {@code 1} otherwise (or a {@link RobustLoss} of the same
 * scaled residuals). It works in three steps:
 *
 * <ol>
 *   <li>a closed-form linear estimate provides the starting point, unless the problem supplies an
 *       initial guess;</li>
 *   <li>Levenberg-Marquardt refines it (repeatedly reweighted when a robust loss is configured);</li>
 *   <li>the <em>mirror check</em> refines a second start: the first solution reflected across the
 *       plane in which the anchors are flattest. The better of the two is returned, which avoids
 *       local minima; if both explain the measurements about equally well, the result is
 *       {@linkplain TrilaterationResult#isAmbiguous() ambiguous} and the other is reported as an
 *       {@linkplain TrilaterationResult#getAlternativePoint() alternative}.</li>
 * </ol>
 *
 * <pre>{@code
 * TrilaterationResult result = new TrilaterationSolver().solve(positions, distances);
 * double[] point = result.getPoint();
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; a single solver can be shared and reused.
 */
public final class TrilaterationSolver {

    /**
     * Default maximum number of model evaluations, per optimizer run. Every iteration evaluates
     * the model at least once, so this also bounds the number of iterations.
     */
    public static final int DEFAULT_MAX_EVALUATIONS = 1000;

    /**
     * Two solutions are considered equally plausible when their normalized costs (chi-square in
     * units of the noise variance) differ by less than this: a likelihood ratio of about
     * {@code e^-2}, comparable to a two-sigma test.
     */
    static final double AMBIGUITY_THRESHOLD = 4.0;

    /**
     * Condition number of the normal matrix {@code J^T W J} above which the covariance is
     * unavailable (equivalent to 1e6 for the Jacobian itself).
     */
    private static final double MAX_CONDITION_NUMBER = 1e12;

    /** Solutions closer than this fraction of the problem's length scale are the same solution. */
    private static final double SAME_POINT_TOLERANCE = 1e-6;

    /** Residuals smaller than this fraction of the problem's length scale count as an exact fit. */
    private static final double EXACT_FIT_TOLERANCE = 1e-9;

    /** Maximum number of reweighting rounds for a robust loss. */
    private static final int MAX_REWEIGHTING_ROUNDS = 100;

    /** Reweighting stops when no robust weight changes by more than this. */
    private static final double REWEIGHTING_TOLERANCE = 1e-6;

    /** Converts a median absolute deviation into a standard deviation for Gaussian noise. */
    private static final double MAD_TO_SIGMA = 1.4826;

    private final int maxEvaluations;
    private final LevenbergMarquardtOptimizer optimizer;
    private final RobustLoss robustLoss;
    private final boolean mirrorCheck;

    /**
     * Creates a solver with default settings.
     */
    public TrilaterationSolver() {
        this(builder());
    }

    private TrilaterationSolver(Builder builder) {
        this.maxEvaluations = builder.maxEvaluations;
        this.robustLoss = builder.robustLoss;
        this.mirrorCheck = builder.mirrorCheck;
        this.optimizer = new LevenbergMarquardtOptimizer()
                .withCostRelativeTolerance(builder.costRelativeTolerance)
                .withParameterRelativeTolerance(builder.parameterRelativeTolerance);
    }

    /**
     * {@return a builder for a solver with non-default settings}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience for {@code solve(TrilaterationProblem.of(positions, distances))}.
     *
     * @param positions positions of the anchors, see {@link TrilaterationProblem#of(double[][], double[])}
     * @param distances measured distance from each anchor to the unknown point
     * @return the solution
     * @throws IllegalArgumentException if the input is invalid
     * @throws TrilaterationException   if the optimizer fails to converge
     */
    public TrilaterationResult solve(double[][] positions, double[] distances) {
        return solve(TrilaterationProblem.of(positions, distances));
    }

    /**
     * Solves a trilateration problem.
     *
     * @param problem the problem to solve
     * @return the solution
     * @throws NullPointerException   if {@code problem} is {@code null}
     * @throws TrilaterationException if the optimizer fails to converge
     */
    public TrilaterationResult solve(TrilaterationProblem problem) {
        return solve(problem, null);
    }

    /** Solves the problem, recording the solver's steps in {@code trace} when it is not {@code null}. */
    TrilaterationResult solve(TrilaterationProblem problem, SolverTrace trace) {
        double[][] positions = problem.positions();
        double[] distances = problem.distances();
        double[] sigmas = problem.standardDeviations();
        boolean knownNoise = sigmas != null;
        int m = problem.getMeasurementCount();
        int n = problem.getDimension();

        double[] weights = new double[m];
        for (int i = 0; i < m; i++) {
            weights[i] = knownNoise ? 1.0 / (sigmas[i] * sigmas[i]) : 1.0;
        }

        LinearEstimator.Estimate estimate = LinearEstimator.estimate(positions, distances, weights);
        double length = lengthScale(positions, distances, estimate.centroid);
        TrilaterationFunction function = new TrilaterationFunction(positions, distances);
        double[] start = problem.initialGuess() != null ? problem.initialGuess() : estimate.point;
        if (trace != null) {
            trace.linearEstimate = estimate.point.clone();
            trace.centroid = estimate.centroid.clone();
            trace.flattestDirection = estimate.flattestDirection.clone();
        }

        Fit best = fit(function, start, weights, knownNoise, length, trace);
        int iterations = best.iterations;
        int evaluations = best.evaluations;
        Fit alternative = null;

        if (mirrorCheck) {
            Fit mirror = null;
            try {
                mirror = fit(function, reflect(best.point, estimate.centroid, estimate.flattestDirection),
                        weights, knownNoise, length, trace);
            } catch (TrilaterationException e) {
                // The mirror start is only a second opinion; keep the first solution.
            }
            if (mirror != null) {
                iterations += mirror.iterations;
                evaluations += mirror.evaluations;
                if (TrilaterationFunction.distance(mirror.point, best.point) > SAME_POINT_TOLERANCE * length) {
                    double scale = best.scale;
                    double bestCost = best.cost(weights, scale);
                    double mirrorCost = mirror.cost(weights, scale);
                    // Strictly better only: on a tie keep the first solution, which honours an initial guess.
                    if (mirrorCost < bestCost - 1e-9 * Math.abs(bestCost)) {
                        Fit swap = best;
                        best = mirror;
                        mirror = swap;
                    }
                    if (isAmbiguous(best, mirror, weights, knownNoise, n, length)) {
                        alternative = mirror;
                    }
                }
            }
        }

        double noiseVariance = noiseVariance(best, weights, knownNoise, n);
        double[][] covariance = Double.isNaN(noiseVariance) ? null : covariance(function, best, weights, noiseVariance);
        double degreesOfFreedom = knownNoise ? Double.POSITIVE_INFINITY : effectiveDegreesOfFreedom(best, n);
        return new TrilaterationResult(best.point, best.residuals, covariance, degreesOfFreedom,
                alternative == null ? null : alternative.point,
                iterations, evaluations, estimate.rank < n);
    }

    /** Levenberg-Marquardt from {@code start}, followed by reweighting rounds for a robust loss. */
    private Fit fit(TrilaterationFunction function, double[] start, double[] weights, boolean knownNoise, double length,
                    SolverTrace trace) {
        int m = weights.length;
        double[] robustWeights = new double[m];
        Arrays.fill(robustWeights, 1.0);
        if (trace != null) {
            trace.startRun(start);
        }

        Optimum optimum = optimize(function, start, weights, trace);
        int iterations = optimum.getIterations();
        int evaluations = optimum.getEvaluations();
        double[] point = optimum.getPoint().toArray();
        double scale = 1.0;

        if (!robustLoss.isNone()) {
            for (int round = 0; round < MAX_REWEIGHTING_ROUNDS; round++) {
                double[] residuals = function.residuals(point);
                scale = knownNoise ? 1.0 : robustScale(residuals, length);
                double change = 0;
                for (int i = 0; i < m; i++) {
                    double updated = robustLoss.weight(Math.sqrt(weights[i]) * residuals[i] / scale);
                    change = Math.max(change, Math.abs(updated - robustWeights[i]));
                    robustWeights[i] = updated;
                }
                if (round > 0 && change < REWEIGHTING_TOLERANCE) {
                    break;
                }
                double[] combined = new double[m];
                for (int i = 0; i < m; i++) {
                    combined[i] = weights[i] * robustWeights[i];
                }
                optimum = optimize(function, point, combined, trace);
                iterations += optimum.getIterations();
                evaluations += optimum.getEvaluations();
                point = optimum.getPoint().toArray();
            }
        }

        return new Fit(point, function.residuals(point), robustWeights, scale, iterations, evaluations, robustLoss);
    }

    private Optimum optimize(TrilaterationFunction function, double[] start, double[] weights, SolverTrace trace) {
        LeastSquaresBuilder builder = new LeastSquaresBuilder()
                .model(function)
                .target(new double[weights.length])
                .start(start)
                .weight(new DiagonalMatrix(weights))
                // Every iteration costs at least one evaluation, so the evaluation limit bounds both.
                .maxIterations(Integer.MAX_VALUE)
                .maxEvaluations(maxEvaluations);
        if (trace != null) {
            // Called after every accepted step; never stops the optimizer by itself.
            builder.checker((iteration, previous, current) -> {
                trace.step(current.getPoint().toArray());
                return false;
            });
        }
        LeastSquaresProblem leastSquaresProblem = builder.build();
        try {
            return optimizer.optimize(leastSquaresProblem);
        } catch (MathRuntimeException e) {
            throw new TrilaterationException("Trilateration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Whether {@code other} explains the measurements about as well as {@code best}: both fit
     * exactly, or their costs differ by less than {@link #AMBIGUITY_THRESHOLD} noise variances.
     */
    private static boolean isAmbiguous(Fit best, Fit other, double[] weights, boolean knownNoise, int n, double length) {
        double exact = EXACT_FIT_TOLERANCE * length;
        if (rms(best.residuals) <= exact) {
            return rms(other.residuals) <= exact;
        }
        double variance = noiseVariance(best, weights, knownNoise, n);
        if (!Double.isNaN(variance) && variance > 0) {
            // Costs in units of the noise variance; for least squares, the chi-square difference.
            double scale = Math.sqrt(variance);
            return other.cost(weights, scale) - best.cost(weights, scale) < AMBIGUITY_THRESHOLD;
        }
        // The noise level cannot be estimated: only an equally good fit is ambiguous.
        double bestCost = best.cost(weights, 1.0);
        return other.cost(weights, 1.0) <= bestCost * (1 + 1e-6);
    }

    /**
     * Variance of unit-weight noise: 1 when standard deviations are known, otherwise the weighted
     * residual sum of squares over the (effective) degrees of freedom. NaN when it cannot be
     * estimated.
     */
    private static double noiseVariance(Fit fit, double[] weights, boolean knownNoise, int n) {
        if (knownNoise) {
            return 1.0;
        }
        double degreesOfFreedom = effectiveDegreesOfFreedom(fit, n);
        if (!(degreesOfFreedom > 0)) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += fit.robustWeights[i] * weights[i] * fit.residuals[i] * fit.residuals[i];
        }
        return sum / degreesOfFreedom;
    }

    /** Measurements minus unknowns, counting down-weighted measurements fractionally. */
    private static double effectiveDegreesOfFreedom(Fit fit, int n) {
        double sum = 0;
        for (double w : fit.robustWeights) {
            sum += w;
        }
        return sum - n;
    }

    /**
     * Covariance {@code sigma^2 (J^T W J)^-1}. The {@code n x n} normal matrix is accumulated
     * directly and decomposed once with an SVD, {@code J^T W J = V S V^T}, which gives both the
     * condition number and the inverse {@code V S^-1 V^T}; the cost does not grow with the number
     * of anchors beyond the accumulation. Returns {@code null} when the matrix is singular or badly
     * conditioned.
     */
    private static double[][] covariance(TrilaterationFunction function, Fit fit, double[] weights, double noiseVariance) {
        double[][] jacobian = function.jacobian(fit.point);
        int m = jacobian.length;
        int n = jacobian[0].length;
        double[][] normal = new double[n][n];
        for (int i = 0; i < m; i++) {
            double w = weights[i] * fit.robustWeights[i];
            for (int a = 0; a < n; a++) {
                for (int b = a; b < n; b++) {
                    normal[a][b] += w * jacobian[i][a] * jacobian[i][b];
                }
            }
        }
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < a; b++) {
                normal[a][b] = normal[b][a];
            }
        }

        SingularValueDecomposition svd = new SingularValueDecomposition(new Array2DRowRealMatrix(normal, false));
        double[] s = svd.getSingularValues();
        if (!(s[n - 1] > 0) || !(s[0] / s[n - 1] < MAX_CONDITION_NUMBER)) {
            return null;
        }
        RealMatrix v = svd.getV();
        double[][] covariance = new double[n][n];
        for (int a = 0; a < n; a++) {
            for (int b = a; b < n; b++) {
                double sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += v.getEntry(a, k) * v.getEntry(b, k) / s[k];
                }
                covariance[a][b] = sum * noiseVariance;
                covariance[b][a] = covariance[a][b];
            }
        }
        return covariance;
    }

    /** Robust noise scale from the median absolute residual, floored to avoid dividing by zero. */
    private static double robustScale(double[] residuals, double length) {
        double[] absolute = new double[residuals.length];
        for (int i = 0; i < residuals.length; i++) {
            absolute[i] = Math.abs(residuals[i]);
        }
        Arrays.sort(absolute);
        int mid = absolute.length / 2;
        double median = absolute.length % 2 == 1 ? absolute[mid] : 0.5 * (absolute[mid - 1] + absolute[mid]);
        return Math.max(MAD_TO_SIGMA * median, EXACT_FIT_TOLERANCE * length);
    }

    /** Reflection of {@code point} across the hyperplane through {@code origin} with unit normal {@code normal}. */
    private static double[] reflect(double[] point, double[] origin, double[] normal) {
        double dot = 0;
        for (int j = 0; j < point.length; j++) {
            dot += (point[j] - origin[j]) * normal[j];
        }
        double[] reflected = point.clone();
        for (int j = 0; j < point.length; j++) {
            reflected[j] -= 2 * dot * normal[j];
        }
        return reflected;
    }

    /** A length characteristic of the problem, used to make tolerances scale-free. */
    private static double lengthScale(double[][] positions, double[] distances, double[] centroid) {
        double length = 0;
        for (int i = 0; i < positions.length; i++) {
            length = Math.max(length, TrilaterationFunction.distance(positions[i], centroid));
            length = Math.max(length, distances[i]);
        }
        return length > 0 ? length : 1.0;
    }

    private static double rms(double[] residuals) {
        double sum = 0;
        for (double r : residuals) {
            sum += r * r;
        }
        return Math.sqrt(sum / residuals.length);
    }

    /** One optimizer run (with its reweighting rounds) from one starting point. */
    private static final class Fit {
        final double[] point;
        final double[] residuals;
        final double[] robustWeights;
        final double scale;
        final int iterations;
        final int evaluations;
        final RobustLoss loss;

        Fit(double[] point, double[] residuals, double[] robustWeights, double scale,
            int iterations, int evaluations, RobustLoss loss) {
            this.point = point;
            this.residuals = residuals;
            this.robustWeights = robustWeights;
            this.scale = scale;
            this.iterations = iterations;
            this.evaluations = evaluations;
            this.loss = loss;
        }

        /** {@code sum_i 2 rho(sqrt(w_i) r_i / scale)}; for least squares, chi-square over scale^2. */
        double cost(double[] weights, double scale) {
            double sum = 0;
            for (int i = 0; i < residuals.length; i++) {
                sum += loss.cost(Math.sqrt(weights[i]) * residuals[i] / scale);
            }
            return sum;
        }
    }

    /**
     * Builder for {@link TrilaterationSolver}.
     */
    public static final class Builder {

        private int maxEvaluations = DEFAULT_MAX_EVALUATIONS;
        private double costRelativeTolerance = 1e-10;
        private double parameterRelativeTolerance = 1e-10;
        private RobustLoss robustLoss = RobustLoss.NONE;
        private boolean mirrorCheck = true;

        private Builder() {
        }

        /**
         * Sets the maximum number of model evaluations of each optimizer run (default
         * {@value TrilaterationSolver#DEFAULT_MAX_EVALUATIONS}). Every iteration evaluates the
         * model at least once, so this also bounds the number of iterations. A run that reaches
         * the limit makes {@code solve} throw {@link TrilaterationException}, except in the
         * mirror check, where the first solution is kept.
         *
         * <p>A typical run needs 5 to 15 evaluations; a few hundred are rare and occur only with
         * very noisy or inconsistent measurements, or with an initial guess far from the anchors.
         *
         * @param maxEvaluations maximum number of model evaluations, at least 1
         * @return this builder
         */
        public Builder maxEvaluations(int maxEvaluations) {
            if (maxEvaluations < 1) {
                throw new IllegalArgumentException("maxEvaluations must be at least 1 but was " + maxEvaluations + ".");
            }
            this.maxEvaluations = maxEvaluations;
            return this;
        }

        /**
         * Convergence is declared when the relative reduction of the cost falls below this value.
         *
         * @param tolerance a non-negative relative tolerance (default {@code 1e-10})
         * @return this builder
         */
        public Builder costRelativeTolerance(double tolerance) {
            this.costRelativeTolerance = requireNonNegative(tolerance, "costRelativeTolerance");
            return this;
        }

        /**
         * Convergence is declared when the relative change of the solution falls below this value.
         *
         * @param tolerance a non-negative relative tolerance (default {@code 1e-10})
         * @return this builder
         */
        public Builder parameterRelativeTolerance(double tolerance) {
            this.parameterRelativeTolerance = requireNonNegative(tolerance, "parameterRelativeTolerance");
            return this;
        }

        /**
         * Sets the loss applied to the scaled residuals (default {@link RobustLoss#NONE}, ordinary
         * least squares). Use {@link RobustLoss#HUBER} or {@link RobustLoss#CAUCHY} when some
         * measurements may be grossly wrong.
         *
         * @param robustLoss the loss
         * @return this builder
         * @throws NullPointerException if {@code robustLoss} is {@code null}
         */
        public Builder robustLoss(RobustLoss robustLoss) {
            this.robustLoss = Objects.requireNonNull(robustLoss, "robustLoss");
            return this;
        }

        /**
         * Enables or disables the mirror check (default enabled). The mirror check runs the
         * optimizer a second time from the first solution reflected across the plane in which the
         * anchors are flattest, which avoids local minima and detects
         * {@linkplain TrilaterationResult#isAmbiguous() ambiguous} solutions, at roughly twice the
         * cost. Disable it only when throughput matters more than robustness.
         *
         * @param enabled {@code true} to enable the mirror check
         * @return this builder
         */
        public Builder mirrorCheck(boolean enabled) {
            this.mirrorCheck = enabled;
            return this;
        }

        /**
         * {@return a new, immutable solver}
         */
        public TrilaterationSolver build() {
            return new TrilaterationSolver(this);
        }

        private static double requireNonNegative(double value, String name) {
            if (!(value >= 0) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(name + " must be finite and non-negative but was " + value + ".");
            }
            return value;
        }
    }
}
