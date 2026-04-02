package org.phoebus.channelfinder.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.phoebus.channelfinder.entity.Channel;
import org.phoebus.channelfinder.entity.Tag;
import org.phoebus.channelfinder.exceptions.ChannelNotFoundException;
import org.phoebus.channelfinder.exceptions.TagValidationException;
import org.phoebus.channelfinder.repository.ChannelRepository;
import org.phoebus.channelfinder.repository.TagRepository;
import org.phoebus.channelfinder.service.AuthorizationService.ROLES;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  @Mock private TagRepository tagRepository;
  @Mock private ChannelRepository channelRepository;
  @Mock private AuthorizationService authorizationService;

  private TagService tagService;

  @BeforeEach
  void setUp() {
    tagService = new TagService(tagRepository, channelRepository, authorizationService);
    when(authorizationService.isAuthorizedRole(any(), eq(ROLES.CF_TAG))).thenReturn(true);
  }

  @Test
  void createTag_nullName_throwsValidationException() {
    Tag tag = new Tag(null, "owner");

    assertThrows(TagValidationException.class, () -> tagService.create("tag", tag));
  }

  @Test
  void createTag_emptyName_throwsValidationException() {
    Tag tag = new Tag("", "owner");

    assertThrows(TagValidationException.class, () -> tagService.create("tag", tag));
  }

  @Test
  void createTag_nullOwner_throwsValidationException() {
    Tag tag = new Tag("tag1", null);

    assertThrows(TagValidationException.class, () -> tagService.create("tag1", tag));
  }

  @Test
  void createTag_emptyOwner_throwsValidationException() {
    Tag tag = new Tag("tag1", "");

    assertThrows(TagValidationException.class, () -> tagService.create("tag1", tag));
  }

  @Test
  void createTag_nonExistentChannel_throwsChannelNotFoundException() {
    Tag tag = new Tag("tag1", "owner");
    tag.setChannels(List.of(new Channel("missing-channel")));
    when(channelRepository.existsById("missing-channel")).thenReturn(false);

    assertThrows(ChannelNotFoundException.class, () -> tagService.create("tag1", tag));
  }

  @Test
  void createTag_validTag_noException() {
    Tag tag = new Tag("tag1", "owner");
    when(authorizationService.isAuthorizedOwner(any(), any(Tag.class))).thenReturn(true);
    when(tagRepository.findById("tag1")).thenReturn(Optional.empty());
    when(tagRepository.index(any())).thenReturn(tag);

    assertDoesNotThrow(() -> tagService.create("tag1", tag));
  }

  @Test
  void createTag_validTagWithExistingChannel_noException() {
    Channel channel = new Channel("ch1", "owner");
    Tag tag = new Tag("tag1", "owner");
    tag.setChannels(List.of(channel));
    when(authorizationService.isAuthorizedOwner(any(), any(Tag.class))).thenReturn(true);
    when(channelRepository.existsById("ch1")).thenReturn(true);
    when(tagRepository.findById("tag1")).thenReturn(Optional.empty());
    when(tagRepository.index(any())).thenReturn(tag);
    when(channelRepository.saveAll(any())).thenReturn(List.of(channel));

    assertDoesNotThrow(() -> tagService.create("tag1", tag));
  }
}
