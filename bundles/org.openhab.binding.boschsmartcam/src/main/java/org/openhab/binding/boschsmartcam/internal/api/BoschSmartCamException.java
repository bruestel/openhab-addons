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

    /**
     * Codes of its own the Bosch cloud answers with, as other integrations found them: 442 the hardware cannot do it,
     * 443 the camera is in privacy mode, 444 too many requests or sessions for now, 449 the firmware cannot do it.
     *
     * @return whether the request failed for a reason that is expected in operation and passes or needs the user,
     *         rather than for a fault
     */
    public boolean isExpected() {
        return httpStatus == 442 || httpStatus == 443 || httpStatus == 444 || httpStatus == 449;
    }

    /**
     * @return whether Bosch limits the requests for now; the camera and the account are fine
     */
    public boolean isRateLimited() {
        return httpStatus == 444;
    }

    /**
     * @return the reason in words for one of the codes of the Bosch cloud, otherwise the message
     */
    public String getReason() {
        return switch (httpStatus) {
            case 442 -> "the camera does not support this";
            case 443 -> "the camera is in privacy mode";
            case 444 -> "Bosch limits the requests for now, try again later";
            case 449 -> "the firmware of the camera does not support this";
            default -> String.valueOf(getMessage());
        };
    }
}
