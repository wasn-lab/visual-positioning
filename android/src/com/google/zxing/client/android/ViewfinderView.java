/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 80L;
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;
  private Result lastResult;
  private double qr_real_size = 17.65; //real QR code length.(cm)

  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    resultColor = resources.getColor(R.color.result_view);
    laserColor = resources.getColor(R.color.viewfinder_laser);
    resultPointColor = resources.getColor(R.color.possible_result_points);
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<ResultPoint>(5);
    lastPossibleResultPoints = null;
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    if (frame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();

    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);

    if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(CURRENT_POINT_OPACITY);
      canvas.drawBitmap(resultBitmap, null, frame, paint);
    } else {
    	showLocationInfo(canvas);
// bravesheng: modify laser animation can be show center of camera
      // Draw a red "laser scanner" line through the middle to show decoding is active
      paint.setColor(laserColor);
      paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
      int horzontal_middle = frame.height() / 2 + frame.top;
      int vertical_middle = frame.width() / 2 + frame.left;
      canvas.drawRect(frame.left + 2, horzontal_middle - 1, frame.right - 1, horzontal_middle + 2, paint);
      canvas.drawRect(vertical_middle - 1,frame.top + 2, vertical_middle + 2, frame.bottom - 1, paint);
     
      Rect previewFrame = cameraManager.getFramingRectInPreview();
      float scaleX = frame.width() / (float) previewFrame.width();
      float scaleY = frame.height() / (float) previewFrame.height();

      List<ResultPoint> currentPossible = possibleResultPoints;
      List<ResultPoint> currentLast = lastPossibleResultPoints;
      int frameLeft = frame.left;
      int frameTop = frame.top;
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null;
      } else {
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = currentPossible;
        paint.setAlpha(CURRENT_POINT_OPACITY);
        paint.setColor(resultPointColor);
        synchronized (currentPossible) {
          for (ResultPoint point : currentPossible) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              POINT_SIZE, paint);
          }
        }
      }
      if (currentLast != null) {
        paint.setAlpha(CURRENT_POINT_OPACITY / 2);
        paint.setColor(resultPointColor);
        synchronized (currentLast) {
          float radius = POINT_SIZE / 2.0f;
          for (ResultPoint point : currentLast) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              radius, paint);
          }
        }
      }

      // Request another update at the animation interval, but only repaint the laser line,
      // not the entire viewfinder mask.
      postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE);
    }
  }

  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  public void drawResultBitmap(Bitmap barcode) {
    resultBitmap = barcode;
    invalidate();
  }

  //bravesheng: This funciton called in DecodeThread
  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

  /**
   * Import last result points into ViewfinderView
   *
   * @param result Result points class.
   * @author bravesheng@gmail.com
   */
  public void addSuccessResult(Result result)
  {
	  lastResult = result;
  }
  
  /**
   * Display SAS info on live screen.
   *
   * @canvas Canvas the canvas on which the background will be drawn
   * @author bravesheng@gmail.com
   */
  private void showLocationInfo(Canvas canvas) {
	  paint.setStrokeWidth(8);
	  paint.setTextSize(40);
	  if(lastResult != null) {
		  ResultPoint[] points = lastResult.getResultPoints();
		  if(points != null) {
			  //calc 4nd point
			  Rect frame = cameraManager.getFramingRect();
			  if (frame == null) {
				  return;
			  }
		      Rect previewFrame = cameraManager.getFramingRectInPreview();
		      float scaleX = frame.width() / (float) previewFrame.width();
		      float scaleY = frame.height() / (float) previewFrame.height();
		      //point[0]->point[1] = vertical
		      //point[1]->point[2] = horizontal
		      paint.setARGB(255, 0, 0, 255); //blue will calculate as distance in current version
			  canvas.drawLine(points[0].getX()*scaleX, points[0].getY()*scaleY, points[1].getX()*scaleX, points[1].getY()*scaleY, paint);
			  paint.setARGB(255, 0, 255, 0); //green
			  canvas.drawLine(points[1].getX()*scaleX, points[1].getY()*scaleY, points[2].getX()*scaleX, points[2].getY()*scaleY, paint);
			  double sasPosition[] = sasRelativePosition();
			  paint.setARGB(255, 255, 255, 255);
			  paint.setShadowLayer((float)Math.PI, 2, 2, 0xFF000000);
			  float azimuth_rad = captureActivity.getOrientationForSas()[1];
			  float vertical_rad = captureActivity.getOrientationForSas()[2];
			  String locStr = String.format(" SIZE(B)=%f, SIZE(G)=%f, DISTANCE=%f", getSasSizeV(), getSasSizeH(), sasPosition[2]);	//the real distance
			  canvas.drawText(locStr, 30, 40, paint);
			  }
		  }
	  }
  
public double[] getSasInfoForFineTune() {
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  ResultPoint[] points = lastResult.getResultPoints();
	  double rad_blue_line = (((points[1].getX() + points[0].getX()) / 2) - (cameraResolution.x / 2)) * angle_per_pixel; //center of blue line in x-axis
	  double rad_green_line = ((cameraResolution.y / 2) - ((points[1].getY() + points[2].getY()) / 2)) * angle_per_pixel; //center of green line in y-axis
	  double sizeV = Math.sqrt(Math.pow(Math.abs(points[0].getX() - points[1].getX()),2) + Math.pow(Math.abs(points[0].getY() - points[1].getY()),2));	  
	  double sasInfo[] = {rad_blue_line, rad_green_line, sizeV};
	  return sasInfo;
}
  
 public double getSasSizeV() {
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  ResultPoint[] points = lastResult.getResultPoints();
	  double rad_blue_line_x = (((points[1].getX() + points[0].getX()) / 2) - (cameraResolution.x / 2)) * angle_per_pixel; //center of blue line in x-axis
	  double rad_center_y = ((cameraResolution.y / 2) - ((points[0].getY() + points[2].getY()) / 2)) * angle_per_pixel; //center of qrcode y-axis
	  double sizeV = Math.sqrt(Math.pow(Math.abs(points[0].getX() - points[1].getX()),2) + Math.pow(Math.abs(points[0].getY() - points[1].getY()),2));	  
	  //according to test. points[0] -> point1[1] = vertical for QRcode blue line. good for horizontal
	  float vertical_rad = captureActivity.getOrientationForSas()[2];
	  sizeV = sizeV * Math.cos(rad_blue_line_x) * Math.cos(rad_center_y) / Math.cos(vertical_rad);
	  return sizeV;
  }
 
 public double getSasSizeH() {
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  ResultPoint[] points = lastResult.getResultPoints();
	  double rad_green_line_y = ((cameraResolution.y / 2) - ((points[1].getY() + points[2].getY()) / 2)) * angle_per_pixel; //center of green line in y-axis
	  double rad_center_x = (((points[0].getX() + points[2].getX()) / 2) - (cameraResolution.x / 2)) * angle_per_pixel; //center of qrcode x-axis	 	 
	  double sizeH = Math.sqrt(Math.pow(Math.abs(points[1].getX() - points[2].getX()),2) + Math.pow(Math.abs(points[1].getY() - points[2].getY()),2));
	  //according to test. points[1] -> point1[2] = horizontal for QRcode. green line. good for vertical
	  float azimuth_rad = captureActivity.getOrientationForSas()[1];
	  sizeH = sizeH * Math.cos(rad_green_line_y) * Math.cos(rad_center_x) / Math.cos(azimuth_rad);
	  return sizeH;
 }
  /**
   * Calculate SAS width. This function use QRcode vertical(BLUE) and horizontal(GREEN) line to do that.
   * And this line is good for horizontal  degree measurement.
   * @author bravesheng@gmail.com
   */
  public double getSasSize() {
	  return (getSasSizeV() + getSasSizeH()) / 2;
  }
  
  
  /**
   * Calculate distance between SAS and camera.
   * @author bravesheng@gmail.com
   */
  public double calcSasCenterDistance() {
	  double sas_pixel_length = getSasSize();
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  double angle_of_sas_size = angle_per_pixel * sas_pixel_length;
	  //center_distance = tan((pi - angle_of_sas_size) / 2) x (qr_real_distance / 2) 
	  //this distance is only for center of screen. Will calculate real distance in sasRelativePosition
	  double center_distance = Math.tan((Math.PI - angle_of_sas_size) / 2) * (qr_real_size / 2);
	  return center_distance / 100; //meters
  }
  
  /**
   * Calculate relative position between camera and SAS.
   * Center of the coordinate system is camera. Horizontal axis = sasX, Vertical axis = sasY , Distance axis = sasZ
   * @author bravesheng@gmail.com
   */
  public double[] sasRelativePosition() {
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  double sasCenterDistance = calcSasCenterDistance();
	  //determin center of SAS
	  ResultPoint[] points = lastResult.getResultPoints();
	  double rad_x = (((points[0].getX() + points[2].getX()) / 2) - (cameraResolution.x / 2)) * angle_per_pixel;
	  double rad_y = ((cameraResolution.y / 2) - ((points[0].getY() + points[2].getY()) / 2)) * angle_per_pixel;
	  double sasX = sasCenterDistance * Math.sin(rad_x);
	  double sasY = sasCenterDistance * Math.sin(rad_y);
	  //double sasRD = Math.sqrt(sasCenterDistance * sasCenterDistance + sasX * sasX + sasY * sasY) ;	//real distance
	  double sasAxis[] = {rad_x, rad_y, sasCenterDistance}; //rad_x = x-axis rad. rad_y = x-axis rad. sasRD = real distance for SAS.
	  return sasAxis;
  }
  
  private CaptureActivity captureActivity;
  
  public void setCaptureActivity(CaptureActivity activity) {
	  captureActivity = activity;
  }
}
