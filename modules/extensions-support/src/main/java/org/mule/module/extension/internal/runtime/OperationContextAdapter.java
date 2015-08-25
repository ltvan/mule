/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extension.internal.runtime;

import org.mule.api.MuleEvent;
import org.mule.extension.runtime.OperationContext;
import org.mule.extension.runtime.event.OperationFailedSignal;
import org.mule.extension.runtime.event.OperationSuccessfulSignal;

import java.util.function.Consumer;

/**
 * Adapter interface which expands the contract of
 * {@link OperationContext} which functionality that is
 * internal to this implementation of the extensions
 * API and shouldn't be accessible for the extensions
 * themselves
 *
 * @since 3.7.0
 */
public interface OperationContextAdapter extends OperationContext
{

    /**
     * Returns the {@link MuleEvent} on which
     * an operation is to be executed
     */
    MuleEvent getEvent();

    /**
     * Sends a {@link OperationSuccessfulSignal} to all the handlers registered through the
     * {@link #onOperationSuccessful(Consumer)} method
     *
     * @param result the operation's result
     */
    void notifySuccessfulOperation(Object result);

    /**
     * Sends a {@link OperationFailedSignal} to all the handlers registered through the
     * {@link #onOperationFailed(Consumer)} method
     *
     * @param exception the {@link Exception} thrown by the operation
     */
    void notifyFailedOperation(Exception exception);

}
