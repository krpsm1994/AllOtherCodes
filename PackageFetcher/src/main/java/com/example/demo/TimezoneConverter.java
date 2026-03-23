package com.example.demo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimezoneConverter {

	public static void main(String[] args) {
		Date currentDate = new Date();
		System.out.println(currentDate.getTimezoneOffset());
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

		// Set the time zone to GMT (UTC)
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));

		// Format the current Date object to GMT
		String formattedDate = formatter.format(currentDate);

		// Print the formatted date
		System.out.println(formattedDate);
	}

}
