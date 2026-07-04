package com.runningcrew.user.adapter.out.token;

import com.runningcrew.user.application.port.out.IssuedTokens;
import com.runningcrew.user.application.port.out.TokenExpiredException;
import com.runningcrew.user.application.port.out.TokenInvalidException;
import com.runningcrew.user.application.port.out.TokenProvider;
import com.runningcrew.user.application.port.out.TokenRefreshInvalidException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 자체 JWT(HS256) 발급·검증 어댑터(계약 auth-api.md 확정 스펙).
 *
 * <p>시크릿은 env {@code JWT_SECRET}(≥256bit). 클레임: sub(내부 user id)·typ·iat·exp·jti(refresh만).
 * kakao_id·닉네임 등 개인정보 클레임은 넣지 않는다(봉인 원칙).
 */
@Component
public class JwtTokenProvider implements TokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final Clock clock;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-ttl-seconds}") long accessTtlSeconds,
                            @Value("${jwt.refresh-ttl-seconds}") long refreshTtlSeconds,
                            Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // fail-fast: 기본 프로필엔 JWT_SECRET 기본값 없음 → 미설정 시 부팅 실패
            throw new IllegalStateException("JWT_SECRET이 설정되지 않았거나 256bit 미만입니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.clock = clock;
    }

    @Override
    public IssuedTokens issue(long userId) {
        Instant now = clock.instant();
        String access = build(userId, TYPE_ACCESS, now, accessTtlSeconds, null);
        String refresh = build(userId, TYPE_REFRESH, now, refreshTtlSeconds, UUID.randomUUID().toString());
        return new IssuedTokens(access, refresh, accessTtlSeconds);
    }

    private String build(long userId, String type, Instant now, long ttlSeconds, String jti) {
        var builder = Jwts.builder()
                .subject(Long.toString(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    @Override
    public long verifyAccess(String token) {
        Claims claims = parse(token, TokenInvalidException::new, TokenExpiredException::new);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new TokenInvalidException("access 토큰이 아닙니다.");
        }
        return subject(claims, TokenInvalidException::new);
    }

    @Override
    public long verifyRefresh(String token) {
        Claims claims = parse(token,
                TokenRefreshInvalidException::new, TokenRefreshInvalidException::new);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new TokenRefreshInvalidException("refresh 토큰이 아닙니다.");
        }
        return subject(claims, TokenRefreshInvalidException::new);
    }

    private Claims parse(String token,
                         java.util.function.Function<String, RuntimeException> onInvalid,
                         java.util.function.Function<String, RuntimeException> onExpired) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw onExpired.apply("토큰이 만료되었습니다.");
        } catch (JwtException | IllegalArgumentException e) {
            throw onInvalid.apply("토큰 검증에 실패했습니다.");
        }
    }

    private long subject(Claims claims, java.util.function.Function<String, RuntimeException> onInvalid) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw onInvalid.apply("잘못된 subject입니다.");
        }
    }
}
