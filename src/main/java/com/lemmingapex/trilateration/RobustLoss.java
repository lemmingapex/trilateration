package com.lemmingapex.trilateration;

/**
 * A loss function that limits the influence of outlying measurements, such as distances that are
 * too long because the signal was blocked or reflected (non-line-of-sight).
 *
 * <p>Each residual is scaled to {@code u = (||x - p_i|| - r_i) / (sigma_i * s)}, where
 * {@code sigma_i} is the measurement's standard deviation (1 if none were given) and {@code s} is
 * 1 when standard deviations were given, or otherwise a robust estimate of the noise level
 * (1.4826 times the median absolute residual). The tuning constant {@code k} is therefore in units
 * of standard deviations: residuals within about {@code k} standard deviations are treated almost
 * as in ordinary least squares, larger ones are progressively down-weighted.
 *
 * <p>Robust fitting is solved by iteratively reweighted least squares. It needs redundant
 * measurements to work: an outlier can only be recognized when the remaining measurements still
 * determine the position, so in {@code n} dimensions expect it to help from about {@code n + 2}
 * anchors.
 *
 * @see TrilaterationSolver.Builder#robustLoss(RobustLoss)
 */
public final class RobustLoss {

    private enum Kind { NONE, HUBER, CAUCHY }

    /** Ordinary least squares: every measurement has full influence (the default). */
    public static final RobustLoss NONE = new RobustLoss(Kind.NONE, Double.POSITIVE_INFINITY);

    /**
     * Huber loss with {@code k = 1.345}: quadratic for small residuals, linear for large ones.
     * Convex, so it never introduces extra local minima. Loses about 5% efficiency when there are
     * no outliers.
     */
    public static final RobustLoss HUBER = huber(1.345);

    /**
     * Cauchy (Lorentzian) loss with {@code k = 2.385}: the influence of a residual falls to zero as
     * it grows, so gross outliers are almost ignored. Not convex; the solver starts it from the
     * least squares solution. Loses about 5% efficiency when there are no outliers.
     */
    public static final RobustLoss CAUCHY = cauchy(2.385);

    private final Kind kind;
    private final double k;

    private RobustLoss(Kind kind, double k) {
        this.kind = kind;
        this.k = k;
    }

    /**
     * Creates a Huber loss with a custom tuning constant.
     *
     * @param k residuals larger than {@code k} standard deviations are down-weighted; must be
     *          finite and strictly positive
     * @return the loss
     * @throws IllegalArgumentException if {@code k} is not finite and strictly positive
     */
    public static RobustLoss huber(double k) {
        return new RobustLoss(Kind.HUBER, requirePositive(k));
    }

    /**
     * Creates a Cauchy loss with a custom tuning constant.
     *
     * @param k scale, in standard deviations, at which a residual's weight halves; must be finite
     *          and strictly positive
     * @return the loss
     * @throws IllegalArgumentException if {@code k} is not finite and strictly positive
     */
    public static RobustLoss cauchy(double k) {
        return new RobustLoss(Kind.CAUCHY, requirePositive(k));
    }

    /**
     * {@return the tuning constant {@code k}, or positive infinity for {@link #NONE}}
     */
    public double getTuningConstant() {
        return k;
    }

    boolean isNone() {
        return kind == Kind.NONE;
    }

    /** IRLS weight {@code psi(u) / u} for the scaled residual {@code u}. */
    double weight(double u) {
        double a = Math.abs(u);
        return switch (kind) {
            case HUBER -> a <= k ? 1.0 : k / a;
            case CAUCHY -> 1.0 / (1.0 + (u / k) * (u / k));
            case NONE -> 1.0;
        };
    }

    /** {@code 2 rho(u)}, scaled so that it equals {@code u^2} for small residuals. */
    double cost(double u) {
        double a = Math.abs(u);
        return switch (kind) {
            case HUBER -> a <= k ? u * u : 2 * k * a - k * k;
            case CAUCHY -> k * k * Math.log1p((u / k) * (u / k));
            case NONE -> u * u;
        };
    }

    private static double requirePositive(double k) {
        if (!(k > 0) || Double.isInfinite(k)) {
            throw new IllegalArgumentException("The tuning constant must be finite and strictly positive but was " + k + ".");
        }
        return k;
    }

    @Override
    public String toString() {
        return kind == Kind.NONE ? "RobustLoss.NONE" : "RobustLoss." + kind.name().toLowerCase(java.util.Locale.ROOT) + "(" + k + ")";
    }
}
