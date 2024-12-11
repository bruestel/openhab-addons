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
package org.openhab.binding.homeconnectdirect.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link HttpConstants} interface contains constant parameters used by the http client or servlet.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public interface HttpConstants {

    String CLIENT_ID = "client_id";
    String RESPONSE_TYPE = "response_type";
    String CODE = "code";
    String CODE_VERIFIER = "code_verifier";
    String STATE = "state";
    String NONCE = "nonce";
    String REDIRECT_URI = "redirect_uri";
    String GRANT_TYPE = "grant_type";
    String SCOPE = "scope";
    String CODE_CHALLENGE = "code_challenge";
    String CODE_CHALLENGE_METHOD = "code_challenge_method";
    String PROMPT = "prompt";
    String LOGIN = "login";
    String S256 = "S256";
    String REDIRECT_TARGET = "redirect_target";
    String BEARER_PREFIX = "Bearer ";
}
