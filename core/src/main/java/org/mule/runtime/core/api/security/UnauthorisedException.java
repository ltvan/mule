/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.security;

import static org.mule.runtime.core.config.i18n.CoreMessages.authDeniedOnEndpoint;
import static org.mule.runtime.core.config.i18n.CoreMessages.authFailedForUser;
import static org.mule.runtime.core.config.i18n.CoreMessages.authSetButNoContext;
import org.mule.runtime.core.api.MuleEvent;
import org.mule.runtime.core.config.i18n.Message;

/**
 * <code>UnauthorisedException</code> is thrown if authentication fails
 */

public class UnauthorisedException extends SecurityException {

  /**
   * Serial version
   */
  private static final long serialVersionUID = -6664384216189042673L;

  public UnauthorisedException(Message message) {
    super(message);
  }

  public UnauthorisedException(Message message, Throwable cause) {
    super(message, cause);
  }

  public UnauthorisedException(MuleEvent event, SecurityContext context, SecurityFilter filter) {
    super(constructMessage(context, event.getContext().getOriginatingConnectorName(), filter));
  }

  private static Message constructMessage(SecurityContext context, String originatingConnectorName, SecurityFilter filter) {
    Message m;
    if (context == null) {
      m = authSetButNoContext(filter.getClass().getName());
    } else {
      m = authFailedForUser(context.getAuthentication().getPrincipal());
    }
    m.setNextMessage(authDeniedOnEndpoint(originatingConnectorName));
    return m;
  }
}
