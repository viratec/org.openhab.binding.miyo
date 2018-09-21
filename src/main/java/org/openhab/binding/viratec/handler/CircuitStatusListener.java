/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.viratec.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.viratec.internal.Circuit;
import org.openhab.binding.viratec.internal.ViraCube;

/**
 *
 *
 *
 */
@NonNullByDefault
public interface CircuitStatusListener {

    void onCircuitStateChanged(@Nullable ViraCube viracube, Circuit circuit);

    void onCircuitRemoved(@Nullable ViraCube viraCube, Circuit circuit);

    void onCircuitAdded(@Nullable ViraCube viraCube, Circuit circuit);

}
