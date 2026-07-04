package com.runningcrew.user.adapter.out.kakao;

import com.runningcrew.user.application.port.out.KakaoTokenInvalidException;
import com.runningcrew.user.application.port.out.KakaoTokenVerifier;
import com.runningcrew.user.domain.KakaoAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 카카오 토큰 검증 스텁(계약 auth-api.md §4) — <b>local/dev 프로필 전용</b>.
 *
 * <p>{@code stub:{fake_kakao_id}} 형식만 수용 → 해당 KakaoAccount 반환(같은 fake id = 같은 User).
 * 그 외 전부 검증 실패. prod/프로필 미지정에서는 이 빈이 없어 {@code KakaoTokenVerifier} 주입 실패로
 * 부팅이 깨진다(fail-fast) — 스텁이 운영에 새는 사고를 구조로 차단한다.
 *
 * <p>실 카카오 어댑터 배선 지점: 같은 out 패키지에 {@code @Profile("prod")} 어댑터 추가(M0 앱 키 확보 후).
 * 그때 이 파일과 §4 계약절만 제거되고 §1~§3(포트 계약)은 무변경.
 */
@Component
@Profile({"local", "dev"})
public class StubKakaoTokenVerifier implements KakaoTokenVerifier {

    private static final String PREFIX = "stub:";

    @Override
    public KakaoAccount verify(String kakaoAccessToken) {
        if (kakaoAccessToken == null || !kakaoAccessToken.startsWith(PREFIX)) {
            throw new KakaoTokenInvalidException("스텁 모드는 stub:{fake_kakao_id} 형식만 수용합니다.");
        }
        String fakeId = kakaoAccessToken.substring(PREFIX.length()).trim();
        if (fakeId.isEmpty()) {
            throw new KakaoTokenInvalidException("fake_kakao_id가 비어 있습니다.");
        }
        return new KakaoAccount(fakeId);
    }
}
