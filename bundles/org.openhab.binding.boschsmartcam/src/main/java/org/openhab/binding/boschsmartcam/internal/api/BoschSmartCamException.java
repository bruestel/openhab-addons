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
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;

/**
 * Signals a failed communication with the Bosch cloud API.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Status of a request that did not complete, so there is no answer.
     */
    public static final int NO_RESPONSE = 0;

    /**
     * Codes of its own the Bosch cloud answers with, as other integrations found them.
     */
    public static final int NOT_SUPPORTED = 442;
    public static final int PRIVACY_MODE = 443;
    public static final int RATE_LIMITED = 444;
    public static final int FIRMWARE_NOT_SUPPORTED = 449;

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
        this.httpStatus = NO_RESPONSE;
    }

    /**
     * @return the HTTP status code of the failed request or {@link #NO_RESPONSE} if the request did not complete
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * @return whether the request failed because the access token was not accepted
     */
    public boolean isAuthorizationFailure() {
        return httpStatus == HttpStatus.UNAUTHORIZED_401 || httpStatus == HttpStatus.FORBIDDEN_403;
    }

    /**
     * Codes of its own the Bosch cloud answers with, as other integrations found them: 442 the hardware cannot do it,
     * 443 the camera is in privacy mode, 444 too many requests or sessions for now, 449 the firmware cannot do it.
     *
     * @return whether the request failed for a reason that is expected in operation and passes or needs the user,
     *         rather than for a fault
     */
    public boolean isExpected() {
        return httpStatus == NOT_SUPPORTED || httpStatus == PRIVACY_MODE || httpStatus == RATE_LIMITED
                || httpStatus == FIRMWARE_NOT_SUPPORTED;
    }

    /**
     * @return whether Bosch limits the requests for now; the camera and the account are fine
     */
    public boolean isRateLimited() {
        return httpStatus == RATE_LIMITED;
    }

    /**
     * Logs that a command could not be carried out: a lost connection at debug, as it is common and says little; the
     * codes Bosch answers with in operation at info, so the user learns why the command had no effect, such as the
     * privacy mode being on; an answer the binding does not know at warn.
     *
     * @param action what could not be done, e.g. {@code "switch the siren of"}
     * @param subject what it was done to, e.g. the UID of the thing
     */
    public void log(Logger logger, String action, Object subject) {
        if (httpStatus == NO_RESPONSE) {
            // a lost connection is common and says little, the command simply had no effect
            logger.debug("Could not {} {}: {}", action, subject, getReason());
        } else if (isExpected()) {
            logger.info("Could not {} {}: {}", action, subject, getReason());
        } else {
            logger.warn("Could not {} {}: {}", action, subject, getReason());
        }
    }

    /**
     * @return the text made safe as an argument of an {@code @text/...} status description, which ends at a quote
     */
    public static String asTextArgument(String text) {
        return text.replace('"', '\'');
    }

    /**
     * @return the reason in words for one of the codes of the Bosch cloud, otherwise the message
     */
    public String getReason() {
        return switch (httpStatus) {
            case NOT_SUPPORTED -> "the camera does not support this";
            case PRIVACY_MODE -> "the camera is in privacy mode";
            case RATE_LIMITED -> "Bosch limits the requests for now, try again later";
            case FIRMWARE_NOT_SUPPORTED -> "the firmware of the camera does not support this";
            default -> String.valueOf(getMessage());
        };
    }
}
