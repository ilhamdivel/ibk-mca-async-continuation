package com.ibkglobal.integrator.util;

import java.util.HashMap;
import java.util.Map;

public class PackedNumericUtil {

	// 최대 9025 자리수를 2자리 문자로 맵핑
	private static String[] INCODING_TABLE = new String[9025];
	// 2자리 문자를 최대 9025 값의 숫자로 맵핑
	private static Map<String, Integer> DECODING_TABLE = new HashMap<String, Integer>();
	
	private static boolean initialized = false;
	
	private static final int MAX_VALUE = 9025;
	
	public static String pack(int value) {
		
		if(value > MAX_VALUE) {
			throw new IllegalArgumentException("Maximum value enable to encode is " + MAX_VALUE);
		}
		
		if(!initialized ) {
			initTable();
		}
		
		return INCODING_TABLE[value];
	}
	
	public static int unpack(String value) {
		if(!initialized ) {
			initTable();
		}
		
		 Integer integer = DECODING_TABLE.get(value);
		 
		 if(integer == null) {
			 throw new IllegalArgumentException("There are no numeric value for " + value);
		 }
		 
		 return integer;
	}

	private static synchronized void initTable() {
		
	
		char[] chars = new char[95];
		int index = 0;
		
		// numeric part
		for(int i=48; i<58; i++) {
			chars[index++] = (char) i;
		}
		
		// alphabet (small)
		for(int i=97; i<123; i++) {
			chars[index++] = (char) i;
		}
		
		// alphabet (capital)
		for(int i=65; i<91; i++) {
			chars[index++] = (char) i;
		}
		
		// symbol (part 1)
		for(int i=32; i<48; i++) {
			chars[index++] = (char) i;
		}
		
		// symbol (part 2)
		for(int i=58; i<65; i++) {
			chars[index++] = (char) i;
		}
		
		// symbol (part 3)
		for(int i=91; i<97; i++) {
			chars[index++] = (char) i;
		}
		
		// symbol (part 3)
		for(int i=123; i<127; i++) {
			chars[index++] = (char) i;
		}
		
		int index2 = 0;
		
		for(int i=0; i<10; i++) {
			for(int j=0; j<10; j++) {
				String value = "" + chars[i] + chars[j];
				DECODING_TABLE.put(value, index2);
				INCODING_TABLE[index2++] = "" + chars[i] + chars[j];
			}
		}
		
		for(int i=0; i<10; i++) {
			for(int j=10; j<95; j++) {
				String value = "" + chars[i] + chars[j];
				DECODING_TABLE.put(value, index2);
				INCODING_TABLE[index2++] = "" + chars[i] + chars[j];
			}
		}
		
		for(int i=10; i<95; i++) {
			for(int j=0; j<10; j++) {
				String value = "" + chars[i] + chars[j];
				DECODING_TABLE.put(value, index2);
				INCODING_TABLE[index2++] = "" + chars[i] + chars[j];
			}
		}
		
		for(int i=10; i<95; i++) {
			for(int j=10; j<95; j++) {
				String value = "" + chars[i] + chars[j];
				DECODING_TABLE.put(value, index2);
				INCODING_TABLE[index2++] = "" + chars[i] + chars[j];
			}
		}
		
		initialized = true;
	}
}