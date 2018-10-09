/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.miyo.internal;

import static org.openhab.binding.miyo.MiyoBindingConstants.CIRCUIT_ID;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.miyo.handler.CubeHandler;
import org.openhab.binding.miyo.handler.MiyoHandler;
import org.openhab.binding.miyo.internal.discovery.CircuitDiscoveryService;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link MiyoHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 *
 */
@Component(service = ThingHandlerFactory.class, immediate = true, configurationPid = "binding.miyo")

public class MiyoHandlerFactory extends BaseThingHandlerFactory {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Stream
            .concat(CubeHandler.SUPPORTED_THING_TYPES.stream(), MiyoHandler.SUPPORTED_THING_TYPES.stream())
            .collect(Collectors.toSet());

    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @Override
    public @Nullable Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration,
            @Nullable ThingUID thingUID, @Nullable ThingUID bridgeUID) {
        if (CubeHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            return super.createThing(thingTypeUID, configuration, thingUID, null);
        }
        if (MiyoHandler.SUPPORTED_THING_TYPES.contains(thingTypeUID)) {
            ThingUID circuitUID = getCircuitUID(thingTypeUID, thingUID, configuration, bridgeUID);
            return super.createThing(thingTypeUID, configuration, circuitUID, bridgeUID);
        }
        throw new IllegalArgumentException("The thing type: " + thingTypeUID + "is not supported by the MIYOCube");

    }

    private ThingUID getCircuitUID(ThingTypeUID thingTypeUID, @Nullable ThingUID thingUID, Configuration configuration,
            @Nullable ThingUID bridgeUID) {
        if (thingUID != null) {
            return thingUID;
        } else {
            String circuitId = (String) configuration.get(CIRCUIT_ID);
            if (bridgeUID != null) {
                return new ThingUID(thingTypeUID, circuitId, bridgeUID.getId());
            } else {
                return new ThingUID(thingTypeUID, circuitId, (String[]) null);
            }
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (CubeHandler.SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID())) {
            CubeHandler handler = new CubeHandler((Bridge) thing); // NEU
            registerCircuitDiscoveryService(handler);
            return handler;
        } else if (MiyoHandler.SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID())) {
            return new MiyoHandler(thing);
        } else {
            return null;
        }
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof CubeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.get(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                CircuitDiscoveryService discoveryService = (CircuitDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                if (discoveryService != null) {
                    discoveryService.deactivate();
                }
                serviceReg.unregister();
                discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            }
        }
    }

    private synchronized void registerCircuitDiscoveryService(CubeHandler cubeHandler) {
        CircuitDiscoveryService discoveryService = new CircuitDiscoveryService(cubeHandler);
        discoveryService.activate();
        this.discoveryServiceRegs.put(cubeHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

}
