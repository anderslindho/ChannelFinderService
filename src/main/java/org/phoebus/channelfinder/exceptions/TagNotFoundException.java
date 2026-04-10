package org.phoebus.channelfinder.exceptions;

public class TagNotFoundException extends RuntimeException {

  public TagNotFoundException(String tagName) {
    super("Tag not found: " + tagName);
  }
}
