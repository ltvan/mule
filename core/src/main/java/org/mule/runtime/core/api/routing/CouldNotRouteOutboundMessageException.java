/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.routing;

import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.runtime.core.config.i18n.Message;

/**
 * <code>CouldNotRouteOutboundMessageException</code> thrown if Mule fails to route the current outbound event.
 */

public class CouldNotRouteOutboundMessageException extends RoutingException {

  /**
   * Serial version
   */
  private static final long serialVersionUID = 4609966704030524483L;

  public CouldNotRouteOutboundMessageException(MessageProcessor target) {
    super(target);
  }

  public CouldNotRouteOutboundMessageException(MessageProcessor target, Throwable cause) {
    super(target, cause);
  }

  public CouldNotRouteOutboundMessageException(Message message, MessageProcessor target) {
    super(message, target);
  }

  public CouldNotRouteOutboundMessageException(Message message, MessageProcessor target, Throwable cause) {
    super(message, target, cause);
  }
}
