package io.meeds.evm.gamification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlockchainNetwork {

  private String name;

  private String providerUrl;

  private long networkId;
}
