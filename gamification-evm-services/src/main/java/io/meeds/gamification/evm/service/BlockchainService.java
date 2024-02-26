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
package io.meeds.gamification.evm.service;

import io.meeds.gamification.evm.blockchain.BlockchainConfigurationProperties;
import io.meeds.gamification.evm.constant.Constants.TokenTransferEvent;
import org.apache.commons.collections.CollectionUtils;
import org.exoplatform.wallet.contract.MeedsToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;


@Component
public class BlockchainService {

  @Autowired
  @Qualifier("polygonNetwork")
  private Web3j polygonWeb3j;

  @Autowired
  BlockchainConfigurationProperties blockchainProperties;

  /**
   * Retrieves the list of ERC20 Token transfer transactions
   * starting from a block to another
   *
   * @param fromBlock Start block
   * @param toBlock End Block to filter
   * @return {@link Set} of NFT ID of type {@link TokenTransferEvent}
   */
  public Set<TokenTransferEvent> getTransferredTokensTransactions(long fromBlock, long toBlock, String contractAddress) {
    EthFilter ethFilter = new EthFilter(new DefaultBlockParameterNumber(fromBlock),
                                        new DefaultBlockParameterNumber(toBlock),
                                        contractAddress);
    ethFilter.addSingleTopic(EventEncoder.encode(MeedsToken.TRANSFER_EVENT));
    try {
      EthLog ethLog = polygonWeb3j.ethGetLogs(ethFilter).send();
      @SuppressWarnings("rawtypes")
      List<EthLog.LogResult> ethLogs = ethLog.getLogs();
      if (CollectionUtils.isEmpty(ethLogs)) {
        return Collections.emptySet();
        }
      List<TokenTransferEvent> transferEvents = ethLogs.stream()
                .map(logResult -> (EthLog.LogObject) logResult.get())
                .filter(logObject -> !logObject.isRemoved())
                .map(EthLog.LogObject::getTransactionHash)
                .map(this::getTransactionReceipt)
                .filter(TransactionReceipt::isStatusOK)
                .flatMap(this::getTransferEvents)
                .filter(Objects::nonNull)
                .toList();
      return new LinkedHashSet<>(transferEvents);
    } catch (IOException e) {
      throw new IllegalStateException("Error retrieving event logs", e);
    }
  }

  /**
   * @return last block number
   */
  public long getLastBlock() {
    try {
      return polygonWeb3j.ethBlockNumber().send().getBlockNumber().longValue();
    } catch (IOException e) {
      throw new IllegalStateException("Error getting last block number", e);
    }
  }

  private Stream<TokenTransferEvent> getTransferEvents(TransactionReceipt transactionReceipt) {
    MeedsToken meedsToken = MeedsToken.load(blockchainProperties.getMeedAddress(),
                                            polygonWeb3j,
                                            new ReadonlyTransactionManager(polygonWeb3j, Address.DEFAULT.toString()),
                                            new StaticGasProvider(BigInteger.valueOf(20000000000l), BigInteger.valueOf(300000l)));
    List<MeedsToken.TransferEventResponse> transferEvents = meedsToken.getTransferEvents(transactionReceipt);
    if (transferEvents != null && !transferEvents.isEmpty()) {
      return transferEvents.stream()
              .map(transferEventResponse -> new TokenTransferEvent(transferEventResponse.from,
                                                                   transferEventResponse.to,
                                                                   transferEventResponse.value,
                                                                   transferEventResponse.log.getTransactionHash()));
    }
    return Stream.empty();
  }

  private TransactionReceipt getTransactionReceipt(String transactionHash) {
    return getTransactionReceipt(transactionHash, polygonWeb3j);
  }

  private TransactionReceipt getTransactionReceipt(String transactionHash, Web3j customWeb3j) {
    try {
      EthGetTransactionReceipt ethGetTransactionReceipt = customWeb3j.ethGetTransactionReceipt(transactionHash).send();
      if (ethGetTransactionReceipt != null) {
        return ethGetTransactionReceipt.getResult();
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error retrieving Receipt for Transaction with hash: " + transactionHash, e);
    }
    return null;
  }
}
