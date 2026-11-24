package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrilaterationProblemTest {

    private static final double[][] POSITIONS = {{0, 0}, {10, 0}, {0, 10}};
    private static final double[] DISTANCES = {5, 7, 8};

    private static TrilaterationProblem valid() {
        return TrilaterationProblem.of(POSITIONS, DISTANCES);
    }

    static Stream<Arguments> invalidInputs() {
        double nan = Double.NaN;
        double inf = Double.POSITIVE_INFINITY;
        return Stream.of(
                Arguments.argumentSet("no positions", (Executable) () -> TrilaterationProblem.of(new double[0][], new double[0])),
                Arguments.argumentSet("count mismatch", (Executable) () -> TrilaterationProblem.of(POSITIONS, new double[] {1, 2})),
                Arguments.argumentSet("zero dimension", (Executable) () -> TrilaterationProblem.of(new double[][] {{}, {}}, new double[] {1, 1})),
                Arguments.argumentSet("ragged positions", (Executable) () -> TrilaterationProblem.of(new double[][] {{0, 0}, {1}}, new double[] {1, 1})),
                Arguments.argumentSet("NaN coordinate", (Executable) () -> TrilaterationProblem.of(new double[][] {{0, nan}}, new double[] {1})),
                Arguments.argumentSet("infinite coordinate", (Executable) () -> TrilaterationProblem.of(new double[][] {{inf, 0}}, new double[] {1})),
                Arguments.argumentSet("NaN distance", (Executable) () -> TrilaterationProblem.of(POSITIONS, new double[] {nan, 1, 1})),
                Arguments.argumentSet("infinite distance", (Executable) () -> TrilaterationProblem.of(POSITIONS, new double[] {inf, 1, 1})),
                Arguments.argumentSet("negative distance", (Executable) () -> TrilaterationProblem.of(POSITIONS, new double[] {-1, 1, 1})),
                Arguments.argumentSet("standard deviation count", (Executable) () -> valid().withStandardDeviations(1, 1)),
                Arguments.argumentSet("zero standard deviation", (Executable) () -> valid().withStandardDeviations(1, 0, 1)),
                Arguments.argumentSet("negative standard deviation", (Executable) () -> valid().withStandardDeviations(1, -1, 1)),
                Arguments.argumentSet("NaN standard deviation", (Executable) () -> valid().withStandardDeviations(1, nan, 1)),
                Arguments.argumentSet("initial guess dimension", (Executable) () -> valid().withInitialGuess(1, 2, 3)),
                Arguments.argumentSet("NaN initial guess", (Executable) () -> valid().withInitialGuess(nan, 0)));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void rejectsInvalidInput(Executable construction) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, construction);
        assertFalse(e.getMessage().isEmpty());
    }

    @Test
    void rejectsNulls() {
        assertThrows(NullPointerException.class, () -> TrilaterationProblem.of(null, DISTANCES));
        assertThrows(NullPointerException.class, () -> TrilaterationProblem.of(POSITIONS, null));
        assertThrows(NullPointerException.class, () -> TrilaterationProblem.of(new double[][] {{0, 0}, null}, new double[] {1, 1}));
        assertThrows(NullPointerException.class, () -> valid().withStandardDeviations((double[]) null));
        assertThrows(NullPointerException.class, () -> valid().withInitialGuess((double[]) null));
        assertThrows(NullPointerException.class, () -> new TrilaterationSolver().solve(null));
    }

    @Test
    void acceptsZeroDistance() {
        assertEquals(0, TrilaterationProblem.of(new double[][] {{1, 2}}, new double[] {0}).getDistances()[0]);
    }

    @Test
    void doesNotModifyOrRetainCallerArrays() {
        double[][] positions = {{0, 0}, {10, 0}};
        double[] distances = {0, 7};
        double[] sigmas = {1, 2};
        double[] guess = {1, 1};

        TrilaterationProblem problem = TrilaterationProblem.of(positions, distances)
                .withStandardDeviations(sigmas)
                .withInitialGuess(guess);
        positions[1][0] = 99;
        distances[1] = 99;
        sigmas[1] = 99;
        guess[1] = 99;

        assertArrayEquals(new double[] {0, 7}, problem.getDistances());
        assertEquals(10, problem.getPositions()[1][0]);
        assertArrayEquals(new double[] {1, 2}, problem.getStandardDeviations());
        assertArrayEquals(new double[] {1, 1}, problem.getInitialGuess());
    }

    @Test
    void gettersReturnCopies() {
        TrilaterationProblem problem = valid();

        problem.getPositions()[0][0] = 99;
        problem.getDistances()[0] = 99;

        assertEquals(0, problem.getPositions()[0][0]);
        assertEquals(5, problem.getDistances()[0]);
    }

    @Test
    void optionalPartsDefaultToAbsent() {
        TrilaterationProblem problem = valid();

        assertFalse(problem.hasStandardDeviations());
        assertNull(problem.getStandardDeviations());
        assertNull(problem.getInitialGuess());
        assertEquals(2, problem.getDimension());
        assertEquals(3, problem.getMeasurementCount());
        assertTrue(problem.withStandardDeviations(1, 1, 1).hasStandardDeviations());
    }

    @Test
    void withMethodsReturnNewInstances() {
        TrilaterationProblem problem = valid();
        TrilaterationProblem weighted = problem.withStandardDeviations(1, 2, 3);

        assertFalse(problem.hasStandardDeviations());
        assertNotEquals(problem, weighted);
    }

    @Test
    void equalsAndHashCodeUseValues() {
        TrilaterationProblem a = valid().withStandardDeviations(1, 2, 3).withInitialGuess(1, 1);
        TrilaterationProblem b = TrilaterationProblem.of(new double[][] {{0, 0}, {10, 0}, {0, 10}}, new double[] {5, 7, 8})
                .withStandardDeviations(1, 2, 3).withInitialGuess(1, 1);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a, a);
        assertNotEquals(a, valid());
        assertNotEquals(a, "not a problem");
        assertTrue(a.toString().contains("standardDeviations=[1.0, 2.0, 3.0]"));
    }
}
