package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.names;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Pins {@code /scroll} and its {@code {id, channels}} response. The Java recceiver and javaCFClient
 * use it to page past the 10000 result cap.
 */
class ScrollApiIT extends V0ApiTestBase {

  @BeforeEach
  void createChannels() {
    createProperties("admin", "area");
    putChannels(
        withProperties(channel("ch1", "admin"), property("area", "admin", "north")),
        withProperties(channel("ch2", "admin"), property("area", "admin", "south")),
        withProperties(channel("ch3", "admin"), property("area", "admin", "north")),
        withProperties(channel("ch4", "admin"), property("area", "admin", "north")),
        withProperties(channel("ch5", "admin"), property("area", "admin", "north")));
  }

  private JsonNode scroll(String pathAndQuery) {
    V0Client.Response response = cf.get("/resources/scroll" + pathAndQuery);
    assertEquals(200, response.status(), response.body());
    return response.json();
  }

  @Test
  void firstPageReturnsTheLastChannelNameAsTheId() {
    JsonNode page = scroll("?~size=2");

    assertEquals("ch2", page.get("id").asString());
    assertEquals(List.of("ch1", "ch2"), names(page.get("channels")));
  }

  @Test
  void followingTheIdPagesThroughEveryChannelAndEndsWithANullId() {
    JsonNode second = scroll("/ch2?~size=2");
    JsonNode third = scroll("/ch4?~size=2");
    JsonNode end = scroll("/ch5?~size=2");

    assertEquals(List.of("ch3", "ch4"), names(second.get("channels")));
    assertEquals("ch4", second.get("id").asString());
    assertEquals(List.of("ch5"), names(third.get("channels")));
    assertEquals("ch5", third.get("id").asString());
    assertEquals(List.of(), names(end.get("channels")));
    assertTrue(end.get("id").isNull());
  }

  @Test
  void scrollAcceptsTheChannelQueryLanguage() {
    JsonNode first = scroll("?area=north&~size=2");
    JsonNode second = scroll("/" + first.get("id").asString() + "?area=north&~size=2");

    assertEquals(List.of("ch1", "ch3"), names(first.get("channels")));
    assertEquals(List.of("ch4", "ch5"), names(second.get("channels")));
  }

  @Test
  void defaultPageSizeIsTheQuerySize() {
    JsonNode page = scroll("");

    assertEquals(List.of("ch1", "ch2", "ch3", "ch4", "ch5"), names(page.get("channels")));
    assertEquals("ch5", page.get("id").asString());
  }
}
