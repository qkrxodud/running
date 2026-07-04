package com.runningcrew.common.appversion;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.common.error.GlobalExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/app-version 계약(app-version.md) 슬라이스 테스트.
 *
 * <p>웹 계층만 로드(@WebMvcTest) — DB/Flyway 불필요. 검증 대상:
 * 응답 shape(snake_case), 200/400/404 매핑. 서비스는 Mockito로 대체.
 */
@WebMvcTest(AppVersionController.class)
@Import(GlobalExceptionHandler.class)
class AppVersionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AppVersionService service;

    @Test
    void returnsMinVersion_snakeCaseAndUtcIso8601() throws Exception {
        when(service.getMinVersion(eq(Platform.ANDROID)))
                .thenReturn(new AppVersionResponse(
                        Platform.ANDROID, "1.2.0", Instant.parse("2026-07-01T00:00:00Z")));

        mockMvc.perform(get("/api/v1/app-version").param("platform", "ANDROID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("ANDROID"))
                .andExpect(jsonPath("$.min_version").value("1.2.0"))
                .andExpect(jsonPath("$.updated_at").value("2026-07-01T00:00:00Z"));
    }

    @Test
    void missingPlatform_returns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/app-version"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownPlatform_returns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/app-version").param("platform", "WINDOWS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void noRecord_returns404NotFound() throws Exception {
        when(service.getMinVersion(eq(Platform.IOS)))
                .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "해당 플랫폼의 최소 버전 정보가 없습니다."));

        mockMvc.perform(get("/api/v1/app-version").param("platform", "IOS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
