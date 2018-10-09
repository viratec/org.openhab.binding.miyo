/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.miyo.internal;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.io.IOUtils;

/**
 *
 * {@link HttpClient} für Http Requests an den Cube
 *
 *
 *
 */

public class HttpClient {
    private int timeout = 1000;

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public Result get(String address) throws IOException {
        return doNetwork(address, "GET", "");
    }

    public Result put(String address, String body) throws IOException {
        return doNetwork(address, "PUT", body);
    }

    public Result post(String address, String body) throws IOException {
        return doNetwork(address, "POST", body);
    }

    public Result delete(String address) throws IOException {
        return doNetwork(address, "DELETE", "");
    }

    protected Result doNetwork(String address, String requestMethod, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setRequestMethod(requestMethod);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            if (body != null && !body.equals("")) {
                connection.setDoOutput(true);
                OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
                out.write(body);
                out.close();
            }
            InputStream in = new BufferedInputStream(connection.getInputStream());
            String output = IOUtils.toString(in, "UTF-8");
            return new Result(output, connection.getResponseCode());
        } finally {
            connection.disconnect();
        }
    }

    public static class Result {
        private final String body;
        private final int responseCode;

        public Result(String body, int responseCode) {
            this.body = body;
            this.responseCode = responseCode;
        }

        public String getBody() {
            return body;
        }

        public int getResponseCode() {
            return responseCode;
        }
    }
}
