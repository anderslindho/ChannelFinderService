package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.phoebus.channelfinder.Application;
import org.phoebus.channelfinder.ElasticsearchTestBase;
import org.phoebus.channelfinder.configuration.ElasticConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.node.ObjectNode;

/**
 * Base for these tests: the whole application on a random port, the production {@code
 * application.properties}, and a real Elasticsearch.
 *
 * <p>Only what a random port and a shared test JVM require is overridden. TLS and the extra plain
 * HTTP connector are switched off because both bind fixed ports, the archiver processor is switched
 * off because it would call out to an archiver, and a third demo user without any role is added.
 * Every test starts from empty indices.
 */
@SpringBootTest(
    classes = Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "server.ssl.enabled=false",
      "server.http.enable=false",
      "aa.enabled=false",
      "elasticsearch.channel.index=v0_api_channelfinder",
      "elasticsearch.tag.index=v0_api_cf_tags",
      "elasticsearch.property.index=v0_api_cf_properties",
      "demo_auth.users=admin,user,guest",
      "demo_auth.pwds=adminPass,userPass,guestPass",
      // AuthorizationService keeps the role groups in static fields, and every Spring context in
      // the test JVM overwrites them. The groups can therefore come from another test's property
      // file. CF-CHANNELS is a channel group in both application.properties and
      // application_test.properties, so the user can write either way.
      "demo_auth.roles=ADMIN,USER:CF-CHANNELS,GUEST"
    })
abstract class V0ApiTestBase extends ElasticsearchTestBase {

  static final String ROOT = "/ChannelFinder";

  @LocalServerPort int port;

  @Autowired ElasticConfig elasticConfig;

  V0Client cf;

  @BeforeEach
  void emptyIndicesAndConnect() throws IOException {
    ElasticsearchClient client = elasticConfig.getSearchClient();
    for (String index :
        new String[] {
          elasticConfig.getES_CHANNEL_INDEX(),
          elasticConfig.getES_PROPERTY_INDEX(),
          elasticConfig.getES_TAG_INDEX()
        }) {
      // Batches above the max result window, which follows the query size, are rejected.
      client.deleteByQuery(
          d ->
              d.index(index)
                  .query(q -> q.matchAll(m -> m))
                  .scrollSize((long) elasticConfig.getES_QUERY_SIZE())
                  .refresh(true));
    }
    cf = new V0Client(serviceUrl());
  }

  String serviceUrl() {
    return "http://localhost:" + port + ROOT;
  }

  void createProperties(String owner, String... names) {
    for (String name : names) {
      assertEquals(
          200,
          cf.put(
                  "/resources/properties/" + name,
                  V0Json.property(name, owner).toString(),
                  V0Client.ADMIN)
              .status());
    }
  }

  void createTags(String owner, String... names) {
    for (String name : names) {
      assertEquals(
          200,
          cf.put("/resources/tags/" + name, V0Json.tag(name, owner).toString(), V0Client.ADMIN)
              .status());
    }
  }

  void putChannels(ObjectNode... channels) {
    assertEquals(
        200,
        cf.put("/resources/channels", V0Json.array(channels).toString(), V0Client.ADMIN).status());
  }
}
