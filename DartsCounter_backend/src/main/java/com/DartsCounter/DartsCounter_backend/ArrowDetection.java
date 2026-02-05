package com.DartsCounter.DartsCounter_backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Arrays;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.core.Point;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;


@Service
@EnableAsync
public class ArrowDetection {
    private volatile boolean isRunning = false;

    @Async
    public void  BackgroundSubtractor(int camIndex) {
        BackgroundSubtractor backSub;
        backSub = org.opencv.video.Video.createBackgroundSubtractorMOG2();

        VideoCapture camera = new VideoCapture(camIndex);
        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, 1920);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, 1080);
        double actualWidth = camera.get(Videoio.CAP_PROP_FRAME_WIDTH);
        double actualHeight = camera.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        System.out.println("Camera Resolution: " + actualWidth + "x" + actualHeight);

        if(!camera.isOpened()){
            System.out.println("Camera not accessible");
            return;
        }

        Mat frame = new Mat();
        Mat fgMask = new Mat();
        Mat fgMaskBlur = new Mat();
        Mat fgCleaned = new Mat();

        int frameCount = 0;

        System.out.println("start capturing frames Frames");

        while(this.isRunning && camera.read(frame)){
            backSub.apply(frame, fgMask);
            System.out.println("Frame: " + frameCount);
            if (frameCount % 2 == 0){
                // enhance IMG
                Imgproc.blur(fgMask, fgMaskBlur, new Size(7,7));
                Imgproc.morphologyEx(fgMaskBlur, fgCleaned, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(10, 10)));

                Double dartPos = showsDarts(fgCleaned);

                if (dartPos != null){
                    Imgcodecs.imwrite("subFrames/arrow/"+"mask_frame_" + frameCount + "_" + camIndex + ".png", fgCleaned);
                }else{
                    Imgcodecs.imwrite("subFrames/empty/"+"mask_frame_" + frameCount + "_" + camIndex + ".png", fgCleaned);
                }
            }
            frameCount++;
        }
        camera.release();
        System.out.println("Finished");
    }

    public Double showsDarts(Mat image){

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(image, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        boolean dartFound = false;

        double recordArea = 0;
        double recordAreaRatio = 0;
        Point[] recordVertices = new Point[4];

        for (MatOfPoint contour : contours){

            MatOfPoint2f contourf2 = new MatOfPoint2f(contour.toArray());
            RotatedRect rotatedRect = Imgproc.minAreaRect(contourf2);

            double width = rotatedRect.size.width;
            double height = rotatedRect.size.height;
            double ratio = Math.max(width, height) / Math.min(height, width);
            double area = Imgproc.contourArea(contour);

            if (area > recordArea){
                recordArea = area;
                recordAreaRatio = ratio;
                rotatedRect.points(recordVertices);
            }

            final double maxRatio = 5;
            final double minRatio = 1.4;
            final double maxArea = 160000;
            final double minArea = 14000;

            if (minRatio < ratio && ratio < maxRatio && minArea < area && area < maxArea){
                dartFound = true;
            }
        }

        Arrays.sort(recordVertices, Comparator.comparingDouble(p -> p.y));
        double avgX = (recordVertices[0].x + recordVertices[1].x) / 2 - 910;

        System.out.println("Area: " + recordArea);
        System.out.println("ratio: " + recordAreaRatio);
        if (dartFound){
            System.out.println("----------PosX: " + avgX);
            return avgX;
        }else{
            return null;
        }
    }

    public void setRunning(boolean isRunning){
        this.isRunning = isRunning;
    }
}
