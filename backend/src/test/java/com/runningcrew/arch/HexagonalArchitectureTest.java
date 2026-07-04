package com.runningcrew.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * 헥사고날 경계 자동 가드(설계 12 §5 — ArchUnit 4규칙). {@code ./gradlew build}에 편입.
 *
 * <ul>
 *   <li>R-1: {@code ..domain..}는 Spring/Jakarta/Hibernate 의존 금지(순수 도메인 — 골든 테스트가 컨테이너 없이 돈다).
 *   <li>R-2: 컨텍스트 간 클래스 의존 금지 — 예외는 common과 <b>타 컨텍스트의 domain.event</b>.
 *   <li>R-3: {@code KakaoAccount}는 user 컨텍스트 밖 + user.adapter.in.web에서 참조 금지(카카오 회원번호 봉인).
 *   <li>R-4: 레이어 방향 — domain은 application/adapter 의존 금지, application은 adapter 의존 금지.
 * </ul>
 */
class HexagonalArchitectureTest {

    private static final String[] CONTEXTS =
            {"user", "crew", "race", "tracking", "ranking", "reward"};

    private static final String KAKAO_ACCOUNT = "com.runningcrew.user.domain.KakaoAccount";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.runningcrew");

    @Test
    void R1_domain_은_프레임워크에_의존하지_않는다() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..");
        rule.check(classes);
    }

    @Test
    void R2_컨텍스트는_타_컨텍스트의_domain_event와_common_외엔_의존하지_않는다() {
        for (String ctx : CONTEXTS) {
            ArchRule rule = noClasses().that().resideInAPackage("com.runningcrew." + ctx + "..")
                    .should().dependOnClassesThat(otherContextOutsideDomainEvent(ctx))
                    // race·ranking·reward는 B1에서 아직 빈 골조 — 클래스가 생기면 자동 강제된다.
                    .allowEmptyShould(true);
            rule.check(classes);
        }
    }

    @Test
    void R3_KakaoAccount는_user_밖과_user_web에서_참조되지_않는다() {
        noClasses().that().resideOutsideOfPackage("com.runningcrew.user..")
                .should().dependOnClassesThat().haveFullyQualifiedName(KAKAO_ACCOUNT)
                .check(classes);
        noClasses().that().resideInAPackage("com.runningcrew.user.adapter.in.web..")
                .should().dependOnClassesThat().haveFullyQualifiedName(KAKAO_ACCOUNT)
                .check(classes);
    }

    @Test
    void R4_레이어_방향__domain_application이_상위를_참조하지_않는다() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
                .check(classes);
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .check(classes);
    }

    /** 대상 클래스가 ctx가 아닌 다른 컨텍스트에 속하고, 그 컨텍스트의 domain.event가 아니면 위반. */
    private static DescribedPredicate<JavaClass> otherContextOutsideDomainEvent(String ctx) {
        return new DescribedPredicate<>("resides in another context outside its domain.event") {
            @Override
            public boolean test(JavaClass target) {
                String pkg = target.getPackageName();
                for (String other : CONTEXTS) {
                    if (other.equals(ctx)) {
                        continue;
                    }
                    String otherRoot = "com.runningcrew." + other;
                    if (pkg.equals(otherRoot) || pkg.startsWith(otherRoot + ".")) {
                        return !pkg.startsWith(otherRoot + ".domain.event");
                    }
                }
                return false;
            }
        };
    }
}
