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
package org.openhab.binding.boschsmartcam.internal.local;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Who a camera is, as its certificate says.
 *
 * The {@code pseudonym} of the subject is the MAC address the camera uses on the network - the same value the ARP
 * table shows and the cloud reports in {@code wifiinfo}. The common name is that address minus one and is not used.
 *
 * @param macAddress MAC address in the form {@code 64:da:a0:12:34:56}
 * @param serialNumber serial number of the device, if the certificate carries one
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record CameraIdentity(String macAddress, @Nullable String serialNumber) {

    private static final String OID_PSEUDONYM = "2.5.4.65";
    private static final String OID_SERIAL_NUMBER = "2.5.4.5";
    private static final String PSEUDONYM = "PSEUDONYM";
    private static final String SERIAL_NUMBER = "SERIALNUMBER";

    static CameraIdentity fromCertificate(X509Certificate certificate) throws IOException {
        // without the mapping both attributes would come out as hex encoded DER
        String subject = certificate.getSubjectX500Principal().getName(X500Principal.RFC2253,
                Map.of(OID_PSEUDONYM, PSEUDONYM, OID_SERIAL_NUMBER, SERIAL_NUMBER));
        String pseudonym = null;
        String serialNumber = null;
        try {
            for (Rdn rdn : new LdapName(subject).getRdns()) {
                if (PSEUDONYM.equalsIgnoreCase(rdn.getType())) {
                    pseudonym = String.valueOf(rdn.getValue());
                } else if (SERIAL_NUMBER.equalsIgnoreCase(rdn.getType())) {
                    serialNumber = String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException e) {
            throw new IOException("Unreadable certificate subject " + subject, e);
        }
        String macAddress = normalizeMacAddress(pseudonym);
        if (macAddress == null) {
            throw new IOException("The certificate carries no MAC address: " + subject);
        }
        return new CameraIdentity(macAddress, serialNumber);
    }

    /**
     * @return the id of a camera thing, the MAC address in lower case without separators
     */
    public String thingId() {
        return thingId(macAddress);
    }

    public static String thingId(String macAddress) {
        return macAddress.replace(":", "");
    }

    /**
     * Brings a MAC address into the form {@code 64:da:a0:12:34:56}. The certificate and the cloud both separate with
     * dashes, openHAB uses colons.
     *
     * @return the normalized address or {@code null} if the value is no MAC address
     */
    public static @Nullable String normalizeMacAddress(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String hex = value.replaceAll("[-:.]", "").toLowerCase(Locale.ROOT);
        if (!hex.matches("[0-9a-f]{12}")) {
            return null;
        }
        return hex.replaceAll("(..)(?!$)", "$1:");
    }
}
