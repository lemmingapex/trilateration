package com.lemmingapex.trilateration;

import java.util.Arrays;
import java.util.Optional;

import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.distribution.continuous.TDistribution;

/**
 * The solution of a {@link TrilaterationProblem}, together with diagnostics and an estimate of its
 * uncertainty. Instances are immutable; array accessors return copies.
 */
public final class TrilaterationResult {

    private final double[] point;
    private final double[] residuals;
    private final double rms;
    private final double[][] covariance;
    private final double degreesOfFreedom;
    private final double[] alternativePoint;
    private final int iterations;
    private final int evaluations;
    private final boolean degenerateGeometry;

    TrilaterationResult(double[] point, double[] residuals, double[][] covariance, double degreesOfFreedom,
                        double[] alternativePoint, int iterations, int evaluations, boolean degenerateGeometry) {
        this.point = point;
        this.residuals = residuals;
        this.covariance = covariance;
        this.degreesOfFreedom = degreesOfFreedom;
        this.alternativePoint = alternativePoint;
        this.iterations = iterations;
        this.evaluations = evaluations;
        this.degenerateGeometry = degenerateGeometry;

        double sum = 0;
        for (double r : residuals) {
            sum += r * r;
        }
        this.rms = Math.sqrt(sum / residuals.length);
    }

    /**
     * {@return a copy of the estimated position, with the same dimension as the anchors}
     */
    public double[] getPoint() {
        return point.clone();
    }

    /**
     * Returns the range error of each measurement at the solution,
     * {@code distance(point, positions[i]) - distances[i]}. A positive residual means the solution
     * is farther from that anchor than measured. With a {@link RobustLoss}, a residual that is
     * large compared with the others identifies an outlier.
     *
     * @return a copy of the residuals, in the unit of the input distances
     */
    public double[] getResiduals() {
        return residuals.clone();
    }

    /**
     * {@return the root mean square of the (unweighted) residuals, in the unit of the input distances;
     *         zero when every distance is matched exactly}
     */
    public double getRms() {
        return rms;
    }

    /**
     * Returns the covariance matrix of the estimated position, in squared distance units.
     *
     * <p>If the problem has {@linkplain TrilaterationProblem#withStandardDeviations(double...)
     * standard deviations}, the covariance follows directly from them. Otherwise the noise variance
     * is estimated from the residuals as {@code chi^2 / (m - n)}, which requires more measurements
     * ({@code m}) than dimensions ({@code n}). That estimate is unbiased but itself uncertain when
     * {@code m - n} is small; use {@link #getConfidenceHalfWidths(double)} for intervals that
     * account for it.
     *
     * <p>The covariance is a linearized approximation, accurate when the noise is small compared
     * with the distances between anchors. It describes the uncertainty around {@link #getPoint()}
     * only; if the result is {@linkplain #isAmbiguous() ambiguous}, the position may instead be the
     * {@linkplain #getAlternativePoint() alternative}.
     *
     * @return a copy of the {@code n x n} covariance matrix, or empty if it cannot be estimated:
     *         the geometry does not constrain every direction, or noise has to be estimated from
     *         {@code n} or fewer measurements
     */
    public Optional<double[][]> getCovariance() {
        if (covariance == null) {
            return Optional.empty();
        }
        double[][] copy = new double[covariance.length][];
        for (int i = 0; i < covariance.length; i++) {
            copy[i] = covariance[i].clone();
        }
        return Optional.of(copy);
    }

    /**
     * Returns the standard deviation of each coordinate of the estimated position (the square root
     * of the covariance diagonal), in the unit of the input distances.
     *
     * @return the per-coordinate standard deviations, or empty when {@link #getCovariance()} is empty
     */
    public Optional<double[]> getStandardDeviation() {
        if (covariance == null) {
            return Optional.empty();
        }
        double[] sigma = new double[covariance.length];
        for (int i = 0; i < sigma.length; i++) {
            sigma[i] = Math.sqrt(Math.max(0, covariance[i][i]));
        }
        return Optional.of(sigma);
    }

    /**
     * Returns the half-width of a confidence interval for each coordinate:
     * {@code point[j] ± halfWidth[j]} contains the true coordinate with the given probability.
     *
     * <p>When standard deviations were supplied, the half-width is a normal quantile times the
     * standard deviation. When the noise level is estimated from the residuals, a Student-t
     * quantile with {@code m - n} degrees of freedom is used instead, which widens the interval to
     * account for the uncertainty of that estimate: with 2 degrees of freedom a 95% interval is
     * {@code ± 4.30} standard deviations rather than {@code ± 1.96}.
     *
     * @param confidence the coverage probability, strictly between 0 and 1, for example {@code 0.95}
     * @return the per-coordinate half-widths, or empty when {@link #getCovariance()} is empty
     * @throws IllegalArgumentException if {@code confidence} is not strictly between 0 and 1
     */
    public Optional<double[]> getConfidenceHalfWidths(double confidence) {
        if (!(confidence > 0 && confidence < 1)) {
            throw new IllegalArgumentException("confidence must be strictly between 0 and 1 but was " + confidence + ".");
        }
        return getStandardDeviation().map(sigma -> {
            double p = 0.5 + confidence / 2;
            double quantile = Double.isInfinite(degreesOfFreedom)
                    ? new NormalDistribution(0, 1).inverseCumulativeProbability(p)
                    : new TDistribution(degreesOfFreedom).inverseCumulativeProbability(p);
            double[] halfWidths = new double[sigma.length];
            for (int j = 0; j < sigma.length; j++) {
                halfWidths[j] = quantile * sigma[j];
            }
            return halfWidths;
        });
    }

    /** Degrees of freedom of the noise estimate, or positive infinity when the noise is known. */
    double degreesOfFreedom() {
        return degreesOfFreedom;
    }

    /**
     * Reports whether a distinctly different position explains the measurements about as well as
     * {@link #getPoint()}. This happens when the anchors are (nearly) collinear in 2D or coplanar in
     * 3D: the measurements then barely distinguish the solution from its mirror image across the
     * anchors. Collect more anchors off that line or plane, or use outside knowledge (for example,
     * an {@linkplain TrilaterationProblem#withInitialGuess(double...) initial guess} from a previous
     * position) to decide between the two.
     *
     * <p>Requires the solver's mirror check, which is enabled by default.
     *
     * @return {@code true} if {@link #getAlternativePoint()} is present
     */
    public boolean isAmbiguous() {
        return alternativePoint != null;
    }

    /**
     * Returns the other candidate position when the result is {@linkplain #isAmbiguous()
     * ambiguous}: a solution whose fit is statistically indistinguishable from {@link #getPoint()}
     * (its chi-square is larger by less than 4, or both fit the measurements exactly).
     *
     * @return a copy of the alternative position, or empty if the solution is unambiguous
     */
    public Optional<double[]> getAlternativePoint() {
        return alternativePoint == null ? Optional.empty() : Optional.of(alternativePoint.clone());
    }

    /**
     * {@return the total number of optimizer iterations, over all optimizer runs}
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * {@return the total number of times the model was evaluated, over all optimizer runs}
     */
    public int getEvaluations() {
        return evaluations;
    }

    /**
     * Reports whether the anchors span fewer dimensions than the space: all anchors exactly
     * collinear in 2D, coplanar in 3D, or at most {@code n} anchors in {@code n} dimensions.
     *
     * <p>The measurements then cannot distinguish the solution from its mirror image across the
     * anchors' affine hull, unless the solution lies on the hull. In the first case the result is
     * also {@linkplain #isAmbiguous() ambiguous}, which additionally covers nearly degenerate
     * geometry and is usually the more useful check.
     *
     * @return {@code true} if the anchor geometry is degenerate
     */
    public boolean isDegenerateGeometry() {
        return degenerateGeometry;
    }

    @Override
    public String toString() {
        return "TrilaterationResult{point=" + Arrays.toString(point)
                + ", rms=" + rms
                + ", standardDeviation=" + getStandardDeviation().map(Arrays::toString).orElse("unavailable")
                + (alternativePoint == null ? "" : ", alternativePoint=" + Arrays.toString(alternativePoint))
                + ", iterations=" + iterations
                + ", evaluations=" + evaluations
                + (degenerateGeometry ? ", degenerateGeometry=true" : "")
                + "}";
    }
}
