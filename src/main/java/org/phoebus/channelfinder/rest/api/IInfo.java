package org.phoebus.channelfinder.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;

public interface IInfo {

  @Operation(
      summary = "Get ChannelFinder service info",
      description =
          "Returns information about the ChannelFinder service and its Elasticsearch backend.",
      operationId = "getServiceInfo",
      tags = {"Info"})
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "ChannelFinder info",
            content = @Content(schema = @Schema(implementation = String.class)))
      })
  @GetMapping
  String info();
}
