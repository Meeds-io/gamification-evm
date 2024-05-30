package io.meeds.evm.gamification.rest;

import java.util.Set;

import io.meeds.evm.gamification.service.EvmNetworkService;
import io.meeds.evm.gamification.model.BlockchainNetwork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/gamification/connectors/evm/networks")
public class EvmNetworkController {

  @Autowired
  EvmNetworkService evmNetworkService;

  @GetMapping
  @Operation(summary = "Retrieves the list of networks", method = "GET")
  @ApiResponse(responseCode = "200", description = "Request fulfilled")
  @ApiResponse(responseCode = "404", description = "Not found")
  public Set<BlockchainNetwork> getNetworks() {
      try {
          return evmNetworkService.getNetworks();
      } catch (IOException e) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No networks found");
      }
  }
}
