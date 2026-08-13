/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.boschsmartcam.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Signals a failed communication with the Bosch cloud API.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public BoschSmartCamException(String message) {
        this(message, 0);
    }

    public BoschSmartCamException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public BoschSmartCamException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    /**
     * @return the HTTP status code of the failed request or {@code 0} if the request did not complete
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return whether the request failed because the access token was not accepted
     */
    public boolean isAuthorizationFailure() {
        return httpStatus == 401 || httpStatus == 403;
    }
}
