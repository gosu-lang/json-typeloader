package org.jschema.typeloader;

import gw.lang.reflect.IType;
import gw.lang.reflect.TypeSystem;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Ignore
public class JSchemaTypeLoaderTest {

  @Test
  public void testBasicGosonTypes() {
    IType nameAndAge = TypeSystem.getByFullName("org.jschema.examples.NameAndAge");
    Assert.assertNotNull( nameAndAge );
  }

  @Test
  public void testNestedGosonTypes() {
    IType fullExample = TypeSystem.getByFullName("org.jschema.examples.fullexample.Example");
    Assert.assertNotNull( fullExample );

    IType someType = TypeSystem.getByFullName("org.jschema.examples.fullexample.Example.SomeType");
    Assert.assertNotNull( someType );

    IType nestedType = TypeSystem.getByFullName("org.jschema.examples.fullexample.Example.SomeType.NestedType");
    Assert.assertNotNull( nestedType );
  }

  @Test
  public void testRPCTypes() {
    Assert.assertNotNull( TypeSystem.getByFullName( "org.jschema.examples.rpc.Sample1" ) );

    Assert.assertNotNull( TypeSystem.getByFullName( "org.jschema.examples.rpc.Sample1.GetEmployee" ) );

    Assert.assertNull( TypeSystem.getByFullNameIfValid( "org.jschema.examples.rpc.Sample1.GetEmployee.Id" ) );

    Assert.assertNotNull( TypeSystem.getByFullNameIfValid( "org.jschema.examples.rpc.Sample1.UpdateEmployee" ) );
    Assert.assertNotNull( TypeSystem.getByFullName( "org.jschema.examples.rpc.Sample1.UpdateEmployee.Employee" ) );
  }

  @Test
  public void testJsonTypes() {
    Assert.assertNotNull( TypeSystem.getByFullName( "org.jschema.examples.flickr.GalleriesList" ) );

    Assert.assertNotNull( TypeSystem.getByFullName( "org.jschema.examples.flickr.GalleriesList.Galleries" ) );

    //assertNull(TypeSystem.getByFullNameIfValid("org.jschema.examples.RegularJson"));
  }

}
