package com.runningcrew.crew.application;

import com.runningcrew.crew.application.port.out.InviteCodeRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 초대 코드 문자열 생성(설계 §3.2): 대문자+숫자 6자, 혼동 문자(0/O/1/I) 제외, 충돌 시 재생성.
 *
 * <p>랜덤 의존이라 골든 대상은 아니다 — 문자 집합·길이 규약만 계약과 일치시킨다.
 */
@Component
public class InviteCodeGenerator {

    /** 0,1,O,I 제외한 대문자+숫자 32자 알파벳. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 20;

    private final InviteCodeRepository inviteCodeRepository;
    private final SecureRandom random = new SecureRandom();

    public InviteCodeGenerator(InviteCodeRepository inviteCodeRepository) {
        this.inviteCodeRepository = inviteCodeRepository;
    }

    public String generateUnique() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성 재시도 한도를 초과했습니다.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
