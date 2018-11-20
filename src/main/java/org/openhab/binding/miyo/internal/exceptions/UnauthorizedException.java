/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.miyo.internal.exceptions;

/**
 *
 * {@link UnauthorizedException} ist Exception, dass man unautorisiert ist
 *
 *
 *
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException() {
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
