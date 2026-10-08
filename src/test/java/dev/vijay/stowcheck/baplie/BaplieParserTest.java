package dev.vijay.stowcheck.baplie;

import dev.vijay.stowcheck.TestPlans;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaplieParserTest {

    @Test
    void readsVesselHeader() {
        StowagePlan plan = BaplieParser.parse(TestPlans.load("valid-plan.edi"));

        assertThat(plan.messageType()).isEqualTo("BAPLIE");
        assertThat(plan.messageVersion()).isEqualTo("D95B");
        assertThat(plan.messageFunction()).isEqualTo("9");
        assertThat(plan.voyage()).isEqualTo("041E");
        assertThat(plan.carrier()).isEqualTo("MSCU");
        assertThat(plan.vesselId()).isEqualTo("9839131");
        assertThat(plan.vesselName()).isEqualTo("MSC AURORA");
        assertThat(plan.departurePort()).isEqualTo("BEANR");
        assertThat(plan.nextPortOfCall()).isEqualTo("CAHAL");
        assertThat(plan.declaredSegmentCount()).isEqualTo(plan.actualSegmentCount());
    }

    @Test
    void readsEveryStowedUnit() {
        StowagePlan plan = BaplieParser.parse(TestPlans.load("valid-plan.edi"));

        assertThat(plan.units()).hasSize(6);
        StowedUnit reefer = plan.units().get(2);
        assertThat(reefer.cell()).isEqualTo("0060082");
        assertThat(reefer.bay()).isEqualTo(6);
        assertThat(reefer.row()).isEqualTo(0);
        assertThat(reefer.tier()).isEqualTo(82);
        assertThat(reefer.containerId()).isEqualTo("CMAU7654327");
        assertThat(reefer.isoSizeType()).isEqualTo("45R1");
        assertThat(reefer.lengthFeet()).isEqualTo(40);
        assertThat(reefer.isReefer()).isTrue();
        assertThat(reefer.temperatureC()).isEqualTo(-18.0);
        assertThat(reefer.weightKg()).isEqualTo(29100);
        assertThat(reefer.weightVerified()).isTrue();
        assertThat(reefer.portOfDischarge()).isEqualTo("USNYC");
        assertThat(reefer.operator()).isEqualTo("CMA");

        StowedUnit dangerous = plan.units().get(3);
        assertThat(dangerous.dangerousGoods()).singleElement()
                .satisfies(dg -> {
                    assertThat(dg.imdgClass()).isEqualTo("3");
                    assertThat(dg.unNumber()).isEqualTo("1993");
                });
    }

    @Test
    void convertsPoundsAndFahrenheit() {
        String raw = """
                UNH+1+BAPLIE:D:95B:UN:SMDG22'
                LOC+147+0010082::5'
                MEA+WT++LBR:10000'
                TMP+2+032:FAH'
                EQD+CN+CSQU3054383+22R1+++5'
                UNT+6+1'
                """;
        StowedUnit unit = BaplieParser.parse(raw).units().get(0);

        assertThat(unit.weightKg()).isEqualTo(4536);
        assertThat(unit.weightVerified()).isFalse();
        assertThat(unit.temperatureC()).isEqualTo(0.0);
    }

    @Test
    void stripsSpacesFromContainerNumbers() {
        String raw = "UNH+1+BAPLIE'LOC+147+0010082::5'EQD+CN+CSQU 3054383+22G1'UNT+4+1'";

        assertThat(BaplieParser.parse(raw).units().get(0).containerId()).isEqualTo("CSQU3054383");
    }
}
