package io.meeds.evm.gamification.rest;

import io.meeds.evm.gamification.model.EvmContract;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/gamification/connectors/evm/tokens")
public class EvmTokenController {

  @Autowired
  EvmBlockchainService evmBlockchainService;

  @GetMapping
  @Operation(summary = "Retrieves ERC20 Token details", method = "GET")
  @ApiResponse(responseCode = "200", description = "Request fulfilled")
  @ApiResponse(responseCode = "404", description = "Not found")
  @ApiResponse(responseCode = "503", description = "Service unavailable")
  public EvmContract getERC20Token(
                                  @RequestParam(name = "contractAddress")
                                  String contractAddress,
                                  @RequestParam(name = "blockchainNetwork")
                                  String blockchainNetwork) {
    if (contractAddress == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract address is missing");
    } else if (blockchainNetwork == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Network url is missing");
    }
    return evmBlockchainService.getERC20TokenDetails(contractAddress, blockchainNetwork);
  }

}
