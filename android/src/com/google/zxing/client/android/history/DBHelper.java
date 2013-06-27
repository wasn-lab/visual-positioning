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

import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

/**
 * @author bravesheng@gmail.com
 * Use database to record road test log. Will save VPP / GPS / REAL positions.
 * 
 */
final class DBHelper extends SQLiteOpenHelper {

  private static final int DB_VERSION = 5;
  private static final String DB_NAME = "visual_positioning.db";
  static final String TABLE_NAME = "history";
  static final String ID_COL = "id";
  static final String SAS_INFO = "sas_info";
  static final String MAG_X = "mag_x";
  static final String MAG_Y = "mag_y";
  static final String MAG_Z = "mag_z";
  static final String FUS_X = "fus_x";
  static final String FUS_Y = "fus_y";
  static final String FUS_Z = "fus_z";
  static final String GPS_LON = "gps_lon";
  static final String GPS_LAT = "gps_lat";
  static final String GPS_ALT = "gps_alt";
  static final String AZIMUTH = "azimuth";
  static final String PITCH = "pitch";
  static final String ROLL = "roll";
  static final String DISTANCE = "distance";
  static final String TIMESTAMP_COL = "timestamp";

  DBHelper(Context context) {
    super(context, DB_NAME, null, DB_VERSION);
  }

  @Override
  public void onCreate(SQLiteDatabase sqLiteDatabase) {
    sqLiteDatabase.execSQL(
            "CREATE TABLE " + TABLE_NAME + " (" +
            ID_COL + " INTEGER PRIMARY KEY, " +
            SAS_INFO + " TEXT, " +
            MAG_X + " REAL, " +
            MAG_Y + " REAL, " +
            MAG_Z + " REAL, " +
            FUS_X + " REAL, " +
            FUS_Y + " REAL, " +
            FUS_Z + " REAL, " +
            GPS_LON + " REAL, " +
            GPS_LAT + " REAL, " +
            GPS_ALT + " REAL, " +
            AZIMUTH + " INTEGER," +
            PITCH + " INTEGER," +
            ROLL + " INTEGER," +
            DISTANCE + " REAL," +
            TIMESTAMP_COL + " INTEGER);");
  }

  @Override
  public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
    sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
    onCreate(sqLiteDatabase);
  }

}
