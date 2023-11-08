/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.smartcocoon.internal.api;

//import static org.openhab.binding.smartcocoon.internal.SmartCocoonBindingConstants.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.openhab.binding.smartcocoon.internal.SmartCocoonException;
import org.openhab.binding.smartcocoon.internal.SmartCocoonBridgeConfiguration;
import org.openhab.binding.smartcocoon.internal.dto.FanInfoResultDTO;

class FansResult {
  int total;
  FanInfoResultDTO[] fans;
}

/**
 * The {@link SmartCocoonAPI} handles the REST API calls
 *
 * @author Mike Fedotov - Initial contribution
 */
@NonNullByDefault
public class SmartCocoonAPI {
    private static final String BASE_URL = "https://app.mysmartcocoon.com/api";
    private static final String USER_AGENT = "SmartCocoon/1 CFNetwork/1312 Darwin/21.0.0";

    private static final String AUTH_URL = BASE_URL + "/auth/sign_in";
    private static final String FANS_URL = BASE_URL + "/fans";

    private static final String JSON_CONTENT_TYPE = "application/json";
    //private static final int MAX_RETRIES = 3;

    private final Logger logger = LoggerFactory.getLogger(SmartCocoonAPI.class);
    private final Gson gson;
    private final HttpClient httpClient;
    private final String username;
    private final String password;
    private @Nullable String accessToken = null;
    private @Nullable String uid = null;
    private @Nullable String client = null;
    private int tokenExpiry = 0;

    public SmartCocoonAPI(SmartCocoonBridgeConfiguration configuration, HttpClient httpClient, Gson gson) {
        this.gson = gson;
	this.username = configuration.username;
	this.password = configuration.password;
        this.httpClient = httpClient;
    }

    public boolean refresh(Map<String, FanInfoResultDTO> smartCocoonThings) {
        try {
            if (Instant.now().isAfter(Instant.ofEpochSecond(this.tokenExpiry))) {
                // Login again since token is expired
                this.login();
            }
            // Get all appliances
            String json = this.getFans();
            FansResult r;
            r = gson.fromJson(json, FansResult.class);
            FanInfoResultDTO[] dtos = r.fans;
            if (dtos != null) {
                for (FanInfoResultDTO dto : dtos) {
                    String deviceId = dto.fan_id;
		    if (deviceId != null){
			smartCocoonThings.put(deviceId, dto);
		    }
                }
                return true;
            }
        } catch (JsonSyntaxException | SmartCocoonException e) {
            logger.warn("Failed to refresh! {}", e.getMessage());
        }
        return false;
    }


   public String getFanInfo(String fanId) throws SmartCocoonException {
        if (fanId == null || fanId.isEmpty()) {
              throw new SmartCocoonException ("Internal error: getFanInfo invalid parameter");
        }
        try {

            if (client == null || accessToken == null || uid == null) {
               tryLogin();
            }

            if (client == null || accessToken == null || uid == null) {
              throw new SmartCocoonException ("Internal error: Expected authentication information not availabl");
            }

            Request request = createRequest(FANS_URL + "/" + fanId, HttpMethod.GET);
            request.header("client", client);
            request.header("access-token", accessToken);
            request.header("uid", uid);

            logger.debug("HTTP GET Request {}.", request.toString());
            ContentResponse httpResponse = request.send();
            if (httpResponse.getStatus() != HttpStatus.OK_200) {
                throw new SmartCocoonException("Failed to get status for fan " + fanId + ", status: " + httpResponse.getStatus() + ", response: " + httpResponse.getContentAsString());
            }
            return httpResponse.getContentAsString();

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new SmartCocoonException(e);
        }
        
   }

   public String getFans() throws SmartCocoonException {
        try {

            if (client == null || accessToken == null || uid == null) {
               tryLogin();
            }

            if (client == null || accessToken == null || uid == null) {
              throw new SmartCocoonException ("Internal error: Expected authentication information not availabl");
            }

            Request request = createRequest(FANS_URL, HttpMethod.GET);
            request.header("client", client);
            request.header("access-token", accessToken);
            request.header("uid", uid);

            logger.debug("HTTP GET Request {}.", request.toString());
            ContentResponse httpResponse = request.send();
            if (httpResponse.getStatus() != HttpStatus.OK_200) {
                throw new SmartCocoonException("Failed to get fans, status: " + httpResponse.getStatus() + ", response: " + httpResponse.getContentAsString());
            }
            return httpResponse.getContentAsString();

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new SmartCocoonException(e);
        }
        
   }

    private Request createRequest(String uri, HttpMethod httpMethod) {
        Request request = httpClient.newRequest(uri).method(httpMethod);

        request.header(HttpHeader.ACCEPT, JSON_CONTENT_TYPE);
        request.header(HttpHeader.CONTENT_TYPE, JSON_CONTENT_TYPE);
        request.header(HttpHeader.USER_AGENT, USER_AGENT);

        logger.debug("HTTP POST Request {}.", request.toString());

        return request;
    }

    private void login() throws SmartCocoonException {
        try {
            client = null;
            accessToken = null;
            uid = null;
            tokenExpiry = 0;

            String json = "{\"email\": \"" + this.username + "\", \"password\": \"" + this.password
                    + "\"}";

            // Fetch ClientToken
            Request request = createRequest(AUTH_URL, HttpMethod.POST);
            request.content(new StringContentProvider(json), JSON_CONTENT_TYPE);

            logger.debug("HTTP POST Request {}.", request.toString());

            ContentResponse httpResponse = request.send();
            if (httpResponse.getStatus() != HttpStatus.OK_200) {
                throw new SmartCocoonException("Failed to authenticate: " + httpResponse.getContentAsString());
            }
            // All required auth data comes in headers:
            // client, access-token, expiry, uid
            client = httpResponse.getHeaders().get("client");
            accessToken = httpResponse.getHeaders().get("access-token");
            uid = httpResponse.getHeaders().get("uid");

            // Those three headers are expected to be present in the response, cannot proceed if missing
            if (client == null || accessToken == null || uid == null) {
              throw new SmartCocoonException ("Expected headers not present in auth response");
            }

            // We generally can do with the missing/invalid expiry header
            tokenExpiry = 0;
            try {
              tokenExpiry = Integer.parseInt(httpResponse.getHeaders().get("expiry"));
            } catch (Exception ignore) {}

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new SmartCocoonException(e);
        }
    }

   private void tryLogin()  throws SmartCocoonException {
      // Plan to implement checks to avoid excessive login attempts...
      login();
   }


  public void setFanMode (String fanId, String mode) throws SmartCocoonException {
        if (fanId == null || fanId.isEmpty()) {
              throw new SmartCocoonException ("Internal error: getFanInfo invalid parameter");
        }
        if ( mode == null || ( !(mode.equals("always_on") || mode.equals("always_off")) ) ) {
              throw new SmartCocoonException ("Internal error: setFanMode invalid parameter");
        }

        try {

            if (client == null || accessToken == null || uid == null) {
               tryLogin();
            }

            if (client == null || accessToken == null || uid == null) {
              throw new SmartCocoonException ("Internal error: Expected authentication information not availabl");
            }

            Request request = createRequest(FANS_URL + "/" + fanId, HttpMethod.PUT);
            request.header("client", client);
            request.header("access-token", accessToken);
            request.header("uid", uid);

            //request.content(new StringContentProvider("{\"mode\": \"" + mode + "\"}", JSON_CONTENT_TYPE));
            request.content(new StringContentProvider("{\"mode\": \"" + mode + "\"}"));

            logger.debug("HTTP PUT Request {}.", request.toString());
            ContentResponse httpResponse = request.send();
            if (httpResponse.getStatus() != HttpStatus.OK_200) {
                throw new SmartCocoonException("Failed to set mode " + mode + " for fan " + fanId + 
                            ", status: " + httpResponse.getStatus() + ", response: " + httpResponse.getContentAsString());
            }


            //return;

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new SmartCocoonException(e);
        }
  }

  public void setFanSpeed(String fanId, int speed) throws SmartCocoonException {
        if (fanId == null || fanId.isEmpty()) {
              throw new SmartCocoonException ("Internal error: getFanInfo invalid parameter");
        }
        if ( speed < 0 || speed > 100) {
              throw new SmartCocoonException ("Internal error: setFanSpeed invalid speed");
        }

        try {

            if (client == null || accessToken == null || uid == null) {
               tryLogin();
            }

            if (client == null || accessToken == null || uid == null) {
              throw new SmartCocoonException ("Internal error: Expected authentication information not availabl");
            }

            Request request = createRequest(FANS_URL + "/" + fanId, HttpMethod.PUT);
            request.header("client", client);
            request.header("access-token", accessToken);
            request.header("uid", uid);

            //request.content(new StringContentProvider("{\"mode\": \"" + mode + "\"}", JSON_CONTENT_TYPE));
            request.content(new StringContentProvider("{\"speed_level\": \"" + speed/8 + "\"}"));

            logger.debug("HTTP PUT Request {}.", request.toString());
            ContentResponse httpResponse = request.send();
            if (httpResponse.getStatus() != HttpStatus.OK_200) {
                throw new SmartCocoonException("Failed to set speed " + speed + " for fan " + fanId + 
                            ", status: " + httpResponse.getStatus() + ", response: " + httpResponse.getContentAsString());
            }


            //return;

        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new SmartCocoonException(e);
        }
  }

}
