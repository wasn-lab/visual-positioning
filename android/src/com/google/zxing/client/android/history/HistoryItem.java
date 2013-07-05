/*
 * Copyright 2012 ZXing authors
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

import java.util.Date;

import com.google.zxing.Result;

public final class HistoryItem {
  private final Result result;	
  private final float mag[];
  private final float fus[];
  private final float gps[];
  private final double sas_axis[];
  private final float orientation[];
  private final float sasSize;
  private final long timestamp;
  
  HistoryItem(Result result, float mag[], float fus[], float gps[], double sas_axis[], float orientation[], float sasSize, long timestamp) {
	  this.result = result;
	  this.mag = mag;
	  this.fus = fus;
	  this.gps = gps;
	  this.sas_axis = sas_axis;
	  this.orientation = orientation;
	  this.sasSize = sasSize;
	  this.timestamp = timestamp;
  }
  
  public Result getResult() {
	  return result;
  }
  
  public String getText() {
	  Date datetime = new Date(timestamp);
	  return datetime.toString();
  }
  
  public String getDisplayAndDetails() {
	  String details;
	  details = "MAG: " + mag[0] + " : " + mag[1] + " : " + mag[2]
			  +"\nFUS: " + fus[0] + " : " + fus[1] + " : " + fus[2]
			  +"\nSAS: " + sas_axis[0] + " : " + sas_axis[1] + " : " + sas_axis[2];
	  return details;
  }
}
