/**
 * Trilateration (multilateration) in any number of dimensions: estimate an unknown position from
 * the known positions of anchors and measured distances to each of them.
 *
 * <p>Start with {@link com.lemmingapex.trilateration.TrilaterationSolver}:
 *
 * <pre>{@code
 * double[][] positions = { { 5.0, -6.0 }, { 13.0, -15.0 }, { 21.0, -3.0 }, { 12.4, -21.2 } };
 * double[] distances = { 8.06, 13.97, 23.32, 15.31 };
 *
 * TrilaterationResult result = new TrilaterationSolver().solve(positions, distances);
 * double[] point = result.getPoint();
 * }</pre>
 *
 * <p>Use {@link com.lemmingapex.trilateration.TrilaterationProblem} to attach measurement
 * uncertainties or an initial guess, and
 * {@link com.lemmingapex.trilateration.TrilaterationResult} for residuals, covariance and
 * convergence diagnostics.
 */
package com.lemmingapex.trilateration;
