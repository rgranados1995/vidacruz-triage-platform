package com.vidacruz.triage.model.report;

import com.vidacruz.triage.model.exception.BusinessError;
import com.vidacruz.triage.model.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportPolicyTest {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final ReportPolicy POLICY = new ReportPolicy(Duration.ofDays(31));

    @Test
    void shouldAcceptARangeAsLongAsTheMaximum() {
        var to = FROM.plus(Duration.ofDays(31));

        assertThat(POLICY.periodBetween(FROM, to)).isEqualTo(new ReportPeriod(FROM, to));
    }

    @Test
    void shouldAcceptTheShortestRange() {
        var to = FROM.plusNanos(1000);

        assertThat(POLICY.periodBetween(FROM, to)).isEqualTo(new ReportPeriod(FROM, to));
    }

    @Test
    void shouldRejectARangeLongerThanTheMaximum() {
        var to = FROM.plus(Duration.ofDays(31)).plusSeconds(1);

        assertThatThrownBy(() -> POLICY.periodBetween(FROM, to))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getError()).isEqualTo(BusinessError.INVALID_REPORT_PERIOD);
                    assertThat(error.getDetail()).isEqualTo("el rango no puede superar 31 días");
                });
    }

    @Test
    void shouldRejectAnEmptyOrInvertedRange() {
        var later = FROM.plusSeconds(60);

        assertThatThrownBy(() -> POLICY.periodBetween(FROM, FROM))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getError()).isEqualTo(BusinessError.INVALID_REPORT_PERIOD);
                    assertThat(error.getDetail()).isEqualTo("la fecha desde debe ser anterior a la fecha hasta");
                });
        assertThatThrownBy(() -> POLICY.periodBetween(later, FROM)).isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @CsvSource({"PT12H, 12 horas", "PT90M, 90 minutos", "P2D, 2 días", "PT25H, 25 horas"})
    void shouldDescribeTheMaximumInItsLargestWholeUnit(Duration maximum, String described) {
        var policy = new ReportPolicy(maximum);
        var to = FROM.plus(maximum).plusSeconds(1);

        assertThatThrownBy(() -> policy.periodBetween(FROM, to))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getDetail()).isEqualTo("el rango no puede superar " + described));
    }

    @Test
    void shouldRequireAPositiveMaximum() {
        var zero = Duration.ZERO;
        var negative = Duration.ofHours(-1);

        assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(zero));
        assertThatIllegalArgumentException().isThrownBy(() -> new ReportPolicy(negative));
    }

    @Test
    void shouldRequireBothEndsOfThePeriod() {
        assertThatThrownBy(() -> new ReportPeriod(null, FROM)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportPeriod(FROM, null)).isInstanceOf(NullPointerException.class);
    }
}
