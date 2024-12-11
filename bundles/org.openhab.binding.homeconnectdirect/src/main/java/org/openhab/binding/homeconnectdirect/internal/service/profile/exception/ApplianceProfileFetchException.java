/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.homeconnectdirect.internal.service.profile.exception;

import java.io.Serial;

public class ApplianceProfileFetchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -1054664073797505077L;

    public ApplianceProfileFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApplianceProfileFetchException(String message) {
        super(message);
    }
}
