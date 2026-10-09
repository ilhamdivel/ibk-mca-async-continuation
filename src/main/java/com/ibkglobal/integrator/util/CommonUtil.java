package com.ibkglobal.integrator.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CommonUtil {

	public static String exceptionTraceToString(Throwable paramE ) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		if ( paramE == null ) 
			return null;
		paramE.printStackTrace(ps);
		String st = baos.toString();
		try {
			baos.close();
		} catch ( Exception e ) {
			e.printStackTrace();
		}
		return st;
	}
		
	/**
	 * 숫자의 length 만큼 0 으로 채움
	 * @param value 값, length 0으로 채울 길이
	 * @return 길이만큼 0으로 채워진 값
	 */
	public static String getNumberD(int value, int length) {
		String returnstr = value+"";
		StringBuffer sb = new StringBuffer(returnstr);
		for (int i = returnstr.length(); i < length ; i++) {
			sb.insert(0, "0");
		}
		return sb.toString();
	}
	
	/**
	 * 문자의 length 만큼 지정된 값으로 채움
	 * @param value 값, length  길이, fillValue 채울값
	 * @return 길이만큼 fillValue로 채워진 값
	 */
	public static String getStringD(String value, int length, String fillValue) {
		StringBuffer sb = new StringBuffer(value);
		for (int i = value.length(); i < length ; i++) {
			sb.append(fillValue);
		}
		return sb.toString();
	}
	
	public static String getTrId() {
		String trId = "TMSG_";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		trId += sdf.format(new Date());
		
		trId += "_" + SystemUtil.getUniqueNum8();
		
		return trId;
	}
	
	public static String getSafTrId() {
		String trId = "TSAF_";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		trId += sdf.format(new Date());
		
		trId += "_" + SystemUtil.getUniqueNum8();

		return trId;
	}
	
	public static String nullCheck(String in) {
		if(in == null) in="";
		return in;
	}
}
