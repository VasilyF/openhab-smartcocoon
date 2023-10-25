package org.openhab.binding.smartcocoon.internal.dto;

@NonNullByDefault
public class FanInfoResultDTO {
	private @Nullable String id;
	private @Nullable String fan_id;
	private @Nullable String mode;
	private int speed_level;
	private boolean fan_on;
}

