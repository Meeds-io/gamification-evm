package io.meeds.gamification.evm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvmTrigger {

  private String trigger;

  private String walletAddress;

  private String type;

  private String transactionHash;

  public EvmTrigger clone() {
      return new EvmTrigger(trigger, walletAddress, type, transactionHash);
  }
}
