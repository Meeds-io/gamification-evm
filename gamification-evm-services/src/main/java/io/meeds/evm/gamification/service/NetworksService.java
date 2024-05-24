/*
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.evm.gamification.service;

import io.meeds.evm.gamification.blockchain.BlockchainConfigurationProperties;
import io.meeds.evm.gamification.model.BlockchainNetwork;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.math.BigInteger;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Service
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
    networks.add(new BlockchainNetwork("Polygon",
                                       blockchainProperties.getPolygonNetworkUrl(),
                                       new BigInteger(Web3j.build(new HttpService(blockchainProperties.getPolygonNetworkUrl()))
                                                           .netVersion()
                                                           .send()
                                                           .getNetVersion()).longValue()));
    networks.add(new BlockchainNetwork("Mainnet",
                                       blockchainProperties.getNetworkUrl(),
                                       new BigInteger(Web3j.build(new HttpService(blockchainProperties.getNetworkUrl()))
                                                           .netVersion()
                                                           .send()
                                                           .getNetVersion()).longValue()));
    return networks;
  }

}
