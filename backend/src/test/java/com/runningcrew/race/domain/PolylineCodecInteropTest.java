package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 폴리라인 <b>상호운용 박제(seed)</b> — 클라 {@code polyline_codec_test.dart}의 골든 벡터를 서버 구현에
 * 그대로 투입해 문자 단위 일치를 확인한다(course-api.md · CO-B1/B2). 경계·tie 전수 카탈로그는
 * test-engineer의 B2-T2가 확장한다. 순수 함수라 컨테이너 없이 돈다.
 */
class PolylineCodecInteropTest {

    private static final String GOOGLE_GOLDEN = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

    @Test
    void 클라_골든_벡터를_디코딩하면_좌표가_일치한다() {
        List<LatLng> pts = PolylineCodec.decode(GOOGLE_GOLDEN);
        assertThat(pts).hasSize(3);
        assertThat(pts.get(0).lat()).isEqualTo(38.5);
        assertThat(pts.get(0).lng()).isEqualTo(-120.2);
        assertThat(pts.get(2).lat()).isEqualTo(43.252);
        assertThat(pts.get(2).lng()).isEqualTo(-126.453);
    }

    @Test
    void 좌표를_인코딩하면_클라와_같은_골든_문자열이다() {
        String encoded = PolylineCodec.encode(List.of(
                new LatLng(38.5, -120.2),
                new LatLng(40.7, -120.95),
                new LatLng(43.252, -126.453)));
        assertThat(encoded).isEqualTo(GOOGLE_GOLDEN);
    }

    @Test
    void 원점_단일점은_물음표_두개다() {
        assertThat(PolylineCodec.encode(List.of(new LatLng(0, 0)))).isEqualTo("??");
        assertThat(PolylineCodec.decode("??")).singleElement()
                .satisfies(p -> {
                    assertThat(p.lat()).isEqualTo(0.0);
                    assertThat(p.lng()).isEqualTo(0.0);
                });
    }

    @Test
    void 음경도_180근처_클라_벡터를_만족한다() {
        // 클라 골든: -179.9832104 → "`~oia@", lat 델타 0 → "?".
        assertThat(PolylineCodec.encode(List.of(new LatLng(0.0, -179.9832104)))).isEqualTo("?`~oia@");
        LatLng back = PolylineCodec.decode("?`~oia@").getFirst();
        assertThat(back.lng()).isCloseTo(-179.9832104, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void tie_half_away_from_zero_는_음좌표에서_Math_round와_갈린다() {
        // *1e5 = 대칭 .5 tie. half-away-from-zero: +0.000005→+1LSB, -0.000005→-1LSB.
        // Math.round(half-up)라면 -0.000005는 0으로 갈려 인코딩이 어긋난다.
        String pos = PolylineCodec.encode(List.of(new LatLng(0.000005, 0.0)));
        String neg = PolylineCodec.encode(List.of(new LatLng(-0.000005, 0.0)));
        // 대칭이어야 한다: 위도 +1LSB(→delta 1)와 -1LSB(→delta -1)의 zigzag는 각각 2, 1.
        assertThat(PolylineCodec.decode(pos).getFirst().lat())
                .isCloseTo(0.00001, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(PolylineCodec.decode(neg).getFirst().lat())
                .isCloseTo(-0.00001, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void 손상된_폴리라인은_InvalidCourseException() {
        assertThatThrownBy(() -> PolylineCodec.decode("_p~iF"))   // lat만 있고 lng 없음
                .isInstanceOf(InvalidCourseException.class);
    }

    @Test
    void 다점_왕복은_1e5_정밀도를_보존한다() {
        List<LatLng> course = List.of(
                new LatLng(37.51235, 126.99640),
                new LatLng(37.51188, 127.00412),
                new LatLng(37.51602, 127.01105));
        List<LatLng> back = PolylineCodec.decode(PolylineCodec.encode(course));
        assertThat(back).hasSize(3);
        for (int i = 0; i < course.size(); i++) {
            assertThat(back.get(i).lat()).isCloseTo(course.get(i).lat(),
                    org.assertj.core.data.Offset.offset(1e-5));
            assertThat(back.get(i).lng()).isCloseTo(course.get(i).lng(),
                    org.assertj.core.data.Offset.offset(1e-5));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T2 — tie(반-마이크로도) 종결 골든 (QA 이월 "폴리라인 정밀도" 최종 종결 장치)
    //
    //  이 블록의 기대 문자열은 Dart `app/test/core/geo/polyline_codec_test.dart`의
    //  '상호운용 tie 종결 골든 (T2)' 그룹과 **문자 단위로 동일**하다(양쪽 강제).
    //  ±0.000005°는 *1e5 = 정확히 ±0.5(IEEE754 정확한 tie)이므로, half-away-from-zero
    //  규약이 강제된다: +0.5→+1LSB('A'), -0.5→-1LSB('@'). 서버가 `Math.round()`(half-up)로
    //  회귀하면 음수 tie가 0으로 잘려 "@?"가 "??"로 어긋나 이 테스트가 red가 된다(회귀 박제).
    //  기대값 출처: Google Encoded Polyline 알고리즘 + course-api.md tie 규약(구현 역산 아님).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void tie_양의위도_반LSB는_절댓값_큰쪽으로_A로_인코딩된다() {
        // +0.000005 → *1e5 = +0.5 → half-away → +1 → zigzag 2 → 'A'; lng 델타 0 → '?'
        assertThat(PolylineCodec.encode(List.of(new LatLng(0.000005, 0.0)))).isEqualTo("A?");
    }

    @Test
    void tie_음의위도_반LSB는_절댓값_큰쪽으로_앳으로_인코딩된다() {
        // -0.000005 → *1e5 = -0.5 → half-away → -1 → zigzag 1 → '@'.
        // Math.round(half-up)면 0 → "??" 로 갈려 실패(회귀 감지 지점).
        assertThat(PolylineCodec.encode(List.of(new LatLng(-0.000005, 0.0)))).isEqualTo("@?");
    }

    @Test
    void tie_음의경도_반LSB도_대칭으로_앳() {
        assertThat(PolylineCodec.encode(List.of(new LatLng(0.0, -0.000005)))).isEqualTo("?@");
    }

    @Test
    void tie_위경도_동시_음의_반LSB는_앳앳() {
        assertThat(PolylineCodec.encode(List.of(new LatLng(-0.000005, -0.000005)))).isEqualTo("@@");
    }

    /**
     * 자체 상호운용 벡터(음좌표 + 양/음 tie 혼합). 표준 알고리즘으로 도출한 박제 문자열 "AABBAA":
     * (0.000005,0.000005)→delta(+1,+1)='A''A', (-0.00001,-0.00001)→delta(-2,-2)='B''B',
     * (0,0)→delta(+1,+1)='A''A'. Dart 그룹과 동일 값.
     */
    @Test
    void 자체_상호운용_벡터는_양쪽_동일한_AABBAA로_인코딩되고_왕복한다() {
        List<LatLng> vec = List.of(
                new LatLng(0.000005, 0.000005),
                new LatLng(-0.00001, -0.00001),
                new LatLng(0.0, 0.0));
        assertThat(PolylineCodec.encode(vec)).isEqualTo("AABBAA");

        List<LatLng> back = PolylineCodec.decode("AABBAA");
        assertThat(back).hasSize(3);
        // tie가 절댓값 큰 쪽으로 갔으므로 첫 점은 +1LSB(0.00001)로 복원된다.
        assertThat(back.get(0).lat()).isEqualTo(0.00001);
        assertThat(back.get(0).lng()).isEqualTo(0.00001);
        assertThat(back.get(1).lat()).isEqualTo(-0.00001);
        assertThat(back.get(2).lat()).isEqualTo(0.0);
    }
}
