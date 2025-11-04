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

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes4;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog.LogResult;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.tx.Contract;

import io.meeds.evm.gamification.blockchain.BlockchainConfiguration;
import io.meeds.evm.gamification.model.EvmContract;
import io.meeds.evm.gamification.model.EvmTransaction;

import static io.meeds.evm.gamification.utils.Utils.*;

@Service
public class EvmBlockchainService {

  private static final Log      LOG            = ExoLogger.getLogger(EvmBlockchainService.class);
  
  private static final Function FUNC_DECIMALS  = new Function("decimals",
                                                              Arrays.asList(),
                                                              Arrays.asList(new TypeReference<Uint8>() {}));

  private static final Function FUNC_SYMBOL    = new Function("symbol",
                                                              Arrays.asList(),
                                                              Arrays.asList(new TypeReference<Utf8String>() {}));

  private static final Function FUNC_NAME      = new Function("name",
                                                              Arrays.asList(),
                                                              Arrays.asList(new TypeReference<Utf8String>() {}));

  @Autowired
  BlockchainConfiguration       blockchainConfiguration;

  @Autowired
  private EvmTransactionService evmTransactionService;

  /**
   * saves the list of ERC20 Token transfer transactions starting from a block to
   * another
   *
   * @param fromBlock Start block
   * @param toBlock End Block to filter
   * @param contractAddress The ERC20 token contract address
   * @param blockchainNetwork The url of used provider
   * @param networkId Network id 
   */
  public void saveTokenTransactions(long fromBlock,
                                    long toBlock,
                                    String contractAddress,
                                    String blockchainNetwork,
                                    long networkId) {
    Event event = TRANSFER_EVENT_ERC20;
    if (isERC1155(blockchainNetwork, contractAddress)) {
      event = TRANSFERSINGLE_EVENT;
    } else if (isERC721(blockchainNetwork, contractAddress)) {
      event = TRANSFER_EVENT_ER721;
    }
    List<EvmTransaction> transferEvents = getEvmTransactions(fromBlock, toBlock, contractAddress, blockchainNetwork, event);
    if (transferEvents != null && !transferEvents.isEmpty()) {
      transferEvents.forEach(transferEvent -> {
        transferEvent.setContractAddress(contractAddress);
        transferEvent.setNetworkId(networkId);
        transferEvent.setTransactionDate(System.currentTimeMillis());
        evmTransactionService.saveTransaction(transferEvent);
      });
    }
  }
  /**
   * get the list of ERC20 Token transfer transactions
   *
   * @param fromBlock Start block
   * @param toBlock End Block to filter
   * @param contractAddress The ERC20 token contract address
   * @param blockchainNetwork The url of used provider
   */
  public List<EvmTransaction> getEvmTransactions(long fromBlock, long toBlock, String contractAddress, String blockchainNetwork, Event event) {
    Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
    EthFilter ethFilter = new EthFilter(new DefaultBlockParameterNumber(fromBlock),
            new DefaultBlockParameterNumber(toBlock),
            contractAddress);
    ethFilter.addSingleTopic(EventEncoder.encode(event));
    try {
      EthLog ethLog = networkWeb3j.ethGetLogs(ethFilter).send();
      @SuppressWarnings("rawtypes")
      List<LogResult> ethLogs = ethLog.getLogs();
      if (CollectionUtils.isEmpty(ethLogs)) {
        return Collections.emptyList();
      }
      return ethLogs.stream()
              .map(logResult -> (EthLog.LogObject) logResult.get())
              .collect(Collectors.toMap(
                      EthLog.LogObject::getTransactionHash,
                      log -> log,
                      (existing, replacement) -> existing
              ))
              .values()
              .stream()
              .map(logResult -> (EthLog.LogObject) logResult.get())
              .filter(logObject -> !logObject.isRemoved())
              .map(EthLog.LogObject::getTransactionHash)
              .map(transactionHash -> getTransactionReceipt(transactionHash, networkWeb3j))
              .filter(TransactionReceipt::isStatusOK)
              .flatMap(transactionReceipt -> getTransferEvents(transactionReceipt, contractAddress, blockchainNetwork, event))
              .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Error retrieving event logs", e);
    }
  }

  /**
   * Retrieves the Token details from its contract address
   *
   * @param contractAddress the token contract address
   * @return token details
   */
  public EvmContract getTokenDetails(String contractAddress, String blockchainNetwork) {
    try {
      Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
      EvmContract token = new EvmContract();
      token.setType(detectTokenType(blockchainNetwork, contractAddress));
      if (token.getType().equals("ERC-20")) {
        BigInteger decimals = new BigInteger(callFunction(networkWeb3j, contractAddress, FUNC_DECIMALS));
        token.setDecimals(decimals);
      }
      String name = getTokenName(networkWeb3j, contractAddress, token.getType());
      token.setName(name);
      String symbol = getTokenSymbol(networkWeb3j, contractAddress, token.getType());
      token.setSymbol(symbol);
      return token;
    } catch (Exception e) {
      LOG.info("the error", e);
    }
    return null;
  }

  /**
   * Get the Token type from the contract address
   *
   * @param blockchainNetwork The url of used provider
   * @param contractAddress the token contract address
   * @return token type
   */
  public String detectTokenType(String blockchainNetwork, String contractAddress) {
    try {
      Web3j web3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
      if (supportsInterface(web3j, contractAddress, ERC721_INTERFACE_ID)) {
        return "ERC-721";
      } else if (supportsInterface(web3j, contractAddress, ERC1155_INTERFACE_ID)) {
        return "ERC-1155";
      } else if (!(new BigInteger(callFunction(web3j, contractAddress, FUNC_DECIMALS))).equals(BigInteger.ZERO)) {
        return "ERC-20";
      }
    } catch (Exception e) {
      LOG.error("Error  when detecting token type", e);
    }
    return "Unknown type token";
  }

  /**
   * Check if the Token is an ERC-1155 or not
   *
   * @param blockchainNetwork The url of used provider
   * @param contractAddress the token contract address
   * @return true if an ERC-1155
   */
  public boolean isERC1155(String blockchainNetwork, String contractAddress) {
     return detectTokenType(blockchainNetwork, contractAddress).equals("ERC-1155");

  }

  /**
   * Check if the Token is an ERC-721 or not
   *
   * @param blockchainNetwork The url of used provider
   * @param contractAddress the token contract address
   * @return true if an ERC-721
   */
  public boolean isERC721(String blockchainNetwork, String contractAddress) {
    return detectTokenType(blockchainNetwork, contractAddress).equals("ERC-721");
  }

  private boolean supportsInterface(Web3j web3j, String tokenAddress, String interfaceId) throws Exception {
    Function supportsInterfaceFunction = new Function("supportsInterface",
                                                      Arrays.asList(new Bytes4(Utils.hexStringToByteArray(interfaceId))),
                                                      Arrays.asList(new TypeReference<Bool>() {
                                                      }));
    String encodedFunction = FunctionEncoder.encode(supportsInterfaceFunction);
    EthCall response = web3j
                            .ethCall(Transaction.createEthCallTransaction("0x0000000000000000000000000000000000000000",
                                                                          tokenAddress,
                                                                          encodedFunction),
                                     DefaultBlockParameterName.LATEST)
                            .send();

    String value = response.getValue();
    if (value != null) {
      if (value.startsWith("0x")) {
        value = value.substring(2);
      }
      value = value.replaceFirst("^0+(?!$)", "");

      return value.equals("1");
    }
    return false;
  }

  private String callFunction(Web3j web3j, String contractAddress, Function function) throws Exception {
    String encodedFunction = FunctionEncoder.encode(function);
    EthCall response = web3j.ethCall(Transaction.createEthCallTransaction(contractAddress, contractAddress, encodedFunction),
                                     DefaultBlockParameterName.LATEST)
                            .send();

    List<Type> result = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
    if (function.getOutputParameters().get(0).getType().equals(Utf8String.class)) {
      return ((Utf8String) result.get(0)).getValue();
    } else if (function.getOutputParameters().get(0).getType().equals(Uint8.class)) {
      return String.valueOf(((Uint8) result.get(0)).getValue());
    } else if (function.getOutputParameters().get(0).getType().equals(Uint256.class)) {
      return String.valueOf(((Uint256) result.get(0)).getValue());
    } else {
        return "Unexpected output type";
    }
  }

  private String getTokenName(Web3j web3j, String contractAddress, String tokenType) throws Exception {
    if ("ERC-20".equals(tokenType) || "ERC-721".equals(tokenType)) {
      return callFunction(web3j, contractAddress, FUNC_NAME);
    }
    return "Unknown name token";
  }

  private String getTokenSymbol(Web3j web3j, String contractAddress, String tokenType) throws Exception {
    if ("ERC-20".equals(tokenType) || "ERC-721".equals(tokenType)) {
      return callFunction(web3j, contractAddress, FUNC_SYMBOL);
    }
    return "Unknown symbol token";
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
   * @param contractAddress Address to get its token balance
   * @param blockchainNetwork Used provider url
   * @param functionParams Function parameters
   * @return {@link BigInteger} representing the balance of address of token which is
   *          retrieved from the used blockchain.
   */
  public BigInteger balanceOf(String contractAddress, String blockchainNetwork, Map<String, String> functionParams) {
    try {
      Function FUNC_BALANCEOF;
      boolean isERC1155 = isERC1155(blockchainNetwork, contractAddress);
      Web3j networkWeb3j = blockchainConfiguration.getNetworkWeb3j(blockchainNetwork);
      if (isERC1155) {
        FUNC_BALANCEOF = new Function("balanceOf",
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, functionParams.get("owner")),
                        new org.web3j.abi.datatypes.generated.Uint256(new BigInteger(functionParams.get("tokenId")))),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
      } else {
        FUNC_BALANCEOF = new Function("balanceOf",
                Arrays.<Type>asList(new Address(160, functionParams.get("owner"))),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
      }
      return new BigInteger(callFunction(networkWeb3j, contractAddress, FUNC_BALANCEOF));
    } catch (Exception e) {
      throw new IllegalStateException("Error calling balanceOf method", e);
    }
  }

  public boolean isBalanceEnough(String contractAddress, String blockchainNetwork, String walletAddress, RuleDTO rule, List<EvmTransaction> transactions) {
    boolean isERC1155 = isERC1155(blockchainNetwork, contractAddress);
    Map<String, String> funcParams = new HashMap<String, String>();
    funcParams.put("owner", walletAddress);
    String minAmount = rule.getEvent().getProperties().get(Utils.MIN_AMOUNT);
    BigInteger desiredMinAmount = new BigInteger(minAmount);
    if (isERC1155) {
      BigInteger finalDesiredMinAmount = desiredMinAmount;
      return transactions.stream()
                         .anyMatch(transaction -> {
        funcParams.put("tokenId", transaction.getTokenId().toString());
        BigInteger balanceOf = balanceOf(contractAddress, blockchainNetwork, funcParams);
        return balanceOf.compareTo(finalDesiredMinAmount) >= 0;
      });
    } else {
      if (StringUtils.isNotBlank(rule.getEvent().getProperties().get(DECIMALS))) {
        BigInteger base = new BigInteger("10");
        Integer decimals = Integer.parseInt(rule.getEvent().getProperties().get(DECIMALS));
        desiredMinAmount = base.pow(decimals).multiply(desiredMinAmount);
      }
      BigInteger balanceOf = balanceOf(contractAddress, blockchainNetwork, funcParams);
      return balanceOf.compareTo(desiredMinAmount) >= 0;
    }
  }

  private Stream<EvmTransaction> getTransferEvents(TransactionReceipt transactionReceipt, String contractAddress, String blockchainNetwork, Event event) {
    try {
      List<TransferEventResponse> transferEvents = getTransactionTransferEvents(transactionReceipt, contractAddress, event);
      if (transferEvents != null && !transferEvents.isEmpty()) {
        return transferEvents.stream().map(transferEventResponse -> {
          EvmTransaction transferEvent = new EvmTransaction();
          Map<String, String> funcParams = new HashMap<String, String>();
          funcParams.put("owner", transferEventResponse.to);
          if (transferEventResponse.tokenId != null) {
            funcParams.put("tokenId", transferEventResponse.tokenId.toString());
            transferEvent.setTokenId(transferEventResponse.tokenId);
          }
          transferEvent.setTransactionHash(transferEventResponse.log.getTransactionHash());
          transferEvent.setBlockHash(transferEventResponse.log.getBlockHash());
          transferEvent.setBlockNumber(transferEventResponse.log.getBlockNumber());
          transferEvent.setFromAddress(transferEventResponse.from);
          transferEvent.setToAddress(transferEventResponse.to);
          transferEvent.setAmount(transferEventResponse.value);
          transferEvent.setWalletBalance(balanceOf(contractAddress, blockchainNetwork, funcParams));
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

  public List<TransferEventResponse> getTransactionTransferEvents(TransactionReceipt transactionReceipt, String contractAddress, Event event) {
    List<EventValuesWithLog> valueList = extractEventParametersWithLog(event, transactionReceipt);
    ArrayList<TransferEventResponse> responses = new ArrayList<>(valueList.size());
    for (EventValuesWithLog eventValues : valueList) { // NOSONAR
      if (!StringUtils.equalsIgnoreCase(contractAddress, eventValues.getLog().getAddress())) {
        continue;
      }
      if (event.equals(TRANSFER_EVENT_ERC20)) {
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
      }
      if (event.equals(TRANSFER_EVENT_ER721)) {
        if (CollectionUtils.isEmpty(eventValues.getIndexedValues())) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is 0",
                  transactionReceipt.getTransactionHash());
          continue;
        }
        if (eventValues.getIndexedValues().size() != 3) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is {} while it's expected to be '3'",
                  transactionReceipt.getTransactionHash(),
                  eventValues.getIndexedValues().size());
          continue;
        }
        if (eventValues.getNonIndexedValues().size() != 0) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The non-indexed values size is {} while it's expected to be '0'",
                  transactionReceipt.getTransactionHash(),
                  eventValues.getNonIndexedValues().size());
          continue;
        }
      }
      if (event.equals(TRANSFERSINGLE_EVENT)) {
        if (CollectionUtils.isEmpty(eventValues.getIndexedValues())) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is 0",
                  transactionReceipt.getTransactionHash());
          continue;
        }
        if (eventValues.getIndexedValues().size() != 3) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The indexed values size is {} while it's expected to be '3'",
                  transactionReceipt.getTransactionHash(),
                  eventValues.getIndexedValues().size());
          continue;
        }
        if (CollectionUtils.isEmpty(eventValues.getNonIndexedValues())) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The non-indexed values size is 0",
                  transactionReceipt.getTransactionHash());
          continue;
        }
        if (eventValues.getNonIndexedValues().size() != 2) {
          LOG.info("Can't parse 'Transfer' event logs of transaction with hash {}. The non-indexed values size is {} while it's expected to be '2'",
                  transactionReceipt.getTransactionHash(),
                  eventValues.getNonIndexedValues().size());
          continue;
        }
      }
      TransferEventResponse typedResponse = newTransferEventResponse(eventValues, event);
      responses.add(typedResponse);
    }
    return responses;
  }

  private TransferEventResponse newTransferEventResponse(EventValuesWithLog eventValues, Event event) {
    TransferEventResponse typedResponse = new TransferEventResponse();
    typedResponse.log = eventValues.getLog();
    if (event.equals(TRANSFER_EVENT_ERC20)) {
      typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
    } else if (event.equals(TRANSFER_EVENT_ER721)) {
      typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.tokenId = (BigInteger) eventValues.getIndexedValues().get(2).getValue();
    }else if (event.equals(TRANSFERSINGLE_EVENT)) {
      typedResponse.from = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.to = (String) eventValues.getIndexedValues().get(2).getValue();
      typedResponse.tokenId = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
    }
    return typedResponse;
  }

  protected List<EventValuesWithLog> extractEventParametersWithLog(Event event, TransactionReceipt transactionReceipt) {
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

  public static class TransferEventResponse {
    private org.web3j.protocol.core.methods.response.Log log;

    private String from;

    private String to;

    private BigInteger value;

    private BigInteger tokenId;
  }

}
