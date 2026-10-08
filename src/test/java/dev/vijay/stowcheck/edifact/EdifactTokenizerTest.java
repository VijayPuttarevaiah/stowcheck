package dev.vijay.stowcheck.edifact;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EdifactTokenizerTest {

    @Test
    void splitsElementsAndComponents() {
        List<Segment> segments = EdifactTokenizer.tokenize("LOC+147+0120082::5'EQD+CN+MSCU1234566+22G1'");

        assertThat(segments).hasSize(2);
        Segment loc = segments.get(0);
        assertThat(loc.tag()).isEqualTo("LOC");
        assertThat(loc.value(0)).isEqualTo("147");
        assertThat(loc.value(1, 0)).isEqualTo("0120082");
        assertThat(loc.value(1, 2)).isEqualTo("5");
        assertThat(loc.position()).isEqualTo(1);
        assertThat(segments.get(1).position()).isEqualTo(2);
    }

    @Test
    void missingElementsReadAsEmpty() {
        Segment s = EdifactTokenizer.tokenize("EQD+CN'").get(0);

        assertThat(s.value(5)).isEmpty();
        assertThat(s.value(0, 3)).isEmpty();
    }

    @Test
    void releaseCharacterEscapesSeparators() {
        Segment s = EdifactTokenizer.tokenize("FTX+AAA+++DRUMS ?+ PALLETS?: 2?''").get(0);

        assertThat(s.value(3)).isEqualTo("DRUMS + PALLETS: 2'");
    }

    @Test
    void unaRedefinesSeparators() {
        List<Segment> segments = EdifactTokenizer.tokenize("UNA|*.# ~LOC*147*0120082||5~");

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).value(1, 0)).isEqualTo("0120082");
        assertThat(segments.get(0).value(1, 2)).isEqualTo("5");
    }

    @Test
    void ignoresLineBreaksBetweenSegments() {
        List<Segment> segments = EdifactTokenizer.tokenize("UNH+1+BAPLIE'\r\nBGM++X+9'\n");

        assertThat(segments).extracting(Segment::tag).containsExactly("UNH", "BGM");
    }

    @Test
    void rejectsUnterminatedSegment() {
        assertThatThrownBy(() -> EdifactTokenizer.tokenize("UNH+1+BAPLIE'BGM++X"))
                .isInstanceOf(EdifactSyntaxException.class)
                .hasMessageContaining("missing its");
    }

    @Test
    void rejectsTextThatIsNotEdifact() {
        assertThatThrownBy(() -> EdifactTokenizer.tokenize("hello world'"))
                .isInstanceOf(EdifactSyntaxException.class)
                .hasMessageContaining("invalid tag");
    }
}
