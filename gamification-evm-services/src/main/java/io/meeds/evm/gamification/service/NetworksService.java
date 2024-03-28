package io.meeds.evm.gamification.service;

import io.meeds.evm.gamification.blockchain.BlockchainConfigurationProperties;
import io.meeds.evm.gamification.model.BlockchainNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class NetworksService {

  @Autowired
  private BlockchainConfigurationProperties blockchainProperties;

  /**
   * Retrieves the list of networks
   *
   * @return {@link Set} of networks of type {@link BlockchainNetwork}
  */
  public Set<BlockchainNetwork> getNetworks() throws IOException {
    Set<BlockchainNetwork> networks = new HashSet<>();
    networks.add(new BlockchainNetwork("Polygon", blockchainProperties.getPolygonNetworkUrl()));
    networks.add(new BlockchainNetwork("Mainnet", blockchainProperties.getNetworkUrl()));
    return networks;
  }

}
