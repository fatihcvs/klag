package io.github.themoah.klag.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.themoah.klag.model.ConsumerGroupState.State;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the name-based mapping in {@link State#fromKafkaState(String)}: it exists so klag
 * never references org.apache.kafka.common.ConsumerGroupState, which kafka-clients 4.x
 * deprecated for removal. The mapping is only safe while the names line up.
 */
class ConsumerGroupStateTest {

  @Test
  void mapsEveryKafkaStateName() {
    // Names as emitted by kafka-clients' group-state enums (both the deprecated
    // ConsumerGroupState and its GroupState replacement use these constants).
    assertEquals(State.PREPARING_REBALANCE, State.fromKafkaState("PREPARING_REBALANCE"));
    assertEquals(State.COMPLETING_REBALANCE, State.fromKafkaState("COMPLETING_REBALANCE"));
    // KIP-848 new consumer protocol: these replace the two rebalance states above and must
    // map to their own tags, otherwise rebalance alerting goes silent on upgraded groups.
    assertEquals(State.ASSIGNING, State.fromKafkaState("ASSIGNING"));
    assertEquals(State.RECONCILING, State.fromKafkaState("RECONCILING"));
    assertEquals(State.STABLE, State.fromKafkaState("STABLE"));
    assertEquals(State.DEAD, State.fromKafkaState("DEAD"));
    assertEquals(State.EMPTY, State.fromKafkaState("EMPTY"));
    assertEquals(State.UNKNOWN, State.fromKafkaState("UNKNOWN"));
  }

  @Test
  void unrecognisedAndNullNamesBecomeUnknown() {
    assertEquals(State.UNKNOWN, State.fromKafkaState(null));
    // Any future state klag does not model must degrade, not throw.
    assertEquals(State.UNKNOWN, State.fromKafkaState("SOME_FUTURE_STATE"));
    assertEquals(State.UNKNOWN, State.fromKafkaState(""));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tr-TR", "az-AZ", "en-US"})
  @ResourceLock(Resources.LOCALE)
  void metricLabelsDoNotDependOnDefaultLocale(String languageTag) {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag(languageTag));
      assertAll(
        () -> assertEquals("preparing_rebalance", State.PREPARING_REBALANCE.toMetricValue()),
        () -> assertEquals("completing_rebalance", State.COMPLETING_REBALANCE.toMetricValue()),
        () -> assertEquals("assigning", State.ASSIGNING.toMetricValue()),
        () -> assertEquals("reconciling", State.RECONCILING.toMetricValue()),
        () -> assertEquals("stable", State.STABLE.toMetricValue()),
        () -> assertEquals("dead", State.DEAD.toMetricValue()),
        () -> assertEquals("empty", State.EMPTY.toMetricValue()),
        () -> assertEquals("unknown", State.UNKNOWN.toMetricValue()));
    } finally {
      Locale.setDefault(previous);
    }
  }
}
