package com.lemmingapex.trilateration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RobustLossTest {

    @Test
    void noneIsLeastSquares() {
        assertEquals(1.0, RobustLoss.NONE.weight(100));
        assertEquals(9.0, RobustLoss.NONE.cost(3));
        assertTrue(RobustLoss.NONE.isNone());
        assertEquals(Double.POSITIVE_INFINITY, RobustLoss.NONE.getTuningConstant());
    }

    @Test
    void huberIsQuadraticThenLinear() {
        RobustLoss huber = RobustLoss.huber(2);

        assertEquals(1.0, huber.weight(1.5));
        assertEquals(0.5, huber.weight(-4));
        assertEquals(2.25, huber.cost(1.5));
        assertEquals(2 * 2 * 4 - 4, huber.cost(4));
        // Continuous at the threshold.
        assertEquals(huber.cost(2), huber.cost(Math.nextUp(2.0)), 1e-12);
    }

    @Test
    void cauchyRedescends() {
        RobustLoss cauchy = RobustLoss.cauchy(2);

        assertEquals(1.0, cauchy.weight(0));
        assertEquals(0.5, cauchy.weight(2));
        assertTrue(cauchy.weight(100) < 1e-3);
        assertEquals(4 * Math.log(2), cauchy.cost(2), 1e-12);
        assertEquals(1e-6, cauchy.cost(1e-3), 1e-12); // ~u^2 near zero
    }

    @Test
    void presetsUseStandardTuningConstants() {
        assertEquals(1.345, RobustLoss.HUBER.getTuningConstant());
        assertEquals(2.385, RobustLoss.CAUCHY.getTuningConstant());
        assertEquals("RobustLoss.huber(1.345)", RobustLoss.HUBER.toString());
        assertEquals("RobustLoss.NONE", RobustLoss.NONE.toString());
    }

    @Test
    void rejectsInvalidTuningConstants() {
        assertThrows(IllegalArgumentException.class, () -> RobustLoss.huber(0));
        assertThrows(IllegalArgumentException.class, () -> RobustLoss.cauchy(-1));
        assertThrows(IllegalArgumentException.class, () -> RobustLoss.huber(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> RobustLoss.cauchy(Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> TrilaterationSolver.builder().robustLoss(null));
    }
}
