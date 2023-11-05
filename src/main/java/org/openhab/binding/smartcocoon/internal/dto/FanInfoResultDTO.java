package org.openhab.binding.smartcocoon.internal.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class FanInfoResultDTO {
	public @Nullable String id;
	public @Nullable String fan_id;
	public @Nullable String mode;
	public int speed_level;
	public boolean fan_on;
}

