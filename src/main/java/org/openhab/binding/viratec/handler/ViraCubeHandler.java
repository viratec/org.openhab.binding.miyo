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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.viratec.internal.Circuit;
import org.openhab.binding.viratec.internal.State;
import org.openhab.binding.viratec.internal.StateUpdate;
import org.openhab.binding.viratec.internal.ViraCube;
import org.openhab.binding.viratec.internal.ViraCubeConfigStatusMessage;
import org.openhab.binding.viratec.internal.exceptions.ApiException;
import org.openhab.binding.viratec.internal.exceptions.IrrigationException;
import org.openhab.binding.viratec.internal.exceptions.LinkButtonException;
import org.openhab.binding.viratec.internal.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ViraCubeHandler} Handler für den ViraCube als Bridge
 *
 *
 *
 */
// @NonNullByDefault
public class ViraCubeHandler extends ConfigStatusBridgeHandler {

    private final String CIRCUIT_STATE_ADDED = "added";

    private final String CIRCUIT_STATE_CHANGED = "changed";

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_BRIDGE);

    private static final int DEFAULT_POLLING_INTERVAL = 10;

    public static final String DEVICE_TYPE = "EclipseSmartHome";

    private final Logger logger = LoggerFactory.getLogger(ViraCube.class);

    private boolean lastViraCubeConnectionState = false;

    private final Map<String, Circuit> lastCircuitStates = new ConcurrentHashMap<>();

    private final List<CircuitStatusListener> circuitStatusListeners = new CopyOnWriteArrayList<>();

    private @Nullable ScheduledFuture<?> pollingJob;

    private ViraCube viraCube = null;

    private final Runnable pollingRunnable = new Runnable() {

        @Override
        public void run() {
            try {
                if (!lastViraCubeConnectionState) {
                    lastViraCubeConnectionState = tryResumeBridgeConnection();
                }
                if (lastViraCubeConnectionState) {
                    Map<String, Circuit> lastCircuitStateCopy = new HashMap<>(lastCircuitStates);
                    for (final Circuit circuit : viraCube.getCircuits()) {
                        final String circuitId = circuit.getOpenhabId();
                        if (lastCircuitStateCopy.containsKey(circuitId)) {
                            final Circuit lastCircuit = lastCircuitStateCopy.remove(circuitId);
                            lastCircuitStates.put(circuitId, circuit);
                            if (!isEqual(lastCircuit, circuit) || circuit.getWintermode()) {
                                logger.debug("Status update for Circuit: {} detected.", circuitId);
                                notifyCircuitStatusListeners(circuit, CIRCUIT_STATE_CHANGED);
                            }
                        } else {
                            lastCircuitStates.put(circuitId, circuit);
                            logger.debug("Circuit {} added.", circuitId);
                            notifyCircuitStatusListeners(circuit, CIRCUIT_STATE_ADDED);
                        }
                    }
                    // Check for removed Circuits
                    for (Entry<String, Circuit> circuitEntry : lastCircuitStateCopy.entrySet()) {
                        lastCircuitStates.remove(circuitEntry.getKey());
                        logger.debug("Circuit {} removed.", circuitEntry.getKey());
                        for (CircuitStatusListener circuitStatusListener : circuitStatusListeners) {
                            try {
                                circuitStatusListener.onCircuitRemoved(viraCube, circuitEntry.getValue());
                            } catch (Exception e) {
                                logger.error("An error ocurred while calling the ViraCubeHeartListener", e);
                            }
                        }
                    }
                }
            } catch (UnauthorizedException | IllegalStateException e) {
                if (isReachable(viraCube.getIp())) {
                    lastViraCubeConnectionState = false;
                    onNotAuthenticated();
                } else {
                    if (lastViraCubeConnectionState || thing.getStatus() == ThingStatus.INITIALIZING) {
                        lastViraCubeConnectionState = false;
                        onConnectionLost();
                    }
                }
            } catch (Exception e) {
                if (viraCube != null) {
                    if (lastViraCubeConnectionState) {
                        logger.debug("Connection to the MIYOCube {} lost.", viraCube.getIp());
                        lastViraCubeConnectionState = false;
                        onConnectionLost();
                    }
                }
            }

            if (!lastViraCubeConnectionState) {
                onNotAuthenticated();
            }
        }

        private boolean isReachable(String ipAddress) {
            try {
                viraCube.authenticate("invalid");
            } catch (IOException e) {
                return false;
            } catch (ApiException e) {
                if (e.getMessage().contains("SocketTimeout") || e.getMessage().contains("ConnectException")
                        || e.getMessage().contains("SocketException")
                        || e.getMessage().contains("NoRouteToHostException")) {
                    return false;
                } else {
                    return true;
                }
            }
            return true;
        }
    };

    // Not needed
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    public ViraCubeHandler(Bridge cube) {
        super(cube);
    }

    public void updateCircuitState(Circuit circuit, StateUpdate stateUpdate) throws IrrigationException {
        if (viraCube != null) {
            String mode = (stateUpdate.commands.get(0).key);
            if (mode.equals("mode")) {
                try {
                    viraCube.setIrrigation(circuit, stateUpdate);
                } catch (IOException e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                } catch (IrrigationException e) {
                    throw new IrrigationException("Irrigation can not be turned on, if wintermode is activated");
                } catch (ApiException e) {
                    logger.warn("Error while accessing circuit: {}", e.getMessage(), e);
                } catch (IllegalStateException e) {
                    logger.trace("Error while accessing circuit: {}", e.getMessage());
                }
            } else if (mode.equals("winter")) {
                try {
                    viraCube.setWinter(circuit, stateUpdate);
                } catch (IOException e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                } catch (ApiException e) {
                    logger.warn("Error while accessing circuit: {}", e.getMessage(), e);
                } catch (IllegalStateException e) {
                    logger.trace("Error while accessing circuit: {}", e.getMessage());
                }
            } else {
                logger.warn("Command is not supported");
            }
        } else {
            logger.warn("No ViraCube connected or selected. Cannot set Circuit State");
        }

    }

    @Override
    public void dispose() {
        logger.debug("Handler disposed");
        if (pollingJob != null && !pollingJob.isCancelled()) {
            pollingJob.cancel(true);
            pollingJob = null;
        }
        if (viraCube != null) {
            viraCube = null;
        }
    }

    @Override // Der ViraCubeHandler wird initialisiert
    public void initialize() {
        logger.debug("Initializing MIYOcubeHandler");
        if (getConfig().get(HOST) != null) {
            if (viraCube == null) {
                viraCube = new ViraCube((String) getConfig().get(HOST));
                viraCube.setTimeout(5000);
            }
            onUpdate();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "@text/offline.conf-error-no-ip-address");
        }
    }

    private synchronized void onUpdate() {
        if (viraCube != null) {
            if (pollingJob == null || pollingJob.isCancelled()) {
                int pollingInterval = DEFAULT_POLLING_INTERVAL;
                try {
                    Object pollingIntervalConfig = getConfig().get(POLLING_INTERVAL);
                    if (pollingIntervalConfig != null) {
                        pollingInterval = ((BigDecimal) pollingIntervalConfig).intValue();
                    } else {
                        logger.info("Polling interval not configured for this MIYOCube. Using default value: {}s",
                                pollingInterval);
                    }
                } catch (NumberFormatException e) {
                    logger.info("Wrong configuration value for polling interval. Using default value: {}s",
                            pollingInterval);
                }
                pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 1, pollingInterval, TimeUnit.SECONDS);
            }

        }
    }

    // This method is called when ever the connection to the ViraCube is lost
    public void onConnectionLost() {
        logger.debug("Bridge conncetion lost. Updating thing status to offline");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "bridge-conncetion lost");
    }

    private void onConnectionResumed() throws ApiException, IOException {
        logger.debug("Bridge connection resumed. Updating Thingstatus to Online.");
        updateStatus(ThingStatus.ONLINE);
    }

    private boolean tryResumeBridgeConnection() throws IOException, ApiException {
        logger.debug("Connection to MIYOCube {} established", viraCube.getIp());
        if (getConfig().get(USER_NAME) == null) {
            logger.warn("User name for MIYOCube authentication not available in configuration.");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "conf-error-no-username");
            return false;
        } else {
            onConnectionResumed();
            return true;
        }

    }

    public boolean onNotAuthenticated() {
        String username = (String) getConfig().get(USER_NAME);
        if (viraCube == null) {
            return false;
        }
        if (username == null) {
            createUser();
        } else {
            try {
                viraCube.authenticate(username);
                return true;
            } catch (Exception e) {
                handleAuthenticationFailure(e, username);
            }
        }
        return false;
    }

    private void createUser() {
        try {
            String newUser = createUserOnPhysicalBridge();
            updateBridgeThingConfiguration(newUser);
        } catch (LinkButtonException e) {
            handleLinkButtonNotPressed(e);
        } catch (Exception e) {
            handleExceptionWhileCreatingUser(e);
        }
    }

    private String createUserOnPhysicalBridge() throws IOException, ApiException {
        logger.info("Creating new User on MIYOCube {} please press the button on the MIYOCube.", getConfig().get(HOST));
        String username = viraCube.link();
        return username;
    }

    private void updateBridgeThingConfiguration(String username) {
        Configuration config = editConfiguration();
        config.put(USER_NAME, username);
        try {
            updateConfiguration(config);
        } catch (IllegalStateException e) {
            logger.trace("Configuration update failed.", e);
            logger.warn("Unable to update configuration of MIYOCube");
        }
    }

    private void handleLinkButtonNotPressed(LinkButtonException e) {
        logger.debug("Failed creating new user on the bridge: {}", e.getMessage());
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                "@text/offline.conf-error-press-pairing-button");
    }

    private void handleExceptionWhileCreatingUser(Exception e) {
        logger.warn("Failed creating new user on the MIYOcube", e);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "error creating username");
    }

    private void handleAuthenticationFailure(Exception e, String username) {
        logger.warn("User {} is not authenticated on MIYOCube {}.", username, getConfig().get(HOST));
        logger.warn("Please configure a valid user or remove user from configuration to generate a new one");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "error invalid username");
    }

    public boolean registerCircuitStatusListener(CircuitStatusListener csl) {
        boolean result = circuitStatusListeners.add(csl);
        if (result) {
            onUpdate();
            for (Circuit circuit : lastCircuitStates.values()) {
                csl.onCircuitAdded(viraCube, circuit);
            }
        }
        return result;
    }

    public boolean unregisterCircuitStatusListener(CircuitStatusListener csl) {
        boolean result = circuitStatusListeners.remove(csl);
        if (result) {
            onUpdate();
        }
        return result;
    }

    public @Nullable Circuit getCircuitById(String id) {
        return lastCircuitStates.get(id);
    }

    public List<Circuit> getCircuits() {
        List<Circuit> ret = withReAuthentication("Search for new Circuits", () -> {
            return viraCube.getCircuits();
        });
        return ret != null ? ret : Collections.emptyList();
    }

    private <T> T withReAuthentication(String taskDescription, Callable<T> runnable) {
        if (viraCube != null) {
            try {
                try {
                    runnable.call();
                } catch (UnauthorizedException | IllegalStateException e) {
                    lastViraCubeConnectionState = false;
                    if (onNotAuthenticated()) {
                        return runnable.call();
                    }
                }
            } catch (Exception e) {
                logger.error("Bridge cannot: {}.", taskDescription, e);
            }
        }
        return null;
    }

    private void notifyCircuitStatusListeners(final Circuit circuit, final String type) {
        if (circuitStatusListeners.isEmpty()) {
            logger.debug("No circuit status Listeners to notify of circuit change for circuit {}",
                    circuit.getOpenhabId());
            return;
        }

        for (CircuitStatusListener csl : circuitStatusListeners) {
            try {
                switch (type) {
                    case CIRCUIT_STATE_ADDED:
                        csl.onCircuitAdded(viraCube, circuit);
                        logger.debug("Sending circuitAdded for circuit: {}", circuit.getOpenhabId());
                        break;
                    case CIRCUIT_STATE_CHANGED:
                        logger.debug("Sending circuitState changed for circuit: {}", circuit.getOpenhabId());
                        csl.onCircuitStateChanged(viraCube, circuit);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Could not notify circuitStatusListeners for unknown event type " + type);
                }
            } catch (Exception e) {
                logger.error("An exception occurred while calling the MIYOcubeHeartListener", e);
            }
        }
    }

    private boolean isEqual(Circuit c1, Circuit c2) {
        boolean irrigation = c1.getIrrigation() == c2.getIrrigation() && c1.getWintermode() == c2.getWintermode()
                && c1.getNextIrrigationStart().equals(c2.getNextIrrigationStart())
                && c1.getNextIrrigationEnd().equals(c2.getNextIrrigationEnd())
                && c1.getExternBlock() == c2.getExternBlock();

        if (!irrigation) {
            return irrigation;
        } else {
            return isEqual(c1.getState(), c2.getState());
        }
    }

    private boolean isEqual(State s1, State s2) {
        return s1.getConsiderMower() == s2.getConsiderMower() && s1.getBorderBottom().equals(s2.getBorderBottom())
                && s1.getBorderTop().equals(s2.getBorderTop());
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        final String cubeIpAddress = (String) getThing().getConfiguration().get(HOST);
        Collection<ConfigStatusMessage> configStatusMessages;

        if (cubeIpAddress == null || cubeIpAddress.isEmpty()) {
            configStatusMessages = Collections.singletonList(ConfigStatusMessage.Builder.error(HOST)
                    .withMessageKeySuffix(ViraCubeConfigStatusMessage.IP_ADDRESS_MISSING).withArguments(HOST).build());
        } else {
            configStatusMessages = Collections.emptyList();
        }
        return configStatusMessages;
    }
}