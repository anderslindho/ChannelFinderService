package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.names;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;
import static org.phoebus.channelfinder.api.v0.V0Json.withTags;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ExpectedToFail;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins what depends on {@code elasticsearch.query.size}. The size is lowered to 5 so that more than
 * one page of channels or definitions takes a handful of documents instead of 10000.
 */
@TestPropertySource(
    properties = {
      "elasticsearch.query.size=5",
      "elasticsearch.channel.index=v0_api_small_channelfinder",
      "elasticsearch.tag.index=v0_api_small_cf_tags",
      "elasticsearch.property.index=v0_api_small_cf_properties"
    })
class QuerySizeApiIT extends V0ApiTestBase {

  private static final List<String> SEVEN_CHANNELS =
      List.of("ch1", "ch2", "ch3", "ch4", "ch5", "ch6", "ch7");

  private void putSevenChannelsWith(ObjectNode property, ObjectNode tag) {
    putChannels(
        SEVEN_CHANNELS.stream()
            .map(name -> withTags(withProperties(channel(name, "admin"), property), tag))
            .toArray(ObjectNode[]::new));
  }

  @Test
  void defaultSizeAndMaxResultWindowFollowTheQuerySize() {
    createProperties("admin", "area");
    createTags("admin", "archived");
    putSevenChannelsWith(property("area", "admin", "north"), tag("archived", "admin"));

    assertEquals(
        List.of("ch1", "ch2", "ch3", "ch4", "ch5"), names(cf.get("/resources/channels").json()));
    assertEquals("7", cf.get("/resources/channels/count").body());
    assertEquals(400, cf.get("/resources/channels?~size=6").status());
  }

  @Test
  void listingTheDefinitionsIsCappedAtTheQuerySize() {
    // One would expect every definition. Only the first page of them is returned.
    createProperties("admin", "p1", "p2", "p3", "p4", "p5", "p6");

    assertEquals(
        List.of("p1", "p2", "p3", "p4", "p5"), names(cf.get("/resources/properties").json()));
  }

  @Test
  @Tag("known-defect")
  @ExpectedToFail(
      "deleting a property that is on more than one page of channels fails after the first page")
  void deletingAPropertyOnMoreThanOnePageRemovesItEverywhere() {
    createProperties("admin", "area");
    createTags("admin", "archived");
    putSevenChannelsWith(property("area", "admin", "north"), tag("archived", "admin"));

    // today: returns 500, and ch6 and ch7 keep the property
    assertEquals(200, cf.delete("/resources/properties/area", V0Client.ADMIN).status());
    assertEquals(404, cf.get("/resources/properties/area").status());
    assertEquals(List.of(), names(cf.get("/resources/channels?area=*").json()));
  }

  @Test
  @Tag("known-defect")
  @ExpectedToFail(
      "deleting a tag that is on more than one page of channels fails after the first page")
  void deletingATagOnMoreThanOnePageRemovesItEverywhere() {
    createProperties("admin", "area");
    createTags("admin", "archived");
    putSevenChannelsWith(property("area", "admin", "north"), tag("archived", "admin"));

    // today: returns 500, and ch6 and ch7 keep the tag
    assertEquals(200, cf.delete("/resources/tags/archived", V0Client.ADMIN).status());
    assertEquals(404, cf.get("/resources/tags/archived").status());
    assertEquals(List.of(), names(cf.get("/resources/channels?~tag=archived").json()));
  }

  @Test
  @Tag("known-defect")
  @ExpectedToFail("channel writes are checked against only the first page of property definitions")
  void aChannelUsingADefinitionPastTheQuerySizeIsAccepted() {
    createProperties("admin", "p1", "p2", "p3", "p4", "p5", "p6");
    String usesP6 =
        withProperties(channel("ch1", "admin"), property("p6", "admin", "v")).toString();

    // today: returns 404, as if p6 did not exist
    assertEquals(200, cf.put("/resources/channels/ch1", usesP6, V0Client.ADMIN).status());
  }
}
