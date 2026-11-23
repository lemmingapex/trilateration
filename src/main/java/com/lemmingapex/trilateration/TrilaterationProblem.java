package com.lemmingapex.trilateration;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable, validated description of a trilateration problem: the known positions of a set
 * of anchors (beacons, base stations, satellites, ...) and the measured distance from each anchor
 * to the unknown point.
 *
 * <p>Positions may have any dimension {@code n >= 1}, but every position must have the same
 * dimension. A unique solution generally requires at least {@code n + 1} anchors that are not
 * contained in a common hyperplane (3 non-collinear anchors in 2D, 4 non-coplanar anchors in 3D).
 * Fewer, or degenerate, anchors are accepted; see {@link TrilaterationResult#isDegenerateGeometry()}.
 *
 * <p>All arrays are defensively copied, both on the way in and on the way out, so a problem can be
 * shared freely between threads.
 *
 * <pre>{@code
 * TrilaterationProblem problem = TrilaterationProblem.of(positions, distances)
 *         .withStandardDeviations(sigmas)   // optional
 *         .withInitialGuess(guess);         // optional
 * }</pre>
 */
public final class TrilaterationProblem {

    private final double[][] positions;
    private final double[] distances;
    private final double[] standardDeviations;
    private final double[] initialGuess;

    private TrilaterationProblem(double[][] positions, double[] distances,
                                 double[] standardDeviations, double[] initialGuess) {
        this.positions = positions;
        this.distances = distances;
        this.standardDeviations = standardDeviations;
        this.initialGuess = initialGuess;
    }

    /**
     * Creates a problem from anchor positions and measured distances.
     *
     * @param positions positions of the anchors; {@code positions[i]} is the coordinate vector of
     *                  anchor {@code i}. At least one position is required and all positions must
     *                  have the same, non-zero, dimension and finite coordinates.
     * @param distances measured distance from each anchor to the unknown point;
     *                  {@code distances[i]} belongs to {@code positions[i]}. Each distance must be
     *                  finite and non-negative.
     * @return a new problem
     * @throws NullPointerException     if an argument or any position is {@code null}
     * @throws IllegalArgumentException if the arguments are empty, of mismatched size, or contain
     *                                  non-finite or negative values
     */
    public static TrilaterationProblem of(double[][] positions, double[] distances) {
        Objects.requireNonNull(positions, "positions");
        Objects.requireNonNull(distances, "distances");
        if (positions.length == 0) {
            throw new IllegalArgumentException("At least one position is required.");
        }
        if (positions.length != distances.length) {
            throw new IllegalArgumentException("The number of positions (" + positions.length
                    + ") does not match the number of distances (" + distances.length + ").");
        }

        Objects.requireNonNull(positions[0], "positions[0]");
        int dimension = positions[0].length;
        if (dimension == 0) {
            throw new IllegalArgumentException("Positions must have at least one dimension.");
        }

        double[][] positionsCopy = new double[positions.length][];
        for (int i = 0; i < positions.length; i++) {
            double[] position = Objects.requireNonNull(positions[i], "positions[" + i + "]");
            if (position.length != dimension) {
                throw new IllegalArgumentException("All positions must have the same dimension: positions[0] has "
                        + dimension + " coordinates but positions[" + i + "] has " + position.length + ".");
            }
            for (int j = 0; j < dimension; j++) {
                requireFinite(position[j], "positions[" + i + "][" + j + "]");
            }
            positionsCopy[i] = position.clone();
        }

        double[] distancesCopy = distances.clone();
        for (int i = 0; i < distancesCopy.length; i++) {
            requireFinite(distancesCopy[i], "distances[" + i + "]");
            if (distancesCopy[i] < 0) {
                throw new IllegalArgumentException("distances[" + i + "] must be non-negative but was " + distancesCopy[i] + ".");
            }
        }

        return new TrilaterationProblem(positionsCopy, distancesCopy, null, null);
    }

    /**
     * Returns a copy of this problem with a standard deviation (measurement uncertainty) for each
     * distance. Measurements are weighted by {@code 1 / sigma^2}, so more certain measurements
     * have more influence on the solution.
     *
     * <p>Supplying standard deviations also tells the solver that the uncertainties are known in
     * absolute terms, so {@link TrilaterationResult#getCovariance()} is reported without rescaling
     * by the observed residuals. For noise that grows with range (common with RSSI-derived
     * distances), use a standard deviation proportional to each distance.
     *
     * @param standardDeviations one strictly positive, finite standard deviation per distance, in
     *                           the same unit as the distances
     * @return a new problem with the given standard deviations
     * @throws NullPointerException     if {@code standardDeviations} is {@code null}
     * @throws IllegalArgumentException if the length does not match the number of distances, or a
     *                                  value is not finite and strictly positive
     */
    public TrilaterationProblem withStandardDeviations(double... standardDeviations) {
        Objects.requireNonNull(standardDeviations, "standardDeviations");
        if (standardDeviations.length != distances.length) {
            throw new IllegalArgumentException("The number of standard deviations (" + standardDeviations.length
                    + ") does not match the number of distances (" + distances.length + ").");
        }
        double[] copy = standardDeviations.clone();
        for (int i = 0; i < copy.length; i++) {
            requireFinite(copy[i], "standardDeviations[" + i + "]");
            if (copy[i] <= 0) {
                throw new IllegalArgumentException("standardDeviations[" + i + "] must be strictly positive but was " + copy[i] + ".");
            }
        }
        return new TrilaterationProblem(positions, distances, copy, initialGuess);
    }

    /**
     * Returns a copy of this problem with an explicit starting point for the optimizer. By default
     * the solver computes a starting point from a closed-form linear estimate, which is usually
     * the better choice; an explicit guess is useful when tracking a moving target, or to select
     * one of two mirror-image solutions when the geometry is degenerate.
     *
     * @param initialGuess a finite point with the same dimension as the positions
     * @return a new problem with the given initial guess
     * @throws NullPointerException     if {@code initialGuess} is {@code null}
     * @throws IllegalArgumentException if the dimension is wrong or a coordinate is not finite
     */
    public TrilaterationProblem withInitialGuess(double... initialGuess) {
        Objects.requireNonNull(initialGuess, "initialGuess");
        if (initialGuess.length != getDimension()) {
            throw new IllegalArgumentException("The initial guess has dimension " + initialGuess.length
                    + " but the positions have dimension " + getDimension() + ".");
        }
        double[] copy = initialGuess.clone();
        for (int i = 0; i < copy.length; i++) {
            requireFinite(copy[i], "initialGuess[" + i + "]");
        }
        return new TrilaterationProblem(positions, distances, standardDeviations, copy);
    }

    /**
     * {@return the dimension {@code n} of the space (the length of every position)}
     */
    public int getDimension() {
        return positions[0].length;
    }

    /**
     * {@return the number of anchors, which equals the number of distance measurements}
     */
    public int getMeasurementCount() {
        return positions.length;
    }

    /**
     * {@return a copy of the anchor positions}
     */
    public double[][] getPositions() {
        double[][] copy = new double[positions.length][];
        for (int i = 0; i < positions.length; i++) {
            copy[i] = positions[i].clone();
        }
        return copy;
    }

    /**
     * {@return a copy of the measured distances}
     */
    public double[] getDistances() {
        return distances.clone();
    }

    /**
     * {@return a copy of the standard deviations, or {@code null} if none were supplied}
     */
    public double[] getStandardDeviations() {
        return standardDeviations == null ? null : standardDeviations.clone();
    }

    /**
     * {@return a copy of the initial guess, or {@code null} if none was supplied}
     */
    public double[] getInitialGuess() {
        return initialGuess == null ? null : initialGuess.clone();
    }

    /**
     * {@return {@code true} if standard deviations were supplied with
     *         {@link #withStandardDeviations(double...)}}
     */
    public boolean hasStandardDeviations() {
        return standardDeviations != null;
    }

    // Package-private, copy-free accessors for the solver. Callers must not modify the arrays.

    double[][] positions() {
        return positions;
    }

    double[] distances() {
        return distances;
    }

    double[] standardDeviations() {
        return standardDeviations;
    }

    double[] initialGuess() {
        return initialGuess;
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite but was " + value + ".");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrilaterationProblem)) {
            return false;
        }
        TrilaterationProblem that = (TrilaterationProblem) o;
        return Arrays.deepEquals(positions, that.positions)
                && Arrays.equals(distances, that.distances)
                && Arrays.equals(standardDeviations, that.standardDeviations)
                && Arrays.equals(initialGuess, that.initialGuess);
    }

    @Override
    public int hashCode() {
        int result = Arrays.deepHashCode(positions);
        result = 31 * result + Arrays.hashCode(distances);
        result = 31 * result + Arrays.hashCode(standardDeviations);
        result = 31 * result + Arrays.hashCode(initialGuess);
        return result;
    }

    @Override
    public String toString() {
        return "TrilaterationProblem{positions=" + Arrays.deepToString(positions)
                + ", distances=" + Arrays.toString(distances)
                + (standardDeviations == null ? "" : ", standardDeviations=" + Arrays.toString(standardDeviations))
                + (initialGuess == null ? "" : ", initialGuess=" + Arrays.toString(initialGuess))
                + "}";
    }
}
