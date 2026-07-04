package com.runningcrew.user.adapter.in.web.dto;

import com.runningcrew.common.appversion.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/v1/users/me/device-token 요청(계약 user-api.md §4). */
public record DeviceTokenRequest(
        @NotBlank @Size(max = 255) String fcmToken,
        @NotNull Platform platform) {
}
