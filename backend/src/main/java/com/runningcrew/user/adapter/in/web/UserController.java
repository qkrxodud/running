package com.runningcrew.user.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.user.adapter.in.web.dto.DeviceTokenRequest;
import com.runningcrew.user.adapter.in.web.dto.NicknameRequest;
import com.runningcrew.user.adapter.in.web.dto.UserResponse;
import com.runningcrew.user.application.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 API(계약 user-api.md) — 전부 인증 필요, 자원은 본인(/users/me).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserAccountService userAccountService;

    public UserController(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    @GetMapping
    public UserResponse me(@AuthUserId Long userId) {
        return UserResponse.from(userAccountService.getMe(userId));
    }

    @PutMapping("/nickname")
    public UserResponse changeNickname(@AuthUserId Long userId,
                                       @Valid @RequestBody NicknameRequest request) {
        return UserResponse.from(userAccountService.changeNickname(userId, request.nickname()));
    }

    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthUserId Long userId) {
        userAccountService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/device-token")
    public ResponseEntity<Void> registerDeviceToken(@AuthUserId Long userId,
                                                     @Valid @RequestBody DeviceTokenRequest request) {
        userAccountService.registerDeviceToken(userId, request.fcmToken(), request.platform());
        return ResponseEntity.noContent().build();
    }
}
