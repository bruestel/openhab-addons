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
package org.openhab.binding.boschsmartcam.internal.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.osgi.service.component.annotations.Component;

/**
 * Keeps track of the accounts that can be authorized and of the cameras by the token in their addresses, for
 * {@link BoschSmartCamAuthServlet}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@Component(service = BoschSmartCamAuthService.class)
@NonNullByDefault
public class BoschSmartCamAuthService {

    private final List<BoschSmartCamAccountHandler> handlers = new CopyOnWriteArrayList<>();

    private final Map<String, BoschSmartCamCameraHandler> camerasByToken = new ConcurrentHashMap<>();

    /**
     * Registers a camera under the token that guards its URLs.
     */
    public void addCamera(String token, BoschSmartCamCameraHandler handler) {
        camerasByToken.put(token, handler);
    }

    /**
     * Forgets the camera behind a token, so its addresses are answered with 404 from then on.
     */
    public void removeCamera(String token) {
        camerasByToken.remove(token);
    }

    /**
     * @return the cameras that are running, for the accounts to tell them what the cloud knows
     */
    public List<BoschSmartCamCameraHandler> getCameraHandlers() {
        return List.copyOf(camerasByToken.values());
    }

    /**
     * @param token the token from the requested URL
     * @return the camera behind it, if the token belongs to one
     */
    public Optional<BoschSmartCamCameraHandler> getCamera(@Nullable String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(camerasByToken.get(token));
    }

    /**
     * Offers an account on the authorization page and to the cameras looking for one.
     */
    public void addAccountHandler(BoschSmartCamAccountHandler handler) {
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    /**
     * Removes an account from the authorization page and from the cameras looking for one.
     */
    public void removeAccountHandler(BoschSmartCamAccountHandler handler) {
        handlers.remove(handler);
    }

    /**
     * @return the accounts that are running, in the order they were added
     */
    public List<BoschSmartCamAccountHandler> getAccountHandlers() {
        return List.copyOf(handlers);
    }

    /**
     * @param state the state that was passed to the authorization server, which is the UID of the account thing
     * @return the handler that started this login, if it still exists
     */
    public Optional<BoschSmartCamAccountHandler> getAccountHandler(@Nullable String state) {
        return handlers.stream().filter(handler -> state != null && handler.matchesState(state)).findFirst();
    }
}
