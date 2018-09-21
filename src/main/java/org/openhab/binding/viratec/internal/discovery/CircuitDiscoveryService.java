/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.viratec.internal.discovery;

import static org.openhab.binding.viratec.ViraTecBindingConstants.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openhab.binding.viratec.handler.CircuitStatusListener;
import org.openhab.binding.viratec.handler.ViraCubeHandler;
import org.openhab.binding.viratec.handler.ViraTecHandler;
import org.openhab.binding.viratec.internal.Circuit;
import org.openhab.binding.viratec.internal.ViraCube;
import org.slf4j.LoggerFactory;

/**
 *
 *
 *
 */

@NonNullByDefault
public class CircuitDiscoveryService extends AbstractDiscoveryService implements CircuitStatusListener {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(CircuitDiscoveryService.class);

    private final static int SEARCH_TIME = 60;

    private final ViraCubeHandler viraCubeHandler;

    public CircuitDiscoveryService(ViraCubeHandler viraCubeHandler) {
        super(SEARCH_TIME);
        this.viraCubeHandler = viraCubeHandler;
    }

    public void activate() {
        viraCubeHandler.registerCircuitStatusListener(this);
    }

    @Override
    public void deactivate() {
        removeOlderResults(new Date().getTime());
        viraCubeHandler.unregisterCircuitStatusListener(this);
    }

    @Override
    public void startScan() {
        List<Circuit> circuits = viraCubeHandler.getCircuits();
        for (Circuit l : circuits) {
            onCircuitAddedInternal(l);
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    public void onCircuitAdded(@Nullable ViraCube viraCube, Circuit circuit) {
        onCircuitAddedInternal(circuit);
    }

    private void onCircuitAddedInternal(Circuit circuit) {
        ThingUID thingUID = getThingUID(circuit);
        ThingTypeUID thingTypeUID = getThingTypeUID(circuit);

        if (thingUID != null && thingTypeUID != null) {
            ThingUID bridgeUID = viraCubeHandler.getThing().getUID();
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(CIRCUIT_ID, circuit.getOpenhabId());
            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withThingType(thingTypeUID)
                    .withProperties(properties).withBridge(bridgeUID).withLabel(circuit.getName()).build();
            thingDiscovered(discoveryResult);
        } else {
            logger.debug("discovered unsupported circuit with Id {}.", circuit.getOpenhabId());
        }
    }

    @Override
    public void onCircuitStateChanged(@Nullable ViraCube viracube, Circuit circuit) {
        // Nicht gebraucht

    }

    @Override
    public void onCircuitRemoved(@Nullable ViraCube viraCube, Circuit circuit) {
        ThingUID thingUID = getThingUID(circuit);
        if (thingUID != null) {
            thingRemoved(thingUID);
        }
    }

    private @Nullable ThingUID getThingUID(Circuit circuit) {
        ThingUID bridgeUID = viraCubeHandler.getThing().getUID();
        ThingTypeUID thingTypeUID = getThingTypeUID(circuit);
        if ((thingTypeUID != null) && (ViraTecHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID))) {
            return new ThingUID(thingTypeUID, bridgeUID, circuit.getOpenhabId());
        } else {
            return null;
        }
    }

    private @Nullable ThingTypeUID getThingTypeUID(Circuit circuit) {
        return new ThingTypeUID(BINDING_ID, "circuit");
    }

}
