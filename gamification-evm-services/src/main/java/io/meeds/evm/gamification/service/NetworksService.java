package io.meeds.evm.gamification.service;

import io.meeds.evm.gamification.blockchain.BlockchainConfigurationProperties;
import io.meeds.evm.gamification.model.BlockchainNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.math.BigInteger;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

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
    networks.add(new BlockchainNetwork("Polygon", blockchainProperties.getPolygonNetworkUrl(),
            new BigInteger(Web3j.build(new HttpService(blockchainProperties.getPolygonNetworkUrl())).netVersion().send().getNetVersion()).longValue()));
    networks.add(new BlockchainNetwork("Mainnet", blockchainProperties.getNetworkUrl(),
            new BigInteger(Web3j.build(new HttpService(blockchainProperties.getNetworkUrl())).netVersion().send().getNetVersion()).longValue()));
    return networks;
  }

}
