package org.phoebus.channelfinder.rest.controller;

import org.phoebus.channelfinder.entity.Scroll;
import org.phoebus.channelfinder.rest.api.IChannelScroll;
import org.phoebus.channelfinder.service.ChannelScrollService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@EnableAutoConfiguration
@RequestMapping("${channelfinder.legacy.service-root:ChannelFinder}/resources/scroll")
public class ChannelScrollController implements IChannelScroll {

  private final ChannelScrollService channelScrollService;

  public ChannelScrollController(ChannelScrollService channelScrollService) {
    this.channelScrollService = channelScrollService;
  }

  @Override
  public Scroll query(MultiValueMap<String, String> allRequestParams) {
    return channelScrollService.search(null, allRequestParams);
  }

  @Override
  public Scroll query(String scrollId, MultiValueMap<String, String> searchParameters) {
    return channelScrollService.search(scrollId, searchParameters);
  }
}
