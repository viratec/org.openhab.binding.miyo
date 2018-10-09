/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.miyo.internal;

import java.lang.reflect.Type;
import java.util.Map;

import org.joda.time.DateTime;

import com.google.gson.reflect.TypeToken;

/**
 *
 *
 *
 */
public class Circuit {
    private boolean wintermode;
    private boolean irrigation;
    private String id; // Entspricht der OriginalId aus dem Cube, die die geschweiften Klammern enthält
    private String openhabId; // Entspricht der Id, aber ohne geschweifte Klammern
    private String name;
    private State params; // Speichert weitere Informationen wie Bordertop und Borderbottom die im JSON eine Ebenen
                          // weiter unten in params liegen
    private DateTime nextIrrigationStart;
    private DateTime nextIrrigationEnd;
    private String sensor;
    private float moisture;
    private float brightness;
    private float temperature;
    private boolean externBlock;

    public final static Type gsonType = new TypeToken<Map<String, Circuit>>() {
    }.getType();

    Circuit() {
    };

    public State getState() {
        return params;
    }

    public String getName() {
        return name;
    }

    void setId(String id) {
        this.id = id;
    }

    public String getOpenhabId() {
        return openhabId;
    }

    void setOpenhabId(String id) {
        this.openhabId = id.substring(1, id.length() - 2);
    }

    public String getId() {
        return id;
    }

    void setIrrigation(boolean irrigation) {
        this.irrigation = irrigation;
    }

    public boolean getIrrigation() {
        return irrigation;
    }

    void setWintermode(boolean wintermode) {
        this.wintermode = wintermode;
    }

    public boolean getWintermode() {
        return wintermode;
    }

    void setIrrigationStart(DateTime nextIrrigationStart) {
        this.nextIrrigationStart = nextIrrigationStart;
    }

    public DateTime getNextIrrigationStart() {
        return nextIrrigationStart;
    }

    void setIrrigationEnd(DateTime nextIrrigationEnd) {
        this.nextIrrigationEnd = nextIrrigationEnd;
    }

    public DateTime getNextIrrigationEnd() {
        return nextIrrigationEnd;
    }

    public String getSensor() {
        return sensor;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public float getMoisture() {
        return moisture;
    }

    public void setMoisture(float moisture) {
        this.moisture = moisture;
    }

    public boolean getExternBlock() {
        return externBlock;
    }

    public void setExternBlock(boolean externBlock) {
        this.externBlock = externBlock;
    }
}