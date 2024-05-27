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
import io.meeds.evm.gamification.model.TransactionDetails;
import io.meeds.evm.gamification.utils.TreatedTransactionStatus;
import io.meeds.evm.gamification.utils.Utils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.wallet.contract.ERC20;
import org.exoplatform.wallet.contract.ERC20.TransferEventResponse;
import org.exoplatform.wallet.model.Wallet;
import org.exoplatform.wallet.service.WalletAccountService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.ReadonlyTransactionManager;
import org.web3j.tx.gas.StaticGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

@Service
public class BlockchainService {

  private static final Log          LOG            = ExoLogger.getLogger(BlockchainService.class);

  @Autowired
  BlockchainConfiguration           blockchainConfiguration;

  @Autowired
  private WalletAccountService      walletAccountService;

  @Autowired
  private TransactionDetailsService transactionDetailsService;

  public static final Event         TRANSFER_EVENT = new Event("Transfer",
                                                               Arrays.<TypeReference<?>> asList(new TypeReference<Address>(true) {
                                                                                                                },
                                                                                                new TypeReference<Address>(true) {
                                                                                                },
                                                                                                new TypeReference<Uint256>(false) {
                                                                                                }));

  /**
   * saves the list of ERC20 Token transfer transactions starting from a block to
   * another
   *
   * @param fromBlock Start block
   * @param toBlock End Block to filter
   */
  public void saveTokenTransactions(long fromBlock,
                                    long toBlock,
                                    String contractAddress,
                                    String blockchainNetwork,
                                    long networkId) {
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
        return;
      }
      List<TransactionDetails> transferEvents = ethLogs.stream()
                                                       .map(logResult -> (EthLog.LogObject) logResult.get())
                                                       .filter(logObject -> !logObject.isRemoved())
                                                       .map(EthLog.LogObject::getTransactionHash)
                                                       .map(transactionHash -> getTransactionReceipt(transactionHash, networkWeb3j))
                                                       .filter(TransactionReceipt::isStatusOK)
                                                       .flatMap(transactionReceipt -> getTransferEvents(transactionReceipt, contractAddress))
                                                       .toList();
      if (transferEvents != null && !transferEvents.isEmpty()) {
        transferEvents.forEach(transferEvent -> {
          Map<String, TreatedTransactionStatus> status = new HashMap<>();
          Wallet sender = walletAccountService.getWalletByAddress(transferEvent.getFromAddress());
          Wallet receiver = walletAccountService.getWalletByAddress(transferEvent.getToAddress());
          if ((sender != null && StringUtils.isNotBlank(sender.getAddress()))
              || (receiver != null && StringUtils.isNotBlank(receiver.getAddress()))) {
            transferEvent.setContractAddress(contractAddress);
            status.put(Utils.SEND_TOKEN_EVENT, TreatedTransactionStatus.NONE);
            status.put(Utils.RECEIVE_TOKEN_EVENT, TreatedTransactionStatus.NONE);
            status.put(Utils.HOLD_TOKEN_EVENT, TreatedTransactionStatus.NONE);
            transferEvent.setTreatedTransactionStatus(status);
            transferEvent.setNetworkId(networkId);
            transferEvent.setSentDate(System.currentTimeMillis());
            transactionDetailsService.saveTransaction(transferEvent);
          }
        });
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error retrieving event logs", e);
    }
  }

  /**
   * Retrieves the details of ERC20 Token from its contract address
   *
   * @param contractAddress the ERC20 token contract address
   * @return erc20 token details
   */
  public ERC20Token getERC20TokenDetails(String contractAddress, String blockchainNetwork) {
    Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
    String name = erc20Name(contractAddress, networkWeb3j);
    String symbol = erc20Symbol(contractAddress, networkWeb3j);
    BigInteger totalSupply = erc20TotalSupply(contractAddress, networkWeb3j);
    BigInteger decimals = erc20Decimals(contractAddress, networkWeb3j);
    ERC20Token erc20Token = new ERC20Token();
    if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(symbol) && !totalSupply.equals(BigInteger.ZERO)
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

  /**
   * @param address Address to get its erc20 token balance
   * @return {@link BigInteger} representing the balance of address of erc20 token
   *         which is * retrieved from the used blockchain.
   */
  public BigInteger erc20BalanceOf(String address, String contractAddress, String blockchainNetwork) {
    try {
      Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
      ERC20 erc20Token = loadERC20Token(contractAddress, networkWeb3j);
      return erc20Token.balanceOf(address).send();
    } catch (Exception e) {
      throw new IllegalStateException("Error calling balanceOf method", e);
    }
  }

  private ERC20 loadERC20Token(String contractAddress, Web3j networkWeb3j) {
    return ERC20.load(contractAddress,
                      networkWeb3j,
                      new ReadonlyTransactionManager(networkWeb3j, Address.DEFAULT.toString()),
                      new StaticGasProvider(BigInteger.valueOf(20000000000l), BigInteger.valueOf(300000l)));
  }

  private Stream<TransactionDetails> getTransferEvents(TransactionReceipt transactionReceipt, String contractAddress) {
    try {
      List<TransferEventResponse> transferEvents = getTransactionTransferEvents(transactionReceipt, contractAddress);
      if (transferEvents != null && !transferEvents.isEmpty()) {
        return transferEvents.stream().map(transferEventResponse -> {
          TransactionDetails transferEvent = new TransactionDetails();
          transferEvent.setTransactionHash(transferEventResponse.log.getTransactionHash());
          transferEvent.setFromAddress(transferEventResponse.from);
          transferEvent.setToAddress(transferEventResponse.to);
          transferEvent.setAmount(transferEventResponse.value);
          return transferEvent;
        });
      }
    } catch (Exception e) {
      LOG.warn("Error while getting Transfer events on transaction with hash {}. This might happen when an incompatible 'Transfer' event is detected",
               transactionReceipt.getTransactionHash(),
               e);
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

  public List<TransferEventResponse> getTransactionTransferEvents(TransactionReceipt transactionReceipt, String contractAddress) {
    List<EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSFER_EVENT, transactionReceipt);
    ArrayList<TransferEventResponse> responses = new ArrayList<>(valueList.size());
    for (EventValuesWithLog eventValues : valueList) { // NOSONAR
      if (!StringUtils.equalsIgnoreCase(contractAddress, eventValues.getLog().getAddress())) {
        continue;
      }
      if (CollectionUtils.isEmpty(eventValues.getIndexedValues())) {
        LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is 0",
                 transactionReceipt.getTransactionHash());
        continue;
      }
      if (eventValues.getIndexedValues().size() != 2) {
        LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is {} while it's expected to be '2'",
                 transactionReceipt.getTransactionHash(),
                 eventValues.getIndexedValues().size());
        continue;
      }
      if (CollectionUtils.isEmpty(eventValues.getNonIndexedValues())) {
        LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The non-indexed values size is 0",
                 transactionReceipt.getTransactionHash());
        continue;
      }
      if (eventValues.getNonIndexedValues().size() != 1) {
        LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The non-indexed values size is {} while it's expected to be '1'",
                 transactionReceipt.getTransactionHash(),
                 eventValues.getNonIndexedValues().size());
        continue;
      }
      TransferEventResponse typedResponse = new TransferEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  protected List<EventValuesWithLog> extractEventParametersWithLog(Event event,
                                                                   TransactionReceipt transactionReceipt) {
    return transactionReceipt.getLogs()
                             .stream()
                             .map(log -> extractEventParametersWithLog(event, log))
                             .filter(Objects::nonNull)
                             .toList();
  }

  protected EventValuesWithLog extractEventParametersWithLog(Event event, org.web3j.protocol.core.methods.response.Log log) {
    return staticExtractEventParametersWithLog(event, log);
  }

  protected static EventValuesWithLog staticExtractEventParametersWithLog(Event event,
                                                                          org.web3j.protocol.core.methods.response.Log log) {
    final EventValues eventValues = Contract.staticExtractEventParameters(event, log);
    return (eventValues == null) ? null : new EventValuesWithLog(eventValues, log);
  }

  public static class EventValuesWithLog {
    private final EventValues                                  eventValues;

    private final org.web3j.protocol.core.methods.response.Log log;

    public EventValuesWithLog(EventValues eventValues, org.web3j.protocol.core.methods.response.Log log) {
      this.eventValues = eventValues;
      this.log = log;
    }

    @SuppressWarnings("rawtypes")
    public List<Type> getIndexedValues() {
      return eventValues.getIndexedValues();
    }

    @SuppressWarnings("rawtypes")
    public List<Type> getNonIndexedValues() {
      return eventValues.getNonIndexedValues();
    }

    public org.web3j.protocol.core.methods.response.Log getLog() {
      return log;
    }
  }

}
