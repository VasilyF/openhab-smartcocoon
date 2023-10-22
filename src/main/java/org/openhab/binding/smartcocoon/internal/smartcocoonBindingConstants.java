/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.smartcocoon.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link smartcocoonBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Mike Fedotov - Initial contribution
 */
@NonNullByDefault
public class smartcocoonBindingConstants {

    private static final String BINDING_ID = "smartcocoon";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_FAN = new ThingTypeUID(BINDING_ID, "fan");

    // List of all Channel ids
    public static final String CHANNEL_FAN_SWITCH = "power";
    public static final String CHANNEL_FAN_SPEED = "fanSpeed";

    // List of all Config properties
    public static final String CONFIG_PROPERTY_FAN_ID = "fanId";

}
