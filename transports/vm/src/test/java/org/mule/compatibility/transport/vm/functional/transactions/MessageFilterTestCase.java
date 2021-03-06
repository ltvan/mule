/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.compatibility.transport.vm.functional.transactions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mule.runtime.core.client.DefaultLocalMuleClient.MESSAGE_FILTERED_ERROR_MESSAGE;

import org.mule.functional.junit4.FunctionalTestCase;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.client.MuleClient;
import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.runtime.core.client.DefaultLocalMuleClient;

import org.hamcrest.core.Is;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test transaction behavior when "joinExternal" is set to disallow joining external transactions There is one test per legal
 * transactional behavior (e.g. ALWAYS_BEGIN).
 */
public class MessageFilterTestCase extends FunctionalTestCase {

  protected static final Logger logger = LoggerFactory.getLogger(MessageFilterTestCase.class);

  private static String rejectMesage;

  @Override
  protected String getConfigFile() {
    return "org/mule/test/config/message-filter-config-flow.xml";
  }

  /** Check that the configuration specifies considers external transactions */
  @Test
  public void testConfiguration() throws Exception {
    MuleClient client = muleContext.getClient();

    assertThat(client.send("vm://order.validation", "OK", null).getLeft().getDescription(),
               Is.is(MESSAGE_FILTERED_ERROR_MESSAGE));
    assertEquals("OK(rejected!-1)", rejectMesage);

    assertThat(client.send("vm://order.validation", "OK-ABC", null).getLeft().getDescription(),
               Is.is(MESSAGE_FILTERED_ERROR_MESSAGE));
    assertEquals("OK-ABC(rejected!-2)", rejectMesage);

    assertThat(client.send("vm://order.validation", "OK-DEF", null).getLeft().getDescription(),
               Is.is(MESSAGE_FILTERED_ERROR_MESSAGE));
    assertEquals("OK-DEF(rejected!-1)", rejectMesage);
    rejectMesage = null;

    MuleMessage response = client.send("vm://order.validation", "OK-ABC-DEF", null).getRight();
    assertEquals("OK-ABC-DEF(success)", getPayloadAsString(response));
    assertNull(rejectMesage);
  }

  public static class Reject1 implements MessageProcessor {

    public void setName(String name) {
      // ignore name
    }

    @Override
    public MuleEvent process(MuleEvent event) throws MuleException {
      try {
        String payload = event.getMessageAsString(muleContext);
        rejectMesage = payload + "(rejected!-1)";
        return null;
      } catch (Exception e) {
        throw new DefaultMuleException(e);
      }
    }
  }

  public static class Reject2 implements MessageProcessor {

    public void setName(String name) {
      // ignore name
    }

    @Override
    public MuleEvent process(MuleEvent event) throws MuleException {
      try {
        String payload = event.getMessageAsString(muleContext);
        rejectMesage = payload + "(rejected!-2)";
        return null;
      } catch (Exception e) {
        throw new DefaultMuleException(e);
      }
    }
  }
}
