package org.phoebus.channelfinder.exceptions;

public class PropertyNotFoundException extends RuntimeException {

  public PropertyNotFoundException(String propertyName) {
    super("Property not found: " + propertyName);
  }
}
