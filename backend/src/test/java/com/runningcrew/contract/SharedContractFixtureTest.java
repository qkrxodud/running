package com.runningcrew.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.common.web.PageResponse;
import com.runningcrew.ranking.adapter.in.web.dto.HistoryRecordResponse;
import com.runningcrew.ranking.adapter.in.web.dto.PersonalBestResponse;
import com.runningcrew.ranking.adapter.in.web.dto.ResultResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

/**
 * P26-2 종결 — 앱↔서버 공유 픽스처(단일 바이트 파일)의 <b>서버측 역방향 가드 + 생성기</b>.
 *
 * <p><b>왜</b>: 결과·히스토리·PB 응답을 서버 DTO의 <b>실 직렬화 바이트</b>로 {@code docs/contracts/fixtures/}
 * 에 박제하고, 앱 테스트가 <b>같은 파일</b>을 파싱한다(교차 CI). 계약 drift(필드 추가·삭제·개명·NON_NULL
 * 키 생략 변화·avg_pace 필드명)가 생기면 <b>양쪽 중 하나가 red</b>가 되어 즉시 잡힌다.
 *
 * <p><b>이 테스트(서버측)</b>: 현 DTO를 앱과 <b>동일 설정</b>({@code @JsonTest} → application.yml
 * {@code spring.jackson} SNAKE_CASE·non_null 바인딩)의 {@link ObjectMapper}로 직렬화해, 커밋된 픽스처
 * 파일과 <b>JSON 트리 동치</b>인지 확인한다. drift → 트리 불일치 → red(=DTO가 픽스처를 벗어남).
 *
 * <p><b>(재)생성</b>: {@code ./gradlew test -Dfixtures.write=true --tests '*SharedContractFixtureTest'}
 * — 현 직렬화로 파일을 덮어쓴다(계약 승인 후 골든 갱신 절차). 기본 실행은 가드만.
 */
@JsonTest
class SharedContractFixtureTest {

    @Autowired
    private ObjectMapper mapper;

    private record Fixture(String fileName, Object payload) {
    }

    /** 픽스처 카탈로그 — 요구 케이스: 결과 DNF/DNS 키 생략 · 히스토리 취소 배지 · PB 목록. */
    private List<Fixture> fixtures() {
        return List.of(
                new Fixture("result_finished_dnf_dns.json", resultFixture()),
                new Fixture("history_records_mixed.json", historyFixture()),
                new Fixture("personal_bests.json", personalBestsFixture()));
    }

    // ── 결과: 완주(전 필드) + DNF(rank/record/pace 키 생략) + DNS(거리까지 생략) ──
    private ResultResponse resultFixture() {
        return new ResultResponse(91L,
                new ResultResponse.Course(55L, "한강 5K", 5000),
                Instant.parse("2026-07-11T09:00:03Z"),
                List.of(
                        new ResultResponse.Entry(3L, "민수", "FINISHED", 1, 1502, 5021, 299, true),
                        // DNF: rank·record_time_s·avg_pace_s_per_km = null → NON_NULL 키 생략, 거리 보존
                        new ResultResponse.Entry(8L, "다은", "DNF", null, null, 3120, null, false),
                        // DNS: rank·record·distance·pace 전부 null → 키 전부 생략
                        new ResultResponse.Entry(9L, "탈퇴한 러너", "DNS", null, null, null, null,
                                false)));
    }

    // ── 히스토리: 완주(PB) + DNF(키 생략) + CANCELLED 배지(session_cancelled=true, rank 생략) ──
    private PageResponse<HistoryRecordResponse> historyFixture() {
        List<HistoryRecordResponse> items = List.of(
                new HistoryRecordResponse(4021L, 91L, 55L, "한강 5K",
                        Instant.parse("2026-07-10T21:00:00Z"), "FINISHED", 1, 1502, 5021, 299,
                        true, false),
                new HistoryRecordResponse(4102L, 88L, 55L, "한강 5K",
                        Instant.parse("2026-07-03T21:00:00Z"), "DNF", null, null, 3120, null,
                        false, false),
                new HistoryRecordResponse(4150L, 84L, 60L, "올림픽공원 3K",
                        Instant.parse("2026-06-28T08:00:00Z"), "FINISHED", null, 1010, 3040, 332,
                        false, true));
        return new PageResponse<>(items, 0, 20, 3L, 1);
    }

    private PageResponse<PersonalBestResponse> personalBestsFixture() {
        List<PersonalBestResponse> items = List.of(
                new PersonalBestResponse(55L, "한강 5K", 5000, 1502, 299, 91L,
                        Instant.parse("2026-07-10T21:00:00Z")));
        return new PageResponse<>(items, 0, 20, 1L, 1);
    }

    @Test
    @DisplayName("앱 설정 매퍼는 snake_case + NON_NULL(키 생략)로 직렬화한다 — 픽스처 진위 앵커")
    void 매퍼_설정_앵커() throws IOException {
        // camelCase → snake_case 확인(전역 SNAKE_CASE 바인딩 증명).
        String json = mapper.writeValueAsString(
                new ResultResponse.Entry(1L, "x", "DNF", null, null, 100, null, false));
        JsonNode node = mapper.readTree(json);
        assertThat(node.has("user_id")).isTrue();          // camelCase 아님
        assertThat(node.has("total_distance_m")).isTrue();
        // NON_NULL: null 필드 키 자체 생략(DNF 의 rank/record/pace)
        assertThat(node.has("rank")).isFalse();
        assertThat(node.has("record_time_s")).isFalse();
        assertThat(node.has("avg_pace_s_per_km")).isFalse();
        // 계약 필드명 고정(@JsonProperty) — avg_pace_sper_km 오변환이 아님
        assertThat(json).doesNotContain("avg_pace_sper_km");
        // 원시 boolean 은 항상 존재
        assertThat(node.has("is_pb")).isTrue();
    }

    @Test
    @DisplayName("공유 픽스처 == 현 DTO 직렬화(역방향 가드) — 계약 drift 시 red")
    void 공유_픽스처_역방향_가드() throws IOException {
        Path dir = fixturesDir();
        boolean write = Boolean.getBoolean("fixtures.write");
        for (Fixture f : fixtures()) {
            String fresh = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(f.payload());
            Path file = dir.resolve(f.fileName());
            if (write) {
                Files.writeString(file, fresh + "\n", StandardCharsets.UTF_8);
                continue;
            }
            assertThat(Files.exists(file))
                    .as("공유 픽스처 부재: %s (생성: -Dfixtures.write=true)", file)
                    .isTrue();
            JsonNode committed = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            JsonNode current = mapper.readTree(fresh);
            assertThat(current)
                    .as("공유 픽스처 %s 가 현 DTO 직렬화와 불일치(계약 drift). 검토 후 -Dfixtures.write=true 로 갱신",
                            f.fileName())
                    .isEqualTo(committed);
        }
    }

    @Test
    @DisplayName("픽스처가 요구 케이스를 실제로 담고 있다(결과 DNF/DNS 생략·히스토리 취소·PB)")
    void 요구_케이스_실측() throws IOException {
        Path dir = fixturesDir();
        // 결과: 2번째 entry(DNF) 는 rank/record/pace 키 부재, distance 존재
        JsonNode result = readTreeIfExists(dir.resolve("result_finished_dnf_dns.json"));
        if (result == null) {
            return;   // write 모드 첫 생성 전 — 스킵(가드 테스트가 생성 후 커버)
        }
        JsonNode entries = result.get("entries");
        Map<String, JsonNode> byStatus = new java.util.HashMap<>();
        entries.forEach(e -> byStatus.put(e.get("status").asText(), e));
        JsonNode dnf = byStatus.get("DNF");
        assertThat(dnf.has("rank")).isFalse();
        assertThat(dnf.has("avg_pace_s_per_km")).isFalse();
        assertThat(dnf.has("total_distance_m")).isTrue();
        JsonNode dns = byStatus.get("DNS");
        assertThat(dns.has("total_distance_m")).isFalse();   // DNS 트랙 없음
        // 히스토리: 취소 배지 항목 존재
        JsonNode history = readTreeIfExists(dir.resolve("history_records_mixed.json"));
        boolean hasCancelled = false;
        for (JsonNode it : history.get("items")) {
            if (it.path("session_cancelled").asBoolean()) {
                hasCancelled = true;
                assertThat(it.has("rank")).as("취소 세션은 rank 미산정(생략)").isFalse();
            }
        }
        assertThat(hasCancelled).as("히스토리 취소 배지 케이스 포함").isTrue();
        // PB: 최소 1개
        JsonNode pb = readTreeIfExists(dir.resolve("personal_bests.json"));
        assertThat(pb.get("items").size()).isGreaterThanOrEqualTo(1);
    }

    private JsonNode readTreeIfExists(Path p) throws IOException {
        return Files.exists(p) ? mapper.readTree(Files.readString(p, StandardCharsets.UTF_8)) : null;
    }

    /** 저장소 루트를 찾아 {@code docs/contracts/fixtures/} 를 반환(테스트 실행 cwd 무관). */
    private static Path fixturesDir() throws IOException {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && cur != null; i++) {
            Path candidate = cur.resolve("docs/contracts/fixtures");
            if (Files.exists(cur.resolve("docs/contracts"))) {
                Files.createDirectories(candidate);
                return candidate;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("docs/contracts 를 찾지 못함(cwd=" + Path.of("").toAbsolutePath() + ")");
    }
}
