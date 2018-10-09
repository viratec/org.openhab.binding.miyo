/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.miyo;

//import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link MiyoBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 *
 */
// @NonNullByDefault
public class MiyoBindingConstants {

    public static final String BINDING_ID = "miyo";

    public static final ThingTypeUID THING_TYPE_BRIDGE = new ThingTypeUID(BINDING_ID, "bridge");
    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_CIRCUIT = new ThingTypeUID(BINDING_ID, "circuit");

    // List of all Channel ids
    public static final String CHANNEL_IRRIGATIONEND = "getIrrigationEnd";
    public static final String CHANNEL_IRRIGATIONSTART = "getIrrigationStart";
    public static final String CHANNEL_IRRIGATION = "setIrrigation";
    public static final String CHANNEL_BORDERBOTTOM = "getBorderbottom";
    public static final String CHANNEL_BORDERTOP = "getBordertop";
    public static final String CHANNEL_WINTERMODE = "setWintermode";
    public static final String CHANNEL_TEMPERATURE = "getTemperature";
    public static final String CHANNEL_BRIGHTNESS = "getBrightness";
    public static final String CHANNEL_HUMIDITY = "getHumidity";
    public static final String CHANNEL_EXTERNBLOCK = "getExternBlock";
    public static final String CHANNEL_CONSIDERMOWER = "getConsiderMower";

    // Bridge bzw. Cube config properties
    public static final String HOST = "ipAddress";
    public static final String USER_NAME = "userName";
    public static final String POLLING_INTERVAL = "pollingInterval";

    // Circuit config properties
    public static final String CIRCUIT_ID = "circuitId";

}
