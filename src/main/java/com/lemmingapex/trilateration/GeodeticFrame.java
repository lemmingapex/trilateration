package com.lemmingapex.trilateration;

import java.util.Objects;

/**
 * A local east-north-up (ENU) coordinate frame on the WGS84 ellipsoid, for solving trilateration
 * problems whose anchors are given as latitude, longitude and, optionally, altitude.
 *
 * <p>Latitude and longitude are not Euclidean coordinates, so they cannot be passed to the solver
 * directly. This frame converts them to metres east, north and up of an origin near the anchors,
 * where the solver applies, and converts the solution back:
 *
 * <pre>{@code
 * double[][] anchorsLatLon = { { 40.01490, -105.26970 }, { 40.01520, -105.26890 }, ... };
 * GeodeticFrame frame = GeodeticFrame.around(anchorsLatLon);
 *
 * TrilaterationResult result = solver.solve(frame.toLocal(anchorsLatLon), distancesInMetres);
 * double[] latLon = frame.toGeodetic(result.getPoint());
 * double[] sigmaMetres = result.getStandardDeviation().get();   // east, north
 * }</pre>
 *
 * <p><b>Three dimensions.</b> Points given as {@code {latitude, longitude, altitude}} map to
 * {@code {east, north, up}}. The frame is a rotation and translation of Earth-centred,
 * Earth-fixed (ECEF) coordinates, so straight-line distances are preserved exactly at any scale.
 *
 * <p><b>Two dimensions.</b> Points given as {@code {latitude, longitude}} are taken at the frame's
 * altitude and map to {@code {east, north}}, their projection onto the horizontal plane through
 * the origin. This ignores the curvature of the Earth, which changes a straight-line distance by
 * about {@code d³ / (8 R²)}: 3 mm at 10 km and 3 m at 100 km. Use three dimensions when the
 * anchors and the target differ in height by more than the accuracy you need.
 *
 * <p>Angles are in degrees and altitudes in metres above the WGS84 ellipsoid, as reported by GNSS
 * receivers. Heights above mean sea level differ from these by an offset that is nearly constant
 * over a few kilometres, so they work too, as long as all points use the same kind of height.
 * The frame is meant for points within a few hundred kilometres of its origin.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class GeodeticFrame {

    /** WGS84 semi-major axis, in metres. */
    static final double A = 6378137.0;
    /** WGS84 flattening. */
    static final double F = 1 / 298.257223563;
    /** WGS84 semi-minor axis, in metres. */
    static final double B = A * (1 - F);
    /** First eccentricity squared. */
    static final double E2 = F * (2 - F);
    /** Second eccentricity squared. */
    private static final double EP2 = E2 / (1 - E2);

    private final double latitude;
    private final double longitude;
    private final double altitude;
    private final double[] origin;
    private final double sinLat;
    private final double cosLat;
    private final double sinLon;
    private final double cosLon;

    private GeodeticFrame(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.origin = ecef(latitude, longitude, altitude);
        double phi = Math.toRadians(latitude);
        double lambda = Math.toRadians(longitude);
        this.sinLat = Math.sin(phi);
        this.cosLat = Math.cos(phi);
        this.sinLon = Math.sin(lambda);
        this.cosLon = Math.cos(lambda);
    }

    /**
     * Creates a frame with its origin on the ellipsoid surface (altitude 0).
     *
     * @param latitude  latitude of the origin, in degrees, from -90 to 90
     * @param longitude longitude of the origin, in degrees
     * @return a new frame
     * @throws IllegalArgumentException if a value is out of range or not finite
     */
    public static GeodeticFrame at(double latitude, double longitude) {
        return at(latitude, longitude, 0);
    }

    /**
     * Creates a frame with the given origin. In two dimensions, points are taken at the origin's
     * altitude, so set it to the height of the site.
     *
     * @param latitude  latitude of the origin, in degrees, from -90 to 90
     * @param longitude longitude of the origin, in degrees
     * @param altitude  altitude of the origin, in metres above the ellipsoid
     * @return a new frame
     * @throws IllegalArgumentException if a value is out of range or not finite
     */
    public static GeodeticFrame at(double latitude, double longitude, double altitude) {
        requireGeodetic(new double[] {latitude, longitude, altitude}, "origin");
        return new GeodeticFrame(latitude, normalizeLongitude(longitude), altitude);
    }

    /**
     * Creates a frame centred on a set of points, such as the anchors. The origin lies under the
     * points' centroid, at their mean altitude (0 for two-dimensional points). The centroid is
     * computed in Earth-centred coordinates, so points on both sides of the 180th meridian or
     * around a pole are handled correctly.
     *
     * @param points one or more points, all {@code {latitude, longitude}} or all
     *               {@code {latitude, longitude, altitude}}, in degrees and metres
     * @return a new frame
     * @throws NullPointerException     if {@code points} or any point is {@code null}
     * @throws IllegalArgumentException if there are no points, the points have mixed or wrong
     *                                  lengths or invalid values, or they are spread over so much
     *                                  of the globe that no local frame suits them
     */
    public static GeodeticFrame around(double[][] points) {
        Objects.requireNonNull(points, "points");
        if (points.length == 0) {
            throw new IllegalArgumentException("At least one point is required.");
        }
        int length = Objects.requireNonNull(points[0], "points[0]").length;
        double[] sum = new double[3];
        double altitudeSum = 0;
        for (int i = 0; i < points.length; i++) {
            double[] point = Objects.requireNonNull(points[i], "points[" + i + "]");
            if (point.length != length) {
                throw new IllegalArgumentException("All points must have the same length: points[0] has "
                        + length + " values but points[" + i + "] has " + point.length + ".");
            }
            requireGeodetic(point, "points[" + i + "]");
            double h = length == 3 ? point[2] : 0;
            double[] xyz = ecef(point[0], point[1], h);
            for (int k = 0; k < 3; k++) {
                sum[k] += xyz[k];
            }
            altitudeSum += h;
        }
        double x = sum[0] / points.length;
        double y = sum[1] / points.length;
        double z = sum[2] / points.length;
        if (Math.sqrt(x * x + y * y + z * z) < A / 2) {
            throw new IllegalArgumentException("The points are spread over too much of the globe for a local frame.");
        }
        double[] centre = geodetic(x, y, z);
        return new GeodeticFrame(centre[0], centre[1], altitudeSum / points.length);
    }

    /**
     * Converts a point to local coordinates in metres.
     *
     * @param point {@code {latitude, longitude}} in degrees, or {@code {latitude, longitude,
     *              altitude}} with the altitude in metres
     * @return {@code {east, north}} for a two-dimensional point, {@code {east, north, up}} for a
     *         three-dimensional one, in metres from the origin
     * @throws NullPointerException     if {@code point} is {@code null}
     * @throws IllegalArgumentException if the point does not have 2 or 3 valid values
     */
    public double[] toLocal(double... point) {
        Objects.requireNonNull(point, "point");
        requireGeodetic(point, "point");
        double[] xyz = ecef(point[0], point[1], point.length == 3 ? point[2] : altitude);
        double dx = xyz[0] - origin[0];
        double dy = xyz[1] - origin[1];
        double dz = xyz[2] - origin[2];
        double east = -sinLon * dx + cosLon * dy;
        double north = -sinLat * cosLon * dx - sinLat * sinLon * dy + cosLat * dz;
        if (point.length == 2) {
            return new double[] {east, north};
        }
        double up = cosLat * cosLon * dx + cosLat * sinLon * dy + sinLat * dz;
        return new double[] {east, north, up};
    }

    /**
     * Converts several points to local coordinates, as {@link #toLocal(double...)} does for one.
     *
     * @param points points in geodetic coordinates
     * @return the points in local coordinates, in the same order
     * @throws NullPointerException     if {@code points} or any point is {@code null}
     * @throws IllegalArgumentException if a point does not have 2 or 3 valid values
     */
    public double[][] toLocal(double[][] points) {
        Objects.requireNonNull(points, "points");
        double[][] local = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            local[i] = toLocal(Objects.requireNonNull(points[i], "points[" + i + "]"));
        }
        return local;
    }

    /**
     * Converts local coordinates back to latitude, longitude and, in three dimensions, altitude.
     * This is the exact inverse of {@link #toLocal(double...)}: a two-dimensional point is mapped
     * to the position at the frame's altitude that projects onto it.
     *
     * @param local {@code {east, north}} or {@code {east, north, up}}, in metres from the origin
     * @return {@code {latitude, longitude}} or {@code {latitude, longitude, altitude}}, in degrees
     *         and metres; longitudes are in [-180, 180]
     * @throws NullPointerException     if {@code local} is {@code null}
     * @throws IllegalArgumentException if the point does not have 2 or 3 finite values
     */
    public double[] toGeodetic(double... local) {
        Objects.requireNonNull(local, "local");
        if (local.length != 2 && local.length != 3) {
            throw new IllegalArgumentException("A local point needs 2 or 3 coordinates but had " + local.length + ".");
        }
        for (int i = 0; i < local.length; i++) {
            if (!Double.isFinite(local[i])) {
                throw new IllegalArgumentException("local[" + i + "] must be finite but was " + local[i] + ".");
            }
        }
        if (local.length == 3) {
            return fromEnu(local[0], local[1], local[2]);
        }
        // Find the height above the plane at which the point reaches the frame's altitude.
        double up = 0;
        double[] point = fromEnu(local[0], local[1], up);
        for (int i = 0; i < 20 && Math.abs(point[2] - altitude) > 1e-9; i++) {
            up -= point[2] - altitude;
            point = fromEnu(local[0], local[1], up);
        }
        return new double[] {point[0], point[1]};
    }

    /**
     * Converts several local points back to geodetic coordinates, as
     * {@link #toGeodetic(double...)} does for one.
     *
     * @param locals points in local coordinates
     * @return the points in geodetic coordinates, in the same order
     * @throws NullPointerException     if {@code locals} or any point is {@code null}
     * @throws IllegalArgumentException if a point does not have 2 or 3 finite values
     */
    public double[][] toGeodetic(double[][] locals) {
        Objects.requireNonNull(locals, "locals");
        double[][] geodetic = new double[locals.length][];
        for (int i = 0; i < locals.length; i++) {
            geodetic[i] = toGeodetic(Objects.requireNonNull(locals[i], "locals[" + i + "]"));
        }
        return geodetic;
    }

    /** {@return the latitude of the origin, in degrees} */
    public double getLatitude() {
        return latitude;
    }

    /** {@return the longitude of the origin, in degrees, in [-180, 180]} */
    public double getLongitude() {
        return longitude;
    }

    /** {@return the altitude of the origin, in metres above the ellipsoid} */
    public double getAltitude() {
        return altitude;
    }

    private double[] fromEnu(double east, double north, double up) {
        double x = origin[0] - sinLon * east - sinLat * cosLon * north + cosLat * cosLon * up;
        double y = origin[1] + cosLon * east - sinLat * sinLon * north + cosLat * sinLon * up;
        double z = origin[2] + cosLat * north + sinLat * up;
        return geodetic(x, y, z);
    }

    /**
     * Converts geodetic coordinates to Earth-centred, Earth-fixed coordinates.
     *
     * @param latitude  in degrees
     * @param longitude in degrees
     * @param altitude  in metres above the ellipsoid
     * @return {@code {x, y, z}} in metres
     */
    static double[] ecef(double latitude, double longitude, double altitude) {
        double phi = Math.toRadians(latitude);
        double lambda = Math.toRadians(longitude);
        double sinPhi = Math.sin(phi);
        double cosPhi = Math.cos(phi);
        double n = A / Math.sqrt(1 - E2 * sinPhi * sinPhi);
        return new double[] {
            (n + altitude) * cosPhi * Math.cos(lambda),
            (n + altitude) * cosPhi * Math.sin(lambda),
            (n * (1 - E2) + altitude) * sinPhi,
        };
    }

    /**
     * Converts Earth-centred, Earth-fixed coordinates to geodetic coordinates, with Heikkinen's
     * closed-form solution (exact up to rounding for points away from the Earth's centre).
     *
     * @param x metres
     * @param y metres
     * @param z metres
     * @return {@code {latitude, longitude, altitude}} in degrees and metres
     */
    static double[] geodetic(double x, double y, double z) {
        double a2 = A * A;
        double b2 = B * B;
        double p2 = x * x + y * y;
        double p = Math.sqrt(p2);
        double z2 = z * z;
        double f = 54 * b2 * z2;
        double g = p2 + (1 - E2) * z2 - E2 * (a2 - b2);
        double c = E2 * E2 * f * p2 / (g * g * g);
        double s = Math.cbrt(1 + c + Math.sqrt(c * c + 2 * c));
        double k = s + 1 + 1 / s;
        double pp = f / (3 * k * k * g * g);
        double q = Math.sqrt(1 + 2 * E2 * E2 * pp);
        double r0 = -pp * E2 * p / (1 + q)
                + Math.sqrt(Math.max(0, a2 / 2 * (1 + 1 / q) - pp * (1 - E2) * z2 / (q * (1 + q)) - pp * p2 / 2));
        double t = p - E2 * r0;
        double u = Math.sqrt(t * t + z2);
        double v = Math.sqrt(t * t + (1 - E2) * z2);
        double z0 = b2 * z / (A * v);
        double height = u * (1 - b2 / (A * v));
        double latitude = Math.toDegrees(Math.atan2(z + EP2 * z0, p));
        double longitude = Math.toDegrees(Math.atan2(y, x));
        return new double[] {latitude, longitude, height};
    }

    private static double normalizeLongitude(double longitude) {
        if (longitude >= -180 && longitude <= 180) {
            return longitude;
        }
        double wrapped = ((longitude + 180) % 360 + 360) % 360 - 180;
        return wrapped == -180 ? 180 : wrapped;
    }

    private static void requireGeodetic(double[] point, String name) {
        if (point.length != 2 && point.length != 3) {
            throw new IllegalArgumentException(name + " needs a latitude, a longitude and optionally an altitude, but had "
                    + point.length + " values.");
        }
        for (int i = 0; i < point.length; i++) {
            if (!Double.isFinite(point[i])) {
                throw new IllegalArgumentException(name + "[" + i + "] must be finite but was " + point[i] + ".");
            }
        }
        if (point[0] < -90 || point[0] > 90) {
            throw new IllegalArgumentException(name + " has latitude " + point[0] + ", outside [-90, 90].");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeodeticFrame other)) {
            return false;
        }
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && Double.compare(altitude, other.altitude) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(latitude);
        result = 31 * result + Double.hashCode(longitude);
        return 31 * result + Double.hashCode(altitude);
    }

    @Override
    public String toString() {
        return "GeodeticFrame{latitude=" + latitude + ", longitude=" + longitude + ", altitude=" + altitude + "}";
    }
}
