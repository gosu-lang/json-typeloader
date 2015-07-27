package org.jschema.parser;

import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.java.JavaTypes;
import org.jschema.util.JSchemaUtils;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class JSONParserTest {

  @Test
  public void testBasics() {
    // null
    Assert.assertNull( JSchemaUtils.parseJson( "null" ) );

    // strings
    Assert.assertEquals( "", JSchemaUtils.parseJson( "\"\"" ) );
    Assert.assertEquals( "hello", JSchemaUtils.parseJson( "\"hello\"" ) );
    Assert.assertEquals( "hello world", JSchemaUtils.parseJson( "\"hello world\"" ) );

    // numbers
    Assert.assertEquals( 1L, JSchemaUtils.parseJson( "1" ) );
    Assert.assertEquals( bd( "1.1" ), JSchemaUtils.parseJson( "1.1" ) );
    Assert.assertEquals( -1L, JSchemaUtils.parseJson( "-1" ) );
    Assert.assertEquals( bd( "-1.1" ), JSchemaUtils.parseJson( "-1.1" ) );
    Assert.assertEquals( bd( "1e1" ), JSchemaUtils.parseJson( "1e1" ) );
    Assert.assertEquals( bd( "1E1" ), JSchemaUtils.parseJson( "1E1" ) );
    Assert.assertEquals( bd( "1e+1" ), JSchemaUtils.parseJson( "1e+1" ) );
    Assert.assertEquals( bd( "1E+1" ), JSchemaUtils.parseJson( "1E+1" ) );
    Assert.assertEquals( bd( "1e-1" ), JSchemaUtils.parseJson( "1e-1" ) );
    Assert.assertEquals( bd( "1E-1" ), JSchemaUtils.parseJson( "1E-1" ) );

    // booleans
    Assert.assertEquals( Boolean.TRUE, JSchemaUtils.parseJson( "true" ) );
    Assert.assertEquals( Boolean.FALSE, JSchemaUtils.parseJson( "false" ) );

    // lists
    Assert.assertEquals( Collections.EMPTY_LIST, JSchemaUtils.parseJson( "[]" ) );
    Assert.assertEquals( Arrays.asList( "asdf", 1L, true ), JSchemaUtils.parseJson( "[\"asdf\", 1, true]" ) );

    // maps
    Assert.assertEquals( Collections.EMPTY_MAP, JSchemaUtils.parseJson( "{}" ) );
    // A map literal! A map literal!  My kingdom for a map literal!
    HashMap map = new HashMap();
    map.put("foo", 10L);
    map.put("bar", false);
    Assert.assertEquals( map, JSchemaUtils.parseJson( "{\"foo\" : 10, \"bar\" : false}" ) );
  }

  private Object bd(String s) {
    return new BigDecimal(s);
  }

  @Test
  public void testLongBigDecimal() {
    BigDecimal bigDecimal = new BigDecimal(Double.MAX_VALUE).add(new BigDecimal(".1"));
    Assert.assertEquals( bigDecimal, JSchemaUtils.parseJson( bigDecimal.toString() ) );

    BigDecimal negativeBigDecimal = new BigDecimal(Double.MIN_VALUE).add(new BigDecimal("-.1"));
    Assert.assertEquals( negativeBigDecimal, JSchemaUtils.parseJson( negativeBigDecimal.toString() ) );
  }

  @Test
  public void testComments() {
    Assert.assertNull( JSchemaUtils.parseJson( "null // test comment" ) );
    Assert.assertNull( JSchemaUtils.parseJson( "\nnull \n// test comment\n" ) );
    Assert.assertNull( JSchemaUtils.parseJson( "\nnull \n/* test \ncomment */\n" ) );
    Assert.assertNull( JSchemaUtils.parseJson( "/* test comment */ null " ) );
    Assert.assertNull( JSchemaUtils.parseJson( "\n/* \ntest comment */\n null \n" ) );
  }

  @Test
  public void testBasicNestedDataStructures() {
    Map obj = (Map) JSchemaUtils.parseJson("{" +
            "\"null\" : null, " +
            " \"number1\" : 1, " +
            " \"number2\" : 1.1, " +
            " \"boolean\" : true, " +
            " \"list1\" : [ 1, 2, 3 ], " +
            " \"list2\" : [ { \"str\" : \"string\" } ]," +
            " \"map\" : { " +
            "    \"map_boolean\" : true," +
            "    \"map_string\" : \"string\"" +
            "  } " +
            "}");
    Assert.assertEquals( null, obj.get( "null" ) );
    Assert.assertEquals( 1L, obj.get( "number1" ) );
    Assert.assertEquals( bd( "1.1" ), obj.get( "number2" ) );
    Assert.assertEquals( true, obj.get( "boolean" ) );
    Assert.assertEquals( Arrays.asList( 1L, 2L, 3L ), obj.get( "list1" ) );

    List list2 = (List) obj.get("list2");
    Assert.assertEquals( 1, list2.size() );

    Map o = (Map) list2.get(0);
    Assert.assertEquals( "string", o.get( "str" ) );

    Map map2 = (Map) obj.get("map");
    Assert.assertEquals( 2, map2.size() );
    Assert.assertEquals( true, map2.get( "map_boolean" ) );
    Assert.assertEquals( "string", map2.get( "map_string" ) );
  }

  @Test
  public void testStrings() {
    Assert.assertEquals( "blah\"blah", JSchemaUtils.parseJson( "\"blah\\\"blah\"" ) );
    Assert.assertEquals( "blah\\blah", JSchemaUtils.parseJson( "\"blah\\\\blah\"" ) );
    Assert.assertEquals( "blah/blah", JSchemaUtils.parseJson( "\"blah\\/blah\"" ) );
    Assert.assertEquals( "blah\bblah", JSchemaUtils.parseJson( "\"blah\\bblah\"" ) );
    Assert.assertEquals( "blah\fblah", JSchemaUtils.parseJson( "\"blah\\fblah\"" ) );
    Assert.assertEquals( "blah\nblah", JSchemaUtils.parseJson( "\"blah\\nblah\"" ) );
    Assert.assertEquals( "blah\rblah", JSchemaUtils.parseJson( "\"blah\\rblah\"" ) );
    Assert.assertEquals( "blah\tblah", JSchemaUtils.parseJson( "\"blah\\tblah\"" ) );
    Assert.assertEquals( "blah\u1234blah", JSchemaUtils.parseJson( "\"blah\\u1234blah\"" ) );
  }

  @Test
  public void testURIsParseCorrectly() throws URISyntaxException {
    URI uri = new URI("http://example.com");
    Assert.assertEquals( uri, JSchemaUtils.parseJson( JSchemaUtils.serializeJson( uri ), TypeSystem.get( URI.class ) ) );

    URI email = new URI("mailto:test@test.com");
    Assert.assertEquals( email, JSchemaUtils.parseJson( JSchemaUtils.serializeJson( email ), TypeSystem.get( URI.class ) ) );

    Object val = JSchemaUtils.parseJson("[\"http://example.com\"]", JavaTypes.LIST().getParameterizedType(TypeSystem.get(URI.class)));
    Assert.assertEquals( Arrays.asList( new URI( "http://example.com" ) ), val );

    Object val2 = JSchemaUtils.parseJson("{\"foo\" : \"http://example.com\"}", JavaTypes.MAP().getParameterizedType(JavaTypes.STRING(), TypeSystem.get(URI.class)));
    Map m = new HashMap();
    m.put("foo", new URI("http://example.com"));
    Assert.assertEquals( m, val2 );

    Assert.assertEquals( uri, JSchemaUtils.parseJson( JSchemaUtils.serializeJson( uri ), TypeSystem.get( URI.class ) ) );

    URI email2 = new URI("mailto:test@test.com");
    Assert.assertEquals( email2, JSchemaUtils.parseJson( JSchemaUtils.serializeJson( email2 ), TypeSystem.get( URI.class ) ) );
  }

  @Test
  public void testDateParsing() {
    Assert.assertEquals( makeDate( 1999, 1, 1, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 1, 1, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-01\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 1, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 1, 2, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-01-02\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00:00.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00:00.00Z\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00:00.00+00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 0, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T00:00:00.00-00:00\"", JavaTypes.DATE() ) );

    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:00:00.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:00:00.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:00:00.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:00:00.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 0, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:00\"", JavaTypes.DATE() ) );

    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:00.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:00.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:00.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:00.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 0, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59\"", JavaTypes.DATE() ) );

    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59.00-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59.00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 0, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59\"", JavaTypes.DATE() ) );

    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 10, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01.01-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 999, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59.999-00:00\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 10, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01.01\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 999, 0, 0 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59.999\"", JavaTypes.DATE() ) );

    Assert.assertEquals( makeDate( 1999, 12, 31, 1, 1, 1, 10, -10, 30 ), JSchemaUtils.parseJson( "\"1999-12-31T01:01:01.01-10:30\"", JavaTypes.DATE() ) );
    Assert.assertEquals( makeDate( 1999, 12, 31, 23, 59, 59, 999, 10, 30 ), JSchemaUtils.parseJson( "\"1999-12-31T23:59:59.999+10:30\"", JavaTypes.DATE() ) );

    try{
      JSchemaUtils.parseJson("\"1999-12-31T23:59.999+10:30\"", JavaTypes.DATE());
      Assert.fail( "Exception not thrown" );
    }
    catch(JsonParseException jpe){
      // gulp
    }
  }

  @Test
  public void testParseDocumentThrowsIfNotMapOrList()
  {
    try{
      JSchemaUtils.parseJsonDocument("\"hello world\"");
      Assert.fail( "Exception not thrown" );
    }
    catch(JsonParseException jpe){
      // Gulp
    }
    // And these should not throw.
    JSchemaUtils.parseJsonDocument("{ \"a\" : \"some value\" }");
    JSchemaUtils.parseJsonDocument("[ \"a\"]");
  }

  @Test
  public void testParserGathersError()
  {
    try{
      JSchemaUtils.parseJson("\"1999-12-31T23:59.999+10:30\"", JavaTypes.DATE());
      Assert.fail( "Exception not thrown" );
    }
    catch(JsonParseException jpe){
      Assert.assertEquals( 1, jpe.getErrorList().size() );
    }
  }

  @Test
  public void testParserDetectsExtraneousCharactersAtEndOfDocument()
  {
    String badJson = "{}{}";
    try{
      JSchemaUtils.parseJsonDocument(badJson);
      Assert.fail( "Exception not thrown" );
    }
    catch(JsonParseException jpe){
      // gulp
    }

    badJson = "[]{";
    try{
      JSchemaUtils.parseJsonDocument(badJson);
      Assert.fail( "Exception not thrown" );
    }
    catch(JsonParseException jpe){
      // gulp
    }

  }


  private Object makeDate(int year, int month, int day, int hour, int minute, int second, int milli, int offsetHours, int offsetMinutes) {
    GregorianCalendar gregorianCalendar = new GregorianCalendar();
    gregorianCalendar.set(Calendar.YEAR, year);
    gregorianCalendar.set(Calendar.MONTH, month - 1);
    gregorianCalendar.set(Calendar.DAY_OF_MONTH, day);
    gregorianCalendar.set(Calendar.HOUR_OF_DAY, hour);
    gregorianCalendar.set(Calendar.MINUTE, minute);
    gregorianCalendar.set(Calendar.SECOND, second);
    gregorianCalendar.set(Calendar.MILLISECOND, milli);
    int millis = ((offsetHours * 60) + ((offsetHours < 0 ? - 1 : 1 ) * offsetMinutes)) * 60 * 1000;
    gregorianCalendar.setTimeZone(new SimpleTimeZone(millis, "Custom"));
    Date time = gregorianCalendar.getTime();
    System.out.println(time.toGMTString());
    return time;
  }
  
}
