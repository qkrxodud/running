package com.runningcrew;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 러닝크루 백엔드 진입점.
 *
 * <p>JVM 기본 타임존을 UTC로 고정한다. 서버의 저장·비교·판정은 전부 UTC이며(계약 conventions.md §3),
 * KST 등 표시 변환은 클라이언트 소관이다. {@code SpringApplication.run} 이전에 고정해
 * 이후 생성되는 모든 시각 처리가 UTC 기준이 되도록 한다.
 */
@SpringBootApplication
public class RunningCrewApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RunningCrewApplication.class, args);
    }
}
