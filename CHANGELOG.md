# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- `isAmbiguous()` returned `false` when a whole circle of positions fits the measurements: collinear
  anchors or at most two anchors in 3D, or a single anchor in 2D. These results are now ambiguous,
  with a mirror-image alternative.
- `isAmbiguous()` could return `true` with an alternative that was the same solution: with
  inconsistent measurements, the mirror check's second run sometimes stopped a tiny distance from
  the first in the same minimum. Solutions now count as distinct only if the step between them
  changes the fit noticeably.

## [2.0.0] - 2026-11-24

A rewrite with a new, self-contained API. The lists below cover everything that changed for 1.x users.

### Added

- `TrilaterationSolver`, an immutable and thread-safe entry point, with a builder for the
  evaluation limit and convergence tolerances.
- Mirror check: a second optimizer run from the first solution reflected across the anchors'
  flattest plane, which avoids most local minima (`mirrorCheck(boolean)`, on by default).
- `TrilaterationResult.isAmbiguous()` and `getAlternativePoint()` report a mirror-image solution
  that fits the measurements about as well, for (nearly) collinear or coplanar anchors.
- `RobustLoss` (`HUBER`, `CAUCHY`, or custom tuning) for data with outliers, solved by iteratively
  reweighted least squares (`robustLoss(RobustLoss)`).
- `TrilaterationResult.getConfidenceHalfWidths(double)`, using Student-t quantiles when the noise
  level is estimated from the residuals.
- `TrilaterationSvg`, a dependency-free renderer that draws 2D results as SVG: distance circles,
  anchors, the estimate with its confidence region, residuals and any alternative, on a coordinate
  grid with a legend, in light and dark mode.
- `GeodeticFrame`, which converts latitude, longitude and altitude (WGS84) to a local east-north-up
  frame in metres and back, so anchors given in geographic coordinates can be solved directly.
- README figures and an animation of the solver, generated from real solver output by
  `./gradlew renderDocs` and checked for staleness in CI.
- `TrilaterationProblem`, a validated, immutable problem description with optional per-measurement
  standard deviations and an optional initial guess.
- `TrilaterationResult` with the position, residuals, RMS, covariance, per-coordinate standard
  deviation and convergence diagnostics.
- Detection of degenerate anchor geometry (collinear anchors in 2D, coplanar in 3D, too few anchors),
  reported by `TrilaterationResult.isDegenerateGeometry()`.
- `TrilaterationException` for optimizer failures.
- `Automatic-Module-Name: com.lemmingapex.trilateration` for the Java module system.

### Changed

- Requires Java 17 or newer.
- The model minimizes geometric range errors `||x - p_i|| - r_i` instead of squared-distance errors.
  With default settings this is equivalent to the 1.x weighting for well-posed problems.
- Covariance and standard deviations are reported in distance units: scaled by the given
  standard deviations, or by the unbiased noise estimate `chi^2 / (m - n)` when the noise is
  estimated from residuals. The 1.x values were unscaled.
- The optimizer starts from a closed-form linear estimate, weighted by the standard deviations when
  given, instead of the centroid of the anchors.
- Input arrays are copied and never modified. Zero distances are accepted; negative, NaN and infinite
  values are rejected with `IllegalArgumentException`.
- Replaced Apache Commons Math 3 with Hipparchus 4. No math library types appear in the public API.
- Build: Gradle 9 with the Kotlin DSL and a version catalog; publishing through the Maven Central
  Portal; CI on GitHub Actions instead of Travis CI; tests on JUnit 6 with coverage reports.

### Removed

- `NonLinearLeastSquaresSolver`, `LinearLeastSquaresSolver` and the public `TrilaterationFunction`.
- The `debugInfo` options that printed to standard output.
- The bundled reference PDF.

### Fixed

- Collinear (2D) or coplanar (3D) anchors could leave the optimizer on a saddle point that matched
  neither solution.
- `TrilaterationFunction` modified the caller's `distances` array.
- `LinearLeastSquaresSolver` returned the first anchor's position when its system was singular.

## [1.0.2] - 2017-05-09

### Fixed

- Measurement weights followed the opposite of the inverse-square law, so the solution drifted away
  from anchors with the strongest signal.

## [1.0.1] - 2016-07-13

### Changed

- Upgraded Apache Commons Math and Gradle.
- Published to Maven Central.

## [1.0.0] - 2015-11-10

- Initial release.

[Unreleased]: https://github.com/lemmingapex/trilateration/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/lemmingapex/trilateration/compare/v1.0.2...v2.0.0
[1.0.2]: https://github.com/lemmingapex/trilateration/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/lemmingapex/trilateration/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/lemmingapex/trilateration/releases/tag/v1.0.0
