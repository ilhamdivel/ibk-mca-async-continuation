package com.ibkglobal.integrator.util;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.StringTokenizer;

import com.ibkglobal.config.IbkGlobalConfig;

public class SystemUtil {

  /**
   * 전달된 String ('YYYYMMDD')을 구분자를 첨부해서 변환하여 반환 사용 예) 20100810 => 2010.08.10 ,
   * 2010/08/10 , 2010-08-10
   *
   * @param strDate
   *          변환할 날짜 String
   * @param gubun
   *          구분자
   * @return 변환된 날짜 String
   * @see
   */
  public static String dateFormate(String strDate, String gubun) {

    String strFormate = "";
    if (strDate.equals("") || strDate == null)
      return "";

    if (strDate.length() >= 4) {
      if (strDate.length() < 6)
        strFormate = strDate.substring(0, 4);
      else if (strDate.length() < 8)
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(4, 6);
      else if (strDate.length() >= 8)
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(4, 6) + gubun + strDate.substring(6, 8);
      else
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(4, 6) + gubun + strDate.substring(6, 8);
    }

    return strFormate;
  }

  /**
   * 구분자를 포함한 String('YYYY-MM-DD')을 다른 구분자로 변환하여 반환 사용 예) 2010-08-10 => 2010.08.10
   * , 2010/08/10 , 20100810
   *
   * @param strDate
   *          변환할 날짜 String
   * @param gubun
   *          날짜String 구분자
   * @return 변환된 날짜 String
   * @see
   */
  public static String dateOtherFormate(String strDate, String gubun) {

    String strFormate = "";
    if (strDate.equals("") || strDate == null)
      return "";

    if (strDate.length() >= 4) {
      if (strDate.length() < 6)
        strFormate = strDate.substring(0, 4);
      else if (strDate.length() < 10)
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(5, 7);
      else if (strDate.length() >= 10)
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(5, 7) + gubun + strDate.substring(8, 10);
      else
        strFormate = strDate.substring(0, 4) + gubun + strDate.substring(5, 7) + gubun + strDate.substring(8, 10);
    }

    return strFormate;
  }

  /**
   * 주어진 날짜/시간을 입력된 날짜/시간 패턴에 따른 문자열로 변환한다. 디폴트 로케일은 Locale("en", "US") 로 설정된다.
   * 
   * @param dt
   *          변환할 날짜/시간 객체
   * @param datepattern
   *          얻고자 하는 날짜/시간 문자열을 나타내는 패턴
   * @return 변환된 날짜/시간 문자열
   * @see java.text.SimpleDateFormat, java.util.Locale
   */
  public static String convertDateToString(Date dt, String datepattern) {
    SimpleDateFormat sdf = new SimpleDateFormat(datepattern, new Locale("en", "US"));
    return sdf.format(dt);
  }

  /**
   * 현 시스템에 대한 시간 리턴
   * 
   * @param pattern
   * @return
   */
  public static String getCurrentDate(String pattern) {
    SimpleDateFormat sdf = new SimpleDateFormat(pattern, IbkGlobalConfig.locale);
    return sdf.format(new Date());
  }

  /**
   * 현 시스템에 대한 시간 Long 리턴
   * 
   * @param pattern
   * @return
   */
  public static String getCurrentDateLong(String pattern) {
    long currentTime = new Date().getTime() / 1000;
    String str = String.valueOf(currentTime);
    str = str.substring(0, 9);
    return str;
  }

  /**
   * 현 시간 long 타입
   * 
   * @return
   */
  public static long getCurrentTime() {
    long currentTime = new Date().getTime() / 1000;

    return currentTime;
  }

  /**
   * 주어진 날짜/시간을 문자열을 입력된 날짜/시간 패턴에 따른 날짜/시간 객체로 변환한다.
   *
   * @param datestr
   *          날짜/시간 문자열
   * @param datepattern
   *          변환에 사용될 날짜/시간 패턴
   * @return 날짜/시간 객체 (Date 객체)
   * @see java.text.SimpleDateFormat, java.util.Locale
   * @exception ParseException
   */
  public static Date convertStringToDate(String datestr, String datepattern) throws ParseException {
    SimpleDateFormat sdf = new SimpleDateFormat(datepattern, new Locale("en", "US"));
    // sdf.setLenient(true);
    return sdf.parse(datestr);
  }

  /**
   * Date Object를 구하기 위해 String을 그 String의 time format을 이용하여 parse
   * 
   * @param dateString
   *          특정 time format으로된 String
   * @param format
   *          time format
   * @return 주어진 String으로 된 날짜의 Date Object
   */
  public static Date stringToDate(String dateString, String format) {

    Date d = null;
    try {
      SimpleDateFormat sdf = new SimpleDateFormat(format);
      ParsePosition pos = new ParsePosition(0);
      d = sdf.parse(dateString, pos);
    } catch (Exception e) {
      d = new Date();
    }

    return d;
  }

  /**
   * Calendar Object를 구하기 위해 String을 그 String의 time format을 이용하여 parse
   * 
   * @param dateString
   *          특정 time format으로된 String
   * @param format
   *          time format
   * @return 주어진 String으로 된 날짜의 Calendar Object
   */
  public static Calendar stringToCalendar(String dateString, String format) {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    ParsePosition pos = new ParsePosition(0);
    Date date = sdf.parse(dateString, pos);
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    return cal;
  }

  /**
   * 특정 Date Object를 time format을 이용하여 String 변환
   * 
   * @param date
   *          특정 일자의 Date Object
   * @param format
   *          time format
   * @return 특정일의 String
   */
  public static String formatDate(Date date, String format) {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    Calendar cal = Calendar.getInstance();
    cal.setTime(date);
    return sdf.format(cal.getTime());
  }

  /**
   * 특정 Calendar Object를 time format을 이용하여 String 변환
   * 
   * @param cal
   *          특정 일자의 Calendar Object
   * @param format
   *          time format
   * @return 특정일의 String
   */
  public static String formatCalendar(Calendar cal, String format) {
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    return sdf.format(cal.getTime());
  }

  /**
   * 특정 날짜에 연도를 더하거나 뺀 결과를 반환한다.
   * 
   * @param startDate
   *          - 기준 날짜
   * @param years
   *          - 더하거나 뺄 연도. 기준 날짜보다 과거로 가고자 한다면 음수 값을 넣는다.
   */
  public static java.sql.Date rollYears(java.util.Date startDate, int years) {
    GregorianCalendar gc = new GregorianCalendar();
    gc.setTime(startDate);
    gc.add(Calendar.YEAR, years);
    return new java.sql.Date(gc.getTime().getTime());
  }

  /**
   * 특정 날짜에 달을 더하거나 뺀 결과를 반환한다.
   * 
   * @param startDate
   *          - 기준 날짜
   * @param months
   *          - 더하거나 뺄 개월수. 기준 날짜보다 과거로 가고자 한다면 음수 값을 넣는다.
   */
  public static java.sql.Date rollMonths(java.util.Date startDate, int months) {
    GregorianCalendar gc = new GregorianCalendar();
    gc.setTime(startDate);
    gc.add(Calendar.MONTH, months);
    return new java.sql.Date(gc.getTime().getTime());
  }

  /**
   * 특정 날짜에 일을 더하거나 뺀 결과를 반환한다.
   * 
   * @param startDate
   *          - 기준 날짜
   * @param days
   *          - 더하거나 뺄 일수. 기준 날짜보다 과거로 가고자 한다면 음수 값을 넣는다.
   */
  public static java.sql.Date rollDays(java.util.Date startDate, int days) {
    GregorianCalendar gc = new GregorianCalendar();
    gc.setTime(startDate);
    gc.add(Calendar.DATE, days);
    return new java.sql.Date(gc.getTime().getTime());
  }

  /**
   * 오늘 날짜에 특정 일을 더하거나 뺀 결과를 yyyyMMdd 형식으로 반환한다.
   * 
   * @param days
   *          더하거나 뺄 일 수. 오늘보다 과거로 가려면 음수 값을 넣는다.
   * @return String
   */
  public static String getDate(int days) {
    return getDate(days, "yyyyMMdd");
  }

  /**
   * 오늘 날짜에 특정 일을 더하거나 뺀 결과를 지정한 형식으로 반환한다.
   * 
   * @param days
   *          더하거나 뺄 일 수. 오늘보다 과거로 가려면 음수 값을 넣는다.
   * @param format
   *          날짜 문자열 형식
   * @return String
   */
  public static String getDate(int days, String format) {
    GregorianCalendar gc = new GregorianCalendar();
    SimpleDateFormat sdf = new SimpleDateFormat(format);
    gc.add(Calendar.DATE, days);

    return sdf.format(gc.getTime());
  }

  /**
   * 넘겨준 형식으로 날짜를 int형으로 반환한다.
   * 
   * @param pattern
   *          형식 "yyyy, MM, dd, HH, mm, ss"
   * @return
   */
  public static int getNumberByPattern(String pattern) {
    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(pattern, IbkGlobalConfig.locale);
    String dateString = formatter.format(new java.util.Date());
    return Integer.parseInt(dateString);
  }

  /**
   * 스트링을 구분자('^')로 분리한다
   * 
   * @param str
   *          구분할 String
   * @param delim
   *          구분자
   * @return 스트링 Array
   */
  public static String[] slice(String str, String delim) {
    String[] rtnArrs = null;
    if (str.indexOf(delim) >= 0) {
      ArrayList attinfolst = new ArrayList();
      StringTokenizer st = new StringTokenizer(str, delim);

      while (st.hasMoreTokens()) {
        attinfolst.add(st.nextToken());
      }
      attinfolst.trimToSize();

      rtnArrs = new String[attinfolst.size()];
      attinfolst.toArray(rtnArrs);
    } else {
      rtnArrs = new String[1];
      rtnArrs[0] = str;
    }
    return (String[]) rtnArrs;
  }

  /**
   * String s에 포함되어 있는 old을 replacement로 바꾸어 return
   * 
   * @param s
   *          원본 스트링
   * @param old
   *          바꾸고자 하는 스트링
   * @param replacement
   *          바꿀 스트링
   * @return 바뀐 문자열
   */
  static public String replace(String s, String old, String replacement) {
    int i = s.indexOf(old);
    StringBuffer r = new StringBuffer();

    if (i == -1)
      return s;
    r.append(s.substring(0, i) + replacement);

    if (i + old.length() < s.length())
      r.append(replace(s.substring(i + old.length(), s.length()), old, replacement));

    return r.toString();
  }

  /**
   * Null String을 "" String으로 바꿔준다.
   *
   * @param str
   *          Null 문자열
   *
   * @return "" 문자열(null이 아닐 경우는 변환할 문자열이 그대로 리턴)
   */
  public static String NVL(String value) {
    if (value == null)
      return "";
    else
      return value;
  }

  /**
   * 문자열이 null인경우 replace_str을 Return한다. 사용 예) 테이블의
   * <td>str</td>에서 str이 null인 경우 replate_str이 &nbsp;로 지정한다.
   *
   * @param str
   *          Null 문자열
   * @param replace_str
   *          변환할 문자열
   * @return 변환할 문자열
   */
  static public String NVL(String str, String replace_str) {
    if (str == null || str.length() <= 0)
      return replace_str;
    else
      return str;
  }

  /**
   * 숫자 문자인지 체크
   * 
   * @param ch
   *          체크할 문자
   * @return 숫자 문자이면 true, 그렇지 않으면 false
   */
  public static boolean isNumeric(char ch) {
    if (ch >= '0' && ch <= '9')
      return true;
    else
      return false;
  }

  /**
   * String을 읽어 알파벳과 숫자만 모아 return ('_', '-', ' ' 포함)
   * 
   * @param s
   *          source String
   * @return 알파벳을 제외하고 걸려진 String
   */
  public static String alphaNumOnly(String s) {
    int i = s.length();
    StringBuffer stringbuffer = new StringBuffer(i);
    for (int j = 0; j < i; j++) {
      char c = s.charAt(j);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-'
          || c == ' ')
        stringbuffer.append(c);
    }

    return stringbuffer.toString();

  }

  /**
   * String을 읽어 알파벳과 숫자만 있는지 check ('_', '-', ' ' 포함)
   * 
   * @param s
   *          source String
   */
  public static boolean isAlphaNumOnly(String s) {
    int i = s.length();
    for (int j = 0; j < i; j++) {
      char c = s.charAt(j);
      if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_' && c != '-' && c != ' ')
        return false;
    }

    return true;
  }

  /**
   * String을 읽어 숫자만 있는지 check
   * 
   * @param s
   *          source String
   */
  public static boolean isNumOnly(String s) {
    int i = s.length();

    for (int j = 0; j < i; j++) {
      char c = s.charAt(j);
      if ((c < '0' || c > '9') && c != '-')
        return false;

    }

    return true;
  }

  /**
   * Alphabet 문자인지 체크
   * 
   * @param ch
   *          체크할 문자
   * @return Alphabet 문자이면 true, 그렇지 않으면 false
   */
  public static boolean isAlpha(char ch) {
    if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z')
      return true;
    else
      return false;
  }

  /**
   * String s에서 String s1에 포함되는 모든 char를 제거한 String으로 return
   * 
   * @param s
   *          source String
   * @param s1
   *          삭제시킬 sub String
   */
  public static String removeCharacters(String s, String s1) {
    int i = s.length();
    StringBuffer stringbuffer = new StringBuffer(i);
    for (int j = 0; j < i; j++) {
      char c = s.charAt(j);
      if (s1.indexOf(c) == -1)
        stringbuffer.append(c);
    }

    return stringbuffer.toString();
  }

  /**
   * String s에서 존재하는 space들을 모두 제거한 String으로 return
   * 
   * @param s
   *          source String
   */
  public static String removeWhiteSpace(String s) {
    int i = s.length();
    StringBuffer stringbuffer = new StringBuffer(i);
    for (int j = 0; j < i; j++) {
      char c = s.charAt(j);
      if (!Character.isWhitespace(c))
        stringbuffer.append(c);
    }

    return stringbuffer.toString();
  }

  /**
   * right
   *
   * 주어진 값을 오른쪽에서 부터 len만큼 짤라서 반환
   *
   * @param s
   *          소스 스트링
   * @param len
   *          자를 길이
   *
   * @return 주어진 스트링을 len만큼 오른쪽에서 자른 값
   */
  public static String right(String s, int len) {
    if (s == null)
      return "";
    int L = s.length();
    if (L <= len)
      return s;
    return s.substring(L - len, L);
  }

  /**
   * right
   *
   * 주어진 값을 왼쪽에서 부터 len만큼 짤라서 반환
   *
   * @param s
   *          소스 스트링
   * @param len
   *          자를 길이
   *
   * @return 주어진 스트링을 len만큼 왼쪽에서 자른 값
   */
  public static String left(String s, int len) {
    if (s == null)
      return "";
    if (s.length() <= len)
      return s;
    return s.substring(0, len);
  }

  /**
   * 주어진 값을 왼쪽에서 부터 len만큼 짤라서 반환
   *
   * @param s
   *          소스 스트링
   * @param len
   *          자를 길이
   *
   * @return 주어진 스트링을 len만큼 왼쪽에서 자른 값
   */
  static public String cutLeft(String s, int len, String replace_str) {
    if (s == null) {
      return "";
    } else if (s.length() <= len) {
      return s;
    } else {
      return s.substring(0, len) + replace_str;
    }
  }

  /**
   * 스트링을 금액형식의 스트링으로 변환한다 "1000000" ==> "1,000,000"
   * 
   * @param str
   *          소스 스트링
   *
   * @return 금액형식의 스트링
   */
  public static String toMoneyFormat(String str) {
    if (str == null || str.length() == 0)
      return null;
    double money = Double.parseDouble(str);
    NumberFormat priceFormat = NumberFormat.getNumberInstance();
    return priceFormat.format(money);
  }

  /**
   * 문자열을 int형으로 변환함.
   * 
   * <pre>
   * 사용 예)
   * parseInt("123") => 123
   * parseInt("  -123") => -123
   * parseInt("abc") => 예외 발생
   * parseInt(null) => 예외 발생
   * </pre>
   * 
   * @param inputValue
   * @return int값
   */
  public static int parseInt(String inputValue) {
    return Integer.parseInt(inputValue.trim());
  }

  /**
   * 문자열을 int형으로 변환함. 변환에러시 디폴트값 반환.
   * 
   * <pre>
   * 사용 예)
   * parseInt("abc", 0) => 0
   * </pre>
   * 
   * @param inputValue
   * @param defaultValue
   * @return int값
   */
  public static int parseInt(String inputValue, int defaultValue) {
    try {
      return Integer.parseInt(inputValue.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 문자열을 long형으로 변환함.
   * 
   * <pre>
   * 사용 예)
   * parseLong("123") => 123L
   * parseLong("  -123") => -123L
   * parseLong("abc") => 예외 발생
   * parseLong(null) => 예외 발생
   * </pre>
   * 
   * @param inputValue
   * @return long값
   */
  public static long parseLong(String inputValue) {
    return Long.parseLong(inputValue.trim());
  }

  /**
   * 문자열을 long형으로 변환함. 변환에러시 디폴트값 반환.
   * 
   * <pre>
   * 사용 예)
   * parseLong("abc", 0L) => 0L
   * </pre>
   * 
   * @param inputValue
   * @param defaultValue
   * @return long값
   */
  public static long parseLong(String inputValue, long defaultValue) {
    try {
      return Long.parseLong(inputValue.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 문자열을 float형으로 변환함.
   * 
   * <pre>
   * 사용 예)
   * parseFloat("123.0") => 123.0
   * parseFloat("  -123.0") => -123.0
   * parseFloat("abc") => 예외 발생
   * parseFloat(null) => 예외 발생
   * </pre>
   * 
   * @param inputValue
   * @return float값
   */
  public static float parseFloat(String inputValue) {
    return Float.parseFloat(inputValue.trim());
  }

  /**
   * 문자열을 float형으로 변환함. 변환에러시 디폴트값 반환.
   * 
   * <pre>
   * 사용 예)
   * parseFloat("abc", (float)0) => 0
   * </pre>
   * 
   * @param inputValue
   * @param defaultValue
   * @return float값
   */
  public static float parseFloat(String inputValue, float defaultValue) {
    try {
      return Float.parseFloat(inputValue.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 문자열을 double형으로 변환함.
   * 
   * <pre>
   * 사용 예)
   * parseDouble("123.0") => 123.0
   * parseDouble("  -123.0") => -123.0
   * parseDouble("abc") => 예외 발생
   * parseDouble(null) => 예외 발생
   * </pre>
   * 
   * @param inputValue
   * @return double값
   */
  public static double parseDouble(String inputValue) {
    return Double.parseDouble(inputValue.trim());
  }

  /**
   * 문자열을 double형으로 변환함. 변환에러시 디폴트값 반환.
   * 
   * <pre>
   * 사용 예)
   * parseDouble("abc", 0) => 0
   * </pre>
   * 
   * @param inputValue
   * @param defaultValue
   * @return double값
   */
  public static double parseDouble(String inputValue, double defaultValue) {
    try {
      return Double.parseDouble(inputValue.trim());
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 넘어온 값을 #,###,###,### 형식으로 바꿈 <br>
   * 
   * @param i
   *          int형의 숫자
   * @return
   */
  public static String mformat(int i) {
    DecimalFormat df = new DecimalFormat("#,###,###,##0");
    String retstr = df.format(i);
    return retstr;
  }

  /**
   * 넘어온 값을 #,###,###,### 형식으로 바꿈 <br>
   * 
   * @param l
   *          long형의 숫자
   * @return
   */
  public static String mformat(long l) {
    DecimalFormat df = new DecimalFormat("#,###,###,##0");
    String retstr = df.format(l);
    return retstr;
  }

  /**
   * 넘어온 값을 #,###,###,### 형식으로 바꿈 <br>
   * 
   * @param d
   *          double형의 숫자
   * @return
   */
  public static String mformat(double d) {
    DecimalFormat df = new DecimalFormat("#,###,###,##0");
    String retstr = df.format(d);
    return retstr;
  }

  /**
   * 넘어온 값을 #,###,###,###.000 형식으로 바꿈 <br>
   * 
   * @param f
   *          float형의 숫자
   * @param i
   *          소수점이하자리
   * @return #,###,###,###.000
   */
  public static String mformat(float f, int i) {
    String form = "#,###,###,##0.";
    if (i == 0) {
      form = "#,###,###,##0";
    }
    for (int j = 0; j < i; j++)
      form = form + "0";
    DecimalFormat df = new DecimalFormat(form);
    String retstr = df.format(f);
    return retstr;
  }

  /**
   * 넘어온 값을 #,###,###,###.000 형식으로 바꿈 <br>
   * 
   * @param l
   *          long형의 숫자
   * @param i
   *          소수점이하자리
   * @return #,###,###,###.000
   */
  public static String mformat(long l, int i) {
    String form = "#,###,###,##0.";
    if (i == 0) {
      form = "#,###,###,##0";
    }
    for (int j = 0; j < i; j++)
      form = form + "0";
    DecimalFormat df = new DecimalFormat(form);
    String retstr = df.format(l);
    return retstr;
  }

  /**
   * 넘어온 값을 #,###,###,###.000 형식으로 바꿈 <br>
   * 
   * @param d
   *          double형의 숫자
   * @param i
   *          소수점이하자리
   * @return #,###,###,###.000
   */
  public static String mformat(double d, int i) {
    String form = "#,###,###,##0.";
    if (i == 0) {
      form = "#,###,###,##0";
    }
    for (int j = 0; j < i; j++)
      form = form + "0";
    DecimalFormat df = new DecimalFormat(form);
    String retstr = df.format(d);
    return retstr;
  }

  /**
   * 전달된 주민번호 String에 (-)를 첨부해서 반환 ex: 1234561234567 => 123456-1234567
   *
   * @param strDate
   *          변환할 String
   * @return 변환된 String
   * @see
   */
  public static String getRsNoFormate(String strRsNo) {

    String strFormate = "";
    String gubun = "-";
    if (strRsNo.equals("") || strRsNo == null)
      return "";

    strRsNo = strRsNo.replaceAll("-", "");
    if (strRsNo.length() <= 6) {
      strFormate = strRsNo;
    } else if (strRsNo.length() > 6 && strRsNo.length() <= 13) {
      strFormate = strRsNo.substring(0, 6) + gubun + strRsNo.substring(6, strRsNo.length());
    }
    return strFormate;
  }

  private static String SERVER_NAME;
  static {
    try {
      // Class clazz = Class.forName("com.ibm.websphere.runtime.ServerName");
      // SERVER_NAME = com.ibm.websphere.runtime.ServerName.getDisplayName();

      Class[] types = new Class[] {};
      Method method = Class.forName("com.ibm.websphere.runtime.ServerName").getMethod("getDisplayName", types);
      Object[] objs = new Object[] {};

      SERVER_NAME = (String) method.invoke(null, objs);
    } catch (Exception cnfe) {
      // cnfe.printStackTrace();
      SERVER_NAME = System.getProperty("server.name", "FEPServer");
    }
  }

  public static String getServerName() {
    return SERVER_NAME;
  }

  public static String getHostName() {
    String hostname = GlobalSystemUtil.getHostname();
    if (hostname.equalsIgnoreCase("unknown")) {
      hostname = System.getProperty("host.name", "unknownn");
    }
    return hostname;
  }

  // WAS Instance ID
  // WAS server name 의 여섯번째글자 + 마지막 글자(숫자) - PFCNAP01 -> P1
  // 반대쪽 서버 아이디와 겹친다. 표준전문 상 유니크하려면 호스이이름과 was instance id 가
  // 합해져야 한다.
  private static String WAS_INSTANCE_ID;
  static {
    try {
      WAS_INSTANCE_ID = SERVER_NAME.substring(5, 6) + SERVER_NAME.substring(7, 8);
      // SERVER_NAME.substring(SERVER_NAME.length() - 1, SERVER_NAME.length());
    } catch (Exception e) {
      e.printStackTrace();
      WAS_INSTANCE_ID = "XX";
    }
  }

  public static String getWasInstanceID() {
    return WAS_INSTANCE_ID;
  }

  public static void setWasInstanceID(String id) {
    WAS_INSTANCE_ID = id;
  }

  private static String NODE_ID;
  static {
    try {
      NODE_ID = SERVER_NAME.substring(1, 2) + SERVER_NAME.substring(5, 6)
          + SERVER_NAME.substring(SERVER_NAME.length() - 2, SERVER_NAME.length());
    } catch (Exception e) {
      e.printStackTrace();
      NODE_ID = "XXXX";
    }
  }

  public static String getNodeID() {
    return NODE_ID;
  }

  public static void setNodeID(String id) {
    NODE_ID = id;
  }

  public static String getUniqueGIDSubNum() {
    return GlobalSystemUtil.getUniqueGIDSubNum();
  }

  public static String getUniqueNum8() {
    return GlobalSystemUtil.getUniqueNum8();
  }

  private static String ipAddr;
  private static String macAddr;
  static {
    loadIpAddr();
  }

  public static void loadIpAddr() {
    try {
      InetAddress ia = InetAddress.getLocalHost();

      // 표준전문IP
      // 추후 dev, test 를 위해 어댑터에서 셋팅 또는
      // 업무코드 별 IP 를 프로퍼티화 하는 것에 대해 논의할 것
      ipAddr = ia.getHostAddress();

      // 표준전문MAC주소값
      NetworkInterface ni = NetworkInterface.getByInetAddress(ia);
      byte[] macB = ni.getHardwareAddress();
      StringBuffer sb = new StringBuffer(12);
      for (int i = 0; i < macB.length; i++) {
        sb.append(String.format("%02X", macB[i]));
      }
      macAddr = sb.toString();
    } catch (Exception e) {
      e.printStackTrace();
      ipAddr = "";
      macAddr = "";
    }
  }

  public static String getDate() {
    Calendar calendar = Calendar.getInstance();
    Date date = calendar.getTime();
    String toDay = (new SimpleDateFormat("yyyyMMdd").format(date));
    return toDay;
  }

  public static String getIpAddr() {
    return ipAddr;
  }

  public static void setIpAddr(String ipAddr) {
    SystemUtil.ipAddr = ipAddr;
  }

  public static String getMacAddr() {
    return macAddr;
  }

  public static void setMacAddr(String macAddr) {
    SystemUtil.macAddr = macAddr;
  }

  public static void main(String arg[]) {
    System.out.println(SystemUtil.getServerName());
  }
}
