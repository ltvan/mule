/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.routing.filters;

import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.api.MuleMessage;
import org.mule.runtime.core.api.routing.filter.Filter;

/**
 * Filters messages that have a consumable payload.
 * <p>
 * The filter accepts only {@link MuleMessage} instances that have a no consumable payload. Check is done using
 * {@see org.mule.runtime.api.metadata.DataType#isStreamType}.
 */
public class ConsumableMuleMessageFilter implements Filter {

  @Override
  public boolean accept(MuleMessage message, MuleEvent.Builder builder) {
    return !message.getDataType().isStreamType();
  }
}
