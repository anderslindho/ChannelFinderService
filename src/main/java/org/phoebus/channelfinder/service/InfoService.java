package org.phoebus.channelfinder.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.phoebus.channelfinder.configuration.ElasticConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InfoService {

  private static final Logger logger = Logger.getLogger(InfoService.class.getName());

  private static final ObjectMapper objectMapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final ElasticConfig esService;

  @Value("${channelfinder.version:4.7.0}")
  private String version;

  public InfoService(ElasticConfig esService) {
    this.esService = esService;
  }

  public String info() {
    Map<String, Object> cfServiceInfo = new LinkedHashMap<>();
    cfServiceInfo.put("name", "ChannelFinder Service");
    cfServiceInfo.put("version", version);

    Map<String, String> elasticInfo = new LinkedHashMap<>();
    try {
      var client = esService.getSearchClient();
      var response = client.info();
      elasticInfo.put("status", "Connected");
      elasticInfo.put("clusterName", response.clusterName());
      elasticInfo.put("clusterUuid", response.clusterUuid());
      elasticInfo.put("version", response.version().number());
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to retrieve Elasticsearch info", e);
      elasticInfo.put("status", "Failed to connect to elastic " + e.getLocalizedMessage());
    }
    cfServiceInfo.put("elastic", elasticInfo);

    try {
      return objectMapper.writeValueAsString(cfServiceInfo);
    } catch (JsonProcessingException e) {
      logger.log(Level.WARNING, "Failed to serialize ChannelFinder service info", e);
      return "Failed to gather ChannelFinder service info";
    }
  }
}
