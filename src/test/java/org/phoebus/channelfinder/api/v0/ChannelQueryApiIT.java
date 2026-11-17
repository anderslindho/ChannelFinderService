package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.names;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;
import static org.phoebus.channelfinder.api.v0.V0Json.withTags;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pins the channel query language on {@code GET /channels}, as Phoebus, pyCFClient, pvinfo and
 * recCeiver use it. Results are compared as ordered name lists, so every test also pins the sort.
 */
class ChannelQueryApiIT extends V0ApiTestBase {

  // Two channels share the prefix MNO. DEF_UPPER and DEF_LOWER differ only in case. GHI has no
  // properties and no tags.
  private static final String DEF_UPPER = "DEF:001";
  private static final String GHI = "GHI:JKL";
  private static final String MNO_1 = "MNO:001";
  private static final String MNO_2 = "MNO:002";
  private static final String DEF_LOWER = "def:003";

  @BeforeEach
  void createChannels() {
    createProperties("admin", "area", "number", "pvStatus");
    createTags("admin", "archived", "beam");
    putChannels(
        withTags(
            withProperties(
                channel(MNO_1, "admin"),
                property("area", "admin", "north"),
                property("number", "admin", "001"),
                property("pvStatus", "admin", "Active")),
            tag("archived", "admin")),
        withTags(
            withProperties(
                channel(MNO_2, "admin"),
                property("area", "admin", "north"),
                property("number", "admin", "002"),
                property("pvStatus", "admin", "Inactive")),
            tag("archived", "admin"),
            tag("beam", "admin")),
        withProperties(
            channel(DEF_UPPER, "admin"),
            property("area", "admin", "South"),
            property("number", "admin", "001"),
            property("pvStatus", "admin", "Active")),
        withTags(
            withProperties(channel(DEF_LOWER, "admin"), property("area", "admin", "south")),
            tag("beam", "admin")),
        channel(GHI, "admin"));
  }

  private List<String> query(String query) {
    V0Client.Response response = cf.get("/resources/channels?" + query);
    assertEquals(200, response.status(), response.body());
    return names(response.json());
  }

  @Test
  void noFilterReturnsEverythingSortedByNameInByteOrder() {
    assertEquals(
        List.of(DEF_UPPER, GHI, MNO_1, MNO_2, DEF_LOWER),
        names(cf.get("/resources/channels").json()));
  }

  @Test
  void nameIsAGlob() {
    assertEquals(List.of(MNO_1, MNO_2), query("~name=MNO:*"));
    assertEquals(List.of(MNO_1, MNO_2), query("~name=MNO:00?"));
    assertEquals(List.of(GHI), query("~name=GHI:JKL"));
  }

  @Test
  void nameMatchingIsCaseInsensitive() {
    assertEquals(List.of(MNO_1), query("~name=mno:001*"));
    assertEquals(List.of(DEF_UPPER, DEF_LOWER), query("~name=DEF:*"));
  }

  @Test
  void pipeCommaAndSemicolonAlternateNames() {
    assertEquals(List.of(DEF_UPPER, GHI), query("~name=GHI:JKL|DEF:001"));
    assertEquals(List.of(DEF_UPPER, GHI), query("~name=GHI:JKL,DEF:001"));
    assertEquals(List.of(DEF_UPPER, GHI), query("~name=GHI:JKL;DEF:001"));
  }

  @Test
  void repeatedNameParametersAreAnded() {
    assertEquals(List.of(MNO_2), query("~name=MNO:*&~name=*002*"));
  }

  @Test
  void negatedNameIsNotNegated() {
    // One would expect every channel except GHI:JKL. The `!` is ignored for `~name`, so only
    // GHI:JKL is returned. Properties and tags do honour the `!`.
    assertEquals(List.of(GHI), query("~name!=GHI:JKL"));
  }

  @Test
  void tagMatchesByNameCaseInsensitively() {
    assertEquals(List.of(MNO_1, MNO_2), query("~tag=archived"));
    assertEquals(List.of(MNO_1, MNO_2), query("~tag=ARCH*"));
  }

  @Test
  void tagAlternationIsOrAndRepetitionIsAnd() {
    assertEquals(List.of(MNO_1, MNO_2, DEF_LOWER), query("~tag=archived|beam"));
    assertEquals(List.of(MNO_2), query("~tag=archived&~tag=beam"));
  }

  @Test
  void negatedTagExcludesChannelsWithTheTag() {
    assertEquals(List.of(DEF_UPPER, GHI, DEF_LOWER), query("~tag!=archived"));
  }

  @Test
  void propertyValueIsACaseInsensitiveGlob() {
    assertEquals(List.of(MNO_1, MNO_2), query("area=north"));
    assertEquals(List.of(DEF_UPPER, DEF_LOWER), query("area=south"));
    assertEquals(List.of(DEF_UPPER, MNO_1), query("number=*1"));
  }

  @Test
  void propertyNameMatchingIsCaseInsensitive() {
    assertEquals(List.of(MNO_1, MNO_2), query("AREA=north"));
  }

  @Test
  void propertyAlternationIsOr() {
    assertEquals(List.of(DEF_UPPER, MNO_1, MNO_2), query("number=001|002"));
    assertEquals(List.of(DEF_UPPER, MNO_1, MNO_2), query("number=001,002"));
    assertEquals(List.of(DEF_UPPER, MNO_1, MNO_2), query("number=001;002"));
  }

  @Test
  void repeatedPropertyParametersAreOred() {
    // One would expect repeated parameters to combine the same way. A repeated property is ORed,
    // while repeated `~name` and `~tag` parameters are ANDed.
    assertEquals(List.of(DEF_UPPER, MNO_1, MNO_2, DEF_LOWER), query("area=north&area=South"));
  }

  @Test
  void differentPropertiesAreAnded() {
    assertEquals(List.of(MNO_1), query("area=north&pvStatus=Active"));
  }

  @Test
  void negatedPropertyValueMatchesOnlyChannelsThatHaveTheProperty() {
    // One might expect channels without the property to match too. They do not; `area!=*` is the
    // query for those.
    assertEquals(List.of(DEF_UPPER, DEF_LOWER), query("area!=north"));
  }

  @Test
  void negatedWildcardMatchesChannelsWithoutTheProperty() {
    assertEquals(List.of(GHI, DEF_LOWER), query("number!=*"));
  }

  @Test
  void wildcardValueIsAnExistenceQuery() {
    assertEquals(List.of(DEF_UPPER, MNO_1, MNO_2), query("number=*"));
  }

  @Test
  void bareKeyMatchesNothing() {
    // pvinfo sends a bare `key` and expects every channel that has the property. The service uses
    // the empty value as a pattern, and no stored value is empty, so nothing matches.
    assertEquals(List.of(), query("number"));
  }

  @Test
  void propertiesTagsAndNamesCombineWithAnd() {
    assertEquals(List.of(MNO_2), query("~name=MNO:*&~tag=beam&area=north"));
  }

  @Test
  void sizeAndFromPage() {
    assertEquals(List.of(DEF_UPPER, GHI), query("~size=2"));
    assertEquals(List.of(MNO_1, MNO_2), query("~size=2&~from=2"));
  }

  @Test
  void repeatedSizeTakesTheLargest() {
    // One might expect the first or the last value to win. The largest one does.
    assertEquals(List.of(DEF_UPPER, GHI, MNO_1), query("~size=1&~size=3"));
  }

  @Test
  void searchAfterContinuesAfterTheNamedChannel() {
    assertEquals(List.of(MNO_1, MNO_2, DEF_LOWER), query("~search_after=GHI:JKL"));
    assertEquals(List.of(MNO_1), query("~search_after=GHI:JKL&~size=1"));
  }

  @Test
  void sizePlusFromAboveTheMaxResultWindowIsRejectedWith400() {
    assertEquals(200, cf.get("/resources/channels?~size=10000").status());
    assertEquals(400, cf.get("/resources/channels?~size=10001").status());
    assertEquals(400, cf.get("/resources/channels?~size=2&~from=9999").status());
  }

  @Test
  void defaultSizeIsTenThousand() {
    putChannels(
        IntStream.range(0, 10_000)
            .mapToObj(i -> channel(String.format("ZZ:%05d", i), "admin"))
            .toArray(ObjectNode[]::new));

    assertEquals(10_000, cf.get("/resources/channels").json().size());
    assertEquals("10005", cf.get("/resources/channels/count").body());
  }

  @Test
  void nonNumericSizeIsAServerError() {
    // One would expect 400 for a malformed parameter. The service answers 500.
    assertEquals(500, cf.get("/resources/channels?~size=ten").status());
  }

  @Test
  void countAppliesFiltersAndIgnoresPaging() {
    assertEquals("2", cf.get("/resources/channels/count?~name=MNO:*&~size=1").body());
    assertEquals("5", cf.get("/resources/channels/count").body());
    assertEquals("0", cf.get("/resources/channels/count?~name=nothing").body());
  }

  @Test
  void combinedCountIsThePageSizeUnlessTotalHitsAreTracked() {
    // One would expect `count` to be the total number of matches. Without `~track_total_hits` it
    // is the number of channels on the page.
    V0Client.Response untracked = cf.get("/resources/channels/combined?~size=2");
    V0Client.Response tracked =
        cf.get("/resources/channels/combined?~size=2&~track_total_hits=true");

    assertEquals(200, untracked.status());
    assertEquals(List.of(DEF_UPPER, GHI), names(untracked.json().get("channels")));
    assertEquals(2, untracked.json().get("count").asLong());
    assertEquals(200, tracked.status());
    assertEquals(List.of(DEF_UPPER, GHI), names(tracked.json().get("channels")));
    assertEquals(5, tracked.json().get("count").asLong());
  }
}
