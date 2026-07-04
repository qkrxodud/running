package com.runningcrew.user.domain;

/**
 * 닉네임 검증 순수 함수(설계 12 §1.2). trim 후 1~30자, 빈 문자열·제어문자 금지, 유일성 없음.
 *
 * <p>IO·시계·랜덤 없는 순수 함수 — 골든/유닛 테스트 대상.
 */
public final class Nickname {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 30;

    private Nickname() {
    }

    /**
     * 원문을 정규화(trim)·검증하고 저장 가능한 문자열을 돌려준다.
     *
     * @throws InvalidNicknameException 길이 위반 또는 제어문자 포함
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidNicknameException("닉네임은 필수입니다.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new InvalidNicknameException("닉네임은 1~30자여야 합니다.");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isISOControl(c)) {
                throw new InvalidNicknameException("닉네임에 제어문자를 포함할 수 없습니다.");
            }
        }
        return trimmed;
    }
}
