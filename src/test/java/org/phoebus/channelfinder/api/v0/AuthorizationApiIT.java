package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins anonymous reads, authenticated writes and owner enforcement. Phoebus, pvinfo and
 * cfNameserver read without credentials.
 *
 * <p>The demo users are {@code admin} (role {@code ADMIN}, an admin group), {@code user} (roles
 * {@code USER} and {@code CF-CHANNELS}, which grant channel, property and tag writes) and {@code
 * guest} (no group).
 */
class AuthorizationApiIT extends V0ApiTestBase {

  @BeforeEach
  void createAdminOwnedData() {
    createProperties("admin", "area");
    createTags("admin", "archived");
    putChannels(
        withProperties(channel("adminChannel", "admin"), property("area", "admin", "north")));
  }

  @Test
  void readsNeedNoCredentials() {
    assertEquals(200, cf.get("/resources/channels").status());
    assertEquals(200, cf.get("/resources/channels/adminChannel").status());
    assertEquals(200, cf.get("/resources/channels/count").status());
    assertEquals(200, cf.get("/resources/channels/combined").status());
    assertEquals(200, cf.get("/resources/properties").status());
    assertEquals(200, cf.get("/resources/tags").status());
    assertEquals(200, cf.get("/resources/scroll").status());
  }

  @Test
  void writesWithoutCredentialsAreRejectedWith401() {
    String ch = channel("ch1", "admin").toString();

    assertEquals(401, cf.put("/resources/channels/ch1", ch, null).status());
    assertEquals(401, cf.post("/resources/channels/ch1", ch, null).status());
    assertEquals(401, cf.delete("/resources/channels/adminChannel", null).status());
    assertEquals(401, cf.put("/resources/tags/t", tag("t", "admin").toString(), null).status());
  }

  @Test
  void wrongPasswordIsRejectedWith401() {
    String ch = channel("ch1", "admin").toString();

    assertEquals(401, cf.put("/resources/channels/ch1", ch, "admin:wrong").status());
  }

  @Test
  void userWithoutAGroupIsRejectedWith401() {
    // One would expect 403 for a known user without the right role. The service answers 401.
    assertEquals(
        401,
        cf.put("/resources/channels/ch1", channel("ch1", "guest").toString(), V0Client.GUEST)
            .status());
    assertEquals(
        401, cf.put("/resources/tags/t", tag("t", "guest").toString(), V0Client.GUEST).status());
    assertEquals(
        401,
        cf.put("/resources/properties/p", property("p", "guest").toString(), V0Client.GUEST)
            .status());
  }

  @Test
  void userCanWriteChannelsItOwnsDirectlyOrThroughAGroup() {
    assertEquals(
        200,
        cf.put("/resources/channels/ch1", channel("ch1", "user").toString(), V0Client.USER)
            .status());
    assertEquals(
        200,
        cf.put("/resources/channels/ch2", channel("ch2", "USER").toString(), V0Client.USER)
            .status());
    assertEquals(200, cf.delete("/resources/channels/ch1", V0Client.USER).status());
  }

  @Test
  void userCannotCreateAChannelForAnotherOwner() {
    assertEquals(
        401,
        cf.put("/resources/channels/ch1", channel("ch1", "admin").toString(), V0Client.USER)
            .status());
  }

  @Test
  void userCannotChangeOrDeleteAChannelOwnedBySomeoneElse() {
    // One would expect 403 for a known user who does not own the channel. The service answers 401.
    String takeOver = channel("adminChannel", "user").toString();

    assertEquals(401, cf.put("/resources/channels/adminChannel", takeOver, V0Client.USER).status());
    assertEquals(
        401, cf.post("/resources/channels/adminChannel", takeOver, V0Client.USER).status());
    assertEquals(401, cf.delete("/resources/channels/adminChannel", V0Client.USER).status());
    assertEquals(
        "admin", cf.get("/resources/channels/adminChannel").json().get("owner").asString());
  }

  @Test
  void userCanUseAPropertyItDoesNotOwnOnItsOwnChannel() {
    // One might expect the owner of the property to be checked too. Only the channel owner is.
    String ch1 =
        withProperties(channel("ch1", "user"), property("area", "user", "south")).toString();

    assertEquals(200, cf.put("/resources/channels/ch1", ch1, V0Client.USER).status());
  }

  @Test
  void userCannotChangeADefinitionOwnedBySomeoneElse() {
    assertEquals(
        401,
        cf.put("/resources/properties/area", property("area", "user").toString(), V0Client.USER)
            .status());
    assertEquals(
        401,
        cf.put(
                "/resources/properties/area/adminChannel",
                property("area", "user", "south").toString(),
                V0Client.USER)
            .status());
    assertEquals(401, cf.delete("/resources/tags/archived", V0Client.USER).status());
  }

  @Test
  void adminCanChangeAnything() {
    putChannels(channel("userChannel", "user"));

    assertEquals(
        200,
        cf.post(
                "/resources/channels/userChannel",
                channel("userChannel", "admin").toString(),
                V0Client.ADMIN)
            .status());
    assertEquals(200, cf.delete("/resources/channels/userChannel", V0Client.ADMIN).status());
  }
}
