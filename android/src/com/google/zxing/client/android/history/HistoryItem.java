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
  private final float vpp[];
  private final float gps[];
  private final int orientation[];
  private final long timestamp;
  
  HistoryItem(Result result, float vpp[], float gps[], int orientation[], long timestamp) {
	  this.result = result;
	  this.vpp = vpp;
	  this.gps = gps;
	  this.orientation = orientation;
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
	  details = "VPP: " + vpp[0] + " : " + vpp[1] + " : " + vpp[2]
			  +"\nGPS: " + gps[0] + " : " + gps[1] + " : " + gps[2];
	  return details;
  }
}
