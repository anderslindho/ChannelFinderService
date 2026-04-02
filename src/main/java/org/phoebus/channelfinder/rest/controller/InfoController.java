package org.phoebus.channelfinder.rest.controller;

import org.phoebus.channelfinder.rest.api.IInfo;
import org.phoebus.channelfinder.service.InfoService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@EnableAutoConfiguration
@RequestMapping("${channelfinder.legacy.service-root:ChannelFinder}")
public class InfoController implements IInfo {

  private final InfoService infoService;

  public InfoController(InfoService infoService) {
    this.infoService = infoService;
  }

  @Override
  public String info() {
    return infoService.info();
  }
}
