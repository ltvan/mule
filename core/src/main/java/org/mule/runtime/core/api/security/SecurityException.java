/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.security;

import org.mule.runtime.core.api.MuleException;
import org.mule.runtime.core.config.i18n.Message;

/**
 * <code>SecurityException</code> is a generic security exception
 */
public abstract class SecurityException extends MuleException {

  protected SecurityException(Message message) {
    super(message);
  }

  protected SecurityException(Message message, Throwable cause) {
    super(message, cause);
  }
}
