package com.li.lipicturecloud.application.companion.observation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ObservationDecisionTest {
 @Test void validSilenceAndEmptyOutputRemainDifferent() {
  assertThat(ObservationDecision.parse("{\"action\":\"SILENT\",\"emote\":null,\"text\":null}").action()).isEqualTo("SILENT");
  assertThatThrownBy(()->ObservationDecision.parse("")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void suggestionsAreDataAndCannotIntroduceToolsOrArbitraryFields() {
  assertThat(ObservationDecision.parse("{\"action\":\"SPEAK\",\"emote\":null,\"text\":\"嘿，我想到个小话题\",\"nextObservationSeconds\":999,\"focus\":\"配色\",\"topic\":null}").nextObservationSeconds()).isEqualTo(600);
  assertThatThrownBy(()->ObservationDecision.parse("{\"action\":\"SILENT\",\"emote\":null,\"text\":null,\"tools\":[]}")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->ObservationDecision.parse("{\"action\":\"SILENT\",\"action\":\"SPEAK\",\"emote\":null,\"text\":null}")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void bothCredentialsAndMultipleEscapingAreRemovedWithoutChangingOrdinaryBody() {
  var scrub=new ObservationRedaction("text-secret","vision-secret");
  assertThat(scrub.safe("text-secret vision-secret")).doesNotContain("text-secret","vision-secret");
  assertThat(scrub.safe("\\u0074ext-secret")).contains("[REDACTED_CREDENTIAL]").doesNotContain("ext-secret","\\u0074");
  String mixed="text-secret vision-secret \\u0074ext-secret \\u0076ision-secret \\\\u0074ext-secret \\\\u0076ision-secret";
  assertThat(scrub.safe(mixed)).isEqualTo("[REDACTED_CREDENTIAL] [REDACTED_CREDENTIAL] [REDACTED_CREDENTIAL] [REDACTED_CREDENTIAL] [REDACTED_CREDENTIAL] [REDACTED_CREDENTIAL]");
  assertThat(scrub.safe("{\"text\":\"\\u4f60\"}")).isEqualTo("{\"text\":\"\\u4f60\"}");
 }
}
