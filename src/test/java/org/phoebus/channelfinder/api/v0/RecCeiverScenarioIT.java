package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.names;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.propertyValue;
import static org.phoebus.channelfinder.api.v0.V0Json.withChannels;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Replays the calls that recCeiver makes to ChannelFinder, in the order it makes them, and pins
 * what ChannelFinder does with each. The sequences follow {@code server/recceiver/cf} in recsync.
 */
class RecCeiverScenarioIT extends V0ApiTestBase {

  private static final String OWNER = "admin";
  private static final String RECCEIVER_ID = "recceiver1";
  // `cfQueryLimit` in the recCeiver configuration; small here so the clean loop takes two rounds.
  private static final int QUERY_LIMIT = 2;
  private static final List<String> MANAGED_PROPERTIES =
      List.of("hostName", "iocName", "iocid", "iocIP", "pvStatus", "time", "recceiverID");

  /** {@code _setup_cf_properties}: define every required property that is not defined yet. */
  private void provisionProperties() {
    Set<String> missing = new LinkedHashSet<>(MANAGED_PROPERTIES);
    names(cf.get("/resources/properties").json()).forEach(missing::remove);
    for (String name : missing) {
      assertEquals(
          200,
          cf.put("/resources/properties/" + name, property(name, OWNER).toString(), V0Client.ADMIN)
              .status());
    }
  }

  private static ObjectNode recordChannel(String name, String iocid, String time) {
    return withProperties(
        channel(name, OWNER),
        property("hostName", OWNER, "host1"),
        property("iocName", OWNER, "IOC1"),
        property("iocid", OWNER, iocid),
        property("iocIP", OWNER, "10.0.0.1"),
        property("pvStatus", OWNER, "Active"),
        property("time", OWNER, time),
        property("recceiverID", OWNER, RECCEIVER_ID));
  }

  private List<JsonNode> find(String query) {
    V0Client.Response response = cf.get("/resources/channels?" + query + "&~size=" + QUERY_LIMIT);
    assertEquals(200, response.status(), response.body());
    List<JsonNode> channels = new ArrayList<>();
    response.json().forEach(channels::add);
    return channels;
  }

  /**
   * {@code _update_channelfinder} for new records: look the IOC and the record names up, keep the
   * properties recCeiver does not manage, and PUT the whole channels back.
   */
  private void commit(String iocid, String time, String... records) {
    find("iocid=" + iocid);
    List<JsonNode> existing = find("~name=" + String.join("|", records));

    List<ObjectNode> channels = new ArrayList<>();
    for (String record : records) {
      ObjectNode channel = recordChannel(record, iocid, time);
      existing.stream()
          .filter(old -> old.get("name").asString().equals(record))
          .flatMap(old -> old.get("properties").valueStream())
          .filter(p -> !MANAGED_PROPERTIES.contains(p.get("name").asString()))
          .forEach(p -> ((ArrayNode) channel.get("properties")).add(p));
      channels.add(channel);
    }
    putChannels(channels.toArray(ObjectNode[]::new));
  }

  /** {@code clean_service}: mark every Active channel of this recCeiver Inactive, page by page. */
  private int clean() {
    int rounds = 0;
    List<JsonNode> active = find("pvStatus=Active&recceiverID=" + RECCEIVER_ID);
    while (!active.isEmpty()) {
      ObjectNode[] channels =
          active.stream()
              .map(
                  ch ->
                      withProperties(
                          channel(ch.get("name").asString(), OWNER),
                          property("pvStatus", OWNER, "Inactive")))
              .toArray(ObjectNode[]::new);
      String body = withChannels(property("pvStatus", OWNER, "Inactive"), channels).toString();
      assertEquals(200, cf.post("/resources/properties/pvStatus", body, V0Client.ADMIN).status());
      rounds++;
      assertTrue(rounds < 10, "clean loop does not terminate");
      active = find("pvStatus=Active&recceiverID=" + RECCEIVER_ID);
    }
    return rounds;
  }

  @Test
  void startUpProvisioningLeavesExistingPropertiesAndTheirValuesAlone() {
    createProperties("curator", "hostName");
    putChannels(withProperties(channel("curated", OWNER), property("hostName", OWNER, "curated")));

    provisionProperties();

    assertEquals(
        List.of("hostName", "iocIP", "iocName", "iocid", "pvStatus", "recceiverID", "time"),
        names(cf.get("/resources/properties").json()));
    assertEquals(
        "curator", cf.get("/resources/properties/hostName").json().get("owner").asString());
    assertEquals(
        "curated", propertyValue(cf.get("/resources/channels/curated").json(), "hostName"));
  }

  @Test
  void firstLookupsOfANewIocReturnEmptyLists() {
    provisionProperties();

    assertEquals(List.of(), find("iocid=host1:5064"));
    assertEquals(List.of(), find("~name=rec1|rec2"));
  }

  @Test
  void commitKeepsCuratedPropertiesThroughReadMergeWrite() {
    provisionProperties();
    createProperties("curator", "archive");
    putChannels(
        withProperties(channel("rec1", OWNER), property("archive", "curator", "monitor 1.0")));

    commit("host1:5064", "2026-11-17 10:00:00", "rec1", "rec2");

    assertEquals(
        List.of("rec1", "rec2"), names(cf.get("/resources/channels?iocid=host1:5064").json()));
    JsonNode rec1 = cf.get("/resources/channels/rec1").json();
    assertEquals("monitor 1.0", propertyValue(rec1, "archive"));
    assertEquals("Active", propertyValue(rec1, "pvStatus"));
    assertEquals(RECCEIVER_ID, propertyValue(rec1, "recceiverID"));
  }

  @Test
  void recommitAfterAnIocRestartReplacesTheManagedValues() {
    provisionProperties();
    commit("host1:5064", "2026-11-17 10:00:00", "rec1");

    commit("host1:5064", "2026-11-17 11:00:00", "rec1");

    assertEquals(
        "2026-11-17 11:00:00", propertyValue(cf.get("/resources/channels/rec1").json(), "time"));
  }

  @Test
  void cleanLoopTerminatesAndOnlyTouchesThisRecCeiversChannels() {
    provisionProperties();
    commit("host1:5064", "2026-11-17 10:00:00", "rec1", "rec2", "rec3");
    putChannels(
        withProperties(
            channel("other", OWNER),
            property("pvStatus", OWNER, "Active"),
            property("recceiverID", OWNER, "recceiver2")));

    int rounds = clean();

    assertEquals(2, rounds);
    for (String record : List.of("rec1", "rec2", "rec3")) {
      JsonNode channel = cf.get("/resources/channels/" + record).json();
      assertEquals("Inactive", propertyValue(channel, "pvStatus"));
      assertEquals("host1:5064", propertyValue(channel, "iocid"));
    }
    assertEquals("Active", propertyValue(cf.get("/resources/channels/other").json(), "pvStatus"));
  }
}
