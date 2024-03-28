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

import io.meeds.evm.gamification.blockchain.BlockchainConfiguration;
import io.meeds.evm.gamification.model.ERC20Token;
import io.meeds.evm.gamification.model.TokenTransferEvent;
import io.micrometer.common.util.StringUtils;
import org.apache.commons.collections.CollectionUtils;
import org.exoplatform.wallet.contract.ERC20;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
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
  BlockchainConfiguration blockchainConfiguration;

  public static final Event TRANSFER_EVENT = new Event("Transfer",
          Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>(false) {}));

  /**
   * Retrieves the list of ERC20 Token transfer transactions
   * starting from a block to another
   *
   * @param fromBlock Start block
   * @param toBlock End Block to filter
   * @return {@link Set} of NFT ID of type {@link TokenTransferEvent}
   */
  public Set<TokenTransferEvent> getTransferredTokensTransactions(long fromBlock, long toBlock, String contractAddress, String blockchainNetwork) {
    Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
    EthFilter ethFilter = new EthFilter(new DefaultBlockParameterNumber(fromBlock),
                                        new DefaultBlockParameterNumber(toBlock),
                                        contractAddress);
    ethFilter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
    try {
      EthLog ethLog = networkWeb3j.ethGetLogs(ethFilter).send();
      @SuppressWarnings("rawtypes")
      List<EthLog.LogResult> ethLogs = ethLog.getLogs();
      if (CollectionUtils.isEmpty(ethLogs)) {
        return Collections.emptySet();
      }
      List<TokenTransferEvent> transferEvents = ethLogs.stream()
                .map(logResult -> (EthLog.LogObject) logResult.get())
                .filter(logObject -> !logObject.isRemoved())
                .map(EthLog.LogObject::getTransactionHash)
                .map(transactionHash -> getTransactionReceipt(transactionHash, networkWeb3j))
                .filter(TransactionReceipt::isStatusOK)
                .flatMap(transactionReceipt -> getTransferEvents(transactionReceipt, contractAddress, networkWeb3j))
                .filter(Objects::nonNull)
                .toList();
      return new LinkedHashSet<>(transferEvents);
    } catch (IOException e) {
      throw new IllegalStateException("Error retrieving event logs", e);
    }
  }

  /**
   * Retrieves the details of ERC20 Token
   * from its contract address
   *
   * @param contractAddress the ERC20 token contract address
   * @return erc20 token details
   */
  public ERC20Token getERC20TokenDetails(String contractAddress, String blockchainNetwork) throws IOException {
    Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
    String name = erc20Name(contractAddress, networkWeb3j);
    String symbol = erc20Symbol(contractAddress, networkWeb3j);
    BigInteger totalSupply = erc20TotalSupply(contractAddress, networkWeb3j);
    BigInteger decimals = erc20Decimals(contractAddress, networkWeb3j);
    ERC20Token erc20Token = new ERC20Token();
    if (StringUtils.isNotBlank(name)
        && StringUtils.isNotBlank(symbol)
        && !totalSupply.equals(BigInteger.ZERO)
        && !decimals.equals(BigInteger.ZERO)) {
      erc20Token.setSymbol(symbol);
      erc20Token.setDecimals(decimals);
      erc20Token.setTotalSupply(totalSupply);
      erc20Token.setName(name);
      return erc20Token;
    }
    return null;
  }

  /**
   * @return last block number
   */
  public long getLastBlock(String blockchainNetwork) {
    try {
      Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
      return networkWeb3j.ethBlockNumber().send().getBlockNumber().longValue();
    } catch (IOException e) {
      throw new IllegalStateException("Error getting last block number", e);
    }
  }

  /**
   * @return ERC20 token name
   */
  public String erc20Name(String contractAddress, Web3j networkWeb3j) {
    try {
      ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
      return erc20Token.name().send();
    } catch (Exception e) {
      throw new IllegalStateException("Error calling name method", e);
    }
  }

  /**
   * @return ERC20 token symbol
   */
  public String erc20Symbol(String contractAddress, Web3j networkWeb3j) {
    try {
      ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
      return erc20Token.symbol().send();
    } catch (Exception e) {
      throw new IllegalStateException("Error calling symbol method", e);
    }
  }

  /**
   * @return ERC20 token decimals
   */
  public BigInteger erc20Decimals(String contractAddress, Web3j networkWeb3j) {
    try {
      ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
      return erc20Token.decimals().send();
    } catch (Exception e) {
      throw new IllegalStateException("Error calling decimals method", e);
    }
  }

  /**
   * @return ERC20 token totalSupply
   */
  public BigInteger erc20TotalSupply(String contractAddress, Web3j networkWeb3j) {
    try {
      ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
      return erc20Token.totalSupply().send();
    } catch (Exception e) {
      throw new IllegalStateException("Error calling totalSupply method", e);
    }
  }

  public ERC20 loadERC20Token(String contractAddress, Web3j networkWeb3j) {
    return ERC20.load(contractAddress,
                      networkWeb3j,
                      new ReadonlyTransactionManager(networkWeb3j, Address.DEFAULT.toString()),
                      new StaticGasProvider(BigInteger.valueOf(20000000000l), BigInteger.valueOf(300000l)));
  }

  private Stream<TokenTransferEvent> getTransferEvents(TransactionReceipt transactionReceipt, String contractAddress, Web3j networkWeb3j) {
    ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
    List<ERC20.TransferEventResponse> transferEvents = erc20Token.getTransferEvents(transactionReceipt);
    if (transferEvents != null && !transferEvents.isEmpty()) {
      return transferEvents.stream()
              .map(transferEventResponse -> new TokenTransferEvent(transferEventResponse.from,
                      transferEventResponse.to,
                      transferEventResponse.value,
                      transferEventResponse.log.getTransactionHash()));
    }
    return Stream.empty();
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
