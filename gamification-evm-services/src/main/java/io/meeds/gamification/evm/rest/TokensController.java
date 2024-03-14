package io.meeds.gamification.evm.rest;

import io.meeds.gamification.evm.model.ERC20Token;
import io.meeds.gamification.evm.service.BlockchainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/gamification/connectors/evm/tokens")
public class TokensController {

  @Autowired
  BlockchainService blockchainService;

  @GetMapping("/{contractAddress}")
  @Operation(summary = "Retrieves ERC20 Token details", method = "GET")
  @ApiResponse(responseCode = "200", description = "Request fulfilled")
  @ApiResponse(responseCode = "404", description = "Not found")
  @ApiResponse(responseCode = "503", description = "Service unavailable")
  public ERC20Token getERC20Token(@Parameter(description = "erc20 contract address")
                                  @PathVariable(name = "contractAddress")
                                  String contractAddress) {
    if (contractAddress == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract address is missing");
    }
    try {
      return blockchainService.getERC20TokenDetails(contractAddress);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ERC20 doesn't exist");
    }
  }

}
