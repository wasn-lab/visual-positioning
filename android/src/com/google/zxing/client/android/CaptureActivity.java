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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.history.HistoryActivity;
import com.google.zxing.client.android.history.HistoryItem;
import com.google.zxing.client.android.history.HistoryManager;
import com.google.zxing.client.android.result.ResultButtonListener;
import com.google.zxing.client.android.result.ResultHandler;
import com.google.zxing.client.android.result.ResultHandlerFactory;
import com.google.zxing.client.android.result.supplement.SupplementalInfoRetriever;
import com.google.zxing.client.android.share.ShareActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.musicg.math.statistics.StandardDeviation;

/**
 * This activity opens the camera and does the actual scanning on a background thread. It draws a
 * viewfinder to help the user place the barcode correctly, shows feedback as the image processing
 * is happening, and then overlays the results when a scan is successful.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 */

public final class CaptureActivity extends Activity implements SurfaceHolder.Callback, SensorEventListener, LocationListener {

  private static final String TAG = CaptureActivity.class.getSimpleName();

  private static final long DEFAULT_INTENT_RESULT_DURATION_MS = 1500L;
  private static final long BULK_MODE_SCAN_DELAY_MS = 1000L;

  private static final String PACKAGE_NAME = "com.google.zxing.client.android";
  private static final String PRODUCT_SEARCH_URL_PREFIX = "http://www.google";
  private static final String PRODUCT_SEARCH_URL_SUFFIX = "/m/products/scan";
  private static final String[] ZXING_URLS = { "http://zxing.appspot.com/scan", "zxing://scan/" };

  public static final int HISTORY_REQUEST_CODE = 0x0000bacc;

  private static final Set<ResultMetadataType> DISPLAYABLE_METADATA_TYPES =
      EnumSet.of(ResultMetadataType.ISSUE_NUMBER,
                 ResultMetadataType.SUGGESTED_PRICE,
                 ResultMetadataType.ERROR_CORRECTION_LEVEL,
                 ResultMetadataType.POSSIBLE_COUNTRY);

  private CameraManager cameraManager;
  private CaptureActivityHandler handler;
  private Result savedResultToShow;
  private ViewfinderView viewfinderView;
  private TextView statusView;
  private View resultView;
  private Result lastResult;
  private boolean hasSurface;
  private boolean copyToClipboard;
  private IntentSource source;
  private String sourceUrl;
  private ScanFromWebPageManager scanFromWebPageManager;
  private Collection<BarcodeFormat> decodeFormats;
  private String characterSet;
  private HistoryManager historyManager;
  private InactivityTimer inactivityTimer;
  private BeepManager beepManager;
  private AmbientLightManager ambientLightManager;
  //for sensors define
  private SensorManager mSensorManager;
  private Sensor mAccelerometer;
  private Sensor mMagneticField;
  private Sensor mGyroscope;
  private float[] accelerometer_values;
  private float[] magnitude_values;
  // orientation angles from accel and magnet
  private float[] accMagOrientation = new float[3];
  // final orientation angles from sensor fusion
  private float[] fusedOrientation = new float[3];
  private double[] magVpsAxis = new double[3];
  private double[] fusVpsAxis = new double[3];
  private double[] gpsAxis = new double[3]; 
  public static final float EPSILON = 0.000000001f;
  private static final float NS2S = 1.0f / 1000000000.0f;
  private float timestamp;
  private boolean initState = true;
  // angular speeds from gyro
  private float[] gyro = new float[3];

  // rotation matrix from gyro data
  private float[] gyroMatrix = new float[9];

  // orientation angles from gyro matrix
  private float[] gyroOrientation = new float[3];
  
  //for fusion
	public static final int TIME_CONSTANT = 90; //original:30
	public static final float FILTER_COEFFICIENT = 0.98f;
	private Timer fuseTimer = new Timer();
  
  //for GPS
  private LocationManager locationManager;    // The minimum distance to change Updates in meters
  private Location lastLocation;
  private static final double centerLatitude = 24.967185;
  private static final double centerLongitude = 121.187019;
  private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 0; // no limit  
  // The minimum time between updates in milliseconds
  private static final long MIN_TIME_BW_UPDATES = 1000; // 1 second
  private float finalDistance = 0;

  ViewfinderView getViewfinderView() {
    return viewfinderView;
  }

  public Handler getHandler() {
    return handler;
  }

  CameraManager getCameraManager() {
    return cameraManager;
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    Log.w("zxing", "onCreate");
    //for sensors
    // initialise gyroMatrix with identity matrix
    gyroMatrix[0] = 1.0f; gyroMatrix[1] = 0.0f; gyroMatrix[2] = 0.0f;
    gyroMatrix[3] = 0.0f; gyroMatrix[4] = 1.0f; gyroMatrix[5] = 0.0f;
    gyroMatrix[6] = 0.0f; gyroMatrix[7] = 0.0f; gyroMatrix[8] = 1.0f;
    mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
    mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    mMagneticField = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);       
    mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    // wait for one second until gyroscope and magnetometer/accelerometer
    // data is initialised then scedule the complementary filter task
    fuseTimer.scheduleAtFixedRate(new calculateFusedOrientationTask(), 5000, TIME_CONSTANT);
    
    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.capture);

    hasSurface = false;
    historyManager = new HistoryManager(this);
    historyManager.trimHistory();
    inactivityTimer = new InactivityTimer(this);
    beepManager = new BeepManager(this);
    ambientLightManager = new AmbientLightManager(this);

    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

    showHelpOnFirstLaunch();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.w("zxing", "onResume");
    //for sensors
    mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    mSensorManager.registerListener(this, mMagneticField, SensorManager.SENSOR_DELAY_NORMAL);
    mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_FASTEST);
    //for Gyroscope
	initState = true;
    //for GPS
    locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
    if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
    	locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
    }
    // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
    // want to open the camera driver and measure the screen size if we're going to show the help on
    // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
    // off screen.
    cameraManager = new CameraManager(getApplication());

    viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    viewfinderView.setCameraManager(cameraManager);
    viewfinderView.setCaptureActivity(this);

    resultView = findViewById(R.id.result_view);
    statusView = (TextView) findViewById(R.id.status_view);

    handler = null;
    lastResult = null;

    resetStatusView();

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
    SurfaceHolder surfaceHolder = surfaceView.getHolder();
    if (hasSurface) {
      // The activity was paused but not stopped, so the surface still exists. Therefore
      // surfaceCreated() won't be called, so init the camera here.
      initCamera(surfaceHolder);
    } else {
      // Install the callback and wait for surfaceCreated() to init the camera.
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    beepManager.updatePrefs();
    ambientLightManager.start(cameraManager);

    inactivityTimer.onResume();

    Intent intent = getIntent();

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    copyToClipboard = prefs.getBoolean(PreferencesActivity.KEY_COPY_TO_CLIPBOARD, true)
        && (intent == null || intent.getBooleanExtra(Intents.Scan.SAVE_HISTORY, true));

    source = IntentSource.NONE;
    decodeFormats = null;
    characterSet = null;

    if (intent != null) {

      String action = intent.getAction();
      String dataString = intent.getDataString();

      if (Intents.Scan.ACTION.equals(action)) {

        // Scan the formats the intent requested, and return the result to the calling activity.
        source = IntentSource.NATIVE_APP_INTENT;
        decodeFormats = DecodeFormatManager.parseDecodeFormats(intent);

        if (intent.hasExtra(Intents.Scan.WIDTH) && intent.hasExtra(Intents.Scan.HEIGHT)) {
          int width = intent.getIntExtra(Intents.Scan.WIDTH, 0);
          int height = intent.getIntExtra(Intents.Scan.HEIGHT, 0);
          if (width > 0 && height > 0) {
            cameraManager.setManualFramingRect(width, height);
          }
        }
        
        String customPromptMessage = intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE);
        if (customPromptMessage != null) {
          statusView.setText(customPromptMessage);
        }

      } else if (dataString != null &&
                 dataString.contains(PRODUCT_SEARCH_URL_PREFIX) &&
                 dataString.contains(PRODUCT_SEARCH_URL_SUFFIX)) {

        // Scan only products and send the result to mobile Product Search.
        source = IntentSource.PRODUCT_SEARCH_LINK;
        sourceUrl = dataString;
        decodeFormats = DecodeFormatManager.PRODUCT_FORMATS;

      } else if (isZXingURL(dataString)) {

        // Scan formats requested in query string (all formats if none specified).
        // If a return URL is specified, send the results there. Otherwise, handle it ourselves.
        source = IntentSource.ZXING_LINK;
        sourceUrl = dataString;
        Uri inputUri = Uri.parse(dataString);
        scanFromWebPageManager = new ScanFromWebPageManager(inputUri);
        decodeFormats = DecodeFormatManager.parseDecodeFormats(inputUri);

      }

      characterSet = intent.getStringExtra(Intents.Scan.CHARACTER_SET);

    }
  }
  
  private static boolean isZXingURL(String dataString) {
    if (dataString == null) {
      return false;
    }
    for (String url : ZXING_URLS) {
      if (dataString.startsWith(url)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void onPause() {
	  Log.w("zxing", "onPause");
	  //pause GPS listener
	  if(locationManager != null){
		  locationManager.removeUpdates(this);
		  }     
    if (handler != null) {
      handler.quitSynchronously();
      handler = null;
    }
    inactivityTimer.onPause();
    ambientLightManager.stop();
    cameraManager.closeDriver();
    if (!hasSurface) {
      SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
      SurfaceHolder surfaceHolder = surfaceView.getHolder();
      surfaceHolder.removeCallback(this);
    }
    super.onPause();
    //for sensors
    mSensorManager.unregisterListener(this);
  }

  @Override
  protected void onDestroy() {
	  Log.w("zxing", "onDestroy");
    inactivityTimer.shutdown();
    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        if (source == IntentSource.NATIVE_APP_INTENT) {
          setResult(RESULT_CANCELED);
          finish();
          return true;
        }
        if ((source == IntentSource.NONE || source == IntentSource.ZXING_LINK) && lastResult != null) {
          restartPreviewAfterDelay(0L);
          return true;
        }
        break;
      case KeyEvent.KEYCODE_FOCUS:
      case KeyEvent.KEYCODE_CAMERA:
        // Handle these events so they don't launch the Camera app
        return true;
      // Use volume up/down to turn on light
      case KeyEvent.KEYCODE_VOLUME_DOWN:
        cameraManager.setTorch(false);
        return true;
      case KeyEvent.KEYCODE_VOLUME_UP:
        cameraManager.setTorch(true);
        return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater menuInflater = getMenuInflater();
    menuInflater.inflate(R.menu.capture, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
    switch (item.getItemId()) {
      case R.id.menu_share:
        intent.setClassName(this, ShareActivity.class.getName());
        startActivity(intent);
        break;
      case R.id.menu_history:
        intent.setClassName(this, HistoryActivity.class.getName());
        startActivityForResult(intent, HISTORY_REQUEST_CODE);
        break;
      case R.id.menu_settings:
        intent.setClassName(this, PreferencesActivity.class.getName());
        startActivity(intent);
        break;
      case R.id.menu_help:
        intent.setClassName(this, HelpActivity.class.getName());
        startActivity(intent);
        break;
      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (resultCode == RESULT_OK) {
      if (requestCode == HISTORY_REQUEST_CODE) {
        int itemNumber = intent.getIntExtra(Intents.History.ITEM_NUMBER, -1);
        if (itemNumber >= 0) {
          HistoryItem historyItem = historyManager.buildHistoryItem(itemNumber);
          decodeOrStoreSavedBitmap(null, historyItem.getResult());
        }
      }
    }
  }

  private void decodeOrStoreSavedBitmap(Bitmap bitmap, Result result) {
    // Bitmap isn't used yet -- will be used soon
    if (handler == null) {
      savedResultToShow = result;
    } else {
      if (result != null) {
        savedResultToShow = result;
      }
      if (savedResultToShow != null) {
        Message message = Message.obtain(handler, R.id.decode_succeeded, savedResultToShow);
        handler.sendMessage(message);
      }
      savedResultToShow = null;
    }
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (holder == null) {
      Log.e(TAG, "*** WARNING *** surfaceCreated() gave us a null surface!");
    }
    if (!hasSurface) {
      hasSurface = true;
      initCamera(holder);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    hasSurface = false;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

  }

  /**
   * A valid barcode has been found, so give an indication of success and show the results.
   *
   * @param rawResult The contents of the barcode.
   * @param scaleFactor amount by which thumbnail was scaled
   * @param barcode   A greyscale bitmap of the camera data which was decoded.
   */
public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
    inactivityTimer.onActivity();
    lastResult = rawResult;
    ResultHandler resultHandler = ResultHandlerFactory.makeResultHandler(this, rawResult);

    boolean fromLiveScan = barcode != null;
    viewfinderView.addSuccessResult(rawResult);
    if (fromLiveScan) {
    	if(!initState) {
    		/*
    		//collect 2 point in the same place.
    		positionData newData = new positionData();
        	newData.orientation = fusedOrientation.clone();
        	newData.sasPosition = viewfinderView.sasRelativePosition();

        	positionItems.add(newData);
        	if(positionItems.size() == sampleNums)
        	{
        		//start calculate optimize angle value
        		if(positionItems.size() > 1) { //do compensation only have at least 2 sample
        			float bestCompensation = getCompensation();	
        			fusedOrientation[0] = fusedOrientation[0] + bestCompensation;
        			//錯的，應該繼續cos與距離相除才對
        		}
        		magVpsAxis =  getVps(accMagOrientation.clone(), positionItems.get(sampleNums-1).sasPosition).clone();
        		fusVpsAxis = getVps(fusedOrientation.clone(), positionItems.get(sampleNums-1).sasPosition).clone();
        		gpsAxis = positionItems.get(sampleNums-1).sasPosition.clone();
        		historyManager.addHistoryItem(magVpsAxis, fusVpsAxis, gpsAxis, accMagOrientation, viewfinderView.getSasSize(), rawResult, resultHandler);
        		positionItems.clear();
            	// Then not from history, so beep/vibrate and we have an image to draw on
            	beepManager.playBeepSoundAndVibrate();
        	}*/
    		double[] sasAxis = viewfinderView.sasRelativePosition().clone();
    		magVpsAxis =  getVps(accMagOrientation.clone(), sasAxis.clone()).clone();
    		fusVpsAxis = getVps(fusedOrientation.clone(), sasAxis.clone()).clone();
    		gpsAxis[0] = viewfinderView.getSasSizeV();
    		gpsAxis[1] = viewfinderView.getSasSizeH();
    		historyManager.addHistoryItem(magVpsAxis, fusVpsAxis, viewfinderView.getSasInfoForFineTune(), sasAxis, accMagOrientation, viewfinderView.getSasSize(), rawResult, resultHandler);
        	// Then not from history, so beep/vibrate and we have an image to draw on
        	beepManager.playBeepSoundAndVibrate();
    	}
    	drawResultPoints(barcode, scaleFactor, rawResult);
    }
    switch (source) {
      case NATIVE_APP_INTENT:
      case PRODUCT_SEARCH_LINK:
        handleDecodeExternally(rawResult, resultHandler, barcode);
        break;
      case ZXING_LINK:
        if (scanFromWebPageManager == null || !scanFromWebPageManager.isScanFromWebPage()) {
          handleDecodeInternally(rawResult, resultHandler, barcode);
        } else {
          handleDecodeExternally(rawResult, resultHandler, barcode);
        }
        break;
      case NONE:
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (fromLiveScan && prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, false)) {
        	// Wait a moment or else it will scan the same barcode continuously about 3 times
        	restartPreviewAfterDelay(BULK_MODE_SCAN_DELAY_MS);
        } else {
          handleDecodeInternally(rawResult, resultHandler, barcode);
        }
        break;
    }
  }

/*
class positionData {
	  public float[] orientation;
	  public double[] sasPosition;
}
private List<positionData> positionItems;
	  	//校正影像傾斜，先寫固定假設SAS的方向是正西方，也就是工五館頂樓的方向
	  	double radDiff = finalOrientation[1] - sasAzimuth;  //傾斜角
	  	sasPosition[2] = sasPosition[2] / (float)Math.cos(radDiff);
	  	finalDistance = sasPosition[2];
*/
//for correct degree error
/*
class positionData {
	  public float[] orientation;
	  public double[] sasPosition;
}
private List<positionData> positionItems = new ArrayList<positionData>();
private static final int minDegree = -89;
private static final int maxDegree = 90;
private double gapArray[] = new double[180];
private static final int sampleNums = 1;


private float getCompensation() {
	  
	  for(int i=0; i < 180; i++) {
		  gapArray[i] = getStandardDeviation(i+minDegree);
		  //Log.w("zxing", "gapArray: " + gapArray[i]);
	  }	  
	  int result =  findBestCompensation(maxDegree-10); //means 0 degree
	  result = result + minDegree;
	  Log.w("zxing", "Compensation:" + result);
	  return (float)Math.toRadians(result);
}

private int findBestCompensation(int compensation) {
	  switch (chooseBetterCompensation(compensation, compensation+1)) {
	  case 1:
		  if(-1 == chooseBetterCompensation(compensation, compensation-1)) {
			  if(compensation -1 == 0) {
				  //bound
				  compensation = compensation - 1;
				  break;
			  } else {
				  //compensation-1 is smaller
				  return findBestCompensation(compensation-1);
			  }
		  } else {
			  //compensation is smallest
			  break;
		  }
	  case -1:
		  if(compensation+1 == 179) {
			  //bound
			  compensation = compensation + 1;
			  break;
		  }
		  else {
			  //compensation + 1 is smaller
			  return findBestCompensation(compensation+1);
		  }
	  case 0:
		  //equal
		  break;
	  }
	return compensation; //if case 0 or out of bound
}
*/
/**
 * Try to find better compensation from 2 different angle
 * @param compensationA
 * @param compensationB
 * @return 1 means compensationA is better. -1 means compensationB is better. 0 means equal.
 */
/*
private int chooseBetterCompensation(int compensationA, int compensationB) {

	  if(gapArray[compensationA] < gapArray[compensationB]) {
		  return 1;
	  } else if (gapArray[compensationA] == gapArray[compensationB]) {
		  return 0;
	  } else {
		  return -1;
	  }
}
*/
/**
 * Calculate distance between SAS and camera.
 * @param compensation An compensation for angle.
 * @return
 */
/*
private double getStandardDeviation(int compensation) {
	  positionData data[] = new positionData[sampleNums];
	  double tiltAngle[] = new double[sampleNums];
	  double distance[] = new double[sampleNums];

	  for(int i=0; i < sampleNums; i++) {
		  data[i] = positionItems.get(i);
		  tiltAngle[i] = data[i].orientation[0] + compensation;	//not yet rotate to landscape mode. So azimuth is orientation[0]
		  distance[i] = data[i].sasPosition[2] / Math.cos(Math.toRadians(tiltAngle[i]));
	  }
	  StandardDeviation stdDeviation = new StandardDeviation(distance);
	  return stdDeviation.evaluate();
	  //return Math.abs(distance[0] - distance[1]);
}
*/
public float[] getOrientationForSas() {
	float orientationForSas[] = rotateToLandscape(accMagOrientation.clone());
	orientationForSas[1] = orientationForSas[1] - centerAzimuth - (float)Math.PI/2;
	return orientationForSas;
}

private float[] rotateToLandscape(float[] beforeRotate) {
	  float[] finalOrientation = new float[3];
	  
	  	finalOrientation[0] = beforeRotate[1];						//horizontal balance degree. clockwise is positive
	  	if(beforeRotate[0] < Math.PI / 2) {
	  		finalOrientation[1] = (beforeRotate[0] + (float)(Math.PI * 0.5)); //compass degree. clockwise is positive
	  	}
	  	else {
	  		finalOrientation[1] = beforeRotate[0] - (float)(Math.PI * 1.5); //compass degree. clockwise is positive
	  	}
	  	if(beforeRotate[0] < Math.PI / 2) {
	  		finalOrientation[2] = (beforeRotate[2] + (float)(Math.PI * 0.5)); //vertical degree. clockwise is positive
	  	}
	  	else {
	  		finalOrientation[2] = beforeRotate[2] - (float)(Math.PI * 1.5); //vertical degree. clockwise is positive
	  	}
	  	return finalOrientation;
}

private double[] getVps(float[] sourceOrientation, double sasPosition[]) {
	double vpsAxis[] = new double[3];
  	//fine tune values for landscape mode
  	float[] finalOrientation = rotateToLandscape(sourceOrientation.clone());	
  	finalOrientation[2] = (float) (finalOrientation[2] - sasPosition[1]);
  	finalOrientation[1] = (float) (finalOrientation[1] + sasPosition[0]);
  	
    //Rotate Y horizontal balance degree
    double x1 = 0;
    double y1 = sasPosition[2];
    double z1 = 0;
    //Rotate X vertical degree
    double x2 = x1;
    double y2 = y1 * Math.cos(finalOrientation[2]) - z1 * Math.sin(finalOrientation[2]);
    double z2 = y1 * Math.sin(finalOrientation[2]) - z1 * Math.cos(finalOrientation[2]);
    //Rotate Z compass degree
    double x3 = x2 * Math.cos(-finalOrientation[1]) - y2 * Math.sin(-finalOrientation[1]);
    double y3 = x2 * Math.sin(-finalOrientation[1]) + y2 * Math.cos(-finalOrientation[1]);
    double z3 = z2;
    //Rotate to world coordinate and convert unit to meters
    vpsAxis[0] = -x3; //mapping to longitude經度
    vpsAxis[1] = -y3; //mapping to latitude緯度
    vpsAxis[2] = z3; //mapping to altitude高度
    return vpsAxis;
}

  /**
   * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
   * bravesheng: This funciton is only for thumbnail view. not for live view. So draw line in this bitmap will have no effect!
   * @param barcode   A bitmap of the captured image.
   * @param scaleFactor amount by which thumbnail was scaled
   * @param rawResult The decoded results which contains the points to draw.
   */
  private void drawResultPoints(Bitmap barcode, float scaleFactor, Result rawResult) {
	 ResultPoint[] points = rawResult.getResultPoints();
    if (points != null && points.length > 0) {
      Canvas canvas = new Canvas(barcode);
      Paint paint = new Paint();
      paint.setColor(getResources().getColor(R.color.result_points));
      if (points.length == 2) {
        paint.setStrokeWidth(4.0f);
        drawLine(canvas, paint, points[0], points[1], scaleFactor);
      } else if (points.length == 4 &&
                 (rawResult.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                  rawResult.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
        // Hacky special case -- draw two lines, for the barcode and metadata
        drawLine(canvas, paint, points[0], points[1], scaleFactor);
        drawLine(canvas, paint, points[2], points[3], scaleFactor);
      } else {
        paint.setStrokeWidth(10.0f);
        for (ResultPoint point : points) {
          canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
        }
      }
    }
  }
  
  private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
    canvas.drawLine(scaleFactor * a.getX(), 
                    scaleFactor * a.getY(), 
                    scaleFactor * b.getX(), 
                    scaleFactor * b.getY(), 
                    paint);
  }

  // Put up our own UI for how to handle the decoded contents.
  private void handleDecodeInternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {
    statusView.setVisibility(View.GONE);
    viewfinderView.setVisibility(View.GONE);
    resultView.setVisibility(View.VISIBLE);

    ImageView barcodeImageView = (ImageView) findViewById(R.id.barcode_image_view);
    if (barcode == null) {
      barcodeImageView.setImageBitmap(BitmapFactory.decodeResource(getResources(),
          R.drawable.launcher_icon));
    } else {
      barcodeImageView.setImageBitmap(barcode);
    }

    TextView formatTextView = (TextView) findViewById(R.id.format_text_view);
    formatTextView.setText(rawResult.getBarcodeFormat().toString());

    TextView typeTextView = (TextView) findViewById(R.id.type_text_view);
    typeTextView.setText(resultHandler.getType().toString());

    DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
    String formattedTime = formatter.format(new Date(rawResult.getTimestamp()));
    TextView timeTextView = (TextView) findViewById(R.id.time_text_view);
    timeTextView.setText(formattedTime);
  

    TextView metaTextView = (TextView) findViewById(R.id.meta_text_view);
    View metaTextViewLabel = findViewById(R.id.meta_text_view_label);
    metaTextView.setVisibility(View.GONE);
    metaTextViewLabel.setVisibility(View.GONE);
    Map<ResultMetadataType,Object> metadata = rawResult.getResultMetadata();
    if (metadata != null) {
      StringBuilder metadataText = new StringBuilder(20);
      for (Map.Entry<ResultMetadataType,Object> entry : metadata.entrySet()) {
        if (DISPLAYABLE_METADATA_TYPES.contains(entry.getKey())) {
          metadataText.append(entry.getValue()).append('\n');
        }
      }
      if (metadataText.length() > 0) {
        metadataText.setLength(metadataText.length() - 1);
        metaTextView.setText(metadataText);
        metaTextView.setVisibility(View.VISIBLE);
        metaTextViewLabel.setVisibility(View.VISIBLE);
      }
    }

    TextView contentsTextView = (TextView) findViewById(R.id.contents_text_view);
    CharSequence displayContents = resultHandler.getDisplayContents();
    contentsTextView.setText(displayContents);
    // Crudely scale between 22 and 32 -- bigger font for shorter text
    int scaledSize = Math.max(22, 32 - displayContents.length() / 4);
    contentsTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, scaledSize);

    TextView supplementTextView = (TextView) findViewById(R.id.contents_supplement_text_view);
    supplementTextView.setText("");
    supplementTextView.setOnClickListener(null);
    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
        PreferencesActivity.KEY_SUPPLEMENTAL, true)) {
      SupplementalInfoRetriever.maybeInvokeRetrieval(supplementTextView,
                                                     resultHandler.getResult(),
                                                     historyManager,
                                                     this);
    }

    int buttonCount = resultHandler.getButtonCount();
    ViewGroup buttonView = (ViewGroup) findViewById(R.id.result_button_view);
    buttonView.requestFocus();
    for (int x = 0; x < ResultHandler.MAX_BUTTON_COUNT; x++) {
      TextView button = (TextView) buttonView.getChildAt(x);
      if (x < buttonCount) {
        button.setVisibility(View.VISIBLE);
        button.setText(resultHandler.getButtonText(x));
        button.setOnClickListener(new ResultButtonListener(resultHandler, x));
      } else {
        button.setVisibility(View.GONE);
      }
    }

    if (copyToClipboard && !resultHandler.areContentsSecure()) {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      if (displayContents != null) {
        try {
          clipboard.setText(displayContents);
        } catch (NullPointerException npe) {
          // Some kind of bug inside the clipboard implementation, not due to null input
          Log.w(TAG, "Clipboard bug", npe);
        }
      }
    }
  }

  // Briefly show the contents of the barcode, then handle the result outside Barcode Scanner.
  private void handleDecodeExternally(Result rawResult, ResultHandler resultHandler, Bitmap barcode) {

    if (barcode != null) {
      viewfinderView.drawResultBitmap(barcode);
    }

    long resultDurationMS;
    if (getIntent() == null) {
      resultDurationMS = DEFAULT_INTENT_RESULT_DURATION_MS;
    } else {
      resultDurationMS = getIntent().getLongExtra(Intents.Scan.RESULT_DISPLAY_DURATION_MS,
                                                  DEFAULT_INTENT_RESULT_DURATION_MS);
    }

    // Since this message will only be shown for a second, just tell the user what kind of
    // barcode was found (e.g. contact info) rather than the full contents, which they won't
    // have time to read.
    if (resultDurationMS > 0) {
      statusView.setText(getString(resultHandler.getDisplayTitle()));
    }

    if (copyToClipboard && !resultHandler.areContentsSecure()) {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
      CharSequence text = resultHandler.getDisplayContents();
      if (text != null) {
        try {
          clipboard.setText(text);
        } catch (NullPointerException npe) {
          // Some kind of bug inside the clipboard implementation, not due to null input
          Log.w(TAG, "Clipboard bug", npe);
        }
      }
    }

    if (source == IntentSource.NATIVE_APP_INTENT) {
      
      // Hand back whatever action they requested - this can be changed to Intents.Scan.ACTION when
      // the deprecated intent is retired.
      Intent intent = new Intent(getIntent().getAction());
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
      intent.putExtra(Intents.Scan.RESULT, rawResult.toString());
      intent.putExtra(Intents.Scan.RESULT_FORMAT, rawResult.getBarcodeFormat().toString());
      byte[] rawBytes = rawResult.getRawBytes();
      if (rawBytes != null && rawBytes.length > 0) {
        intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
      }
      Map<ResultMetadataType,?> metadata = rawResult.getResultMetadata();
      if (metadata != null) {
        if (metadata.containsKey(ResultMetadataType.UPC_EAN_EXTENSION)) {
          intent.putExtra(Intents.Scan.RESULT_UPC_EAN_EXTENSION,
                          metadata.get(ResultMetadataType.UPC_EAN_EXTENSION).toString());
        }
        Integer orientation = (Integer) metadata.get(ResultMetadataType.ORIENTATION);
        if (orientation != null) {
          intent.putExtra(Intents.Scan.RESULT_ORIENTATION, orientation.intValue());
        }
        String ecLevel = (String) metadata.get(ResultMetadataType.ERROR_CORRECTION_LEVEL);
        if (ecLevel != null) {
          intent.putExtra(Intents.Scan.RESULT_ERROR_CORRECTION_LEVEL, ecLevel);
        }
        Iterable<byte[]> byteSegments = (Iterable<byte[]>) metadata.get(ResultMetadataType.BYTE_SEGMENTS);
        if (byteSegments != null) {
          int i = 0;
          for (byte[] byteSegment : byteSegments) {
            intent.putExtra(Intents.Scan.RESULT_BYTE_SEGMENTS_PREFIX + i, byteSegment);
            i++;
          }
        }
      }
      sendReplyMessage(R.id.return_scan_result, intent, resultDurationMS);
      
    } else if (source == IntentSource.PRODUCT_SEARCH_LINK) {
      
      // Reformulate the URL which triggered us into a query, so that the request goes to the same
      // TLD as the scan URL.
      int end = sourceUrl.lastIndexOf("/scan");
      String replyURL = sourceUrl.substring(0, end) + "?q=" + resultHandler.getDisplayContents() + "&source=zxing";      
      sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
      
    } else if (source == IntentSource.ZXING_LINK) {

      if (scanFromWebPageManager != null && scanFromWebPageManager.isScanFromWebPage()) {
        String replyURL = scanFromWebPageManager.buildReplyURL(rawResult, resultHandler);
        sendReplyMessage(R.id.launch_product_query, replyURL, resultDurationMS);
      }
      
    }
  }
  
  private void sendReplyMessage(int id, Object arg, long delayMS) {
    Message message = Message.obtain(handler, id, arg);
    if (delayMS > 0L) {
      handler.sendMessageDelayed(message, delayMS);
    } else {
      handler.sendMessage(message);
    }
  }

  /**
   * We want the help screen to be shown automatically the first time a new version of the app is
   * run. The easiest way to do this is to check android:versionCode from the manifest, and compare
   * it to a value stored as a preference.
   */
  private boolean showHelpOnFirstLaunch() {
    try {
      PackageInfo info = getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
      int currentVersion = info.versionCode;
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      int lastVersion = prefs.getInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, 0);
      if (currentVersion > lastVersion) {
        prefs.edit().putInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, currentVersion).commit();
        Intent intent = new Intent(this, HelpActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        // Show the default page on a clean install, and the what's new page on an upgrade.
        String page = lastVersion == 0 ? HelpActivity.DEFAULT_PAGE : HelpActivity.WHATS_NEW_PAGE;
        intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, page);
        startActivity(intent);
        return true;
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, e);
    }
    return false;
  }

  private void initCamera(SurfaceHolder surfaceHolder) {
    if (surfaceHolder == null) {
      throw new IllegalStateException("No SurfaceHolder provided");
    }
    if (cameraManager.isOpen()) {
      Log.w(TAG, "initCamera() while already open -- late SurfaceView callback?");
      return;
    }
    try {
      cameraManager.openDriver(surfaceHolder);
      // Creating the handler starts the preview, which can also throw a RuntimeException.
      if (handler == null) {
        handler = new CaptureActivityHandler(this, decodeFormats, characterSet, cameraManager);
      }
      decodeOrStoreSavedBitmap(null, null);
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
      displayFrameworkBugMessageAndExit();
    } catch (RuntimeException e) {
      // Barcode Scanner has seen crashes in the wild of this variety:
      // java.?lang.?RuntimeException: Fail to connect to camera service
      Log.w(TAG, "Unexpected error initializing camera", e);
      displayFrameworkBugMessageAndExit();
    }
  }

  private void displayFrameworkBugMessageAndExit() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.app_name));
    builder.setMessage(getString(R.string.msg_camera_framework_bug));
    builder.setPositiveButton(R.string.button_ok, new FinishListener(this));
    builder.setOnCancelListener(new FinishListener(this));
    builder.show();
  }

  public void restartPreviewAfterDelay(long delayMS) {
    if (handler != null) {
      handler.sendEmptyMessageDelayed(R.id.restart_preview, delayMS);
    }
    resetStatusView();
  }
  
  public void logcatArrayString(String name, double[] array) {
	    String print = String.format("%8.2f  %8.2f  %8.2f", Math.toDegrees(array[0]), Math.toDegrees(array[1]), Math.toDegrees(array[2]));
	    Log.w("zxing", name + print);
}
  
  private String prepareInfoString() {
	    //Change to position for debug
	    float tmpOrientation[] = new float[3];
	    
	  	//rotate to landscape
	    tmpOrientation = rotateToLandscape(accMagOrientation.clone());

	    String print = String.format("magVpsAxis:%8.2f  %8.2f  %8.2f", magVpsAxis[0], magVpsAxis[1], magVpsAxis[2])
	    		+ String.format("\ngpsAxis:%8.2f  %8.2f  %8.2f", gpsAxis[0], gpsAxis[1], gpsAxis[2])
	    		+ String.format("\nMAG:%8.2f  %8.2f  %8.2f", tmpOrientation[0]*180/Math.PI, tmpOrientation[1]*180/Math.PI, tmpOrientation[2]*180/Math.PI);
	  	//rotate to landscape
	    tmpOrientation = rotateToLandscape(gyroOrientation.clone());
	    print = print + String.format("\nGYR:%8.2f  %8.2f  %8.2f", tmpOrientation[0]*180/Math.PI, tmpOrientation[1]*180/Math.PI, tmpOrientation[2]*180/Math.PI);
	    tmpOrientation = rotateToLandscape(fusedOrientation.clone());    
	    print = print + String.format("\nFUS:%8.2f  %8.2f  %8.2f", tmpOrientation[0]*180/Math.PI, tmpOrientation[1]*180/Math.PI, tmpOrientation[2]*180/Math.PI);
	 return print;
  }
  
  //bravesheng: Can add information here. Will display on live screen.
  private void resetStatusView() {
    resultView.setVisibility(View.GONE);
    statusView.setTextSize(20);
    //statusView.setTextColor(android.graphics.Color.BLUE);
    statusView.setShadowLayer((float)Math.PI, 2, 2, 0xFF000000);
    statusView.setText(prepareInfoString());  		
    statusView.setVisibility(View.VISIBLE);
    viewfinderView.setVisibility(View.VISIBLE);
    lastResult = null;
	//Log.w("zxing",print);
    
  }

  public void drawViewfinder() {
    viewfinderView.drawViewfinder();
  }

@Override
public void onSensorChanged(SensorEvent event) {
	if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
		accelerometer_values = (float[]) event.values.clone(); 
		calculateAccMagOrientation();
	}
	else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
		magnitude_values = (float[]) event.values.clone();
	}
	else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
		gyroFunction(event);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(PreferencesActivity.KEY_BULK_MODE, true)) {
        	resetStatusView();
        }
	}
}

// calculates orientation angles from accelerometer and magnetometer output
public void calculateAccMagOrientation() {
	float[] rotationMatrix = new float[9];
	if((accelerometer_values != null) && (magnitude_values != null)) {
	    if(SensorManager.getRotationMatrix(rotationMatrix, null, accelerometer_values, magnitude_values)) {
	        SensorManager.getOrientation(rotationMatrix, accMagOrientation);
	        accMagOrientation[0] = accMagOrientation[0] - shiftRad;
	    }
	}
}

// This function is borrowed from the Android reference
// at http://developer.android.com/reference/android/hardware/SensorEvent.html#values
// It calculates a rotation vector from the gyroscope angular speed values.
private void getRotationVectorFromGyro(float[] gyroValues,
        float[] deltaRotationVector,
        float timeFactor)
{
	float[] normValues = new float[3];
	
	// Calculate the angular speed of the sample
	float omegaMagnitude =
	(float)Math.sqrt(gyroValues[0] * gyroValues[0] +
	gyroValues[1] * gyroValues[1] +
	gyroValues[2] * gyroValues[2]);
	
	// Normalize the rotation vector if it's big enough to get the axis
	if(omegaMagnitude > EPSILON) {
	normValues[0] = gyroValues[0] / omegaMagnitude;
	normValues[1] = gyroValues[1] / omegaMagnitude;
	normValues[2] = gyroValues[2] / omegaMagnitude;
	}
	
	// Integrate around this axis with the angular speed by the timestep
	// in order to get a delta rotation from this sample over the timestep
	// We will convert this axis-angle representation of the delta rotation
	// into a quaternion before turning it into the rotation matrix.
	float thetaOverTwo = omegaMagnitude * timeFactor;
	float sinThetaOverTwo = (float)Math.sin(thetaOverTwo);
	float cosThetaOverTwo = (float)Math.cos(thetaOverTwo);
	deltaRotationVector[0] = sinThetaOverTwo * normValues[0];
	deltaRotationVector[1] = sinThetaOverTwo * normValues[1];
	deltaRotationVector[2] = sinThetaOverTwo * normValues[2];
	deltaRotationVector[3] = cosThetaOverTwo;
}

// This function performs the integration of the gyroscope data.
// It writes the gyroscope based orientation into gyroOrientation.
public void gyroFunction(SensorEvent event) {
    // don't start until first accelerometer/magnetometer orientation has been acquired
    if (accMagOrientation == null)
        return;
 
    // waiting for gyroscope initialize done in calculateFusedOrientationTask
    if(initState) {
        return;
    }
 
    // copy the new gyro values into the gyro array
    // convert the raw gyro data into a rotation vector
    float[] deltaVector = new float[4];
    if(timestamp != 0) {
        final float dT = (event.timestamp - timestamp) * NS2S;
    System.arraycopy(event.values, 0, gyro, 0, 3);
    getRotationVectorFromGyro(gyro, deltaVector, dT / 2.0f);
    }
 
    // measurement done, save current time for next interval
    timestamp = event.timestamp;
 
    // convert rotation vector into rotation matrix
    float[] deltaMatrix = new float[9];
    SensorManager.getRotationMatrixFromVector(deltaMatrix, deltaVector);
 
    // apply the new rotation interval on the gyroscope based rotation matrix
    gyroMatrix = matrixMultiplication(gyroMatrix, deltaMatrix);
 
    // get the gyroscope based orientation from the rotation matrix
    SensorManager.getOrientation(gyroMatrix, gyroOrientation);
}

private float[] getRotationMatrixFromOrientation(float[] o) {
    float[] xM = new float[9];
    float[] yM = new float[9];
    float[] zM = new float[9];
 
    float sinX = (float)Math.sin(o[1]);
    float cosX = (float)Math.cos(o[1]);
    float sinY = (float)Math.sin(o[2]);
    float cosY = (float)Math.cos(o[2]);
    float sinZ = (float)Math.sin(o[0]);
    float cosZ = (float)Math.cos(o[0]);
 
    // rotation about x-axis (pitch)
    xM[0] = 1.0f; xM[1] = 0.0f; xM[2] = 0.0f;
    xM[3] = 0.0f; xM[4] = cosX; xM[5] = sinX;
    xM[6] = 0.0f; xM[7] = -sinX; xM[8] = cosX;
 
    // rotation about y-axis (roll)
    yM[0] = cosY; yM[1] = 0.0f; yM[2] = sinY;
    yM[3] = 0.0f; yM[4] = 1.0f; yM[5] = 0.0f;
    yM[6] = -sinY; yM[7] = 0.0f; yM[8] = cosY;
 
    // rotation about z-axis (azimuth)
    zM[0] = cosZ; zM[1] = sinZ; zM[2] = 0.0f;
    zM[3] = -sinZ; zM[4] = cosZ; zM[5] = 0.0f;
    zM[6] = 0.0f; zM[7] = 0.0f; zM[8] = 1.0f;
 
    // rotation order is y, x, z (roll, pitch, azimuth)
    float[] resultMatrix = matrixMultiplication(xM, yM);
    resultMatrix = matrixMultiplication(zM, resultMatrix);
    return resultMatrix;
}

private float[] matrixMultiplication(float[] A, float[] B) {
    float[] result = new float[9];
 
    result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
    result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
    result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];
 
    result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
    result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
    result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];
 
    result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
    result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
    result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];
 
    return result;
}

@Override
public void onAccuracyChanged(Sensor sensor, int accuracy) {
	Log.w("zxing", "onAccuracyChanged: " + accuracy);
}

@Override
public void onLocationChanged(Location location) {
	// TODO Auto-generated method stub. On GPS location changed.
	//Log.w("zxing", "onLocationChanged " + location.getLatitude() + " " + location.getLongitude());
	lastLocation = location;
	float[] results = new float[3];
	Location.distanceBetween(centerLatitude, lastLocation.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude(), results);
	if( (int)results[1] == 0) {
		//0 means bearing to north. Otherwise will be 180. Means bearing to south
		gpsAxis[0] = results[0];
	} else {
		gpsAxis[0] = -results[0];
	}
	Location.distanceBetween(lastLocation.getLatitude(), centerLongitude, lastLocation.getLatitude(), lastLocation.getLongitude(), results);
	if((int)results[1] == 89) {
		//89 means bearing to east. Otherwise will be -89. Means bearing to west.
		gpsAxis[1] = results[0];
	} else {
		gpsAxis[1] = -results[0];
	}
	gpsAxis[2] = (float)(150 - lastLocation.getAltitude());
}

@Override
public void onStatusChanged(String provider, int status, Bundle extras) {
	// TODO Auto-generated method stub
	Log.w("zxing", "onStatusChanged");
}

@Override
public void onProviderEnabled(String provider) {
	// TODO Auto-generated method stub
	Log.w("zxing", "onProviderEnabled");
}

@Override
public void onProviderDisabled(String provider) {
	// TODO Auto-generated method stub
	Log.w("zxing", "onProviderDisabled");
}
//Fusion Sensor
//private float centerAzimuth = (float)-Math.PI; //表示誠正西拍攝(因為尚未校正為橫向，所以是-PI)
private float centerAzimuth = (float)-Math.PI/2; //表示誠正北拍攝(因為尚未校正為橫向，所以是-PI/2)
private float shiftRad = 0;
class calculateFusedOrientationTask extends TimerTask {
    public void run() {
    	//initial Gyroscope data
    	if(initState) {
    		shiftRad = accMagOrientation[0] - centerAzimuth;
    		accMagOrientation[0] = accMagOrientation[0] - shiftRad;
            gyroMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            System.arraycopy(accMagOrientation, 0, gyroOrientation, 0, 3);
            initState = false;
    	}
    	else {
            if(count <= 50) {
            	doCalibrateGyroscope();
            }
            else if(count == 51) {
            	gyroMatrix = getRotationMatrixFromOrientation(accMagOrientation);
            	count++;
            }
            else
            {
            	
            	//Log.w("zxing", "GyroDiff:" + avgGyroDiffValues[0] + ":" + avgGyroDiffValues[1] + ":" + avgGyroDiffValues[2]);
            	gyroOrientation[0] = gyroOrientation[0] - avgGyroDiffValues[0];
            	gyroOrientation[1] = gyroOrientation[1] - avgGyroDiffValues[1];
            	gyroOrientation[2] = gyroOrientation[2] - avgGyroDiffValues[2];
            	
            	/*
            	gyroOrientation[0] = (float) (gyroOrientation[0] -2.061634E-4);
            	gyroOrientation[1] = (float) (gyroOrientation[1] -6.9556135E-4);
            	gyroOrientation[2] = (float) (gyroOrientation[2] -0.0012522547);
            	*/
            	gyroMatrix = getRotationMatrixFromOrientation(gyroOrientation);
            }
    	}
        float oneMinusCoeff = 1.0f - FILTER_COEFFICIENT;
        /*
         * Fix for 179<--> -179transition problem:
         * Check whether one of the two orientation angles (gyro or accMag) is negative while the other one is positive.
         * If so, add 360(2 * math.PI) to the negative value, perform the sensor fusion, and remove the 360from the result
         * if it is greater than 180 This stabilizes the output in positive-to-negative-transition cases.
         */
        
        // azimuth   
        if (gyroOrientation[0] < -0.5 * Math.PI && accMagOrientation[0] > 0.0) {
        	fusedOrientation[0] = (float) (FILTER_COEFFICIENT * (gyroOrientation[0] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[0]);
    		fusedOrientation[0] -= (fusedOrientation[0] > Math.PI) ? 2.0 * Math.PI : 0;
        }
        else if (accMagOrientation[0] < -0.5 * Math.PI && gyroOrientation[0] > 0.0) {
        	fusedOrientation[0] = (float) (FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * (accMagOrientation[0] + 2.0 * Math.PI));
        	fusedOrientation[0] -= (fusedOrientation[0] > Math.PI)? 2.0 * Math.PI : 0;
        }
        else {
        	fusedOrientation[0] = FILTER_COEFFICIENT * gyroOrientation[0] + oneMinusCoeff * accMagOrientation[0];
        }
        // pitch
        if (gyroOrientation[1] < -0.5 * Math.PI && accMagOrientation[1] > 0.0) {
        	fusedOrientation[1] = (float) (FILTER_COEFFICIENT * (gyroOrientation[1] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[1]);
    		fusedOrientation[1] -= (fusedOrientation[1] > Math.PI) ? 2.0 * Math.PI : 0;
        }
        else if (accMagOrientation[1] < -0.5 * Math.PI && gyroOrientation[1] > 0.0) {
        	fusedOrientation[1] = (float) (FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * (accMagOrientation[1] + 2.0 * Math.PI));
        	fusedOrientation[1] -= (fusedOrientation[1] > Math.PI)? 2.0 * Math.PI : 0;
        }
        else {
        	fusedOrientation[1] = FILTER_COEFFICIENT * gyroOrientation[1] + oneMinusCoeff * accMagOrientation[1];
        }
        
        // roll
        if (gyroOrientation[2] < -0.5 * Math.PI && accMagOrientation[2] > 0.0) {
        	fusedOrientation[2] = (float) (FILTER_COEFFICIENT * (gyroOrientation[2] + 2.0 * Math.PI) + oneMinusCoeff * accMagOrientation[2]);
    		fusedOrientation[2] -= (fusedOrientation[2] > Math.PI) ? 2.0 * Math.PI : 0;
        }
        else if (accMagOrientation[2] < -0.5 * Math.PI && gyroOrientation[2] > 0.0) {
        	fusedOrientation[2] = (float) (FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * (accMagOrientation[2] + 2.0 * Math.PI));
        	fusedOrientation[2] -= (fusedOrientation[2] > Math.PI)? 2.0 * Math.PI : 0;
        }
        else {
        	fusedOrientation[2] = FILTER_COEFFICIENT * gyroOrientation[2] + oneMinusCoeff * accMagOrientation[2];
        }

        
        // overwrite gyro matrix and orientation with fused orientation
        // to comensate gyro drift
        gyroMatrix = getRotationMatrixFromOrientation(fusedOrientation);
        System.arraycopy(fusedOrientation, 0, gyroOrientation, 0, 3);
    }
}

static int count = 0;
static float preGyroValues[] = {0,0,0};
static float cumulativeDiffValues[] = {0,0,0};
static float avgGyroDiffValues[] = {0,0,0};
private void doCalibrateGyroscope() {
	if(count > 0){
		cumulativeDiffValues[0] = cumulativeDiffValues[0] + (gyroOrientation[0] - preGyroValues[0]);
		cumulativeDiffValues[1] = cumulativeDiffValues[1] + (gyroOrientation[1] - preGyroValues[1]);
		cumulativeDiffValues[2] = cumulativeDiffValues[2] + (gyroOrientation[2] - preGyroValues[2]);
		avgGyroDiffValues[0] = cumulativeDiffValues[0] / count;
		avgGyroDiffValues[1] = cumulativeDiffValues[1] / count;
		avgGyroDiffValues[2] = cumulativeDiffValues[2] / count;
	}
	preGyroValues[0] = gyroOrientation[0];
	preGyroValues[1] = gyroOrientation[1];
	preGyroValues[2] = gyroOrientation[2];
	count++;
}

}
