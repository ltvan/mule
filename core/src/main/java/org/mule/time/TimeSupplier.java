/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.time;

import java.util.function.Supplier;

/**
 * A {@link Supplier} which provides the current system time
 * in milliseconds. This is a singleton class, use {@link #INSTANCE}
 * to get an instance of it.
 *
 * @since 4.0
 */
public final class TimeSupplier implements Supplier<Long>
{

    public static final TimeSupplier INSTANCE = new TimeSupplier();

    private TimeSupplier()
    {
    }

    /**
     * Returns {@link System#currentTimeMillis()}
     *
     * @return the current time in milliseconds
     */
    @Override
    public Long get()
    {
        return System.currentTimeMillis();
    }
}
