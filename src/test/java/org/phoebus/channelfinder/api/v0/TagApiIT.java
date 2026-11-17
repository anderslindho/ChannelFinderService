package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.phoebus.channelfinder.api.v0.V0Json.array;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.names;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.tagNames;
import static org.phoebus.channelfinder.api.v0.V0Json.withChannels;
import static org.phoebus.channelfinder.api.v0.V0Json.withTags;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins the tag endpoints: the exclusive PUT, the merging POST, rename and single-channel writes, as
 * {@code src/site/sphinx/api.rst} documents them.
 */
class TagApiIT extends V0ApiTestBase {

  @BeforeEach
  void createChannels() {
    createTags("admin", "archived", "beam");
    putChannels(
        withTags(channel("ch1", "admin"), tag("archived", "admin"), tag("beam", "admin")),
        withTags(channel("ch2", "admin"), tag("archived", "admin")),
        channel("ch3", "admin"));
  }

  /** A tag body that adds the tag to each named channel, as pyCFClient builds it. */
  private static String onChannels(String name, String... channelNames) {
    ObjectNode[] channels = new ObjectNode[channelNames.length];
    for (int i = 0; i < channelNames.length; i++) {
      channels[i] = withTags(channel(channelNames[i], "admin"), tag(name, "admin"));
    }
    return withChannels(tag(name, "admin"), channels).toString();
  }

  private List<String> channelsWith(String tag) {
    return names(cf.get("/resources/channels?~tag=" + tag).json());
  }

  @Test
  void getListsTheChannelsThatHaveTheTag() {
    V0Client.Response response = cf.get("/resources/tags/beam");

    assertEquals(200, response.status());
    assertEquals(List.of("ch1"), names(response.json().get("channels")));
    assertEquals(
        0, cf.get("/resources/tags/beam?withChannels=false").json().get("channels").size());
  }

  @Test
  void putWithChannelsTagsThoseChannels() {
    assertEquals(
        200, cf.put("/resources/tags/beam", onChannels("beam", "ch3"), V0Client.ADMIN).status());

    assertEquals(List.of("ch3"), channelsWith("beam"));
  }

  @Test
  void putIsExclusiveAndUntagsChannelsNotInThePayload() {
    cf.put("/resources/tags/archived", onChannels("archived", "ch3"), V0Client.ADMIN);

    assertEquals(List.of("ch3"), channelsWith("archived"));
    assertEquals(List.of("beam"), tagNames(cf.get("/resources/channels/ch1").json()));
  }

  @Test
  void putWithoutChannelsUntagsEveryChannel() {
    cf.put("/resources/tags/archived", tag("archived", "admin").toString(), V0Client.ADMIN);

    assertEquals(200, cf.get("/resources/tags/archived").status());
    assertEquals(List.of(), channelsWith("archived"));
  }

  @Test
  void batchPutCreatesEachDefinition() {
    String tags = array(tag("alarm", "admin"), tag("vacuum", "admin")).toString();

    assertEquals(200, cf.put("/resources/tags", tags, V0Client.ADMIN).status());
    assertEquals(
        List.of("alarm", "archived", "beam", "vacuum"), names(cf.get("/resources/tags").json()));
  }

  @Test
  void postWithChannelsMergesAndKeepsTheOtherChannels() {
    assertEquals(
        200, cf.post("/resources/tags/beam", onChannels("beam", "ch3"), V0Client.ADMIN).status());

    assertEquals(List.of("ch1", "ch3"), channelsWith("beam"));
  }

  @Test
  void postWithoutChannelsCreatesAMissingDefinition() {
    assertEquals(
        200,
        cf.post("/resources/tags/alarm", tag("alarm", "admin").toString(), V0Client.ADMIN)
            .status());
    assertEquals(200, cf.get("/resources/tags/alarm").status());
  }

  @Test
  void postWithANewNameAddsTheNewTagButKeepsTheOldOneOnChannels() {
    assertEquals(
        200,
        cf.post("/resources/tags/archived", tag("saved", "admin").toString(), V0Client.ADMIN)
            .status());

    assertEquals(404, cf.get("/resources/tags/archived").status());
    assertEquals(List.of("ch1", "ch2"), channelsWith("saved"));
    // One would expect the old tag to disappear, as the old property does on a property rename.
    // It stays on the channels, because the channels read before the delete are written back.
    assertEquals(List.of("ch1", "ch2"), channelsWith("archived"));
  }

  @Test
  void putOnASingleChannelAddsTheTag() {
    assertEquals(200, cf.put("/resources/tags/beam/ch3", "", V0Client.ADMIN).status());

    assertEquals(List.of("ch1", "ch3"), channelsWith("beam"));
    assertEquals(404, cf.put("/resources/tags/beam/missing", "", V0Client.ADMIN).status());
  }

  @Test
  void deleteRemovesTheDefinitionAndUntagsEveryChannel() {
    assertEquals(200, cf.delete("/resources/tags/archived", V0Client.ADMIN).status());

    assertEquals(404, cf.get("/resources/tags/archived").status());
    assertEquals(List.of("beam"), tagNames(cf.get("/resources/channels/ch1").json()));
    assertEquals(List.of(), tagNames(cf.get("/resources/channels/ch2").json()));
  }

  @Test
  void deleteFromASingleChannelLeavesTheOthers() {
    assertEquals(200, cf.delete("/resources/tags/archived/ch1", V0Client.ADMIN).status());

    assertEquals(List.of("ch2"), channelsWith("archived"));
  }
}
