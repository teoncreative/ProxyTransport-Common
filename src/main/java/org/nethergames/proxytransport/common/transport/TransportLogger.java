/*
 * Copyright (c) 2024-2026 NetherGamesMC
 * Licensed under the MIT license.
 */

package org.nethergames.proxytransport.common.transport;

/** Logging seam, so the transports depend on no particular logging framework. */
public interface TransportLogger {

    void info(String message);

    void warn(String message);

    void error(String message, Throwable cause);
    
}