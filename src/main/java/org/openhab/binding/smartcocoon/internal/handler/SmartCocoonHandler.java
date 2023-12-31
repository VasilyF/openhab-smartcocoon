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
package org.openhab.binding.smartcocoon.internal.handler;

import static org.openhab.binding.smartcocoon.internal.SmartCocoonBindingConstants.*;

import java.lang.String;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

import javax.measure.quantity.Dimensionless;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import org.openhab.binding.smartcocoon.internal.SmartCocoonConfiguration;
import org.openhab.binding.smartcocoon.internal.dto.FanInfoResultDTO;
import org.openhab.binding.smartcocoon.internal.api.SmartCocoonAPI;
import org.openhab.binding.smartcocoon.internal.SmartCocoonException;

import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusDetail.OfflineStatus;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import org.eclipse.jetty.client.HttpClient;


/**
 * The {@link SmartCocoonHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Mike Fedotov - Initial contribution
 */
@NonNullByDefault
public class SmartCocoonHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SmartCocoonHandler.class);

    private SmartCocoonConfiguration config = new SmartCocoonConfiguration();


    private @Nullable ScheduledFuture<?> pollingJob;
    private final Gson gson = new Gson();
    private final HttpClient httpClient;

    public SmartCocoonHandler(Thing thing, HttpClient httpClient, Gson gson) {
        super(thing);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        this.config = getConfigAs(SmartCocoonConfiguration.class);
	updateStatus(ThingStatus.UNKNOWN);

	scheduler.execute(() -> {
            update();
	    /*
	     * TODO - update properties
            Map<String, String> properties = refreshProperties();
            updateProperties(properties);
	    */
        });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        this.logger.debug("Command received: {}", command);

        if (command instanceof RefreshType) {
                Bridge bridge = this.getBridge();
                if (bridge != null) {
                    BridgeHandler bridgeHandler = bridge.getHandler();
                    if (bridgeHandler != null) {
                        bridgeHandler.handleCommand(channelUID, command);
                    }
                }
            } 
        else {
            String deviceId = this.config.deviceId;
            // TODO - do we need to check?
            if(deviceId == null){
                return;
            }
            SmartCocoonAPI api = this.getSmartCocoonAPI();
            if (api == null) {
		logger.error("api is null");
		return;
            }

	    //TODO - cache? 
	    FanInfoResultDTO dto = this.getFanInfoResultDTO();
		    if (dto == null){
		logger.error("dto is null");
		return;
	    }

	    String fanId = dto.id;
	    if (fanId == null){
		logger.error("id is null");
		return;
	    }

            try{
                if (CHANNEL_FAN_SWITCH.equals(channelUID.getId())){
                   if (command == OnOffType.ON) {
                     api.setFanMode(fanId, "always_on");
                    } 
                   else if (command == OnOffType.OFF) {
                      api.setFanMode(fanId, "always_off");
                    }
                   else{
                        logger.error("Error issuing command {} to fan {}", command, channelUID.getId());
                    }
                }
                else if (CHANNEL_FAN_SPEED.equals(channelUID.getId())){
                    api.setFanSpeed(fanId, Integer.parseInt(command.toString()));
                }
            }
            catch(SmartCocoonException ex){
                this.logger.error("error issuing command {} to fan {}", command, channelUID.getId());
                this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, ex.getMessage());
            }
        }

    }


    public void update() {
        FanInfoResultDTO dto = this.getFanInfoResultDTO();
        if (dto != null) {
            this.update(dto);
        } else {
            Map<String, FanInfoResultDTO> t = this.getSmartCocoonThings();
            if (t != null) {
               if (t.size() > 0) {
                 this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid device ID " + this.config.deviceId + 
                                  ". Valid device IDs: " + String.join(", ", t.keySet()) + ".");
               } else {
                 this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid device ID " + this.config.deviceId + 
                                  ". No devices returned by API.");
               }
            }
            else {
              logger.warn("list of fans is null");
              this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NOT_YET_READY, "Did not get the list of fans from API");
            }
        }
    }

    private @Nullable SmartCocoonAPI getSmartCocoonAPI() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            SmartCocoonBridgeHandler handler = (SmartCocoonBridgeHandler) bridge.getHandler();
            if (handler != null) {
                return handler.api;
            }
        }
        return null;
    }


    private @Nullable Map<String, FanInfoResultDTO> getSmartCocoonThings() {
        Bridge bridge = this.getBridge();
        if (bridge != null) {
            SmartCocoonBridgeHandler bridgeHandler = (SmartCocoonBridgeHandler) bridge.getHandler();
            if (bridgeHandler != null) {
               return bridgeHandler.getSmartCocoonThings();
            }
        }
        return null;
    }

    private @Nullable FanInfoResultDTO getFanInfoResultDTO() {
        var t = this.getSmartCocoonThings();
        if (t != null) {
           return t.get(this.config.deviceId);
        }
        return null;
    }

    private void update(@Nullable FanInfoResultDTO dto) {
        if (dto != null) {
            // Update all channels from the updated data
            getThing().getChannels().stream().map(Channel::getUID).filter(channelUID -> isLinked(channelUID))
                    .forEach(channelUID -> {
                        State state = getValue(channelUID.getId(), dto);
                        this.logger.trace("Channel: {}, State: {}", channelUID, state);
                        this.updateState(channelUID, state);
                    });
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private State getValue(String channelId, FanInfoResultDTO dto) {
        switch (channelId) {
            case CHANNEL_FAN_SWITCH:
                return OnOffType.from(dto.fan_on);
            case CHANNEL_FAN_SPEED:
                return new StringType(Integer.toString(dto.speed_level*8 + dto.speed_level/3));
        }
        return UnDefType.UNDEF;
    }
}
