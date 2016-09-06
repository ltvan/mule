/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.connector;

import org.mule.runtime.core.api.processor.MessageProcessor;
import org.mule.runtime.core.api.routing.RoutingException;
import org.mule.runtime.core.config.i18n.Message;

/**
 * <code>DispatchException</code> is thrown when a connector operation dispatcher fails to send, dispatch or receive a message.
 */
public class DispatchException extends RoutingException {

  /**
   * Serial version
   */
  private static final long serialVersionUID = -8204621943732496606L;

  public DispatchException(MessageProcessor target) {
    super(target);
  }

  public DispatchException(MessageProcessor target, Throwable cause) {
    super(target, cause);
  }

  public DispatchException(Message message, MessageProcessor target) {
    super(message, target);
  }

  public DispatchException(Message message, MessageProcessor target, Throwable cause) {
    super(message, target, cause);
  }
}
