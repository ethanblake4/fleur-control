package org.flyline;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

public class PatternFinder {

    public static double[] analyzeRed(Mat matFile){
        double[] cordinates = {0.0,0.0};

        Scalar lowerRed = new Scalar(100, 84, 141);
        Scalar upperRed = new Scalar(186,255,255);
        List<MatOfPoint> cnts = new ArrayList<>();

        Mat blurredImage = new Mat();
        Mat hsvImage = new Mat();
        Imgproc.blur(matFile, blurredImage, new Size(11, 11));
        Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);

        Mat kernel = new Mat();
        Mat mask = new Mat();
        Mat hierachy = new Mat();
        Core.inRange(hsvImage,lowerRed,upperRed,mask);
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_OPEN,kernel);
        Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,kernel);

        Imgproc.findContours(mask,cnts, hierachy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);


        double area=0.0;
        Mat largestArea  = new Mat();
        if(cnts.size()>0){
            for(int i = 0; i<cnts.size();i++){
                double temp = Imgproc.contourArea(cnts.get(i));
                Mat temp2 = cnts.get(i);
                if(temp>area){
                    area = temp;
                    largestArea = temp2;
                }
            }
            if(area == 0.0) area = Imgproc.contourArea(mask);

        }
        Moments M = new Moments();
        Imgproc.moments(largestArea);

        cordinates[0] = M.get_m10() / M.get_m00();
        cordinates[1]= M.get_m01() / M.get_m00();

        // Point center = new Point((M.get_m10() / M.get_m00()), (M.get_m01() / M.get_m00()));



        return cordinates;
    }
}