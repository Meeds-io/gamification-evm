package io.meeds.evm.gamification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenTransferEvent {

  private String from;

  private String to;

  private BigInteger amount;

  private String transactionHash;

}
