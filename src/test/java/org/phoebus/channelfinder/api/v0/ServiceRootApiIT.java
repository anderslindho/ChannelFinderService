package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins {@code channelfinder.legacy.service-root}, which multi-service deployments behind one
 * reverse proxy set to tell the services apart.
 */
@TestPropertySource(properties = "channelfinder.legacy.service-root=cf-api")
class ServiceRootApiIT extends V0ApiTestBase {

  @Override
  String serviceUrl() {
    return "http://localhost:" + port;
  }

  @Test
  void endpointsMoveUnderTheConfiguredRoot() {
    assertEquals(200, cf.get("/cf-api").status());
    assertEquals(200, cf.get("/cf-api/resources/channels").status());
    assertEquals(200, cf.get("/cf-api/resources/properties").status());
    assertEquals(200, cf.get("/cf-api/resources/tags").status());
    assertEquals(200, cf.get("/cf-api/resources/scroll").status());
  }

  @Test
  void defaultRootIsNoLongerServed() {
    assertEquals(404, cf.get("/ChannelFinder/resources/channels").status());
  }
}
