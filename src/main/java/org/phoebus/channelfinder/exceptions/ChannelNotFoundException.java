package org.phoebus.channelfinder.exceptions;

public class ChannelNotFoundException extends RuntimeException {

  public ChannelNotFoundException(String channelName) {
    super("Channel not found: " + channelName);
  }
}
