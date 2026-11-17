package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins a service root configured with slashes. {@code LegacyApiProperties} strips them, but the
 * controllers build their mappings from the raw property, so the resources end up behind a double
 * slash that the security firewall rejects.
 */
@TestPropertySource(properties = "channelfinder.legacy.service-root=/cf/api/")
class ServiceRootSlashesApiIT extends V0ApiTestBase {

  @Override
  String serviceUrl() {
    return "http://localhost:" + port;
  }

  @Test
  void resourcesAreUnreachable() {
    assertEquals(404, cf.get("/cf/api/resources/channels").status());
    assertEquals(400, cf.get("/cf/api//resources/channels").status());
  }
}
