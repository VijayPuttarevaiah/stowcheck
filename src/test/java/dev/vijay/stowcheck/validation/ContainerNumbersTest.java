package dev.vijay.stowcheck.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerNumbersTest {

    @Test
    void computesTheIso6346ReferenceExample() {
        // The worked example from ISO 6346: CSQU 305438, check digit 3
        assertThat(ContainerNumbers.checkDigit("CSQU305438")).isEqualTo(3);
        assertThat(ContainerNumbers.hasValidCheckDigit("CSQU3054383")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MSCU1234566", "MAEU5551235", "HLXU2223330", "TRIU5550002"})
    void acceptsValidNumbers(String id) {
        assertThat(ContainerNumbers.hasValidCheckDigit(id)).isTrue();
    }

    @Test
    void handlesLettersAfterTheSkippedValues() {
        // L, V and Z sit after the skipped values 22 and 33, the usual place for off-by-one bugs
        assertThat(ContainerNumbers.hasValidCheckDigit("OOLU4445558")).isTrue();
        assertThat(ContainerNumbers.hasValidCheckDigit("HLXU2223330")).isTrue();
    }

    @Test
    void rejectsWrongCheckDigit() {
        assertThat(ContainerNumbers.hasValidCheckDigit("MSCU1234560")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MSC1234566", "MSCX1234566", "MSCU123456", "mscu1234566", ""})
    void rejectsMalformedNumbers(String id) {
        assertThat(ContainerNumbers.hasValidFormat(id)).isFalse();
    }
}
