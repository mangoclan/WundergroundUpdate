/*
 * WundergroundUpdate updates the Weather Underground site (wunderground.com) with data from the Oregon Scientific WMR200A
 * personal weather station. It will probably work with other Oregon Scientific weather stations, and perhaps with slight
 * modification for any weather station supported by Weather Display.
 * 
 * This code was developed for Weather Display version 10.37j build 01. When executed, this program reads the last line
 * of Weather Display's log file, parses the values into a hashmap using wunderground.com's URL parameters as keys. It then
 * updates the data to wunderground.com via an HTTP request.
 * 
 * In order to use this program you must first create an account and a station ID and password at wunderground.com. The station
 * ID and password must then be configured in WundergruondUpdate.properties, along with the Weather Display log location if
 * you did not install Weather Display in the default location (c:/wdisplay).
 * 
 * The properties file is distributed as WundergroundUpdate.properties.dist. Copy this file to WundergroundUpdate.properties
 * and edit the new file to add your wunderground.com credentials and Weather Update log path.
 * 
 * This work is licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ or send a letter to
 * Creative Commons, 444 Castro Street, Suite 900, Mountain View, California, 94041, USA.
 */
package com.mangoclan;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/*
 * 
 * The following wunderground.com upload URL parameters are also used as HashMap keys
 * when parsing log lines:
 * 
 * dateutc - [YYYY-MM-DD HH:MM:SS (mysql format)] In Universal Coordinated Time (UTC) Not local time
 * winddir - [0-360 instantaneous wind direction]
 * windspeedmph - [mph instantaneous wind speed]
 * windgustmph - [mph current wind gust, using software specific time period]
 * windgustdir - [0-360 using software specific time period]
 * windspdmph_avg2m  - [mph 2 minute average wind speed mph]
 * winddir_avg2m - [0-360 2 minute average wind direction]
 * windgustmph_10m - [mph past 10 minutes wind gust mph ]
 * windgustdir_10m - [0-360 past 10 minutes wind gust direction]
 * humidity - [% outdoor humidity 0-100%]
 * 
 * dewptf- [F outdoor dewpoint F]
 *
 * tempf - [F outdoor temperature] 
 * for extra outdoor sensors use temp2f, temp3f, and so on
 * 
 * rainin - [rain inches over the past hour)] -- the accumulated rainfall in the past 60 min
 * dailyrainin - [rain inches so far today in local time]
 * 
 * baromin - [barometric pressure inches]
 * 
 * weather - [text] -- metar style (+RA)
 * clouds - [text] -- SKC, FEW, SCT, BKN, OVC
 * 
 * soiltempf - [F soil temperature]
 * for sensors 2,3,4 use soiltemp2f, soiltemp3f, and soiltemp4f
 * soilmoisture - [%]
 * for sensors 2,3,4 use soilmoisture2, soilmoisture3, and soilmoisture4
 * 
 * leafwetness  - [%]
 * + for sensor 2 use leafwetness2
 * 
 * solarradiation - [W/m^2]
 * UV - [index]
 * 
 * visibility - [nm visibility]
 * 
 * indoortempf - [F indoor temperature F]
 * indoorhumidity - [% indoor humidity 0-100]
 * 
 * softwaretype - [text] ie: WeatherLink, VWS, WeatherDisplay
 * 
 */

public class WundergroundUpdate
{

	Properties props = new Properties();
	HashMap<String, String> logLineMap = null;
	SimpleTimeZone est = null;
	
	private void init()
	{
		// load application properties
        try
        {
        	props.load(this.getClass().getResourceAsStream("WundergroundUpdate.properties"));
        }

        //catch exception in case properties file does not exist
        catch(IOException e)
        {
     	   e.printStackTrace();
        }
        
        // set up eastern time zone with daylight savings start/end rules
        String[] ids = TimeZone.getAvailableIDs(-8 * 60 * 60 * 1000);
        // if no ids were returned, something is wrong. get out.
        if (ids.length == 0)
            System.exit(0);

        // create Eastern Standard Time time zone
        est = new SimpleTimeZone(-5 * 60 * 60 * 1000, ids[0]);

        // set up rules for daylight savings time
        est.setStartRule(Calendar.MARCH, 2, Calendar.SUNDAY, 2 * 60 * 60 * 1000);
        est.setEndRule(Calendar.NOVEMBER, 1, Calendar.SUNDAY, 2 * 60 * 60 * 1000);

	}
	
	private String getLastLogLine()
	{
		String retval = null;
		
		try 
		{
			FileInputStream in = new FileInputStream(props.getProperty("dataFilePath"));
			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			String strLine = null, tmp;

			while ((tmp = br.readLine()) != null)
			{
				strLine = tmp;
			}
			retval = strLine;
			in.close();
		}
		
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

		return retval;

	}
	
	// convert log line time to UTC formatted as YYYY-MM-DD HH:MM:SS
	private String formatTimeString(int year, int month, int day, int hour, int minute)
	{
		String retval = null;
		
		GregorianCalendar localCal = new GregorianCalendar(year, month-1, day, hour, minute);
		localCal.setTimeZone(est);
		
		DateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		DateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		retval = utcFormat.format(localCal.getTime());
				
//		System.out.println("local: " + localFormat.format(localCal.getTime()));
//		System.out.println("UTC  : " + retval);
		
		return retval;
	}
	
	/**
	 * @param logLine a single line from the "weather display" dailylog.txt file
	 * @return a map of values parsed form the line, with keys that are the URL parameters for wunderground.com upload
	 * 
	 * A log line looks like (PR=precip):
	 *  0  1    2  3  4    5   6    7      8   9  10  11     12    13    14    15   16
	 * DD MM YYYY HH MM TEMP HUM DEWP BARO   WSP GSP WDR  MinPR DayPR MonPC YrPR  HIDX
	 * 10  4 2010  7 10 40.5  55 25.5 30.002   0   3 224  0.000 0.000 0.917 4.598 40.5
	 */
	private HashMap<String, String> parseLogLine(String logLine)
	{
		HashMap<String, String> retval = null;
		if (logLine != null && !logLine.isEmpty())
		{
			retval = new HashMap<String, String>();
			String tokens[] = logLine.split(" ");
			ArrayList<String> fields = new ArrayList<String>();
			for (String t: tokens)
			{
				if (!t.isEmpty())
				{
					fields.add(t);
				}
			}
			
			retval.put("dateutc", formatTimeString(Integer.parseInt(fields.get(2)),
													Integer.parseInt(fields.get(1)),
													Integer.parseInt(fields.get(0)),
													Integer.parseInt(fields.get(3)),
													Integer.parseInt(fields.get(4))));

			retval.put("tempf", fields.get(5));
			retval.put("humidity", fields.get(6));
			retval.put("dewptf", fields.get(7));
			retval.put("baromin", fields.get(8));
			retval.put("windspeedmph", fields.get(9));
			retval.put("windgustmph", fields.get(10));
			retval.put("winddir", fields.get(11));
			retval.put("dailyrainin", fields.get(13));
			retval.put("softwaretype", "WeatherDisplay");
		}
		return retval;
	}
	
	private URL formUpdateURL(HashMap<String, String> args)
	{
		URL updateURL = null;
		
		StringBuffer sbufURL = new StringBuffer();
		for (String key : args.keySet())
		{
			try
			{
				sbufURL.append("&" + key + "=" + URLEncoder.encode(args.get(key), "UTF-8"));
			}
			catch (UnsupportedEncodingException e)
			{
				e.printStackTrace();
			}
		}

		try
		{
			String urlStr = props.getProperty("urlNormalUpload") + "?" +
												"ID=" + props.getProperty("stationID") +
												"&PASSWORD=" + props.getProperty("password") +
												sbufURL.toString() +
												"&action=updateraw";
			updateURL = new URL(urlStr);
		}
		catch (MalformedURLException e)
		{
			e.printStackTrace();
		}
		
		return updateURL;
	}
	
	private void echoProperties()
	{
		String propVal;
		
		String[] propNames = new String[] {"urlNormalUpload", "uploadIntervalSecs", "stationID", "password", "dataFilePath"};

	    for (String propName : propNames)
	    {
		   propVal = props.getProperty(propName);
		   System.out.println(propName + ": " + propVal);
	    }
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		WundergroundUpdate wu = new WundergroundUpdate();
		wu.init();
		// wu.echoProperties();
//		for (;;)
//		{
			HashMap<String, String> fields = wu.parseLogLine(wu.getLastLogLine());
			URL updateURL = wu.formUpdateURL(fields);
			System.out.println("URL: " + updateURL.toString());
			try
			{
				URLConnection conn = updateURL.openConnection();
				conn.connect();
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				
				String inputLine;
				while ((inputLine = in.readLine()) != null) 
				System.out.println(inputLine);
				in.close();
				
//				Thread.sleep(300000);
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			catch (InterruptedException e)
//			{
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		
	}

}

	