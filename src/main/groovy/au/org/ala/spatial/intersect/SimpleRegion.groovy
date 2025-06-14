/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/

package au.org.ala.spatial.intersect

import au.org.ala.spatial.util.SpatialUtils
import groovy.util.logging.Slf4j

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 * SimpleRegion enables point to shape intersections, where the shape
 * is stored within SimpleRegion as a circle, bounding box or polygon.
 * <p/>
 * Other utilities include shape presence on a defined grid;
 * fully present, partially present, absent.
 *
 * @author Adam Collins
 */
@Slf4j
//@CompileStatic
class SimpleRegion implements Serializable {

    /**
     * shape type not declared
     */
    public static final int UNDEFINED = 0
    /**
     * shape type bounding box; upper, lower, left, right
     */
    public static final int BOUNDING_BOX = 1
    /**
     * shape type circle; point and radius
     */
    public static final int CIRCLE = 2
    /**
     * shape type polygon; list of points as longitude, latitude pairs
     * last point == first point
     */
    public static final int POLYGON = 3
    /**
     * UNDEFINED state for grid intersection output
     * <p/>
     * can be considered ABSENCE
     */
    public static final byte GI_UNDEFINED = 0
    /**
     * PARTiALLy PRESENT state for grid intersection output
     */
    public static final byte GI_PARTIALLY_PRESENT = 1
    /**
     * FULLY PRESENT state for grid intersection output
     */
    public static final byte GI_FULLY_PRESENT = 2
    /**
     * ABSENCE state for grid intersection output
     */
    public static final byte GI_ABSENCE = 0
    static final long serialVersionUID = -5509351896749940566L
    //private static final Logger logger = log.getLogger(SimpleRegion.class);
    /**
     * assigned shape type
     */
    int type
    /**
     * points store
     * BOUNDING_BOX = double [2][2]
     * CIRCLE = double [1][2]
     * POLYGON, n points (start = end) = double[n][2]
     */
    double[] points
    /**
     * bounding box for types BOUNDING_BOX and POLYGON
     * <p/>
     * bounding_box = double [2][2]
     * where
     * [0][0] = minimum longitude
     * [0][1] = minimum latitude
     * [1][0] = maximum longitude
     * [1][1] = maximum latitude
     */
    double[][] bounding_box //for polygons
    /**
     * radius for type CIRCLE in m
     */
    double radius
    private final int map_offset = 268435456 // half the Earth's circumference at zoom level 21
    private final double map_radius = map_offset / Math.PI

    /**
     * Constructor for a SimpleRegion with no shape
     */
    SimpleRegion() {
        type = UNDEFINED
    }

    /**
     * defines a region by a points string, POLYGON only
     * <p/>
     * TODO: define better format for parsing, including BOUNDING_BOX and CIRCLE
     *
     * @param pointsString points separated by ',' with longitude and latitude separated by ':'
     * @return SimpleRegion object
     */
    static SimpleRegion parseSimpleRegion(String pointsString) {
        if (pointsString.equalsIgnoreCase("none")) {
            return null
        }
        SimpleRegion simpleregion = new SimpleRegion()

        ArrayList<Double> points = new ArrayList<Double>()

        int pos
        int lastpos = 0
        while ((pos = Math.min(pointsString.indexOf(',', lastpos), pointsString.indexOf(' ', lastpos))) > 0) {
            if (pos > lastpos) {
                try {
                    points.add(Double.parseDouble(pointsString.substring(lastpos, pos)))
                } catch (Exception e) {
                    points.add(0.0d)
                }
            }
            lastpos = pos + 1
        }
        // test for extra space
        if (pointsString.charAt(lastpos) == ' ' as char) lastpos++

        //one coordinate pair left
        pos = pointsString.indexOf(' ', lastpos)
        try {
            points.add(Double.parseDouble(pointsString.substring(lastpos, pos)))
            lastpos = pos + 1
        } catch (Exception ignored) {
            points.add(0.0d)
        }
        try {
            points.add(Double.parseDouble(pointsString.substring(lastpos)))
        } catch (Exception ignored) {
            points.add(0.0d)
        }

        //test for box
        //  get min/max long/lat
        //  each point has only one identical lat or long to previous point
        //  4 or 5 points (start and end points may be identical)
        if (((points.size() == 8 && (points.get(0) != points.get(6) || points.get(1) != points.get(7)))
                || (points.size() == 5 && points.get(0) == points.get(8)
                && points.get(1) == points.get(9)))) {

            //get min/max long/lat
            double minlong = 0, minlat = 0, maxlong = 0, maxlat = 0
            for (int i = 0; i < points.size(); i += 2) {
                if (i == 0 || minlong > points.get(i)) {
                    minlong = points.get(i)
                }
                if (i == 0 || maxlong < points.get(i)) {
                    maxlong = points.get(i)
                }
                if (i == 0 || minlat > points.get(i + 1)) {
                    minlat = points.get(i + 1)
                }
                if (i == 0 || maxlat < points.get(i + 1)) {
                    maxlat = points.get(i + 1)
                }
            }

            //  each point has only one identical lat or long to previous point
            int prev_idx = 6
            int i = 0
            for (i = 0; i < 8; i += 2) {
                if ((points.get(i) == points.get(prev_idx))
                        == (points.get(i + 1) == points.get(prev_idx + 1))) {
                    break
                }
                prev_idx = i
            }
            //it is a box if no 'break' occurred
            if (i == 8) {
                simpleregion.setBox(minlong, minlat, maxlong, maxlat)
                return simpleregion
            }
        }

        double[] pointsArray = new double[points.size()]
        for (int i = 0; i < points.size(); i++) {
            pointsArray[i] = points.get(i)
        }
        log.debug("Calculating BBox of a polygon")
        simpleregion.setPolygon(pointsArray)
        return simpleregion
    }

    /**
     * gets number of points for type POLYGON
     * <p/>
     * note: first point = last point
     *
     * @return number of points as int
     */
    int getNumberOfPoints() {
        return (int)(points.length / 2)
    }

    /**
     * gets the bounding box for types POLYGON and BOUNDING_BOX
     *
     * @return bounding box as double[2][2]
     * with [][0] longitude and [][1] latitude
     * minimum values at [0][], maximum values at [1][0]
     */
    double[][] getBoundingBox() {
        return bounding_box
    }

    /**
     * defines the SimpleRegion as type BOUNDING_BOX
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     */
    void setBox(double longitude1, double latitude1, double longitude2, double latitude2) {
        type = BOUNDING_BOX
        points = new double[4]
        points[0] = Math.min(longitude1, longitude2)
        points[1] = Math.min(latitude1, latitude2)
        points[2] = Math.max(longitude1, longitude2)
        points[3] = Math.max(latitude1, latitude2)

        for (int i = 0; i < points.length; i += 2) {
            //fix at -180 and 180
            if (points[i] < -180) {
                points[i] = -180
            }
            if (points[i] > 180) {
                points[i] = 180
            }
            while (points[i + 1] < -90) {
                points[i + 1] = -90
            }
            while (points[i + 1] > 90) {
                points[i + 1] = 90
            }
        }

        bounding_box = new double[2][2]
        bounding_box[0][0] = points[0]
        bounding_box[0][1] = points[2]
        bounding_box[1][0] = points[1]
        bounding_box[1][1] = points[3]
    }

    /**
     * defines the SimpleRegion as type UNDEFINED
     */
    void setNone() {
        type = UNDEFINED
    }

    /**
     * defines the SimpleRegion as type CIRCLE
     *
     * @param longitude
     * @param latitude
     * @param radius_ radius of the circle in m
     */
    void setCircle(double longitude, double latitude, double radius_) {
        type = CIRCLE
        points = new double[2]
        points[0] = longitude
        points[1] = latitude
        radius = radius_
    }

    /**
     * defines the SimpleRegion as type POLYGON
     *
     * @param points_ array of points as longitude and latiude
     *                in double [n][2] where n is the number of points
     */
    void setPolygon(double[] points_) {
        if (points_ != null && points_.length > 1) {
            type = POLYGON
            int i

            for (i = 0; i < points_.length; i += 2) {
                //fix at -180 and 180
                if (points_[i] < -180) {
                    points_[i] = -180
                }
                if (points_[i] > 180) {
                    points_[i] = 180
                }
                while (points_[i + 1] < -90) {
                    points_[i + 1] = -90
                }
                while (points_[i + 1] > 90) {
                    points_[i + 1] = 90
                }
            }

            /* copy and ensure last point == first point */
            int len = points_.length - 2
            if (points_[0] != points_[len] || points_[1] != points_[len + 1]) {
                points = new double[points_.length + 2]
                for (i = 0; i < points_.length; i++) {
                    points[i] = points_[i]
                }
                points[points_.length] = points_[0]
                points[points_.length + 1] = points_[1]
            } else {
                points = new double[points_.length]
                for (i = 0; i < points_.length; i++) {
                    points[i] = points_[i]
                }
            }
            /* bounding box setup */
            bounding_box = new double[2][2]
            bounding_box[0][0] = points[0]
            bounding_box[0][1] = points[1]
            bounding_box[1][0] = points[0]
            bounding_box[1][1] = points[1]
            for (i = 2; i < points.length; i += 2) {
                if (bounding_box[0][0] > points[i]) {
                    bounding_box[0][0] = points[i]
                }
                if (bounding_box[1][0] < points[i]) {
                    bounding_box[1][0] = points[i]
                }
                if (bounding_box[0][1] > points[i + 1]) {
                    bounding_box[0][1] = points[i + 1]
                }
                if (bounding_box[1][1] < points[i + 1]) {
                    bounding_box[1][1] = points[i + 1]
                }
            }
        }
    }

    /**
     * returns true when the point provided is within the SimpleRegion
     * <p/>
     * note: type UNDEFINED implies no boundary, always returns true.
     *
     * @param longitude
     * @param latitude
     * @return true iff point is within or on the edge of this SimpleRegion
     */
    boolean isWithin(double longitude, double latitude) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return true
            case 1:
                /* return for bounding box */
                return (longitude <= points[2] && longitude >= points[0]
                        && latitude <= points[3] && latitude >= points[1])
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0]
                double y = latitude - points[1]
                return Math.sqrt(x * x + y * y) <= radius
            case 3:
                /* determine for Polygon */
                return isWithinPolygon(longitude, latitude)
        }
        return false
    }

    /**
     * returns true when the point provided is within distance of the SimpleRegion
     * <p/>
     * note: type UNDEFINED implies no boundary, always returns true.
     *
     * @param longitude
     * @param latitude
     * @param distance distance in degrees
     * @return true iff point is within or on the edge or near to this SimpleRegion
     */
    boolean isWithin(double longitude, double latitude, double distance) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return true
            case 1:
                /* return for bounding box */
                // Distance going beyond east/west bounds
                return (longitude <= points[2] + distance && longitude >= points[0] - distance && latitude <= points[3] && latitude >= points[1])
                        // Going beyond north/south bounds
                        || (longitude <= points[2] && longitude >= points[0] && latitude <= points[3] + distance && latitude >= points[1] - distance)
                        // Within the quarter-circles at the corners
                        || (Math.pow(longitude - points[0], 2) + Math.pow(latitude - points[1], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[0], 2) + Math.pow(latitude - points[3], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[2], 2) + Math.pow(latitude - points[1], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[2], 2) + Math.pow(latitude - points[3], 2) <= Math.pow(distance, 2))
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0]
                double y = latitude - points[1]
                return (Math.sqrt(x * x + y * y) <= radius + distance)
            case 3:
                /* determine for Polygon */
                return isWithinPolygon(longitude, latitude, distance)
        }
        return false
    }

    /**
     * returns true when the point provided is within the SimpleRegion
     * <p/>
     * note: type UNDEFINED implies no boundary, always returns true.
     *
     * @param longitude
     * @param latitude
     * @return the distance if point is within or on the edge or near to this SimpleRegion,
     *         otherwise null.
     */
    Double distance(double longitude, double latitude, double distance, boolean evenWithin) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return 0d
            case 1:
                /* return for bounding box */
                // Distance going beyond east/west bounds
                if ((longitude <= points[2] + distance && longitude >= points[0] - distance && latitude <= points[3] && latitude >= points[1])
                        // Going beyond north/south bounds
                        || (longitude <= points[2] && longitude >= points[0] && latitude <= points[3] + distance && latitude >= points[1] - distance)
                        // Within the quarter-circles at the corners
                        || (Math.pow(longitude - points[0], 2) + Math.pow(latitude - points[1], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[0], 2) + Math.pow(latitude - points[3], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[2], 2) + Math.pow(latitude - points[1], 2) <= Math.pow(distance, 2))
                        || (Math.pow(longitude - points[2], 2) + Math.pow(latitude - points[3], 2) <= Math.pow(distance, 2))) {
                    return Math.min(
                            Math.min(longitude - (points[2] + distance),
                                    longitude - (points[0] + distance)),
                            Math.min(latitude - (points[3] + distance),
                                    latitude - (points[1] + distance))
                    )
                } else {
                    return null
                }
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0]
                double y = latitude - points[1]
                double rootx2y2 = Math.sqrt(x * x + y * y)
                if (rootx2y2 <= radius + distance) {
                    return rootx2y2 - radius
                } else {
                    return null
                }
            case 3:
                /* determine for Polygon */
                return distancePolygon(longitude, latitude, distance, true, evenWithin)
        }
        return null
    }

    Double distance(double longitude, double latitude, double distance) {
        distance(longitude,latitude,distance,false)
    }

    boolean isWithin_EPSG900913(double longitude, double latitude) {
        switch (type) {
            case 0:
                /* no region defined, must be within this absence of a boundary */
                return true
            case 1:
                /* return for bounding box */
                return (longitude <= points[2] && longitude >= points[0]
                        && latitude <= points[3] && latitude >= points[1])
            case 2:
                /* TODO: fix to use radius units m not degrees */
                double x = longitude - points[0]
                double y = latitude - points[1]
                return Math.sqrt(x * x + y * y) <= radius
            case 3:
                /* determine for Polygon */
                return isWithinPolygon_EPSG900913(longitude, latitude)
        }
        return false
    }

    /**
     * returns true when point is within the polygon
     * <p/>
     * method:
     * treat as segments with target longitude in the middle:
     * <p/>
     * <pre>
     * __-1__|___1_
     *       |
     * </pre>
     * <p/>
     * iterate through points and count number of latitude axis crossings where
     * crossing is > latitude.
     * <p/>
     * point is inside of area when number of crossings is odd;
     * <p/>
     * point is on a polygon edge return true
     *
     * @param longitude
     * @param latitude
     * @return true iff longitude and latitude point is on edge or within polygon
     */
    private boolean isWithinPolygon(double longitude, double latitude) {
        if (longitude > bounding_box[1][0] || longitude < bounding_box[0][0]
                || latitude > bounding_box[1][1] || latitude < bounding_box[0][1]) {
            return false
        }

        //initial segment
        boolean segment = points[0] > longitude

        double y
        int len = points.length
        int score = 0

        for (int i = 2; i < len; i += 2) {
            // is it in a new segment?
            if ((points[i] > longitude) != segment) {
                //lat value at line crossing > target point
                y = (longitude - points[i]) * ((points[i + 1] - points[i - 1]) / (points[i] - points[i - 2])) + points[i + 1]
                if (y > latitude) {
                    score++
                } else if (y == latitude) {
                    //line crossing
                    return true
                }

                segment = !segment
            } else if (points[i] == longitude && points[i + 1] == latitude) {
                //point on point
                return true
            }
        }

        return (score % 2 != 0)
    }

    /**
     * Dot product of vectors p·q = p₁q₁ + p₂q₂
     * @return p1*q1 + p2*q2
     */
    private double dotProduct(double p1, double p2, double q1, double q2) {
        return p1 * q1 + p2 * q2
    }

    /**
     * Magnitude of vectors |p| = √(p₁² + p₂²)
     * @return
     */
    private double magnitude(double p1, double p2) {
        return Math.sqrt((p1 * p1) + (p2 * p2))
    }

    /**
     * returns true if a point is within, or a distance from a polygon.
     *
     * See {@link #distancePolygon(double, double, double, boolean, boolean)} for implementation details.
     *
     * @param longitude
     * @param latitude
     * @return true iff longitude and latitude point is on edge or within or near polygon
     */
    private boolean isWithinPolygon(double longitude, double latitude, double distance) {
        return distancePolygon(longitude, latitude, distance, false, false) != null
    }

    /**
     * returns a distance of a point from a polygon, or 0 if it is
     * within the polygon.
     *
     * See {@link #distancePolygon(double, double, double, boolean, boolean)} for implementation details.
     *
     * @param longitude
     * @param latitude
     * @return Distance from the polygon, 0 if within, null if too far outside.
     */
    private Double distancePolygon(double longitude, double latitude, double distance) {
        return distancePolygon(longitude, latitude, distance, true, false)
    }

    /**
     * returns a distance of a point from a polygon, or 0 if it is
     * within the polygon.  Returns the minimum (rather than the first found)
     * if requested.
     * <p/>
     * method:
     * first check whether the point is within the polygon using {@link #isWithin(double, double)}.
     * <p/>
     * Then measure the distance from each line segment in turn.
     *
     * @param longitude
     * @param latitude
     * @param distance
     * @param findShortestDistance If false, return the first nearby point.  If true, find the minimum distance.
     * @param evenIfInside If true, returns the distance from the edge, even if the point is within the polygon.
     * @return Distance from the polygon, 0 if within, null if too far outside.
     */
    private Double distancePolygon(double longitude, double latitude, double distance, boolean findShortestDistance, boolean evenIfInside) {
        // Set multiplier to -1 if point is within the polygon.
        double multiplier = 1
        if (isWithinPolygon(longitude, latitude)) {
            if (!evenIfInside) {
                return 0d
            } else {
                multiplier = -1
            }
        }

        if (distance <= 0) return null

        // Quick check on the expanded bounding box
        if (longitude > bounding_box[1][0] + distance || longitude < bounding_box[0][0] - distance
                || latitude > bounding_box[1][1] + distance || latitude < bounding_box[0][1] - distance) {
            return null
        }

        int len = points.length
        double minimumDistance = Double.MAX_VALUE

        // x is the target point (longitude,latitude)
        // For each line segment [p,q] in the polygon
        for (int i = 2; i < len; i += 2) {
            double px = points[i - 2]
            double py = points[i - 1]
            double qx = points[i]
            double qy = points[i + 1]

            // Calculate r = (q - p) · (x - p) ÷ |q - p|²
            double r = dotProduct(qx - px, qy - py, longitude - px, latitude - py)
            r /= Math.pow(magnitude(qx - px, qy - py), 2)

            double dist
            if (r < 0) {
                // dist = |x - p|
                dist = magnitude(longitude - px, latitude - py)
            } else if (r > 1) {
                // dist = |q - x|
                dist = magnitude(qx - longitude, qy - latitude)
            } else {
                // √(|x-p|² - (r×|q-p|)²)
                dist = Math.sqrt(
                        Math.pow(magnitude(longitude - px, latitude - py), 2)
                                - Math.pow(r * magnitude(qx - px, qy - py), 2))
            }

            if (!findShortestDistance && dist <= distance) {
                return multiplier * dist
            }

            minimumDistance = Math.min(minimumDistance, dist)
        }

        if (minimumDistance <= distance || multiplier == -1) {
            return multiplier * minimumDistance
        } else {
            return null
        }
    }

    private boolean isWithinPolygon_EPSG900913(double longitude, double latitude) {
        // bounding box test
        if (longitude <= bounding_box[1][0] && longitude >= bounding_box[0][0]
                && latitude <= bounding_box[1][1] && latitude >= bounding_box[0][1]) {

            //initial segment
            int longitudePx = SpatialUtils.convertLngToPixel(longitude)
            boolean segment = SpatialUtils.convertLngToPixel(points[0]) > longitudePx

            int y
            int i
            int len = points.length
            int score = 0

            for (i = 2; i < len; i += 2) {
                // is it in a new segment?
                if ((SpatialUtils.convertLngToPixel(points[i]) > longitudePx) != segment) {
                    //lat value at line crossing > target point
                    y = (int) ((longitudePx - SpatialUtils.convertLngToPixel(points[i])) * ((SpatialUtils.convertLatToPixel(points[i + 1]) - SpatialUtils.convertLatToPixel(points[i - 1])) / (double) (SpatialUtils.convertLngToPixel(points[i]) - SpatialUtils.convertLngToPixel(points[i - 2]))) + SpatialUtils.convertLatToPixel(points[i + 1]))
                    if (y > SpatialUtils.convertLatToPixel(latitude)) {
                        score++
                    } else if (y == SpatialUtils.convertLatToPixel(latitude)) {
                        //line crossing
                        return true
                    }

                    segment = !segment
                } else if (points[i] == longitude && points[i + 1] == latitude) {
                    //point on point
                    return true
                }
            }
            return (score % 2 != 0)
        }
        return false        //not within bounding box
    }

    /**
     * determines overlap with a grid
     * <p/>
     * for type POLYGON
     * when <code>three_state_map</code> is not null populate it with one of:
     * GI_UNDEFINED
     * GI_PARTIALLY_PRESENT
     * GI_FULLY_PRESENT
     * GI_ABSENCE
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     * @param noCellsReturned
     * @return (x, y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    int[][] getOverlapGridCells(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int[][] cells = null
        switch (type) {
            case 0:
                break
            case 1:
                cells = getOverlapGridCells_Box(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map, noCellsReturned)
                break
            case 2:
                break /* TODO: circle grid */
            case 3:
                cells = getOverlapGridCells_Polygon(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, noCellsReturned)
        }

        return cells
    }

    /**
     * stacks PARTIALLY_PRESENT shape outline onto three_state_map
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     */
    void getOverlapGridCells_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        switch (type) {
            case 0:
                break
            case 1:
                getOverlapGridCells_Box_Acc(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map)
                break
            case 2:
                break /* TODO: circle grid */
            case 3:
                getOverlapGridCells_Polygon_Acc(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map)
        }
    }

    int[][] getOverlapGridCells(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        return getOverlapGridCells(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, false)
    }

    /**
     * determines overlap with a grid for a BOUNDING_BOX
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param bb
     * @param three_state_map
     * @param noCellsReturned
     * @return (x, y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    int[][] getOverlapGridCells_Box(double longitude1, double latitude1,
                                    double longitude2, double latitude2, int width, int height, double[][] bb, byte[][] three_state_map, boolean noCellsReturned) {

        double xstep = Math.abs(longitude2 - longitude1) / (double) width
        double ystep = Math.abs(latitude2 - latitude1) / (double) height

        //double maxlong = Math.max(longitude1, longitude2);
        double minlong = Math.min(longitude1, longitude2)
        //double maxlat = Math.max(latitude1, latitude2);
        double minlat = Math.min(latitude1, latitude2)

        //setup minimums from bounding box (TODO: should this have -1 on steps?)
        int xstart = (int) Math.floor((bb[0][0] - minlong) / xstep)
        int ystart = (int) Math.floor((bb[0][1] - minlat) / ystep)
        int xend = (int) Math.ceil((bb[1][0] - minlong) / xstep)
        int yend = (int) Math.ceil((bb[1][1] - minlat) / ystep)
        if (xstart < 0) {
            xstart = 0
        }
        if (ystart < 0) {
            ystart = 0
        }
        if (xend > width) {
            xend = width
        }
        if (yend > height) {
            yend = height
        }

        // fill data with cell coordinates
        int out_width = xend - xstart
        int out_height = yend - ystart
        int j, i, p = 0
        int[][] data = null
        if (!noCellsReturned) {
            data = new int[out_width * out_height][2]
            if (three_state_map == null) {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i
                        data[p][1] = j
                        p++
                    }
                }
            } else {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i
                        data[p][1] = j
                        three_state_map[j][i] = SimpleRegion.GI_FULLY_PRESENT
                        p++
                    }
                }
                //set three state map edges to partially present
                if (xstart < xend && xend > 0) {
                    for (j = ystart; j < yend; j++) {
                        three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT
                        three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT
                    }
                }
                if (ystart < yend && yend > 0) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT
                        three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT
                    }
                }

                //no need to set SimpleRegion.GI_ABSENCE
            }
        } else {
            if (three_state_map == null) {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        data[p][0] = i
                        data[p][1] = j
                        p++
                    }
                }
            } else {
                for (j = ystart; j < yend; j++) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[j][i] = SimpleRegion.GI_FULLY_PRESENT
                    }
                }
                //set three state map edges to partially present
                if (xstart < xend && xend > 0) {
                    for (j = ystart; j < yend; j++) {
                        three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT
                        three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT
                    }
                }
                if (ystart < yend && yend > 0) {
                    for (i = xstart; i < xend; i++) {
                        three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT
                        three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT
                    }
                }
            }
        }
        return data
    }

    double getWidth() {
        return bounding_box[1][0] - bounding_box[0][0]
    }

    double getHeight() {
        return bounding_box[1][1] - bounding_box[0][1]
    }

    int getType() {
        return type
    }

    void saveGridAsImage(byte[][] three_state_map) {
        try {
            long t1 = System.currentTimeMillis()
            BufferedImage bi = new BufferedImage(three_state_map[0].length, three_state_map.length, BufferedImage.TYPE_INT_RGB)
            for (int i = 0; i < three_state_map.length; i++) {
                for (int j = 0; j < three_state_map[i].length; j++) {
                    if (three_state_map[i][j] == 0) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0xFFFFFF)
                    } else if (three_state_map[i][j] == 1) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0x99FF99)
                    } else if (three_state_map[i][j] == 2) {
                        bi.setRGB(j, (three_state_map.length - 1 - i), 0x9999FF)
                    }
                }
            }

            ImageIO.write(bi, "png", File.createTempFile("grd", ".png", new File("d:\\")))
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
    }

    /**
     * determines overlap with a grid for POLYGON
     * <p/>
     * when <code>three_state_map</code> is not null populate it with one of:
     * GI_UNDEFINED
     * GI_PARTIALLY_PRESENT
     * GI_FULLY_PRESENT
     * GI_ABSENCE
     * <p/>
     * 1. Get 3state mask and fill edge passes as 'partial'.
     * then
     * 3. Test 0,0 then progress across vert raster until finding cells[][] entry
     * 4. Repeat from (3).
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     * @param noCellsReturned
     * @return (x, y) as double [][2] for each grid cell at least partially falling
     * within the specified region of the specified resolution beginning at 0,0
     * for minimum longitude and latitude through to xres,yres for maximums
     */
    int[][] getOverlapGridCells_Polygon(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int i, j
        if (three_state_map == null) {
            three_state_map = new byte[height][width]
        }
        log.debug("Calculating overlapped grids over polygons")
        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height

        //to cells
        int x, y, xend, yend, xDirection, icross
        double xcross, endlat, dx1, dx2, dy1, dy2, slope, intercept
        for (j = 2; j < points.length; j += 2) {
            if (points[j + 1] < points[j - 1]) {
                dx1 = points[j]
                dy1 = points[j + 1]
                dx2 = points[j - 2]
                dy2 = points[j - 1]
            } else {
                dx2 = points[j]
                dy2 = points[j + 1]
                dx1 = points[j - 2]
                dy1 = points[j - 1]
            }
            x = (int) ((dx1 - longitude1) / divx)
            y = (int) ((dy1 - latitude1) / divy)
            xend = (int) ((dx2 - longitude1) / divx)
            yend = (int) ((dy2 - latitude1) / divy)

            if (y >= 0 && y < height && x >= 0 && x < width) {
                three_state_map[y][x] = GI_PARTIALLY_PRESENT
            }

            if (x == xend && y == yend) {
                continue
            }

            xDirection = (x < xend) ? 1 : -1

            slope = (dy1 - dy2) / (dx1 - dx2)
            intercept = dy1 - slope * dx1

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            } else { //sloped line
                endlat = dy2
                for (double k = (y + 1) * divy + latitude1; k < endlat; k += (int)(divy)) {
                    //move in yDirection to get x
                    xcross = (k - intercept) / slope
                    icross = (int) ((xcross - longitude1) / divx)

                    while (x != icross && x != xend) {
                        x += xDirection
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT
                        }
                    }

                    if (y != yend) {
                        y++
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            }
        }

        //do raster check
        int[][] data = new int[width * height][2]
        boolean cellsReturned = !noCellsReturned
        int p = 0
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j]
                    } else if (isWithin(j * divx + divx / 2 + longitude1, i * divy + divy / 2 + latitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1]
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j
                    data[p][1] = i
                    p++
                }
            }
        }
        return java.util.Arrays.copyOfRange(data, 0, p)
    }

    void getOverlapGridCells_Polygon_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        int i, j

        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height

        //to cells
        int x, y, xend, yend, xDirection, icross
        double xcross, endlat, dx1, dx2, dy1, dy2, slope, intercept
        for (j = 2; j < points.length; j += 2) {
            if (points[j + 1] < points[j - 1]) {
                dx1 = points[j]
                dy1 = points[j + 1]
                dx2 = points[j - 2]
                dy2 = points[j - 1]
            } else {
                dx2 = points[j]
                dy2 = points[j + 1]
                dx1 = points[j - 2]
                dy1 = points[j - 1]
            }
            x = (int) ((dx1 - longitude1) / divx)
            y = (int) ((dy1 - latitude1) / divy)
            xend = (int) ((dx2 - longitude1) / divx)
            yend = (int) ((dy2 - latitude1) / divy)

            if (x == xend && y == yend) {
                continue
            }

            xDirection = (x < xend) ? 1 : -1

            slope = (dy1 - dy2) / (dx1 - dx2)
            intercept = dy1 - slope * dx1

            if (y >= 0 && y < height && x >= 0 && x < width) {
                three_state_map[y][x] = GI_PARTIALLY_PRESENT
            }

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            } else { //sloped line
                endlat = dy2
                for (double k = (y + 1) * divy + latitude1; k < endlat; k += (int)(divy)) {
                    //move in yDirection to get x
                    xcross = (k - intercept) / slope
                    icross = (int) ((xcross - longitude1) / divx)

                    while (x != icross && x != xend) {
                        x += xDirection
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT
                        }
                    }

                    if (y != yend) {
                        y++
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            three_state_map[y][x] = GI_PARTIALLY_PRESENT
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        three_state_map[y][x] = GI_PARTIALLY_PRESENT
                    }
                }
            }
        }
    }

    int[][] fillAccMask(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height

        int i, j
        //do raster check
        int[][] data = null
        boolean cellsReturned = !noCellsReturned
        if (cellsReturned) {
            data = new int[width * height][2]
        }
        int p = 0
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j]
                    } else if (isWithin(j * divx + divx / 2 + longitude1, i * divy + divy / 2 + latitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1]
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j
                    data[p][1] = i
                    p++
                }
            }
        }
        if (data != null) {
            data = java.util.Arrays.copyOf(data, p)
        }
        return data
    }

    private void getOverlapGridCells_Box_Acc(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, double[][] bb, byte[][] three_state_map) {
        double xstep = Math.abs(longitude2 - longitude1) / (double) width
        double ystep = Math.abs(latitude2 - latitude1) / (double) height

        //double maxlong = Math.max(longitude1, longitude2);
        double minlong = Math.min(longitude1, longitude2)
        //double maxlat = Math.max(latitude1, latitude2);
        double minlat = Math.min(latitude1, latitude2)

        //setup minimums from bounding box (TODO: should this have -1 on steps?)
        int xstart = (int) Math.floor((bb[0][0] - minlong) / xstep)
        int ystart = (int) Math.floor((bb[0][1] - minlat) / ystep)
        int xend = (int) Math.ceil((bb[1][0] - minlong) / xstep)
        int yend = (int) Math.ceil((bb[1][1] - minlat) / ystep)
        if (xstart < 0) {
            xstart = 0
        }
        if (ystart < 0) {
            ystart = 0
        }
        if (xend > width) {
            xend = width
        }
        if (yend > height) {
            yend = height
        }

        // fill data with cell coordinates
        //int out_width = xend - xstart;
        //int out_height = yend - ystart;
        int j, i, p = 0
        //int[][] data = null;

        //set three state map edges to partially present
        if (xstart < xend && xend > 0) {
            for (j = ystart; j < yend; j++) {
                three_state_map[j][xstart] = SimpleRegion.GI_PARTIALLY_PRESENT
                three_state_map[j][xend - 1] = SimpleRegion.GI_PARTIALLY_PRESENT
            }
        }
        if (ystart < yend && yend > 0) {
            for (i = xstart; i < xend; i++) {
                three_state_map[ystart][i] = SimpleRegion.GI_PARTIALLY_PRESENT
                three_state_map[yend - 1][i] = SimpleRegion.GI_PARTIALLY_PRESENT
            }
        }
    }

    int[][] getOverlapGridCells_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        int[][] cells = null
        switch (type) {
            case 0:
                break
            case 1:
                cells = getOverlapGridCells_Box(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map, noCellsReturned)
                break
            case 2:
                break /* TODO: circle grid */
            case 3:
                cells = getOverlapGridCells_Polygon_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, noCellsReturned)
        }

        return cells
    }

    /**
     * stacks PARTIALLY_PRESENT shape outline onto three_state_map
     *
     * @param longitude1
     * @param latitude1
     * @param longitude2
     * @param latitude2
     * @param width
     * @param height
     * @param three_state_map
     */
    void getOverlapGridCells_Acc_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        switch (type) {
            case 0:
                break
            case 1:
                getOverlapGridCells_Box_Acc(longitude1, latitude1, longitude2, latitude2, width, height, bounding_box, three_state_map)
                break
            case 2:
                break /* TODO: circle grid */
            case 3:
                getOverlapGridCells_Polygon_Acc_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map)
        }
    }

    int[][] getOverlapGridCells_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map) {
        return getOverlapGridCells_EPSG900913(longitude1, latitude1, longitude2, latitude2, width, height, three_state_map, false)
    }

    int[][] getOverlapGridCells_Polygon_EPSG900913(double olongitude1, double olatitude1, double olongitude2, double olatitude2, int owidth, int oheight, byte[][] three_state_map, boolean noCellsReturned) {
        int i, j
        if (three_state_map == null) {
            three_state_map = new byte[oheight][owidth]
        }

        int longitude1 = SpatialUtils.convertLngToPixel(olongitude1)
        int longitude2 = SpatialUtils.convertLngToPixel(olongitude2)
        int latitude1 = SpatialUtils.convertLatToPixel(olatitude2)
        int latitude2 = SpatialUtils.convertLatToPixel(olatitude1)
        int scale = 100 //if it is too small the 'fill' operation is messed up
        int width = owidth * scale
        int height = oheight * scale

        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height
        double odivx = (olongitude2 - olongitude1) / owidth
        double odivy = (olatitude2 - olatitude1) / oheight

        int oy, ox

        //to cells
        int x, y, xend, yend, xDirection, icross
        double slope, intercept
        int xcross, endlat, dx1, dx2, dy1, dy2
        for (j = 2; j < points.length; j += 2) {
            if (points[j + 1] > points[j - 1]) {
                dx1 = SpatialUtils.convertLngToPixel(points[j])
                dy1 = SpatialUtils.convertLatToPixel(points[j + 1])
                dx2 = SpatialUtils.convertLngToPixel(points[j - 2])
                dy2 = SpatialUtils.convertLatToPixel(points[j - 1])
            } else {
                dx2 = SpatialUtils.convertLngToPixel(points[j])
                dy2 = SpatialUtils.convertLatToPixel(points[j + 1])
                dx1 = SpatialUtils.convertLngToPixel(points[j - 2])
                dy1 = SpatialUtils.convertLatToPixel(points[j - 1])
            }
            x = (int) ((dx1 - longitude1) / divx)
            y = (int) ((dy1 - latitude1) / divy)
            xend = (int) ((dx2 - longitude1) / divx)
            yend = (int) ((dy2 - latitude1) / divy)

            if (y >= 0 && y < height && x >= 0 && x < width) {
                oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                if (oy >= oheight) {
                    oy = oheight - 1
                }
                if (ox >= owidth) {
                    ox = owidth - 1
                }
                if (oy < 0) {
                    oy = 0
                }
                if (ox < 0) {
                    ox = 0
                }
                three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
            }

            if (x == xend && y == yend) {
                continue
            }

            xDirection = (x < xend) ? 1 : -1

            slope = (dy1 - dy2) / (double) (dx1 - dx2)
            intercept = dy1 - slope * dx1

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            } else { //sloped line
                endlat = dy2
                for (int k = (int) ((y + 1) * divy + latitude1); k < endlat; k +=  (int)(divy)) {
                    //move in yDirection to get x
                    xcross = (int) ((k - intercept) / slope)
                    icross = (int) ((xcross - longitude1) / divx)

                    while (x != icross && x != xend) {
                        x += xDirection
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                            ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                            if (oy >= oheight) {
                                oy = oheight - 1
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1
                            }
                            if (oy < 0) {
                                oy = 0
                            }
                            if (ox < 0) {
                                ox = 0
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                        }
                    }

                    if (y != yend) {
                        y++
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                            ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                            if (oy >= oheight) {
                                oy = oheight - 1
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1
                            }
                            if (oy < 0) {
                                oy = 0
                            }
                            if (ox < 0) {
                                ox = 0
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            }
        }

        //do raster check
        int[][] data = new int[owidth * oheight][2]
        boolean cellsReturned = !noCellsReturned
        int p = 0
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j]
                    } else if (isWithin_EPSG900913(j * odivx + odivx / 2 + olongitude1, i * odivy + odivy / 2 + olatitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1]
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j
                    data[p][1] = i
                    p++
                }
            }
        }
        return java.util.Arrays.copyOfRange(data, 0, p)
    }

    void getOverlapGridCells_Polygon_Acc_EPSG900913(double olongitude1, double olatitude1, double olongitude2, double olatitude2, int owidth, int oheight, byte[][] three_state_map) {
        int i, j
        if (three_state_map == null) {
            three_state_map = new byte[oheight][owidth]
        }

        int longitude1 = SpatialUtils.convertLngToPixel(olongitude1)
        int longitude2 = SpatialUtils.convertLngToPixel(olongitude2)
        int latitude1 = SpatialUtils.convertLatToPixel(olatitude2)
        int latitude2 = SpatialUtils.convertLatToPixel(olatitude1)
        int scale = 16 //if it is too small the 'fill' operation is messed up
        int width = owidth * scale
        int height = oheight * scale

        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height
        double odivx = (olongitude2 - olongitude1) / owidth
        double odivy = (olatitude2 - olatitude1) / oheight

        int oy, ox

        //to cells
        int x, y, xend, yend, xDirection, icross
        double slope, intercept
        int xcross, endlat, dx1, dx2, dy1, dy2
        for (j = 2; j < points.length; j += 2) {
            if (points[j + 1] > points[j - 1]) {
                dx1 = SpatialUtils.convertLngToPixel(points[j])
                dy1 = SpatialUtils.convertLatToPixel(points[j + 1])
                dx2 = SpatialUtils.convertLngToPixel(points[j - 2])
                dy2 = SpatialUtils.convertLatToPixel(points[j - 1])
            } else {
                dx2 = SpatialUtils.convertLngToPixel(points[j])
                dy2 = SpatialUtils.convertLatToPixel(points[j + 1])
                dx1 = SpatialUtils.convertLngToPixel(points[j - 2])
                dy1 = SpatialUtils.convertLatToPixel(points[j - 1])
            }
            x = (int) ((dx1 - longitude1) / divx)
            y = (int) ((dy1 - latitude1) / divy)
            xend = (int) ((dx2 - longitude1) / divx)
            yend = (int) ((dy2 - latitude1) / divy)

            if (y >= 0 && y < height && x >= 0 && x < width) {
                oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                if (oy >= oheight) {
                    oy = oheight - 1
                }
                if (ox >= owidth) {
                    ox = owidth - 1
                }
                if (oy < 0) {
                    oy = 0
                }
                if (ox < 0) {
                    ox = 0
                }
                three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
            }

            if (x == xend && y == yend) {
                continue
            }

            xDirection = (x < xend) ? 1 : -1

            slope = (dy1 - dy2) / (double) (dx1 - dx2)
            intercept = dy1 - slope * dx1

            if (x == xend) {
                //vertical line
                while (y != yend) {
                    y++
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            } else if (y == yend) {
                //horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            } else { //sloped line
                endlat = dy2
                for (int k = (int) ((y + 1) * divy + latitude1); k < endlat; k +=  (int)(divy)) {
                    //move in yDirection to get x
                    xcross = (int) ((k - intercept) / slope)
                    icross = (int) ((xcross - longitude1) / divx)

                    while (x != icross && x != xend) {
                        x += xDirection
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                            ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                            if (oy >= oheight) {
                                oy = oheight - 1
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1
                            }
                            if (oy < 0) {
                                oy = 0
                            }
                            if (ox < 0) {
                                ox = 0
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                        }
                    }

                    if (y != yend) {
                        y++
                        if (y >= 0 && y < height && x >= 0 && x < width) {
                            oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                            ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                            if (oy >= oheight) {
                                oy = oheight - 1
                            }
                            if (ox >= owidth) {
                                ox = owidth - 1
                            }
                            if (oy < 0) {
                                oy = 0
                            }
                            if (ox < 0) {
                                ox = 0
                            }
                            three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                        }
                    }
                }

                //finish horizontal line
                while (x != xend) {
                    x += xDirection
                    if (y >= 0 && y < height && x >= 0 && x < width) {
                        oy = (int) ((SpatialUtils.convertPixelToLat((int) (y * divy + latitude1)) - olatitude1) / odivy)
                        ox = (int) ((SpatialUtils.convertPixelToLng((int) (x * divx + longitude1)) - olongitude1) / odivx)
                        if (oy >= oheight) {
                            oy = oheight - 1
                        }
                        if (ox >= owidth) {
                            ox = owidth - 1
                        }
                        if (oy < 0) {
                            oy = 0
                        }
                        if (ox < 0) {
                            ox = 0
                        }
                        three_state_map[oy][ox] = GI_PARTIALLY_PRESENT
                    }
                }
            }
        }
    }

    int[][] fillAccMask_EPSG900913(double longitude1, double latitude1, double longitude2, double latitude2, int width, int height, byte[][] three_state_map, boolean noCellsReturned) {
        double divx = (longitude2 - longitude1) / width
        double divy = (latitude2 - latitude1) / height

        int i, j
        //do raster check
        int[][] data = null
        boolean cellsReturned = !noCellsReturned
        if (cellsReturned) {
            data = new int[width * height][2]
        }
        int p = 0
        for (j = 0; j < three_state_map[0].length; j++) {
            for (i = 0; i < three_state_map.length; i++) {
                if (three_state_map[i][j] == GI_PARTIALLY_PRESENT) {
                    //if it is partially present, do nothing
                } else if ((j == 0 || three_state_map[i][j - 1] == GI_PARTIALLY_PRESENT)) {
                    if (i > 0
                            && (three_state_map[i - 1][j] == GI_FULLY_PRESENT
                            || three_state_map[i - 1][j] == GI_ABSENCE)) {
                        //use same as LHS
                        three_state_map[i][j] = three_state_map[i - 1][j]
                    } else if (isWithin_EPSG900913(j * divx + divx / 2 + longitude1, i * divy + divy / 2 + latitude1)) {
                        //if the previous was partially present, test
                        three_state_map[i][j] = GI_FULLY_PRESENT
                    } //else absent
                } else {
                    //if the previous was fully present, repeat
                    //if the previous was absent, repeat
                    three_state_map[i][j] = three_state_map[i][j - 1]
                }

                //apply to cells;
                if (cellsReturned && three_state_map[i][j] != GI_UNDEFINED) {   //undefined == absence
                    data[p][0] = j
                    data[p][1] = i
                    p++
                }
            }
        }
        if (data != null) {
            data = java.util.Arrays.copyOf(data, p)
        }
        return data
    }
}

