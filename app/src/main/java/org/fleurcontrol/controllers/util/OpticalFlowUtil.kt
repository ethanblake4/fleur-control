package org.fleurcontrol.controllers.util

import org.nield.kotlinstatistics.median
import org.nield.kotlinstatistics.standardDeviation
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import timber.log.Timber





object OpticalFlowUtil {

    fun calc(first: Mat, second: Mat, useMedian: Boolean): Pair<Double, Double>? {

        /* detect features */
        val corners = MatOfPoint().apply {
            Imgproc.goodFeaturesToTrack(first, this, 100,
                    0.1, 7.0, Mat(), 7,
                    false, 0.04)
        }

        val oldPoints = MatOfPoint2f(*corners.toArray())
        val newPoints = MatOfPoint2f()
        val status = MatOfByte()
        val error = MatOfFloat()

        try {

            Video.calcOpticalFlowPyrLK(first, second,
                    oldPoints, newPoints,
                    status, error, Size(15.0, 15.0), 2,
                    TermCriteria(TermCriteria.COUNT or TermCriteria.EPS, 10, 0.03),
                    0, 1e-4)

            var xs = 0.0
            var ys = 0.0
            var count = 0

            val pointList = mutableListOf<Point>()

            oldPoints.toArray()
                    .zip(newPoints.toArray())
                    .zip(status.toList()).forEach { (point, status) ->
                if (status == 1.toByte()) {
                    pointList.add(Point(point.second.x - point.first.x,
                           point.second.y - point.first.y))
                    count++
                }
            }

            val centroid = getCentroid(pointList)
            //val median = getGeoMedian(centroid, pointList)


            if(!useMedian) return Pair(centroid.x, centroid.y)

            val m = dualMedian(pointList) ?: return null
            return Pair(m.x, m.y)

        } catch (e: CvException) {
            Timber.e(e)
        }

        return null
    }

    fun dualMedian(points: List<Point>): Point? {

        var direction = points.map { Math.atan2(it.y, it.x) }
        var magnitude = points.map { Math.hypot(it.x, it.y) }

        var avg = direction.average()
        val mavg = magnitude.average()

        direction.standardDeviation().let { stdev ->
            //if(stdev > Math.toRadians((1/Math.sqrt(mavg)) * 66.0)) return null
            direction = direction.filter { it < avg + stdev && it > avg - stdev }
        }

        magnitude.standardDeviation().let { stdev ->
            //if(stdev > 7.0) return null
            magnitude = magnitude.filter { it < mavg + stdev && it > mavg - stdev }
        }

        avg = direction.median()
        val med = magnitude.median()

        return Point(
                Math.cos(avg) * med,
                Math.sin(avg) * med
        )

    }

    fun getCentroid(points: List<Point>): Point {
        var cx = 0.0
        var cy = 0.0
        for (i in 0 until points.size) {
            val pt = points[i]
            if(Math.hypot(pt.x, pt.y) < 30.0) {
                cx += pt.x
                cy += pt.y
            }
        }
        return Point(cx / points.size, cy / points.size)
    }

    fun getGeoMedian(start: Point, points: List<Point>): Point {

        var cx = 0.0
        var cy = 0.0

        var iterations = 0;

        val centroidx = start.x
        val centroidy = start.y
        do {
            iterations++
            var totalWeight = 0.0
            for (i in 0 until points.size) {
                val pt = points[i]
                val weight = 1 / distance(pt.x, pt.y, centroidx, centroidy)
                cx += pt.x * weight
                cy += pt.y * weight
                totalWeight += weight
            }
            cx /= totalWeight
            cy /= totalWeight
        } while ((Math.abs(cx - centroidx) > 0.5 || Math.abs(cy - centroidy) > 0.5)
                && iterations < 100)

        return Point(cx, cy)
    }

    private fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        var x1 = x1
        var y1 = y1
        x1 -= x2
        y1 -= y2
        return Math.sqrt(x1 * x1 + y1 * y1)
    }
}