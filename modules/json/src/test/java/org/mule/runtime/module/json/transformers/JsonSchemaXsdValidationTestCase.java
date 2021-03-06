/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.json.transformers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleEvent.Builder;
import org.mule.runtime.core.util.IOUtils;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class JsonSchemaXsdValidationTestCase extends AbstractMuleContextTestCase {

  private static final String EXPECTED_JSON =
      "{" + "    \"cust:customer\" : {" + "        \"@xmlns:cust\" : \"http:customer.com\"," + "        \"cust:id\" : \"112\","
          + "        \"cust:first-name\" : \"Jane\"," + "        \"cust:last-name\" : \"Doe\"," + "        \"cust:address\" : {"
          + "           \"cust:street\" : \"123 A Street\"" + "        }," + "        \"cust:phone-number\" : [ {"
          + "            \"@type\" : \"work\"," + "            \"$\" : \"555-1111\"" + "        }, {"
          + "            \"@type\" : \"cell\"," + "            \"$\" : \"555-2222\"" + "        } ]" + "    }" + "}";

  private static final String BAD_JSON = "{\n" + "  \"cust:customer\" : {\n" + "    \"@xmlns:cust\" : \"http:customer.com\",\n"
      + "    \"cust:ID\" : \"112\",\n" + "    \"cust:first-name\" : \"Jane\",\n" + "    \"cust:last-name\" : \"Doe\",\n"
      + "    \"cust:address\" : {\n" + "      \"cust:street\" : \"123 A Street\"\n" + "    },\n"
      + "    \"cust:phone-number\" : [ {\n" + "      \"@type\" : \"work\",\n" + "      \"$\" : \"555-1111\"\n" + "    }, {\n"
      + "      \"@type\" : \"cell\",\n" + "      \"$\" : \"555-2222\"\n" + "    } ]\n" + "  }\n" + "}";

  private JsonSchemaValidationFilter filter;
  private CountingErrorHandler errorHandler;

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();

    filter = new JsonSchemaValidationFilter();
    filter.setSchemaLocations("customer.xsd");

    errorHandler = new CountingErrorHandler();
    filter.setErrorHandler(errorHandler);

    filter.setResourceResolver(new Resolver());
    filter.setReturnResult(true);
    filter.setMuleContext(muleContext);
    filter.setFlowConstruct(getTestFlow());
    filter.initialise();
  }

  @Test
  public void filterShouldAcceptStringInput() throws Exception {
    final MuleEvent event = getTestEvent(EXPECTED_JSON, muleContext);
    Builder builder = MuleEvent.builder(event);
    boolean accepted = filter.accept(event, builder);
    assertTrue(accepted);
    assertEquals(0, errorHandler.getErrorCount());
    JSONAssert.assertEquals(EXPECTED_JSON, getPayloadAsString(builder.build().getMessage()), false);
  }

  @Test
  public void filterShouldAcceptReaderInput() throws Exception {
    final MuleEvent event = getTestEvent(new StringReader(EXPECTED_JSON), muleContext);
    Builder builder = MuleEvent.builder(event);
    boolean accepted = filter.accept(event, builder);
    assertTrue(accepted);
    assertEquals(0, errorHandler.getErrorCount());
    JSONAssert.assertEquals(EXPECTED_JSON, getPayloadAsString(builder.build().getMessage()), false);
  }

  @Test
  public void filterShouldAcceptByteArrayInput() throws Exception {
    final MuleEvent event = getTestEvent(EXPECTED_JSON.getBytes(), muleContext);
    Builder builder = MuleEvent.builder(event);
    boolean accepted = filter.accept(event, builder);
    assertTrue(accepted);
    assertEquals(0, errorHandler.getErrorCount());
    JSONAssert.assertEquals(EXPECTED_JSON, getPayloadAsString(builder.build().getMessage()), false);
  }

  @Test
  public void filterShouldAcceptInputStreamInput() throws Exception {
    final MuleEvent event = getTestEvent(new ByteArrayInputStream(EXPECTED_JSON.getBytes()), muleContext);
    Builder builder = MuleEvent.builder(event);
    boolean accepted = filter.accept(event, builder);
    assertTrue(accepted);
    assertEquals(0, errorHandler.getErrorCount());
    JSONAssert.assertEquals(EXPECTED_JSON, getPayloadAsString(builder.build().getMessage()), false);
  }

  @Test
  public void filterShouldNotAcceptInvalidJson() throws Exception {
    final MuleEvent event = getTestEvent(BAD_JSON, muleContext);
    boolean accepted = filter.accept(event, MuleEvent.builder(event));
    assertFalse(accepted);
  }

  private static class CountingErrorHandler implements ErrorHandler {

    private int errorCount = 0;

    @Override
    public void warning(SAXParseException exception) throws SAXException {
      // ignored
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
      errorCount++;
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
      errorCount++;
    }

    public int getErrorCount() {
      return errorCount;
    }
  }

  private static class Resolver implements LSResourceResolver {

    private String schema =
        IOUtils.toString(JsonSchemaXsdValidationTestCase.class.getClassLoader().getResourceAsStream("customer.xsd"));

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      return new LSInput() {

        @Override
        public Reader getCharacterStream() {
          return new StringReader(schema);
        }

        @Override
        public void setCharacterStream(Reader characterStream) {
          // ignored
        }

        @Override
        public InputStream getByteStream() {
          return new ByteArrayInputStream(schema.getBytes());
        }

        @Override
        public void setByteStream(InputStream byteStream) {
          // ignored
        }

        @Override
        public String getStringData() {
          return schema;
        }

        @Override
        public void setStringData(String stringData) {
          // ignored
        }

        @Override
        public String getSystemId() {
          return "customer.schema";
        }

        @Override
        public void setSystemId(String id) {
          // ignored
        }

        @Override
        public String getPublicId() {
          return "customer.schema";
        }

        @Override
        public void setPublicId(String id) {
          // ignored
        }

        @Override
        public String getBaseURI() {
          return "customer.schema";
        }

        @Override
        public void setBaseURI(String uri) {
          // ignored
        }

        @Override
        public String getEncoding() {
          return "UTF-8";
        }

        @Override
        public void setEncoding(String enc) {
          // ignored
        }

        @Override
        public boolean getCertifiedText() {
          return false;
        }

        @Override
        public void setCertifiedText(boolean text) {
          // ignored
        }
      };
    }
  }
}
