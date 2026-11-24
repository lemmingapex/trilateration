package com.lemmingapex.trilateration;

import java.util.ArrayList;
import java.util.List;

/**
 * Records what the solver did, for visualizations and diagnostics. Package-private: not part of
 * the public API.
 */
final class SolverTrace {

    /** The closed-form linear estimate. */
    double[] linearEstimate;

    /** Weighted centroid of the anchors. */
    double[] centroid;

    /** Unit vector along which the anchors are flattest; the mirror check reflects across its normal plane. */
    double[] flattestDirection;

    /**
     * One entry per optimizer run, in order: the primary fit, then the mirror check. Each run lists
     * its starting point followed by every accepted Levenberg-Marquardt iterate (including the
     * points of any robust reweighting rounds).
     */
    final List<List<double[]>> runs = new ArrayList<>();

    void startRun(double[] start) {
        List<double[]> run = new ArrayList<>();
        run.add(start.clone());
        runs.add(run);
    }

    void step(double[] point) {
        runs.get(runs.size() - 1).add(point.clone());
    }
}
