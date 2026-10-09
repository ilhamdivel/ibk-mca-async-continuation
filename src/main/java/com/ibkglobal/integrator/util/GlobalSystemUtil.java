package com.ibkglobal.integrator.util;

import java.net.UnknownHostException;

public class GlobalSystemUtil {

	// HOSTNAME
	private static int HOSTNAME_LENGTH = 8;
	private static String HOSTNAME;
	static {
		try {
			HOSTNAME = java.net.InetAddress.getLocalHost ().getHostName ();
		}
		catch (UnknownHostException e) {
			e.printStackTrace();
			HOSTNAME = "unknown";
		}
	}
	
	public static String getHostname () {
		return HOSTNAME;
	}
	
	// 고유값
	private static int uNumber8 = 0;
	private static int uMaxNumber = 99999999;
	public static synchronized String getUniqueNum8 () {
		if (uNumber8 >= uMaxNumber) {
			uNumber8 = 0;
		}
		
		uNumber8++;
		
		return String.format("%08d", uNumber8);
	}
	
	public static int getCurrUniqueNum8 () {
		return uNumber8;
	}
	
	public static void putUniqueNum8 (int num) {
		uNumber8 = num;
	}

	// 고유값
	private static int uGIDSubNumber = 0;
	private static int uGIDSubMaxNumber = 999;
	public static synchronized String getUniqueGIDSubNum () {
		if (uGIDSubNumber >= uGIDSubMaxNumber) {
			uGIDSubNumber = 0;
		}
		
		uGIDSubNumber++;
		
		return String.format("%03d", uGIDSubNumber);
	}

	public static int getCurruGIDSubNumber () {
		return uNumber8;
	}
	
	public static void putuGIDSubNumber (int num) {
		uGIDSubNumber = num;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
	}
}
