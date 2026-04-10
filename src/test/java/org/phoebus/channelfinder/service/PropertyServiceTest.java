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
import org.phoebus.channelfinder.entity.Property;
import org.phoebus.channelfinder.exceptions.PropertyValidationException;
import org.phoebus.channelfinder.repository.ChannelRepository;
import org.phoebus.channelfinder.repository.PropertyRepository;
import org.phoebus.channelfinder.service.AuthorizationService.ROLES;

@ExtendWith(MockitoExtension.class)
class PropertyServiceTest {

  @Mock private PropertyRepository propertyRepository;
  @Mock private ChannelRepository channelRepository;
  @Mock private AuthorizationService authorizationService;

  private PropertyService propertyService;

  @BeforeEach
  void setUp() {
    propertyService =
        new PropertyService(propertyRepository, channelRepository, authorizationService);
    when(authorizationService.isAuthorizedRole(any(), eq(ROLES.CF_PROPERTY))).thenReturn(true);
  }

  @Test
  void createProperty_nullName_throwsValidationException() {
    Property property = new Property(null, "owner");

    assertThrows(PropertyValidationException.class, () -> propertyService.create("prop", property));
  }

  @Test
  void createProperty_emptyName_throwsValidationException() {
    Property property = new Property("", "owner");

    assertThrows(PropertyValidationException.class, () -> propertyService.create("prop", property));
  }

  @Test
  void createProperty_nullOwner_throwsValidationException() {
    Property property = new Property("prop1", null);

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_emptyOwner_throwsValidationException() {
    Property property = new Property("prop1", "");

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_nonExistentChannel_throwsValidationException() {
    Property property = new Property("prop1", "owner");
    Channel channel = new Channel("missing-channel", "owner", List.of(), List.of());
    channel.addProperty(new Property("prop1", "owner", "value"));
    property.setChannels(List.of(channel));
    when(channelRepository.existsById("missing-channel")).thenReturn(false);

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_channelMissingPropertyValue_throwsValidationException() {
    Property property = new Property("prop1", "owner");
    Channel channel = new Channel("ch1", "owner", List.of(), List.of());
    property.setChannels(List.of(channel));
    when(channelRepository.existsById("ch1")).thenReturn(true);

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_channelWithNullPropertyValue_throwsValidationException() {
    Property property = new Property("prop1", "owner");
    Channel channel =
        new Channel("ch1", "owner", List.of(new Property("prop1", "owner", null)), List.of());
    property.setChannels(List.of(channel));
    when(channelRepository.existsById("ch1")).thenReturn(true);

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_channelWithEmptyPropertyValue_throwsValidationException() {
    Property property = new Property("prop1", "owner");
    Channel channel =
        new Channel("ch1", "owner", List.of(new Property("prop1", "owner", "")), List.of());
    property.setChannels(List.of(channel));
    when(channelRepository.existsById("ch1")).thenReturn(true);

    assertThrows(
        PropertyValidationException.class, () -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_validPropertyNoChannels_noException() {
    Property property = new Property("prop1", "owner");
    when(authorizationService.isAuthorizedOwner(any(), any(Property.class))).thenReturn(true);
    when(propertyRepository.findById("prop1")).thenReturn(Optional.empty());
    when(propertyRepository.index(any())).thenReturn(property);

    assertDoesNotThrow(() -> propertyService.create("prop1", property));
  }

  @Test
  void createProperty_validPropertyWithChannel_noException() {
    Property property = new Property("prop1", "owner");
    Channel channel =
        new Channel("ch1", "owner", List.of(new Property("prop1", "owner", "value")), List.of());
    property.setChannels(List.of(channel));
    when(authorizationService.isAuthorizedOwner(any(), any(Property.class))).thenReturn(true);
    when(channelRepository.existsById("ch1")).thenReturn(true);
    when(propertyRepository.findById("prop1")).thenReturn(Optional.empty());
    when(propertyRepository.index(any())).thenReturn(property);
    when(channelRepository.saveAll(any())).thenReturn(List.of(channel));

    assertDoesNotThrow(() -> propertyService.create("prop1", property));
  }
}
