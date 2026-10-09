package com.ibkglobal.integrator.config;

public class EndpointCode {
	
	/** Protocol Base **/
	public final static String DIRECT = "direct:";
	public final static String HTTP   = "netty4-http:http://";
	public final static String TCP    = "netty4:tcp://";
	public final static String BEAN   = "bean:";
	public final static String QUEUE  = "jms:queue:";
	public final static String FILE   = "file:";
	public final static String FTP    = "ftp:";
	public final static String SFTP   = "sftp:";
	public final static String SEDA   = "seda:";
	
	/** HTTP Option **/
	public final static String HTTP_POST = "?httpMethodRestrict=POST";
	public final static String HTTP_GET  = "?httpMethodRestrict=GET";
	
	/** TCP Option **/
	
	
	/** QUEUE Option **/
	
	/** DYNAMIC Option **/
	public final static String DYNAMIC          = "${header.DYNAMIC_ENDPOINT}";
	public final static String DYNAMIC_ENDPOINT = "DYNAMIC_ENDPOINT";
	
	/** LOCAL Option **/
	public final static String LOCAL          = "${header.LOCAL_ENDPOINT}";
	public final static String LOCAL_ENDPOINT = "LOCAL_ENDPOINT";
	
	/** Codec Option **/
	public final static String LENGTH_OFFSET = "lengthOffset";
	public final static String LENGTH_LEN    = "lengthLen";
}
