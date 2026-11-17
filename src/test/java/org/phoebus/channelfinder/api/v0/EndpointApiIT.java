package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;
import static org.phoebus.channelfinder.api.v0.V0Json.withTags;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.JsonNode;

/**
 * Pins the endpoints under the service root, their status codes and the JSON shape of their
 * responses. Phoebus and the INFN feeder treat any status other than exactly 200 as a failure.
 */
class EndpointApiIT extends V0ApiTestBase {

  @Test
  void serviceRootReturnsServiceInfoAsJson() {
    V0Client.Response response = cf.get("");

    assertEquals(200, response.status());
    JsonNode info = response.json();
    assertEquals("ChannelFinder Service", info.get("name").asString());
    assertEquals("Connected", info.get("elastic").get("status").asString());
  }

  @Test
  void emptyDirectoryListsReturn200AndEmptyArrays() {
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/channels"));
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/properties"));
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/tags"));
  }

  @Test
  void searchWithoutMatchesReturns200AndAnEmptyArrayNever404() {
    // One might expect 404 when nothing matches. The service answers 200 with []. recCeiver
    // treats any other answer to an empty lookup as a failure (recsync #73).
    createProperties("admin", "iocid");
    createTags("admin", "archived");
    putChannels(
        withTags(
            withProperties(channel("ch1", "admin"), property("iocid", "admin", "ioc1")),
            tag("archived", "admin")));

    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/channels?~name=nothing*"));
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/channels?iocid=ioc2"));
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/channels?undefined=x"));
    assertEquals(new V0Client.Response(200, "[]"), cf.get("/resources/channels?~tag=nothing"));
    assertEquals(
        new V0Client.Response(200, "[]"),
        cf.get("/resources/channels?~name=ch1&iocid=ioc1&~tag=nothing"));
  }

  @Test
  void countReturnsABareInteger() {
    assertEquals(new V0Client.Response(200, "0"), cf.get("/resources/channels/count"));
  }

  @Test
  void combinedReturnsChannelsAndCount() throws Exception {
    V0Client.Response response = cf.get("/resources/channels/combined");

    assertEquals(200, response.status());
    JSONAssert.assertEquals(
        "{\"channels\":[],\"count\":0}", response.body(), JSONCompareMode.STRICT);
  }

  @Test
  void scrollReturnsIdAndChannels() throws Exception {
    V0Client.Response response = cf.get("/resources/scroll");

    assertEquals(200, response.status());
    JSONAssert.assertEquals(
        "{\"id\":null,\"channels\":[]}", response.body(), JSONCompareMode.STRICT);
  }

  @Test
  void processorsReportCountAndInfo() {
    // The archiver processor is off, but the test classpath contributes processors of its own
    // (ElasticConfig scans the whole package), so only the shape is pinned here.
    V0Client.Response count = cf.get("/resources/processors/count");
    V0Client.Response info = cf.get("/resources/processors/processors");

    assertEquals(200, count.status());
    assertEquals(200, info.status());
    assertEquals(Long.parseLong(count.body()), info.json().size());
    for (JsonNode processor : info.json()) {
      assertEquals(
          List.of("name", "enabled", "properties"), processor.propertyNames().stream().toList());
    }
  }

  @Test
  void missingSingleResourcesReturn404() {
    assertEquals(404, cf.get("/resources/channels/missing").status());
    assertEquals(404, cf.get("/resources/properties/missing").status());
    assertEquals(404, cf.get("/resources/tags/missing").status());
  }

  @Test
  void deletingMissingResourcesReturns404() {
    assertEquals(404, cf.delete("/resources/channels/missing", V0Client.ADMIN).status());
    assertEquals(404, cf.delete("/resources/properties/missing", V0Client.ADMIN).status());
    assertEquals(404, cf.delete("/resources/tags/missing", V0Client.ADMIN).status());
  }

  @Test
  void successfulWritesReturnExactly200() {
    assertEquals(
        200,
        cf.put("/resources/properties/area", property("area", "admin").toString(), V0Client.ADMIN)
            .status());
    assertEquals(
        200,
        cf.put("/resources/tags/archived", tag("archived", "admin").toString(), V0Client.ADMIN)
            .status());
    String ch1 =
        withTags(
                withProperties(channel("ch1", "admin"), property("area", "admin", "north")),
                tag("archived", "admin"))
            .toString();
    assertEquals(200, cf.put("/resources/channels/ch1", ch1, V0Client.ADMIN).status());
    assertEquals(200, cf.post("/resources/channels/ch1", ch1, V0Client.ADMIN).status());
    assertEquals(200, cf.put("/resources/tags/archived/ch1", "", V0Client.ADMIN).status());
    assertEquals(
        200,
        cf.put(
                "/resources/properties/area/ch1",
                property("area", "admin", "south").toString(),
                V0Client.ADMIN)
            .status());
    assertEquals(200, cf.delete("/resources/tags/archived/ch1", V0Client.ADMIN).status());
    assertEquals(200, cf.delete("/resources/properties/area/ch1", V0Client.ADMIN).status());
    assertEquals(200, cf.delete("/resources/channels/ch1", V0Client.ADMIN).status());
    assertEquals(200, cf.delete("/resources/tags/archived", V0Client.ADMIN).status());
    assertEquals(200, cf.delete("/resources/properties/area", V0Client.ADMIN).status());
  }

  @Test
  void channelResponseShape() throws Exception {
    createProperties("admin", "area");
    createTags("admin", "archived");
    putChannels(
        withTags(
            withProperties(channel("ch1", "admin"), property("area", "admin", "north")),
            tag("archived", "admin")));

    V0Client.Response response = cf.get("/resources/channels/ch1");

    assertEquals(200, response.status());
    JSONAssert.assertEquals(
        """
        {"name":"ch1","owner":"admin",
         "properties":[{"name":"area","owner":"admin","value":"north","channels":[]}],
         "tags":[{"name":"archived","owner":"admin","channels":[]}]}
        """,
        response.body(),
        JSONCompareMode.STRICT);
  }

  @Test
  void definitionResponseShape() throws Exception {
    createProperties("admin", "area");
    createTags("admin", "archived");

    JSONAssert.assertEquals(
        "[{\"name\":\"area\",\"owner\":\"admin\",\"value\":null,\"channels\":[]}]",
        cf.get("/resources/properties").body(),
        JSONCompareMode.STRICT);
    JSONAssert.assertEquals(
        "[{\"name\":\"archived\",\"owner\":\"admin\",\"channels\":[]}]",
        cf.get("/resources/tags").body(),
        JSONCompareMode.STRICT);
  }

  @Test
  void writingAChannelWithAnUndefinedPropertyReturns404() {
    String ch1 =
        withProperties(channel("ch1", "admin"), property("undefined", "admin", "x")).toString();

    assertEquals(404, cf.put("/resources/channels/ch1", ch1, V0Client.ADMIN).status());
  }

  @Test
  void malformedBodyReturns400() {
    assertEquals(400, cf.put("/resources/channels/ch1", "{not json", V0Client.ADMIN).status());
  }
}
