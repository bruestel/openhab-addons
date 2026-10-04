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
package org.openhab.binding.boschsmartcam.internal.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches addresses against a list of CIDR blocks, e.g. {@code 192.168.0.0/16, fe80::/10}.
 *
 * Comparing addresses as text is not enough here: {@code 192.168.0.9} is a prefix of {@code 192.168.0.99}, and the
 * same address can be written in more than one way, so the comparison works on the raw bytes.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CidrMatcher {

    private final Logger logger = LoggerFactory.getLogger(CidrMatcher.class);

    private final List<Block> blocks = new ArrayList<>();

    /**
     * @param cidrList comma separated CIDR blocks; entries that cannot be parsed are skipped with a warning so one
     *            typo does not take the whole list down
     */
    public CidrMatcher(String cidrList) {
        for (String entry : cidrList.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                blocks.add(parse(trimmed));
            } catch (IllegalArgumentException | UnknownHostException e) {
                logger.warn("Ignoring '{}', it is not a valid CIDR block: {}", trimmed, e.getMessage());
            }
        }
    }

    /**
     * @return whether the list is empty, in which case nothing can ever match
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public boolean matches(InetAddress address) {
        byte[] candidate = address.getAddress();
        return blocks.stream().anyMatch(block -> block.contains(candidate));
    }

    /**
     * @param address address in text form, as a servlet hands it out
     */
    public boolean matches(String address) {
        try {
            // an IPv6 address may carry a zone index, which is irrelevant for the comparison
            int zone = address.indexOf('%');
            return matches(InetAddress.getByName(zone < 0 ? address : address.substring(0, zone)));
        } catch (UnknownHostException e) {
            logger.debug("Cannot interpret '{}' as an address", address);
            return false;
        }
    }

    private static Block parse(String entry) throws UnknownHostException {
        int slash = entry.indexOf('/');
        if (slash < 0) {
            // a bare address is the block of exactly that address
            byte[] address = InetAddress.getByName(entry).getAddress();
            return new Block(address, address.length * Byte.SIZE);
        }
        byte[] address = InetAddress.getByName(entry.substring(0, slash)).getAddress();
        int prefix = Integer.parseInt(entry.substring(slash + 1).trim());
        if (prefix < 0 || prefix > address.length * Byte.SIZE) {
            throw new IllegalArgumentException("prefix length " + prefix + " does not fit the address");
        }
        return new Block(address, prefix);
    }

    private record Block(byte[] address, int prefixBits) {

        boolean contains(byte[] candidate) {
            if (candidate.length != address.length) {
                // an IPv4 address never sits in an IPv6 block and the other way round
                return false;
            }
            int fullBytes = prefixBits / Byte.SIZE;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != address[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % Byte.SIZE;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[fullBytes] & mask) == (address[fullBytes] & mask);
        }
    }
}
