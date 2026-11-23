package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrilaterationSolverTest {

    private static final TrilaterationSolver SOLVER = new TrilaterationSolver();

    private static Arguments testCase(String name, double[][] positions, double[] distances,
                                      double[] expected, double delta) {
        return Arguments.argumentSet(name, positions, distances, expected, delta);
    }

    /** Distances from {@code point} to each position, i.e. noise-free measurements. */
    private static double[] exactDistances(double[][] positions, double[] point) {
        double[] distances = new double[positions.length];
        for (int i = 0; i < positions.length; i++) {
            distances[i] = TrilaterationFunction.distance(positions[i], point);
        }
        return distances;
    }

    @Nested
    class KnownSolutions {

        static Stream<Arguments> cases() {
            return Stream.of(
                    testCase("1D exact", new double[][] {{1.0}, {2.0}, {3.0}}, new double[] {1.1, 0.1, 0.9}, new double[] {2.1}, 1e-4),
                    testCase("1D exact, large coordinates", new double[][] {{1000.0}, {2000.0}, {3000.0}}, new double[] {1100, 100, 900}, new double[] {2100.0}, 1e-4),
                    testCase("1D inexact", new double[][] {{1000.0}, {2000.0}, {3000.0}}, new double[] {1110, 110, 910}, new double[] {2100.0}, 30),
                    testCase("2D exact 1", new double[][] {{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}}, new double[] {1.0, 1.0, 1.0}, new double[] {2.0, 1.0}, 1e-4),
                    testCase("2D exact 2", new double[][] {{0.0, 0.0}, {-1.0, 0.0}, {0.0, -1.0}}, new double[] {Math.sqrt(2.0), 1.0, 1.0}, new double[] {-1.0, -1.0}, 1e-4),
                    testCase("2D exact 3", new double[][] {{0.0, 0.0}, {1000.0, 0.0}, {0.0, 1000.0}}, new double[] {Math.sqrt(2.0) * 1000.0, 1000.0, 1000.0}, new double[] {1000.0, 1000.0}, 1e-4),
                    testCase("2D exact 4", new double[][] {{1.0, 1.0}, {1.0, 3.0}, {8.0, 8.0}, {2.0, 2.0}}, new double[] {5.0, 5.0, 6.36, 3.9}, new double[] {5.9, 2.0}, 0.01),
                    testCase("2D exact 5", new double[][] {{5.0, -6.0}, {13.0, -15.0}, {21.0, -3.0}}, new double[] {8.06, 13.97, 23.32}, new double[] {-0.6, -11.8}, 0.01),
                    testCase("2D inexact 1", new double[][] {{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}}, new double[] {0.9, 1.0, 1.0}, new double[] {2.0, 1.0}, 0.1),
                    testCase("2D inexact 2", new double[][] {{5.0, -6.0}, {13.0, -15.0}, {21.0, -3.0}, {12.42, -21.2}}, new double[] {8.06, 13.97, 23.32, 15.31}, new double[] {-0.6, -11.8}, 1.0),
                    testCase("2D zero distance", new double[][] {{1.0, 1.0}, {2.0, 1.0}}, new double[] {0.0, 1.0}, new double[] {1.0, 1.0}, 1e-4),
                    testCase("2D non-intersecting circles", new double[][] {{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}}, new double[] {0.5, 0.5, 0.5}, new double[] {2.0, 1.0}, 0.25),
                    testCase("2D over-intersecting circles", new double[][] {{1.0, 1.0}, {3.0, 1.0}, {2.0, 2.0}}, new double[] {2.0, 2.0, 2.0}, new double[] {2.0, 1.0}, 2.0),
                    testCase("2D duplicate anchor", new double[][] {{1.0, 1.0}, {1.0, 1.0}, {3.0, 1.0}}, new double[] {1.0, 1.0, 1.0}, new double[] {2.0, 1.0}, 1e-4),
                    testCase("2D two tangent circles", new double[][] {{1.0, 1.0}, {3.0, 1.0}}, new double[] {1.0, 1.0}, new double[] {2.0, 1.0}, 1e-4),
                    testCase("3D exact, tangent spheres", new double[][] {{1.0, 1.0, 1.0}, {3.0, 1.0, 1.0}, {2.0, 2.0, 1.0}}, new double[] {1.0, 1.0, 1.0}, new double[] {2.0, 1.0, 1.0}, 1e-4),
                    testCase("3D inexact", new double[][] {{0.0, 0.0, 0.0}, {8.84, 4.57, 12.59}, {0.0, -8.84, 8.84}, {10.72, -8.96, 8.84}}, new double[] {8.84, 8.84, 8.84, 8.84}, new double[] {5.2, -1.2, 7.7}, 1.0));
        }

        @ParameterizedTest
        @MethodSource("cases")
        void findsExpectedPosition(double[][] positions, double[] distances, double[] expected, double delta) {
            TrilaterationResult result = SOLVER.solve(positions, distances);
            assertArrayEquals(expected, result.getPoint(), delta, result::toString);
        }

        @Test
        void readmeExample() {
            double[][] positions = {{5.0, -6.0}, {13.0, -15.0}, {21.0, -3.0}, {12.4, -21.2}};
            double[] distances = {8.06, 13.97, 23.32, 15.31};

            TrilaterationResult result = SOLVER.solve(positions, distances);

            assertArrayEquals(new double[] {-0.6, -11.8}, result.getPoint(), 1.0);
            assertFalse(result.isDegenerateGeometry());
            assertTrue(result.getStandardDeviation().isPresent());
        }

        @Test
        void exactMeasurementsHaveZeroResiduals() {
            double[][] positions = {{0, 0, 0}, {10, 0, 0}, {0, 10, 0}, {0, 0, 10}, {10, 10, 10}};
            double[] truth = {3.3, 4.4, 5.5};

            TrilaterationResult result = SOLVER.solve(positions, exactDistances(positions, truth));

            assertArrayEquals(truth, result.getPoint(), 1e-9);
            assertArrayEquals(new double[positions.length], result.getResiduals(), 1e-9);
            assertEquals(0, result.getRms(), 1e-9);
        }

        @Test
        void fourDimensions() {
            double[][] positions = {{0, 0, 0, 0}, {9, 0, 0, 0}, {0, 9, 0, 0}, {0, 0, 9, 0}, {0, 0, 0, 9}};
            double[] truth = {1, 2, 3, 4};

            TrilaterationResult result = SOLVER.solve(positions, exactDistances(positions, truth));

            assertArrayEquals(truth, result.getPoint(), 1e-9);
        }

        @Test
        void farFromOriginCoordinates() {
            // e.g. UTM coordinates: large offsets must not cost precision.
            double offset = 5_000_000;
            double[][] positions = {{offset, offset}, {offset + 100, offset}, {offset, offset + 100}};
            double[] truth = {offset + 30, offset + 40};

            TrilaterationResult result = SOLVER.solve(positions, exactDistances(positions, truth));

            assertArrayEquals(truth, result.getPoint(), 1e-6);
        }
    }

    @Nested
    class DegenerateGeometry {

        @Test
        void collinearAnchorsFindAMirrorSolution() {
            double[][] positions = {{0, 0}, {5, 0}, {10, 0}};
            double[] distances = exactDistances(positions, new double[] {3, 4});

            TrilaterationResult result = SOLVER.solve(positions, distances);

            double[] point = result.getPoint();
            assertTrue(result.isDegenerateGeometry());
            assertEquals(3, point[0], 1e-6);
            assertEquals(4, Math.abs(point[1]), 1e-6);
            assertEquals(0, result.getRms(), 1e-9);
        }

        @Test
        void initialGuessSelectsTheMirrorSolution() {
            double[][] positions = {{0, 0}, {5, 0}, {10, 0}};
            double[] distances = exactDistances(positions, new double[] {3, 4});
            TrilaterationProblem problem = TrilaterationProblem.of(positions, distances);

            assertArrayEquals(new double[] {3, 4}, SOLVER.solve(problem.withInitialGuess(0, 1)).getPoint(), 1e-6);
            assertArrayEquals(new double[] {3, -4}, SOLVER.solve(problem.withInitialGuess(0, -1)).getPoint(), 1e-6);
        }

        @Test
        void coplanarAnchorsIn3D() {
            double[][] positions = {{0, 0, 0}, {10, 0, 0}, {0, 10, 0}, {10, 10, 0}};
            double[] distances = exactDistances(positions, new double[] {2, 3, 7});

            TrilaterationResult result = SOLVER.solve(positions, distances);

            double[] point = result.getPoint();
            assertTrue(result.isDegenerateGeometry());
            assertEquals(2, point[0], 1e-6);
            assertEquals(3, point[1], 1e-6);
            assertEquals(7, Math.abs(point[2]), 1e-6);
        }

        @Test
        void identicalAnchorsGiveAPointOnTheCircle() {
            double[][] positions = {{1, 1}, {1, 1}, {1, 1}};

            TrilaterationResult result = SOLVER.solve(positions, new double[] {1, 1, 1});

            assertTrue(result.isDegenerateGeometry());
            assertEquals(1, TrilaterationFunction.distance(positions[0], result.getPoint()), 1e-9);
        }

        @Test
        void singleMeasurement() {
            double[][] positions = {{2, 3, 4}};

            TrilaterationResult result = SOLVER.solve(positions, new double[] {5});

            assertTrue(result.isDegenerateGeometry());
            assertEquals(5, TrilaterationFunction.distance(positions[0], result.getPoint()), 1e-9);
            assertTrue(result.getCovariance().isEmpty());
        }

        @Test
        void fewerAnchorsThanDimensions() {
            double[][] positions = {{0, 0, 0}, {4, 0, 0}};

            TrilaterationResult result = SOLVER.solve(positions, new double[] {3, 3});

            assertTrue(result.isDegenerateGeometry());
            assertEquals(0, result.getRms(), 1e-9);
        }

        @Test
        void tangentSolutionHasNoCovariance() {
            // All anchors in the plane z = 1 and the solution in that plane: z is unconstrained to first order.
            double[][] positions = {{1, 1, 1}, {3, 1, 1}, {2, 2, 1}};

            TrilaterationResult result = SOLVER.solve(TrilaterationProblem.of(positions, new double[] {1, 1, 1})
                    .withStandardDeviations(0.1, 0.1, 0.1));

            assertTrue(result.getCovariance().isEmpty());
        }

        @Test
        void wellPosedGeometryIsNotDegenerate() {
            double[][] positions = {{0, 0}, {10, 0}, {0, 10}};
            assertFalse(SOLVER.solve(positions, new double[] {5, 7, 8}).isDegenerateGeometry());
        }
    }

    @Nested
    class Uncertainty {

        private final double[][] anchors = {{0, 0}, {100, 0}, {0, 100}, {100, 100}};
        private final double[] truth = {30, 60};

        @Test
        void knownNoiseCovarianceMatchesMonteCarlo() {
            double sigma = 1.0;
            Random random = new Random(42);
            int trials = 3000;
            double[] sumSquaredError = new double[2];
            double[] reported = null;

            for (int t = 0; t < trials; t++) {
                double[] distances = exactDistances(anchors, truth);
                for (int i = 0; i < distances.length; i++) {
                    distances[i] += sigma * random.nextGaussian();
                }
                TrilaterationResult result = SOLVER.solve(TrilaterationProblem.of(anchors, distances)
                        .withStandardDeviations(sigma, sigma, sigma, sigma));
                double[] point = result.getPoint();
                for (int j = 0; j < 2; j++) {
                    sumSquaredError[j] += (point[j] - truth[j]) * (point[j] - truth[j]);
                }
                reported = result.getStandardDeviation().orElseThrow();
            }

            for (int j = 0; j < 2; j++) {
                double empirical = Math.sqrt(sumSquaredError[j] / trials);
                assertEquals(empirical, reported[j], 0.1 * empirical, "coordinate " + j);
            }
        }

        /**
         * With noise estimated from only m - n = 2 degrees of freedom, the variance must still be
         * unbiased: chi^2 / (m - n). (A chi^2 / (m - n + 1) estimate would come out at 2/3.)
         */
        @Test
        void estimatedNoiseVarianceIsUnbiasedWithFewMeasurements() {
            Random random = new Random(7);
            int trials = 8000;
            double sumSquaredError = 0;
            double sumVariance = 0;
            for (int t = 0; t < trials; t++) {
                TrilaterationResult result = SOLVER.solve(anchors, noisy(random, exactDistances(anchors, truth), 2.0));
                double error = result.getPoint()[0] - truth[0];
                sumSquaredError += error * error;
                sumVariance += result.getCovariance().orElseThrow()[0][0];
            }

            assertEquals(1.0, sumVariance / sumSquaredError, 0.08);
        }

        @Test
        void confidenceIntervalsHaveNominalCoverageWithEstimatedNoise() {
            Random random = new Random(8);
            int trials = 8000;
            int covered = 0;
            for (int t = 0; t < trials; t++) {
                TrilaterationResult result = SOLVER.solve(anchors, noisy(random, exactDistances(anchors, truth), 2.0));
                double error = Math.abs(result.getPoint()[1] - truth[1]);
                if (error <= result.getConfidenceHalfWidths(0.95).orElseThrow()[1]) {
                    covered++;
                }
            }

            assertEquals(0.95, (double) covered / trials, 0.012);
        }

        @Test
        void confidenceHalfWidthsUseNormalOrStudentQuantiles() {
            double[] distances = {67.3, 92.0, 49.6, 80.1};

            TrilaterationResult known = SOLVER.solve(TrilaterationProblem.of(anchors, distances).withStandardDeviations(1, 1, 1, 1));
            double[] sigma = known.getStandardDeviation().orElseThrow();
            assertEquals(1.959964 * sigma[0], known.getConfidenceHalfWidths(0.95).orElseThrow()[0], 1e-5 * sigma[0]);

            TrilaterationResult estimated = SOLVER.solve(anchors, distances);
            double[] estimatedSigma = estimated.getStandardDeviation().orElseThrow();
            // Student t, 2 degrees of freedom, 97.5% quantile
            assertEquals(4.302653 * estimatedSigma[0], estimated.getConfidenceHalfWidths(0.95).orElseThrow()[0], 1e-5 * estimatedSigma[0]);

            assertThrows(IllegalArgumentException.class, () -> estimated.getConfidenceHalfWidths(1.0));
            assertThrows(IllegalArgumentException.class, () -> estimated.getConfidenceHalfWidths(Double.NaN));
            assertTrue(SOLVER.solve(new double[][] {{0, 0}, {10, 0}}, new double[] {5, 5}).getConfidenceHalfWidths(0.9).isEmpty());
        }

        @Test
        void covarianceIsSymmetricAndMatchesStandardDeviation() {
            TrilaterationResult result = SOLVER.solve(anchors, new double[] {67.3, 92.0, 49.6, 80.1});

            double[][] covariance = result.getCovariance().orElseThrow();
            double[] sigma = result.getStandardDeviation().orElseThrow();
            assertEquals(covariance[0][1], covariance[1][0], 1e-12);
            assertEquals(Math.sqrt(covariance[0][0]), sigma[0], 1e-12);
            assertEquals(Math.sqrt(covariance[1][1]), sigma[1], 1e-12);
        }

        @Test
        void covarianceNeedsRedundancyOrKnownNoise() {
            double[][] positions = {{0, 0}, {10, 0}};
            double[] distances = exactDistances(positions, new double[] {5, 5});

            assertTrue(SOLVER.solve(positions, distances).getCovariance().isEmpty());
            assertTrue(SOLVER.solve(TrilaterationProblem.of(positions, distances).withStandardDeviations(1, 1))
                    .getCovariance().isPresent());
        }

        @Test
        void standardDeviationsDownWeightUnreliableMeasurements() {
            double[][] positions = {{0, 0}, {10, 0}, {0, 10}, {10, 10}};
            double[] target = {4, 6};
            double[] distances = exactDistances(positions, target);
            distances[3] += 5; // a badly wrong measurement

            double unweightedError = TrilaterationFunction.distance(target, SOLVER.solve(positions, distances).getPoint());
            double weightedError = TrilaterationFunction.distance(target, SOLVER.solve(
                    TrilaterationProblem.of(positions, distances).withStandardDeviations(0.1, 0.1, 0.1, 100)).getPoint());

            assertTrue(weightedError < 0.01, "weighted error " + weightedError);
            assertTrue(unweightedError > 10 * weightedError, "unweighted error " + unweightedError);
        }
    }

    @Nested
    class MirrorCheck {

        private final double[] truth = {30, 40};

        @Test
        void collinearAnchorsReportTheMirrorAsAlternative() {
            double[][] positions = {{0, 0}, {50, 0}, {100, 0}};

            TrilaterationResult result = SOLVER.solve(positions, exactDistances(positions, truth));

            assertTrue(result.isAmbiguous());
            double[] point = result.getPoint();
            double[] alternative = result.getAlternativePoint().orElseThrow();
            assertEquals(40, Math.abs(point[1]), 1e-6);
            assertArrayEquals(new double[] {point[0], -point[1]}, alternative, 1e-6);
        }

        @Test
        void initialGuessWinsATie() {
            double[][] positions = {{0, 0}, {50, 0}, {100, 0}};
            TrilaterationProblem problem = TrilaterationProblem.of(positions, exactDistances(positions, truth));

            TrilaterationResult below = SOLVER.solve(problem.withInitialGuess(50, -1));

            assertArrayEquals(new double[] {30, -40}, below.getPoint(), 1e-6);
            assertArrayEquals(new double[] {30, 40}, below.getAlternativePoint().orElseThrow(), 1e-6);
        }

        @Test
        void nearlyCollinearAnchorsAreAmbiguousWhenNoiseHidesTheDifference() {
            double[][] positions = {{0, 0}, {50, 0.01}, {100, 0}, {25, -0.01}};
            double[] distances = noisy(new Random(4), exactDistances(positions, truth), 0.5);

            TrilaterationResult result = SOLVER.solve(positions, distances);

            assertFalse(result.isDegenerateGeometry());
            assertTrue(result.isAmbiguous());
            assertEquals(-result.getPoint()[1], result.getAlternativePoint().orElseThrow()[1], 2.0);
        }

        @Test
        void exactMeasurementsResolveNearlyCollinearAnchors() {
            double[][] positions = {{0, 0}, {50, 0.01}, {100, 0}, {25, -0.01}};

            TrilaterationResult result = SOLVER.solve(positions, exactDistances(positions, truth));

            assertFalse(result.isAmbiguous());
            assertArrayEquals(truth, result.getPoint(), 1e-6);
        }

        @Test
        void wellSpreadAnchorsAreNotAmbiguous() {
            double[][] positions = {{0, 0}, {100, 0}, {0, 100}, {100, 100}};

            TrilaterationResult result = SOLVER.solve(positions, noisy(new Random(5), exactDistances(positions, truth), 2.0));

            assertFalse(result.isAmbiguous());
            assertTrue(result.getAlternativePoint().isEmpty());
        }

        @Test
        void avoidsLocalMinima() {
            // Minimal 3D configurations with heavy noise, where a single optimizer run sometimes
            // stops in a local minimum. The global minimum is approximated by many random starts.
            TrilaterationSolver withoutCheck = TrilaterationSolver.builder().mirrorCheck(false).build();
            Random random = new Random(2024);
            int withCheckMisses = 0;
            int withoutCheckMisses = 0;
            for (int t = 0; t < 1500; t++) {
                double[][] positions = new double[4][];
                for (int i = 0; i < positions.length; i++) {
                    positions[i] = randomPoint(random, 3, 0, 100);
                }
                double[] distances = noisy(random, exactDistances(positions, randomPoint(random, 3, 0, 100)), 5.0);
                TrilaterationProblem problem = TrilaterationProblem.of(positions, distances);

                double best = Double.MAX_VALUE;
                for (int s = 0; s < 30; s++) {
                    best = Math.min(best, cost(withoutCheck.solve(problem.withInitialGuess(randomPoint(random, 3, -100, 200)))));
                }
                double tolerance = best * (1 + 1e-6) + 1e-9;
                if (cost(SOLVER.solve(problem)) > tolerance) {
                    withCheckMisses++;
                }
                if (cost(withoutCheck.solve(problem)) > tolerance) {
                    withoutCheckMisses++;
                }
            }

            // Measured: 13 misses without the check, 2 with it. The check is a second start, not a
            // guarantee, so assert a large reduction rather than zero.
            assertTrue(withoutCheckMisses >= 8, "the scenario should be hard enough to need the check: " + withoutCheckMisses);
            assertTrue(withCheckMisses * 4 <= withoutCheckMisses,
                    "misses with check " + withCheckMisses + ", without " + withoutCheckMisses);
        }

        @Test
        void canBeDisabled() {
            double[][] positions = {{0, 0}, {50, 0}, {100, 0}};
            TrilaterationSolver solver = TrilaterationSolver.builder().mirrorCheck(false).build();

            TrilaterationResult result = solver.solve(positions, exactDistances(positions, truth));

            assertFalse(result.isAmbiguous());
            assertTrue(result.isDegenerateGeometry());
        }

        private static double cost(TrilaterationResult result) {
            double sum = 0;
            for (double r : result.getResiduals()) {
                sum += r * r;
            }
            return sum;
        }
    }

    @Nested
    class Robust {

        private final double[][] anchors = {{0, 0}, {100, 0}, {0, 100}, {100, 100}, {50, -20}, {-20, 50}, {120, 50}, {50, 120}};
        private final double[] truth = {35, 60};

        private double[] measurementsWithOutlier(long seed) {
            double[] distances = noisy(new Random(seed), exactDistances(anchors, truth), 0.5);
            distances[2] += 20; // e.g. a non-line-of-sight measurement
            return distances;
        }

        @Test
        void robustLossesResistAnOutlier() {
            double[] distances = measurementsWithOutlier(31);
            double leastSquaresError = TrilaterationFunction.distance(truth, SOLVER.solve(anchors, distances).getPoint());

            for (RobustLoss loss : new RobustLoss[] {RobustLoss.HUBER, RobustLoss.CAUCHY}) {
                TrilaterationResult result = TrilaterationSolver.builder().robustLoss(loss).build().solve(anchors, distances);
                double error = TrilaterationFunction.distance(truth, result.getPoint());

                assertTrue(error < 1.0, loss + " error " + error);
                assertTrue(leastSquaresError > 3 * error, loss + " vs least squares error " + leastSquaresError);
                // The outlier stands out in the residuals.
                assertEquals(-20, result.getResiduals()[2], 2.0);
            }
        }

        @Test
        void robustLossesWorkWithKnownStandardDeviations() {
            TrilaterationProblem problem = TrilaterationProblem.of(anchors, measurementsWithOutlier(32))
                    .withStandardDeviations(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5);

            TrilaterationResult result = TrilaterationSolver.builder().robustLoss(RobustLoss.HUBER).build().solve(problem);

            assertTrue(TrilaterationFunction.distance(truth, result.getPoint()) < 1.0, result::toString);
            assertTrue(result.getCovariance().isPresent());
        }

        @Test
        void robustLossesAgreeWithLeastSquaresWithoutOutliers() {
            // Without outliers the robust estimates lose only a few percent of efficiency, so
            // averaged over many draws their error is close to least squares.
            Random random = new Random(33);
            double leastSquaresError = 0;
            double[] robustError = new double[2];
            RobustLoss[] losses = {RobustLoss.HUBER, RobustLoss.CAUCHY};
            for (int t = 0; t < 400; t++) {
                double[] distances = noisy(random, exactDistances(anchors, truth), 0.5);
                leastSquaresError += TrilaterationFunction.distance(truth, SOLVER.solve(anchors, distances).getPoint());
                for (int k = 0; k < losses.length; k++) {
                    robustError[k] += TrilaterationFunction.distance(truth,
                            TrilaterationSolver.builder().robustLoss(losses[k]).build().solve(anchors, distances).getPoint());
                }
            }
            for (int k = 0; k < losses.length; k++) {
                assertEquals(1.0, robustError[k] / leastSquaresError, 0.1, losses[k].toString());
            }
        }

        @Test
        void exactMeasurementsStayExact() {
            TrilaterationResult result = TrilaterationSolver.builder().robustLoss(RobustLoss.CAUCHY).build()
                    .solve(anchors, exactDistances(anchors, truth));

            assertArrayEquals(truth, result.getPoint(), 1e-6);
        }
    }

    private static double[] noisy(Random random, double[] distances, double sigma) {
        double[] noisy = distances.clone();
        for (int i = 0; i < noisy.length; i++) {
            noisy[i] = Math.max(0, noisy[i] + sigma * random.nextGaussian());
        }
        return noisy;
    }

    private static double[] randomPoint(Random random, int dimension, double low, double high) {
        double[] point = new double[dimension];
        for (int j = 0; j < dimension; j++) {
            point[j] = low + (high - low) * random.nextDouble();
        }
        return point;
    }

    @Nested
    class Configuration {

        @Test
        void failureToConvergeThrowsTrilaterationException() {
            TrilaterationSolver solver = TrilaterationSolver.builder().maxEvaluations(1).build();
            TrilaterationProblem problem = TrilaterationProblem.of(new double[][] {{0, 0}, {10, 0}, {0, 10}}, new double[] {5, 7, 8})
                    .withInitialGuess(1000, 1000);

            TrilaterationException e = assertThrows(TrilaterationException.class, () -> solver.solve(problem));
            assertTrue(e.getMessage().startsWith("Trilateration failed"));
        }

        @Test
        void builderRejectsInvalidSettings() {
            assertThrows(IllegalArgumentException.class, () -> TrilaterationSolver.builder().maxIterations(0));
            assertThrows(IllegalArgumentException.class, () -> TrilaterationSolver.builder().maxEvaluations(-1));
            assertThrows(IllegalArgumentException.class, () -> TrilaterationSolver.builder().costRelativeTolerance(-1));
            assertThrows(IllegalArgumentException.class, () -> TrilaterationSolver.builder().parameterRelativeTolerance(Double.NaN));
        }

        @Test
        void customToleranceStillConverges() {
            TrilaterationSolver solver = TrilaterationSolver.builder()
                    .maxIterations(50)
                    .maxEvaluations(100)
                    .costRelativeTolerance(1e-6)
                    .parameterRelativeTolerance(1e-6)
                    .build();
            double[][] positions = {{0, 0}, {10, 0}, {0, 10}};

            assertArrayEquals(new double[] {3, 4}, solver.solve(positions, exactDistances(positions, new double[] {3, 4})).getPoint(), 1e-4);
        }

        @Test
        void resultReturnsDefensiveCopies() {
            TrilaterationResult result = SOLVER.solve(new double[][] {{0, 0}, {10, 0}, {0, 10}, {10, 10}}, new double[] {5, 7, 8, 9});

            double[] point = result.getPoint();
            point[0] = Double.NaN;
            assertNotSame(point, result.getPoint());
            assertFalse(Double.isNaN(result.getPoint()[0]));

            double[][] covariance = result.getCovariance().orElseThrow();
            covariance[0][0] = Double.NaN;
            assertFalse(Double.isNaN(result.getCovariance().orElseThrow()[0][0]));

            assertTrue(result.toString().startsWith("TrilaterationResult{point="));
            assertTrue(result.getIterations() > 0);
            assertTrue(result.getEvaluations() >= result.getIterations());
        }
    }
}
