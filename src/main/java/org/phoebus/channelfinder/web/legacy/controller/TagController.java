package org.phoebus.channelfinder.web.legacy.controller;

import org.phoebus.channelfinder.entity.Tag;
import org.phoebus.channelfinder.service.TagService;
import org.phoebus.channelfinder.web.legacy.api.ITag;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableAutoConfiguration
@RequestMapping("${channelfinder.legacy.service-root:ChannelFinder}/resources/tags")
public class TagController implements ITag {

  private final TagService tagService;

  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

  @Override
  public Iterable<Tag> list() {
    return tagService.list();
  }

  @Override
  public Tag read(String tagName, boolean withChannels) {
    return tagService.read(tagName, withChannels);
  }

  @Override
  public Tag create(String tagName, Tag tag) {
    return tagService.create(tagName, tag);
  }

  @Override
  public Iterable<Tag> create(Iterable<Tag> tags) {
    return tagService.create(tags);
  }

  @Override
  public Tag addSingle(String tagName, String channelName) {
    return tagService.addSingle(tagName, channelName);
  }

  @Override
  public Tag update(String tagName, Tag tag) {
    return tagService.update(tagName, tag);
  }

  @Override
  public Iterable<Tag> update(Iterable<Tag> tags) {
    return tagService.update(tags);
  }

  @Override
  public void remove(String tagName) {
    tagService.remove(tagName);
  }

  @Override
  public void removeSingle(String tagName, String channelName) {
    tagService.removeSingle(tagName, channelName);
  }
}
