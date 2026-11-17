package org.phoebus.channelfinder.api.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.phoebus.channelfinder.api.v0.V0Json.array;
import static org.phoebus.channelfinder.api.v0.V0Json.channel;
import static org.phoebus.channelfinder.api.v0.V0Json.property;
import static org.phoebus.channelfinder.api.v0.V0Json.tag;
import static org.phoebus.channelfinder.api.v0.V0Json.withChannels;
import static org.phoebus.channelfinder.api.v0.V0Json.withProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.phoebus.channelfinder.configuration.ChannelProcessor;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.service.model.archiver.ChannelProcessorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins which writes reach the channel processors. A recording processor stands in for the archiver
 * processor, which stays off.
 *
 * <p>Single and batch channel PUT and POST reach the processors. Property writes, tag writes and
 * channel deletes do not. This includes the {@code POST /properties/pvStatus} that recCeiver sends
 * when it cleans up, so {@code aa.auto_pause} does not see those channels go inactive.
 */
@Import(ProcessorTriggerApiIT.RecordingProcessor.class)
@TestPropertySource(
    properties = {
      "elasticsearch.channel.index=v0_api_processor_channelfinder",
      "elasticsearch.tag.index=v0_api_processor_cf_tags",
      "elasticsearch.property.index=v0_api_processor_cf_properties"
    })
class ProcessorTriggerApiIT extends V0ApiTestBase {

  /** Deliberately not a component: it must only exist in this test's context. */
  static class RecordingProcessor implements ChannelProcessor {

    final Set<String> processed = ConcurrentHashMap.newKeySet();

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public void setEnabled(boolean enabled) {}

    @Override
    public ChannelProcessorInfo processorInfo() {
      return new ChannelProcessorInfo("RecordingProcessor", true, Map.of());
    }

    @Override
    public long process(List<Channel> channels) {
      channels.forEach(channel -> processed.add(channel.getName()));
      return channels.size();
    }
  }

  @Autowired RecordingProcessor processor;

  @BeforeEach
  void createDefinitionsAndForgetEarlierCalls() throws InterruptedException {
    createProperties("admin", "pvStatus");
    createTags("admin", "archived");
    putChannels(channel("ch1", "admin"), channel("ch2", "admin"));
    awaitProcessed("ch1");
    awaitProcessed("ch2");
    processor.processed.clear();
  }

  private void awaitProcessed(String name) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
    while (!processor.processed.contains(name) && Instant.now().isBefore(deadline)) {
      Thread.sleep(20);
    }
    assertTrue(processor.processed.contains(name), name + " was not processed");
  }

  /**
   * Processors run asynchronously, so absence is checked after a later channel write has been
   * processed.
   */
  private void assertNotProcessedBeforeTheNextChannelWrite(String... names)
      throws InterruptedException {
    putChannels(channel("barrier", "admin"));
    awaitProcessed("barrier");
    for (String name : names) {
      assertFalse(processor.processed.contains(name), name + " was processed");
    }
  }

  @Test
  void channelPutAndPostReachTheProcessors() throws InterruptedException {
    cf.put("/resources/channels/ch1", channel("ch1", "admin").toString(), V0Client.ADMIN);
    awaitProcessed("ch1");
    cf.post("/resources/channels/ch2", channel("ch2", "admin").toString(), V0Client.ADMIN);
    awaitProcessed("ch2");
    cf.post("/resources/channels", array(channel("ch3", "admin")).toString(), V0Client.ADMIN);
    awaitProcessed("ch3");
  }

  @Test
  void propertyWriteDoesNotReachTheProcessors() throws InterruptedException {
    String inactive =
        withChannels(
                property("pvStatus", "admin", "Inactive"),
                withProperties(channel("ch1", "admin"), property("pvStatus", "admin", "Inactive")))
            .toString();

    assertEquals(200, cf.post("/resources/properties/pvStatus", inactive, V0Client.ADMIN).status());
    assertNotProcessedBeforeTheNextChannelWrite("ch1");
  }

  @Test
  void tagWriteDoesNotReachTheProcessors() throws InterruptedException {
    assertEquals(200, cf.put("/resources/tags/archived/ch1", "", V0Client.ADMIN).status());
    assertEquals(
        200,
        cf.post(
                "/resources/tags/archived",
                withChannels(tag("archived", "admin"), channel("ch2", "admin")).toString(),
                V0Client.ADMIN)
            .status());
    assertNotProcessedBeforeTheNextChannelWrite("ch1", "ch2");
  }

  @Test
  void channelDeleteDoesNotReachTheProcessors() throws InterruptedException {
    assertEquals(200, cf.delete("/resources/channels/ch1", V0Client.ADMIN).status());
    assertNotProcessedBeforeTheNextChannelWrite("ch1");
  }
}
