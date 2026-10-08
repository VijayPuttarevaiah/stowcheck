package dev.vijay.stowcheck.validation;

import dev.vijay.stowcheck.TestPlans;
import dev.vijay.stowcheck.baplie.BaplieParser;
import dev.vijay.stowcheck.validation.rules.ContainerIdRule;
import dev.vijay.stowcheck.validation.rules.DangerousGoodsRule;
import dev.vijay.stowcheck.validation.rules.EnvelopeRule;
import dev.vijay.stowcheck.validation.rules.PortRule;
import dev.vijay.stowcheck.validation.rules.ReeferRule;
import dev.vijay.stowcheck.validation.rules.StowagePositionRule;
import dev.vijay.stowcheck.validation.rules.WeightRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorTest {

    private static final PlanValidator validator = new PlanValidator(List.of(
            new ContainerIdRule(), new StowagePositionRule(), new WeightRule(36000),
            new ReeferRule(), new DangerousGoodsRule(), new PortRule(), new EnvelopeRule()));

    private static List<Issue> broken;

    @BeforeAll
    static void validateBrokenPlan() {
        broken = validator.validate(BaplieParser.parse(TestPlans.load("broken-plan.edi")));
    }

    @Test
    void cleanPlanHasNoFindings() {
        List<Issue> issues = validator.validate(BaplieParser.parse(TestPlans.load("valid-plan.edi")));

        assertThat(issues).isEmpty();
    }

    @Test
    void flagsBadCheckDigit() {
        assertThat(find("CHECK_DIGIT")).singleElement().satisfies(i -> {
            assertThat(i.containerId()).isEqualTo("MSCU1234560");
            assertThat(i.message()).contains("expected 6");
        });
    }

    @Test
    void flagsTwoContainersInOneCell() {
        assertThat(find("CELL_CONFLICT")).singleElement().satisfies(i -> {
            assertThat(i.severity()).isEqualTo(Issue.Severity.ERROR);
            assertThat(i.cell()).isEqualTo("0020084");
            assertThat(i.containerId()).isEqualTo("OOLU4445558");
        });
    }

    @Test
    void flagsFortyFootUnitInOddBay() {
        assertThat(find("BAY_PARITY")).singleElement()
                .satisfies(i -> assertThat(i.cell()).isEqualTo("0130082"));
    }

    @Test
    void flagsMissingAndExcessiveWeight() {
        assertThat(find("MISSING_WEIGHT")).singleElement()
                .satisfies(i -> assertThat(i.containerId()).isEqualTo("TCNU8889991"));
        assertThat(find("WEIGHT_RANGE")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("41000 kg"));
        assertThat(find("VGM_MISSING")).singleElement()
                .satisfies(i -> assertThat(i.containerId()).isEqualTo("MSKU0012347"));
    }

    @Test
    void flagsReeferProblems() {
        assertThat(find("TEMPERATURE_ON_NON_REEFER")).singleElement()
                .satisfies(i -> assertThat(i.containerId()).isEqualTo("SEGU6667772"));
        assertThat(find("REEFER_NO_TEMPERATURE")).singleElement()
                .satisfies(i -> assertThat(i.severity()).isEqualTo(Issue.Severity.WARNING));
    }

    @Test
    void flagsDangerousGoodsProblems() {
        assertThat(find("DG_CLASS")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("'X'"));
        assertThat(find("DG_UN_NUMBER")).singleElement()
                .satisfies(i -> assertThat(i.severity()).isEqualTo(Issue.Severity.WARNING));
    }

    @Test
    void flagsMissingDischargePort() {
        assertThat(find("MISSING_PORT")).singleElement()
                .satisfies(i -> assertThat(i.containerId()).isEqualTo("TRIU5550002"));
    }

    @Test
    void flagsDuplicateContainer() {
        assertThat(find("DUPLICATE_CONTAINER")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("0020084"));
    }

    @Test
    void flagsWrongSegmentCount() {
        assertThat(find("ENVELOPE")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("declares 99"));
    }

    @Test
    void returnsFindingsInFileOrder() {
        assertThat(broken).extracting(Issue::position).isSorted();
    }

    @Test
    void flatRacksSharingACellAreOnlyAWarning() {
        String raw = """
                UNH+1+BAPLIE'
                LOC+147+0020082::5'
                MEA+WT++KGM:3000'
                LOC+9+BEANR'
                LOC+11+CAHAL'
                EQD+CN+CSQU3054383+42P1+++4'
                LOC+147+0020082::5'
                MEA+WT++KGM:3000'
                LOC+9+BEANR'
                LOC+11+CAHAL'
                EQD+CN+MSCU1234566+42P1+++4'
                UNT+12+1'
                """;
        List<Issue> issues = validator.validate(BaplieParser.parse(raw));

        assertThat(issues).singleElement().satisfies(i -> {
            assertThat(i.rule()).isEqualTo("CELL_CONFLICT");
            assertThat(i.severity()).isEqualTo(Issue.Severity.WARNING);
        });
    }

    private static List<Issue> find(String rule) {
        return broken.stream().filter(i -> i.rule().equals(rule)).toList();
    }
}
