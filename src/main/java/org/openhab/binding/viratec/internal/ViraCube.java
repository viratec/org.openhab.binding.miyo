/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.viratec.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.joda.time.DateTime;
import org.openhab.binding.viratec.internal.HttpClient.Result;
import org.openhab.binding.viratec.internal.exceptions.ApiException;
import org.openhab.binding.viratec.internal.exceptions.IrrigationException;
import org.openhab.binding.viratec.internal.exceptions.LinkButtonException;
import org.openhab.binding.viratec.internal.exceptions.UnauthorizedException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 *
 *
 * {@link ViraCube} als Bridge für alle Circuits Verbindung zwischen Circuits und openhab über ViraCube
 */

@NonNullByDefault
public class ViraCube {
    private final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private @Nullable String username;
    private final String ip;
    private final Gson gson = new GsonBuilder().setDateFormat(DATE_FORMAT).create();
    private final JsonParser parser = new JsonParser();
    private HttpClient http = new HttpClient();

    public ViraCube(String ip) {
        this.ip = ip;
    }

    public ViraCube(String ip, String username) throws IOException, ApiException {
        this.ip = ip;
        authenticate(username);
    }

    // Setzen des Connect und Read Timeout für Http Anfragen
    public void setTimeout(int timeout) {
        http.setTimeout(timeout);
    }

    public String getIp() {
        return ip;
    }

    public @Nullable String getUsername() {
        return username;
    }

    // Hier sieht man ob die Authentifizierung mit dem ViraCube erfolgreich war oder nicht
    public boolean isAuthenticated() {
        return getUsername() != null;
    }

    public void authenticate(String username) throws IOException, ApiException {
        try {
            this.username = username;
            getCircuits();
        } catch (ApiException e) {
            this.username = null;
            throw new UnauthorizedException(e.toString());
        }
    }

    // Verbinden mit dem ViraCube erfolgt hier, hier wird der API-Key beantragt
    public String link() throws IOException, ApiException {
        if (username != null) {
            throw new IllegalStateException("already linked");
        }
        // Anfrage des API-Keys
        Result result = http.post("http://" + ip + "/api/link", "");
        String erg = result.getBody();
        if (parser.parse(erg).getAsJsonObject().get("status").getAsString().equals("error")) {
            throw new LinkButtonException("Pairing button not pressed");
        }
        username = erg.substring(11, 49);
        return erg.substring(11, 49);
    }

    // Es wird geschaut ob eine Authentifizierung bereits stattgefunden hat, also ob ein API-Key zurückgegeben wurde
    private void requireAuthentication() {
        if (this.username == null) {
            throw new IllegalStateException("Linking is required before interacting with the MIYOCube");
        }
    }

    // Gibt eine Liste mit allen der Bridge/Viracube bekannten Circuits aus und deren vollständige Konfiguration
    public List<Circuit> getCircuits() throws IOException, ApiException {
        requireAuthentication();
        Result result = http.post("http://" + ip + "/api/circuit/all?apiKey=" + username, "");
        if (parser.parse(result.getBody()).getAsJsonObject().get("status").getAsString().equals("error")) {
            throw new ApiException("Error bei Http-Request");
        }
        JsonObject data = parser.parse(result.getBody()).getAsJsonObject().get("params").getAsJsonObject();
        JsonObject c = parser.parse(data.toString()).getAsJsonObject().get("circuits").getAsJsonObject();
        ArrayList<Circuit> circuitList = new ArrayList<>();
        Set<Entry<String, JsonElement>> circuitSet = c.entrySet();
        for (Entry<String, JsonElement> entry : circuitSet) {
            // circuit mit id, name und params (Bordertop, Borderbottom und considerMower) wird automatisch erzeugt
            Circuit circuit = safeFromJson(entry.getValue().toString(), Circuit.class);
            // Weiteres Parsen um irrigation, wintermode, nextirrigationStart und nextirrigationStop zu setzen
            JsonObject statetypes = parser.parse(entry.getValue().toString()).getAsJsonObject().get("stateTypes")
                    .getAsJsonObject();

            // Neues Parsen ab hier Es werden irrigationMode, winterMode, irrigationNextStart bzw. End und externBlock
            // gesetzt
            Set<Entry<String, JsonElement>> statetypesSet = statetypes.entrySet();
            for (Entry<String, JsonElement> state : statetypesSet) {
                String type = parser.parse(state.getValue().toString()).getAsJsonObject().get("type").getAsString();
                if (type.equals("irrigation")) {
                    boolean irrigationMode = parser.parse(state.getValue().toString()).getAsJsonObject().get("value")
                            .getAsBoolean();
                    circuit.setIrrigation(irrigationMode);
                } else if (type.equals("winterMode")) {
                    boolean winter = parser.parse(state.getValue().toString()).getAsJsonObject().get("value")
                            .getAsBoolean();
                    circuit.setWintermode(winter);
                } else if (type.equals("irrigationNextStart")) {
                    long start = parser.parse(state.getValue().toString()).getAsJsonObject().get("value").getAsLong();
                    circuit.setIrrigationStart(new DateTime(start * 1000));
                } else if (type.equals("irrigationNextEnd")) {
                    long end = parser.parse(state.getValue().toString()).getAsJsonObject().get("value").getAsLong();
                    circuit.setIrrigationEnd(new DateTime(end * 1000));
                } else if (type.equals("externBlock")) {
                    boolean externBlock = parser.parse(state.getValue().toString()).getAsJsonObject().get("value")
                            .getAsBoolean();
                    circuit.setExternBlock(externBlock);
                }
            }

            if (!circuit.getSensor().equals("0")) {
                Result result2 = http.get(
                        "http://" + ip + "/api/device/status?apiKey=" + username + "&deviceId=" + circuit.getSensor());
                if (parser.parse(result2.getBody()).getAsJsonObject().get("status").getAsString().equals("error")) {
                    throw new ApiException("Error bei Http-Request Sensor");
                }
                // Parsen des http-Results
                JsonObject params = parser.parse(result2.getBody()).getAsJsonObject().get("params").getAsJsonObject();
                JsonObject device = parser.parse(params.toString()).getAsJsonObject().get("device").getAsJsonObject();
                JsonObject states = parser.parse(device.toString()).getAsJsonObject().get("stateTypes")
                        .getAsJsonObject();
                Set<Entry<String, JsonElement>> entries = states.entrySet();
                for (Entry<String, JsonElement> element : entries) {
                    String type = parser.parse(element.getValue().toString()).getAsJsonObject().get("type")
                            .getAsString();
                    if (type.equals("temperature")) {
                        circuit.setTemperature(parser.parse(element.getValue().toString()).getAsJsonObject()
                                .get("value").getAsFloat());
                    } else if (type.equals("moisture")) {
                        circuit.setMoisture(parser.parse(element.getValue().toString()).getAsJsonObject().get("value")
                                .getAsFloat());
                    } else if (type.equals("brightness")) {
                        circuit.setBrightness(parser.parse(element.getValue().toString()).getAsJsonObject().get("value")
                                .getAsFloat());
                    }
                }
            }

            // Setzen der OpenhabId und Hinzufügen zur Liste
            circuit.setOpenhabId(circuit.getId());
            circuitList.add(circuit);
        }
        return circuitList;
    }

    // Starten bzw. Stoppen der Bewässerung des circuits
    public void setIrrigation(Circuit circuit, StateUpdate stateUpdate) throws IOException, ApiException {
        requireAuthentication();
        String mode = (String) (stateUpdate.commands.get(0).value);
        Result result = http.post("http://" + ip + "/api/circuit/irrigation?apiKey=" + username + "&mode=" + mode
                + "&circuitId=" + circuit.getId(), "");
        if (parser.parse(result.getBody()).getAsJsonObject().get("status").getAsString().equals("error")) {
            throw new IrrigationException("Irrigation mode could not turned on or off"); // NEU
        }

    }

    public void setWinter(Circuit circuit, StateUpdate stateUpdate) throws IOException, ApiException {
        requireAuthentication();
        String winter = (String) (stateUpdate.commands.get(0).value);
        Result result = http.post("http://" + ip + "/api/circuit/winter?apiKey=" + username + "&winter=" + winter
                + "&circuitId=" + circuit.getId(), "");
        if (parser.parse(result.getBody()).getAsJsonObject().get("status").getAsString().equals("error")) {
            throw new ApiException("Wintermode couldnt been set");
        }

    }

    private <T> T safeFromJson(String json, Class<T> classOfT) throws ApiException {
        try {
            return gson.fromJson(json, classOfT);
        } catch (JsonParseException e) {
            throw new ApiException("API returned unexpected result: " + e.getMessage());
        }
    }

}
