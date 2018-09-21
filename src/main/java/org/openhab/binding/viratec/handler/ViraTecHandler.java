/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.viratec.handler;

import static org.openhab.binding.viratec.ViraTecBindingConstants.*;

import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.joda.time.format.DateTimeFormat;
import org.openhab.binding.viratec.internal.Circuit;
import org.openhab.binding.viratec.internal.StateUpdate;
import org.openhab.binding.viratec.internal.ViraCube;
import org.openhab.binding.viratec.internal.exceptions.IrrigationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ViraTecHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 *
 */

@NonNullByDefault
public class ViraTecHandler extends BaseThingHandler implements CircuitStatusListener {

    private final Logger logger = LoggerFactory.getLogger(ViraTecHandler.class);

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Stream.of(THING_TYPE_CIRCUIT)
            .collect(Collectors.toSet());

    @NonNullByDefault({})
    private String circuitID;
    private @Nullable ViraCubeHandler viraCubeHandler;
    @Nullable
    ScheduledFuture<?> refreshJob;

    private org.joda.time.format.DateTimeFormatter formatter = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");

    public ViraTecHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initialize ViraTecHandler.");
        Bridge bridge = getBridge();
        initializeThing((bridge == null) ? null : bridge.getStatus());
    }

    private void initializeThing(@Nullable ThingStatus bridgestatus) {
        logger.debug("Initialize Thing {} bridgestatus {}", getThing().getUID(), bridgestatus);

        getConfig().put(CIRCUIT_ID, getThing().getProperties().get(CIRCUIT_ID));
        final String configCircuitId = (String) getConfig().get(CIRCUIT_ID);
        if (configCircuitId != null) {
            circuitID = configCircuitId;
            if (getViraCubeHandler() != null) {
                if (bridgestatus == ThingStatus.ONLINE) {
                    // initializeProperties();
                    updateStatus(ThingStatus.ONLINE);
                    startAutomaticRefresh();
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                }
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "error no circuit id");
        }
    }

    private void startAutomaticRefresh() {
        refreshJob = scheduler.scheduleWithFixedDelay(() -> {
            try {
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_BORDERBOTTOM), getBorderbottom(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_BORDERTOP), getBordertop(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_IRRIGATIONSTART),
                        getIrrigationStart(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_IRRIGATIONEND), getIrrigationEnd(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_TEMPERATURE), getTemperature(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUMIDITY), getHumidity(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_BRIGHTNESS), getBrightness(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_CONSIDERMOWER), getConsiderMower(getCircuit()));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_EXTERNBLOCK), getExternBlock(getCircuit()));
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private synchronized @Nullable ViraCubeHandler getViraCubeHandler() {
        if (this.viraCubeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof ViraCubeHandler) {
                this.viraCubeHandler = (ViraCubeHandler) handler;
                this.viraCubeHandler.registerCircuitStatusListener(this);
            } else {
                return null;
            }
        }
        return this.viraCubeHandler;
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposes");
        if (circuitID != null) {
            ViraCubeHandler viraCubeHandler = getViraCubeHandler();
            if (viraCubeHandler != null) {
                viraCubeHandler.unregisterCircuitStatusListener(this);
                this.viraCubeHandler = null;
            }
            circuitID = null;
        }
        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
    }

    private @Nullable Circuit getCircuit() {
        ViraCubeHandler viraCubeHandler = getViraCubeHandler();
        if (viraCubeHandler != null) {
            return viraCubeHandler.getCircuitById(circuitID);
        }
        return null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ViraCubeHandler cubeHandler = getViraCubeHandler();
        if (viraCubeHandler == null) {
            logger.warn("MIYOCubeHandler not found. Cannot handle Command without MIYOCube");
            return;
        }

        Circuit circuit = getCircuit();
        if (circuit == null) {
            logger.warn("Circuit not known to the bridge. Cannot handle Command");
            return;
        }
        StateUpdate circuitState = null;
        switch (channelUID.getId()) {
            case CHANNEL_IRRIGATION:
                logger.trace("CHANNEL Irrigation handling command {}", command);
                if (circuit.getWintermode()) {
                    updateState(CHANNEL_IRRIGATION, OnOffType.OFF);
                    return;
                }
                if (command instanceof OnOffType) {
                    circuitState = new StateUpdate().setOn(OnOffType.ON.equals(command));
                }
                break;
            case CHANNEL_BORDERBOTTOM:
                updateState(channelUID, getBorderbottom(circuit));
                break;
            case CHANNEL_BORDERTOP:
                updateState(channelUID, getBordertop(circuit));
                break;
            case CHANNEL_IRRIGATIONSTART:
                updateState(channelUID, getIrrigationStart(circuit));
                break;
            case CHANNEL_IRRIGATIONEND:
                updateState(channelUID, getIrrigationEnd(circuit));
                break;
            case CHANNEL_TEMPERATURE:
                updateState(channelUID, getTemperature(circuit));
                break;
            case CHANNEL_HUMIDITY:
                updateState(channelUID, getHumidity(circuit));
                break;
            case CHANNEL_BRIGHTNESS:
                updateState(channelUID, getBrightness(circuit));
                break;
            case CHANNEL_WINTERMODE:
                logger.trace("CHANNEL Wintermode handling command {}", command);
                if (command instanceof OnOffType) {
                    circuitState = new StateUpdate().setWinter(OnOffType.ON.equals(command));
                }
                break;
            case CHANNEL_CONSIDERMOWER:
                updateState(channelUID, getConsiderMower(circuit));
                break;
            case CHANNEL_EXTERNBLOCK:
                updateState(channelUID, getExternBlock(circuit));
                break;
        }
        if (circuitState != null) {
            try {
                cubeHandler.updateCircuitState(circuit, circuitState);
            } catch (IrrigationException e) {
                updateState(CHANNEL_IRRIGATION, OnOffType.OFF);
            }
        } else {
            logger.warn("Command sent to an unknown Channel ID: {}", channelUID);
        }
    }

    private State getBordertop(@Nullable Circuit circuit) {
        return new DecimalType(circuit.getState().getBorderTop());
    }

    private State getBorderbottom(@Nullable Circuit circuit) {
        return new DecimalType(circuit.getState().getBorderBottom());
    }

    private State getIrrigationStart(@Nullable Circuit circuit) {
        return new StringType(formatter.print(circuit.getNextIrrigationStart()));
    }

    private State getIrrigationEnd(@Nullable Circuit circuit) {
        return new StringType(formatter.print(circuit.getNextIrrigationEnd()));
    }

    private State getTemperature(@Nullable Circuit circuit) {
        return new DecimalType(circuit.getTemperature());
    }

    private State getHumidity(@Nullable Circuit circuit) {
        return new DecimalType(circuit.getMoisture());
    }

    private State getBrightness(@Nullable Circuit circuit) {
        return new DecimalType(circuit.getBrightness());
    }

    private State getConsiderMower(@Nullable Circuit circuit) {
        if (circuit.getState().getConsiderMower()) {
            return OpenClosedType.OPEN;
        } else {
            return OpenClosedType.CLOSED;
        }
    }

    private State getExternBlock(@Nullable Circuit circuit) {
        if (circuit.getExternBlock()) {
            return OpenClosedType.OPEN;
        } else {
            return OpenClosedType.CLOSED;
        }
    }

    @Override
    public void onCircuitAdded(@Nullable ViraCube viraCube, Circuit circuit) {
        if (circuit.getOpenhabId().equals(circuitID)) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public void onCircuitRemoved(@Nullable ViraCube viraCube, Circuit circuit) {
        if (circuit.getOpenhabId().equals(circuitID)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "offline circuit removed");
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        ViraCubeHandler handler = getViraCubeHandler();
        if (handler != null) {
            Circuit circuit = handler.getCircuitById(circuitID);
            if (circuit != null) {
                onCircuitStateChanged(null, circuit);
            }
        }

    }

    @Override
    public void onCircuitStateChanged(@Nullable ViraCube viracube, Circuit circuit) {
        logger.trace("OnCircuitStateChanged() was called");
        if (!circuit.getOpenhabId().equals(circuitID)) {
            logger.trace("Received state changed for another handler's circuit({}). Will be ignored", circuit.getId());
            return;
        }

        if (circuit.getOpenhabId() != null) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "circuit not reachable");
        }

        if (circuit.getWintermode()) {
            updateState(CHANNEL_WINTERMODE, OnOffType.ON);
        } else {
            updateState(CHANNEL_WINTERMODE, OnOffType.OFF);
        }

        if (circuit.getIrrigation()) {
            updateState(CHANNEL_IRRIGATION, OnOffType.ON);
        } else {
            updateState(CHANNEL_IRRIGATION, OnOffType.OFF);
        }
    }

}
