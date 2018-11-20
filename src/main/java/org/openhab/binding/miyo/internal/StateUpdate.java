/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.miyo.internal;

import java.util.ArrayList;

/**
 *
 *
 *
 */
public class StateUpdate {
    public ArrayList<Command> commands = new ArrayList<>();

    String toJson() {
        StringBuilder json = new StringBuilder("{");

        for (int i = 0; i < commands.size(); i++) {
            json.append(commands.get(i).toJson());
            if (i < commands.size() - 1) {
                json.append(",");
            }
        }

        json.append("}");

        return json.toString();
    }

    public StateUpdate turnOn() {
        return setOn(true);
    }

    public StateUpdate turnOff() {
        return setOn(false);
    }

    // Irrigation anschalten bzw. ausschalten
    public StateUpdate setOn(boolean on) {
        if (on) {
            commands.add(new Command("mode", "start"));
        } else {
            commands.add(new Command("mode", "stop"));
        }
        return this;
    }

    // Wintermodus an bzw. ausschalten
    public StateUpdate setWinter(boolean on) {
        if (on) {
            commands.add(new Command("winter", "true"));
        } else {
            commands.add(new Command("winter", "false"));
        }
        return this;
    }

}
