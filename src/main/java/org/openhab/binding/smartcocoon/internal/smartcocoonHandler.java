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
package org.openhab.binding.smartcocoon.internal;

import static org.openhab.binding.smartcocoon.internal.smartcocoonBindingConstants.*;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

import javax.measure.quantity.Dimensionless;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
//import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.smartcocoon.internal.api.SmartcocoonAPI;
//import org.openhab.binding.smartcocoon.internal.SmartcocoonException;
import org.openhab.core.library.types.OnOffType;


/**
 * The {@link smartcocoonHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mike Fedotov - Initial contribution
 */
@NonNullByDefault
public class smartcocoonHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(smartcocoonHandler.class);

    //private @Nullable smartcocoonConfiguration config;
    private @Nullable SmartcocoonAPI api;
    private @Nullable ScheduledFuture<?> pollingJob;
    //private final SmartcocoonAPI api;
    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private @Nullable String fanId;
    private long refreshInterval;

    private class FanInfoResult {
        @Nullable String id;
        @Nullable String fan_id;
        @Nullable String mode;
        int speed_level;
        boolean fan_on;
    }

    private class FansResult {
        int total;
        @Nullable FanInfoResult @Nullable [] fans;
    }

    public smartcocoonHandler(Thing thing, HttpClient httpClient, Gson gson) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String fanId = this.fanId;
        if(fanId == null){
            return;
        }

        try{
            if(CHANNEL_FAN_SWITCH.equals(channelUID.getId())) {
                 if (command instanceof RefreshType) {
                     // TODO: handle data refresh
                 }

            try {
                SmartcocoonAPI api = this.api;
                if (api != null) {
                   if (command == OnOffType.ON) {
                     api.setFanMode(fanId, "always_on");
                    } else if (command == OnOffType.OFF) {
                      api.setFanMode(fanId, "always_off");
                   }
                } else {
                   throw new SmartcocoonException("Internal Error: api is null");
                }
            } catch (SmartcocoonException e) {
                logger.error("Error issuing command {} to fan {}", command, channelUID.getId());
                throw e;
            }

            // TODO: handle command

            // Note: if communication with thing fails for some reason,
            // indicate that by setting the status with detail information:
            // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
            // "Could not control device at IP address x.x.x.x");
        }
	else if (CHANNEL_FAN_SPEED.equals(channelUID.getId())){
		api.setFanSpeed(fanId, Integer.parseInt(command.toString()));

	}

      } catch(SmartcocoonException ex) {
        // catch exceptions and handle it in your binding
        //statusUpdated(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, ex.getMessage());
        updateStatus(ThingStatus.ONLINE);

      }
    }

    private void onUpdate() {
        logger.debug("Polling Interval Set: {}", refreshInterval);
        if (refreshInterval > 0) {
            ScheduledFuture<?> pollingJob = this.pollingJob;
            if (pollingJob == null || pollingJob.isCancelled()) {
                this.pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, 0, refreshInterval,
                        TimeUnit.SECONDS);
            }
        }
    }


    @Override
    public void initialize() {
        smartcocoonConfiguration config = getConfigAs(smartcocoonConfiguration.class);
        refreshInterval = config.refreshInterval;
        String fanIdExt = config.fanIdExt;


        api = new SmartcocoonAPI(config, httpClient, gson);
        try{
            FansResult res = gson.fromJson(api.getFans(), FansResult.class);
            this.fanId = null;
            for(FanInfoResult fan : res.fans){
                if(fanIdExt.equals(fan.fan_id)){
                    this.fanId = fan.id;
                }
            }
        } catch (Exception e){
            logger.warn("An exception occurred while getting fanId " +  e.getMessage());

        }
        logger.warn("fanId " +  this.fanId);


        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly, i.e. any network access must be done in
        // the background initialization below.
        // Also, before leaving this method a thing status from one of ONLINE, OFFLINE or UNKNOWN must be set. This
        // might already be the real thing status in case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        if(this.fanId == null){
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        // Example for background initialization:
        scheduler.execute(() -> {
            boolean thingReachable = true; // <background task with long running initialization here>
            // when done do:
            if (thingReachable) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        });

        // These logging types should be primarily used by bindings
        // logger.trace("Example trace message");
        // logger.debug("Example debug message");
        // logger.warn("Example warn message");
        //
        // Logging to INFO should be avoided normally.
        // See https://www.openhab.org/docs/developer/guidelines.html#f-logging

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
	
	onUpdate();
    }


    private Runnable pollingRunnable = () -> {
        try {
              String fanId = this.fanId;
              if(fanId == null){
                  return;
              }
				  FanInfoResult res = gson.fromJson(api.getFanInfo(fanId), FanInfoResult.class);

              StringType speedState = new StringType("" + (res.speed_level*8 + res.speed_level/3));
              updateState(CHANNEL_FAN_SPEED, speedState);

              if(res.fan_on){
                    updateState(CHANNEL_FAN_SWITCH, OnOffType.ON);
              } 
              else {
                    updateState(CHANNEL_FAN_SWITCH, OnOffType.OFF);
              }
       } catch (Exception e) {
            logger.warn("An exception occurred while polling " + e.getMessage());
       }
    };

}
