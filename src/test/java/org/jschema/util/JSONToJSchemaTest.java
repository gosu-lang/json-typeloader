package org.jschema.util;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

@Ignore
public class JSONToJSchemaTest {
  private static final String JSON_FILE = "test/org/jschema/examples/json/GithubCreate.json";
  private static final String JSC_OUTPUT = "result.jschema";
  private static final PrintStream SYS_OUT = System.out;

  @Test
  public void testValidation() throws IOException {
    try {
      JSONToJSchema.main(new String[] {});
    } catch (IllegalArgumentException e) {}

    try {
      JSONToJSchema.main(new String[] {"-blahhh", "somefile.json"});
    } catch (IllegalArgumentException e) {}

    try {
      JSONToJSchema.main(new String[] {"-json", JSON_FILE, "third without a fourth"});
    } catch (IllegalArgumentException e) {}

    try {
      JSONToJSchema.main(new String[] {"-json", JSON_FILE, "-brickhouse", JSC_OUTPUT});
    } catch (IllegalArgumentException e) {}

    JSONToJSchema.main(new String[] {"-json", JSON_FILE, "-jschema", JSC_OUTPUT});
  }

  @Test
  public void testCreateStdoutOutput() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream outRedirect = new PrintStream(output);
    System.setOut(outRedirect);
    JSONToJSchema.main(new String[] {"-json", JSON_FILE});
    System.setOut(SYS_OUT);
    System.out.println(output.toString());
//    JsonMap schema = (JsonMap)JSONParser.parseJSON(output.toString());
    Assert.assertTrue( !output.toString().isEmpty() );
  }

  @Test
  public void testCreateOutputJschemaFile() throws Exception {
    JSONToJSchema.main(new String[] {"-json", JSON_FILE, "-jschema", JSC_OUTPUT});
    Assert.assertTrue( new File( JSC_OUTPUT ).exists() );
  }

}
