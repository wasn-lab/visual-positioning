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
      DBHelper.SAS_INFO,
      DBHelper.MAG_X,
      DBHelper.MAG_Y,
      DBHelper.MAG_Z,
      DBHelper.FUS_X,
      DBHelper.FUS_Y,
      DBHelper.FUS_Z,     
      DBHelper.GPS_LON,
      DBHelper.GPS_LAT,
      DBHelper.GPS_ALT,
      DBHelper.AZIMUTH,
      DBHelper.PITCH,
      DBHelper.ROLL,
      DBHelper.SAS_SIZE,
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
    	  float mag_axis[] = {cursor.getFloat(1), cursor.getFloat(2), cursor.getFloat(3)};
    	  float fus_axis[] = {cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6)};
    	  float gps_axis[] = {cursor.getFloat(7), cursor.getFloat(8), cursor.getFloat(9)};
    	  float orientation[] = {cursor.getFloat(10), cursor.getFloat(11), cursor.getFloat(12)};
    	  float distance = cursor.getFloat(13);
    	  long timestamp = cursor.getLong(14);
    	  Result result = new Result(cursor.getString(0), null, null, BarcodeFormat.valueOf("QR_CODE"), timestamp);
    	  items.add(new HistoryItem(result, mag_axis, fus_axis, gps_axis, orientation, distance, timestamp));
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
  	  float mag_axis[] = {cursor.getFloat(1), cursor.getFloat(2), cursor.getFloat(3)};
  	  float fus_axis[] = {cursor.getFloat(4), cursor.getFloat(5), cursor.getFloat(6)};
  	  float gps_axis[] = {cursor.getFloat(7), cursor.getFloat(8), cursor.getFloat(9)};
  	  float orientation[] = {cursor.getFloat(10), cursor.getFloat(11), cursor.getFloat(12)};
  	  float distance = cursor.getFloat(13);
  	  long timestamp = cursor.getLong(14);
    	Result result = new Result(cursor.getString(0), null, null, BarcodeFormat.valueOf("QR_CODE"), timestamp);
    	return new HistoryItem(result, mag_axis, fus_axis, gps_axis, orientation, distance, timestamp);
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
  public void logcatArrayString(String name, double[] array) {
	    String print = String.format("%8.2f  %8.2f  %8.2f", Math.toDegrees(array[0]), Math.toDegrees(array[0]), Math.toDegrees(array[0]));
	    Log.w("zxing", name + print);
}

  public void addHistoryItem(double magAxis[], double fusAxis[], double gpsAxis[], float orientation[], double sasSize, Result result, ResultHandler handler) {
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
	    values.put(DBHelper.SAS_INFO, result.getText());
	    values.put(DBHelper.MAG_X, magAxis[0]);
	    values.put(DBHelper.MAG_Y, magAxis[1]);
	    values.put(DBHelper.MAG_Z, magAxis[2]);
	    values.put(DBHelper.FUS_X, fusAxis[0]);
	    values.put(DBHelper.FUS_Y, fusAxis[1]);
	    values.put(DBHelper.FUS_Z, fusAxis[2]);
	    values.put(DBHelper.GPS_LON, gpsAxis[0]);
	    values.put(DBHelper.GPS_LAT, gpsAxis[1]);
	    values.put(DBHelper.GPS_ALT, gpsAxis[2]);	
	    values.put(DBHelper.AZIMUTH, orientation[0]);
	    values.put(DBHelper.PITCH, orientation[1]);
	    values.put(DBHelper.ROLL, orientation[2]);
	    values.put(DBHelper.SAS_SIZE, sasSize);
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

  private void deletePrevious(String text) {
    SQLiteOpenHelper helper = new DBHelper(activity);
    SQLiteDatabase db = null;
    try {
      db = helper.getWritableDatabase();      
      //Vincent: I'm not sure how to change this SQL command too.
      db.delete(DBHelper.TABLE_NAME, DBHelper.SAS_INFO + "=?", new String[] { text });
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
      //add title
      historyText.append('"').append(massageHistoryField("sas_info")).append("\",");
      historyText.append('"').append(massageHistoryField("mag_x")).append("\",");
      historyText.append('"').append(massageHistoryField("mag_y")).append("\",");
      historyText.append('"').append(massageHistoryField("mag_z")).append("\",");
      historyText.append('"').append(massageHistoryField("fus_x")).append("\",");
      historyText.append('"').append(massageHistoryField("fus_y")).append("\",");
      historyText.append('"').append(massageHistoryField("fus_z")).append("\",");
      historyText.append('"').append(massageHistoryField("gps_lon")).append("\",");
      historyText.append('"').append(massageHistoryField("gps_lat")).append("\",");
      historyText.append('"').append(massageHistoryField("gps_alt")).append("\",");
      historyText.append('"').append(massageHistoryField("azimuth")).append("\",");
      historyText.append('"').append(massageHistoryField("pitch")).append("\",");
      historyText.append('"').append(massageHistoryField("roll")).append("\",");
      historyText.append('"').append(massageHistoryField("sas_size")).append("\",");
      historyText.append('"').append(massageHistoryField("timestamp")).append("\"\r\n");
      //save data into csv file
      while (cursor.moveToNext()) {
        historyText.append('"').append(massageHistoryField(cursor.getString(0))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(1)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(2)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(3)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(4)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(5)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(6)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(7)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(8)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(9)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(10)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(11)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(12)))).append("\",");
        historyText.append('"').append(massageHistoryField(String.valueOf(cursor.getFloat(13)))).append("\",");
        // Add timestamp again, formatted
        long timestamp = cursor.getLong(14);
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
