/*
 * Copyright (C) 2009 ZXing authors
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

package com.google.zxing.client.android.history;

import android.database.sqlite.SQLiteException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.client.android.Intents;
import com.google.zxing.client.android.PreferencesActivity;
import com.google.zxing.client.android.gps.GPSTracker;
import com.google.zxing.client.android.result.ResultHandler;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * <p>Manages functionality related to scan history.</p>
 *
 * @author Sean Owen
 */
public final class HistoryManager {

  private static final String TAG = HistoryManager.class.getSimpleName();

  private static final int MAX_ITEMS = 2000;

  private static final String[] COLUMNS = {
      DBHelper.REAL_POS,
      DBHelper.VPP_X,
      DBHelper.VPP_Y,
      DBHelper.VPP_Z,
      DBHelper.GPS_LON,
      DBHelper.GPS_LAT,
      DBHelper.GPS_ALT,
      DBHelper.TIMESTAMP_COL,
  };

  private static final String[] COUNT_COLUMN = { "COUNT(1)" };

  private static final String[] ID_COL_PROJECTION = { DBHelper.ID_COL };
  private static final DateFormat EXPORT_DATE_TIME_FORMAT =
      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

  private final Activity activity;

  public HistoryManager(Activity activity) {
    this.activity = activity;
  }

  public boolean hasHistoryItems() {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
      db = helper.getReadableDatabase();
      cursor = db.query(DBHelper.TABLE_NAME, COUNT_COLUMN, null, null, null, null, null);
      cursor.moveToFirst();
      return cursor.getInt(0) > 0;
    } finally {
      close(cursor, db);
    }
  }

  public List<HistoryItem> buildHistoryItems() {
    SQLiteOpenHelper helper = new DBHelper(activity);
    List<HistoryItem> items = new ArrayList<HistoryItem>();
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
      db = helper.getReadableDatabase();
      cursor = db.query(DBHelper.TABLE_NAME, COLUMNS, null, null, null, null, DBHelper.TIMESTAMP_COL + " DESC");
      while (cursor.moveToNext()) {
    	  String realPositio = cursor.getString(0);
    	  float vpp_axis[] = {cursor.getFloat(1), cursor.getFloat(2), cursor.getFloat(3)};
    	  double gps_axis[] = {cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6)};
    	  long timestamp = cursor.getLong(7);
    	  Result result = new Result(realPositio, null, null, BarcodeFormat.valueOf("QR_CODE"), timestamp);
    	  items.add(new HistoryItem(result, realPositio, vpp_axis, gps_axis, timestamp));
    	  }
      } finally {
    	  close(cursor, db);
    	  }
    return items;
  }

  public HistoryItem buildHistoryItem(int number) {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
    	db = helper.getReadableDatabase();
    	cursor = db.query(DBHelper.TABLE_NAME, COLUMNS, null, null, null, null, DBHelper.TIMESTAMP_COL + " DESC");
    	cursor.move(number + 1);
    	String realPositio = cursor.getString(0);
    	float vpp_axis[] = {cursor.getFloat(1), cursor.getFloat(2), cursor.getFloat(3)};
    	double gps_axis[] = {cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6)};
    	long timestamp = cursor.getLong(7);
    	Result result = new Result(realPositio, null, null, BarcodeFormat.valueOf("QR_CODE"), timestamp);
    	return new HistoryItem(result, realPositio, vpp_axis, gps_axis, timestamp);
    	} finally {
    		close(cursor, db);
    		}
  }
  
  public void deleteHistoryItem(int number) {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
      db = helper.getWritableDatabase();      
      cursor = db.query(DBHelper.TABLE_NAME,
                        ID_COL_PROJECTION,
                        null, null, null, null,
                        DBHelper.TIMESTAMP_COL + " DESC");
      cursor.move(number + 1);
      db.delete(DBHelper.TABLE_NAME, DBHelper.ID_COL + '=' + cursor.getString(0), null);
    } finally {
      close(cursor, db);
    }
  }

  public void addHistoryItem(String realPosition, float vppAxis[], double gpsAxis[], Result result, ResultHandler handler) {
	  // Do not save this item to the history if the preference is turned off, or the contents are
	  // considered secure.
	  if (!activity.getIntent().getBooleanExtra(Intents.Scan.SAVE_HISTORY, true) ||
			  handler.areContentsSecure()) {
		  return;
		  }
	  SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	  if (!prefs.getBoolean(PreferencesActivity.KEY_REMEMBER_DUPLICATES, false)) {
		  deletePrevious(result.getText());
		  }
	    ContentValues values = new ContentValues();
	    //Vincent: We need change code to get new result and put data into database.
	    values.put(DBHelper.REAL_POS, realPosition);
	    values.put(DBHelper.VPP_X, vppAxis[0]);
	    values.put(DBHelper.VPP_Y, vppAxis[1]);
	    values.put(DBHelper.VPP_Z, vppAxis[2]);
	    values.put(DBHelper.GPS_LON, gpsAxis[0]);
	    values.put(DBHelper.GPS_LAT, gpsAxis[1]);
	    values.put(DBHelper.GPS_ALT, gpsAxis[2]);	
	    values.put(DBHelper.TIMESTAMP_COL, System.currentTimeMillis());
	    
	    SQLiteOpenHelper helper = new DBHelper(activity);
	    SQLiteDatabase db = null;
	    try {
	      db = helper.getWritableDatabase();      
	      // Insert the new entry into the DB.
	      db.insert(DBHelper.TABLE_NAME, DBHelper.TIMESTAMP_COL, values);
	    } finally {
	      close(null, db);
	    }
  }
  
  public void addHistoryItem(String realPosition, float vppAxis[], Result result, ResultHandler handler) {
  	  GPSTracker gps;
  	  gps = new GPSTracker(activity);
      // check if GPS enabled     
      if(gps.canGetLocation()) {
    	  double gpsAxis[] = {gps.getLatitude(), gps.getLongitude(), gps.getAltitude()};
          addHistoryItem(realPosition, vppAxis, gpsAxis, result, handler);
      }
      
  }

  private void deletePrevious(String text) {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    try {
      db = helper.getWritableDatabase();      
      //Vincent: I'm not sure how to change this SQL command too.
      db.delete(DBHelper.TABLE_NAME, DBHelper.REAL_POS + "=?", new String[] { text });
    } finally {
      close(null, db);
    }
  }

  public void trimHistory() {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
      db = helper.getWritableDatabase();      
      cursor = db.query(DBHelper.TABLE_NAME,
                        ID_COL_PROJECTION,
                        null, null, null, null,
                        DBHelper.TIMESTAMP_COL + " DESC");
      cursor.move(MAX_ITEMS);
      while (cursor.moveToNext()) {
        String id = cursor.getString(0);
        Log.i(TAG, "Deleting scan history ID " + id);
        db.delete(DBHelper.TABLE_NAME, DBHelper.ID_COL + '=' + id, null);
      }
    } catch (SQLiteException sqle) {
      // We're seeing an error here when called in CaptureActivity.onCreate() in rare cases
      // and don't understand it. First theory is that it's transient so can be safely ignored.
      Log.w(TAG, sqle);
      // continue
    } finally {
      close(cursor, db);
    }
  }

  /**
   * <p>Builds a text representation of the scanning history. Each scan is encoded on one
   * line, terminated by a line break (\r\n). The values in each line are comma-separated,
   * and double-quoted. Double-quotes within values are escaped with a sequence of two
   * double-quotes. The fields output are:</p>
   *
   * <ul>
   *  <li>Raw text</li>
   *  <li>Display text</li>
   *  <li>Format (e.g. QR_CODE)</li>
   *  <li>Timestamp</li>
   *  <li>Formatted version of timestamp</li>
   * </ul>
   * Vincent: will save as csv file here.
   */
  CharSequence buildHistory() {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    Cursor cursor = null;
    try {
      db = helper.getWritableDatabase();
      cursor = db.query(DBHelper.TABLE_NAME,
                        COLUMNS,
                        null, null, null, null,
                        DBHelper.TIMESTAMP_COL + " DESC");

      StringBuilder historyText = new StringBuilder(1000);
      while (cursor.moveToNext()) {

        historyText.append('"').append(massageHistoryField(cursor.getString(0))).append("\",");
        historyText.append('"').append(massageHistoryField(cursor.getString(1))).append("\",");
        historyText.append('"').append(massageHistoryField(cursor.getString(2))).append("\",");
        historyText.append('"').append(massageHistoryField(cursor.getString(3))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getDouble(4)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getDouble(5)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getDouble(6)))).append("\",");

        // Add timestamp again, formatted
        long timestamp = cursor.getLong(7);
        historyText.append('"').append(massageHistoryField(
            EXPORT_DATE_TIME_FORMAT.format(new Date(timestamp)))).append("\"\r\n");
      }
      return historyText;
    } finally {
      close(cursor, db);
    }
  }
  
  void clearHistory() {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    try {
      db = helper.getWritableDatabase();      
      db.delete(DBHelper.TABLE_NAME, null, null);
    } finally {
      close(null, db);
    }
  }

  static Uri saveHistory(String history) {
    File bsRoot = new File(Environment.getExternalStorageDirectory(), "BarcodeScanner");
    File historyRoot = new File(bsRoot, "History");
    if (!historyRoot.exists() && !historyRoot.mkdirs()) {
      Log.w(TAG, "Couldn't make dir " + historyRoot);
      return null;
    }
    File historyFile = new File(historyRoot, "history-" + System.currentTimeMillis() + ".csv");
    OutputStreamWriter out = null;
    try {
      out = new OutputStreamWriter(new FileOutputStream(historyFile), Charset.forName("UTF-8"));
      out.write(history);
      return Uri.parse("file://" + historyFile.getAbsolutePath());
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't access file " + historyFile + " due to " + ioe);
      return null;
    } finally {
      if (out != null) {
        try {
          out.close();
        } catch (IOException ioe) {
          // do nothing
        }
      }
    }
  }

  private static String massageHistoryField(String value) {
    return value == null ? "" : value.replace("\"","\"\"");
  }
  
  private static void close(Cursor cursor, SQLiteDatabase database) {
    if (cursor != null) {
      cursor.close();
    }
    if (database != null) {
      database.close();
    }
  }

}
