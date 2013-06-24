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
  private double qr_real_size = 12.2; //real QR code length.(cm)

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
	  paint.setARGB(255, 255, 255, 0);
	  paint.setStrokeWidth(5);
	  paint.setTextSize(30);
	  if(lastResult != null) {
		  ResultPoint[] points = lastResult.getResultPoints();
		  //calc 4nd point
		  Rect frame = cameraManager.getFramingRect();
		  if (frame == null) {
			  return;
		  }
	      Rect previewFrame = cameraManager.getFramingRectInPreview();
	      float scaleX = frame.width() / (float) previewFrame.width();
	      float scaleY = frame.height() / (float) previewFrame.height();
		  canvas.drawLine(points[0].getX()*scaleX, points[0].getY()*scaleY, points[1].getX()*scaleX, points[1].getY()*scaleY, paint);
		  canvas.drawLine(points[1].getX()*scaleX, points[1].getY()*scaleY, points[2].getX()*scaleX, points[2].getY()*scaleY, paint);
		  String locStr = "";
		  locStr = "length = " + calcSasDistance();
		  canvas.drawText(locStr, (points[0].getX() + points[2].getX()) / 2 *scaleX, (points[0].getY() + points[2].getY()) / 2 *scaleY, paint);
		  }
	  }
  
  /**
   * Calculate SAS width. Will compare 2 distance and use longest.
   * @author bravesheng@gmail.com
   */
  public double calcSasSize() {
	  ResultPoint[] points = lastResult.getResultPoints();
	  double dist1 = Math.sqrt(Math.pow(Math.abs(points[0].getX() - points[1].getX()),2) + Math.pow(Math.abs(points[0].getY() - points[1].getY()),2));
	  double dist2 = Math.sqrt(Math.pow(Math.abs(points[1].getX() - points[2].getX()),2) + Math.pow(Math.abs(points[1].getY() - points[2].getY()),2));
	  return (dist1 + dist2) / 2;
  }
  
  /**
   * Calculate distance between SAS and camera.
   * @author bravesheng@gmail.com
   */
  public double calcSasDistance() {
	  double sas_pixel_length = calcSasSize();
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  double angle_of_sas_size = angle_per_pixel * sas_pixel_length;
	  //real_distance = tan((pi - angle_of_sas_size) / 2) x (qr_real_distance / 2) 
	  double real_distance = Math.tan((Math.PI - angle_of_sas_size) / 2) * (qr_real_size / 2);
	  return real_distance;
  }
  
  /**
   * Calculate relative position between camera and SAS.
   * Center of the coordinate system is camera. Horizontal axis = sasX, Distance axis = sasY, Vertical axis = sasZ 
   * @author bravesheng@gmail.com
   */
  public float[] sasRelativePosition() {
	  Point cameraResolution = cameraManager.getCameraResolution();
	  double angle_per_pixel = cameraManager.getHorizontalViewAngle() / cameraResolution.x;
	  double sasDistance = calcSasDistance();
	  //determin center of SAS
	  ResultPoint[] points = lastResult.getResultPoints();
	  float rad_x = (float) ((((points[0].getX() + points[2].getX()) / 2) - (cameraResolution.x / 2)) * angle_per_pixel);
	  float rad_y = (float) (((cameraResolution.y / 2) - ((points[0].getY() + points[2].getY()) / 2)) * angle_per_pixel);
	  float sasX = (float) (sasDistance * Math.sin(rad_x));
	  float sasZ = (float) (sasDistance * Math.sin(rad_y));
	  float sasY = (float) (sasDistance * (Math.cos(rad_x) + Math.cos(rad_y))/2);
	  float sasAxis[] = {sasX, sasY, sasZ};
	  return sasAxis;
  }
}
