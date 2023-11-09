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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.smartcocoon.internal.SmartCocoonBridgeConfiguration;
import org.openhab.binding.smartcocoon.internal.api.SmartCocoonAPI;
// import org.openhab.binding.smartcocoon.internal.discovery.SmartCocoonDiscoveryService;
import org.openhab.binding.smartcocoon.internal.dto.FanInfoResultDTO; // TODO - SmartCocoonFansInfo
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;

import com.google.gson.Gson;

/**
 * The {@link SmartCocoonBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jan Gustafsson - Initial contribution
 */
@NonNullByDefault
public class SmartCocoonBridgeHandler extends BaseBridgeHandler {


    // public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_BRIDGE);

    private int refreshInterval = 300;

    private final Gson gson;
    private final HttpClient httpClient;
    private final Map<String, FanInfoResultDTO> smartCocoonThings = new ConcurrentHashMap<>();

    @Nullable SmartCocoonAPI api;
    private @Nullable ScheduledFuture<?> refreshJob;

    public SmartCocoonBridgeHandler(Bridge bridge, HttpClient httpClient, Gson gson) {
        super(bridge);
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public void initialize() {
        SmartCocoonBridgeConfiguration config = getConfigAs(SmartCocoonBridgeConfiguration.class);
        this.refreshInterval = config.refreshInterval;

	// TODO - annotated NonNullByDefault
        if (config.username == null || config.password == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Configuration of username, password is mandatory");
        } else if (this.refreshInterval < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Refresh time cannot be negative!");
        } else {
            try {
                this.api = new SmartCocoonAPI(config, httpClient, gson);
                scheduler.execute(() -> {
                    this.updateStatus(ThingStatus.UNKNOWN);
                    this.startAutomaticRefresh();
                });
            } catch (RuntimeException e) {
                this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    public Map<String, FanInfoResultDTO> getSmartCocoonThings() {
        return this.smartCocoonThings;
    }

    /*
    @Override
    // TODO - create SmartCocoonDiscoveryService
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(SmartCocoonDiscoveryService.class);
    }
    */

    @Override
    public void dispose() {
        this.stopAutomaticRefresh();
    }

    public @Nullable SmartCocoonAPI getSmartCocoonAPI() {
        return this.api;
    }

    // TODO - make explicit Runnable
    private boolean refreshAndUpdateStatus() {
        if (api != null) {
            if (api.refresh(this.smartCocoonThings)) {
                this.getThing().getThings().stream().forEach(thing -> {
                    SmartCocoonHandler handler = (SmartCocoonHandler) thing.getHandler();
                    if (handler != null) {
                        handler.update();
                    }
                });
                this.updateStatus(ThingStatus.ONLINE);
                return true;
            } else {
                this.updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        }
        return false;
    }

    private void startAutomaticRefresh() {
        ScheduledFuture<?> refreshJob = this.refreshJob;
        if (refreshJob == null || refreshJob.isCancelled()) {
            refreshJob = scheduler.scheduleWithFixedDelay(this::refreshAndUpdateStatus, 0, this.refreshInterval,
                    TimeUnit.SECONDS);
        }
    }

    private void stopAutomaticRefresh() {
        ScheduledFuture<?> refreshJob = this.refreshJob;
        if (refreshJob != null) {
            refreshJob.cancel(true);
            this.refreshJob = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.schedule(this::refreshAndUpdateStatus, 0, TimeUnit.SECONDS); // TODO - wait 1 sec?
        }
    }
}
