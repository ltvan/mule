/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.db.integration;

import org.mule.extension.db.integration.model.AbstractTestDatabase;
import org.mule.runtime.api.message.MuleEvent;

import java.util.List;

import org.junit.Test;
import org.junit.runners.Parameterized;

public class SequeiraTestCase extends AbstractDbIntegrationTestCase {

  public SequeiraTestCase(String dataSourceConfigResource, AbstractTestDatabase testDatabase) {
    super(dataSourceConfigResource, testDatabase);
  }

  @Parameterized.Parameters
  public static List<Object[]> parameters() {
    return TestDbConfig.getResources();
  }

  @Override
  protected String[] getFlowConfigurationResources() {
    return new String[] {"integration/sequeira.xml"};
  }

  @Override protected void doTearDown() throws Exception {
    flowRunner("tearDown").run();
  }

  @Test
  public void sequeira() throws Exception {
    MuleEvent response = flowRunner("setup").run();
    response = flowRunner("test").run();
    response = flowRunner("deleteCustomers").run();

    response = flowRunner("test").run();
  }
}
