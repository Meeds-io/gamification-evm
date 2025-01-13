package io.meeds.evm.gamification.service;

import io.meeds.common.ContainerTransactional;
import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.model.EvmTrigger;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.constant.DateFilterType;
import io.meeds.gamification.constant.EntityStatusType;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.model.filter.RuleFilter;
import io.meeds.gamification.service.RuleService;
import io.meeds.wallet.model.Wallet;
import io.meeds.wallet.service.WalletAccountService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.abi.datatypes.Event;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.meeds.evm.gamification.utils.Utils.*;

@Service
public class EvmContractTransferService {

  private static final Log      LOG                         = ExoLogger.getLogger(EvmContractTransferService.class);

  private static final Scope    SETTING_SCOPE               = Scope.APPLICATION.id("GAMIFICATION_EVM_HOLD_EVENT");

  private static final Context  SETTING_CONTEXT             = Context.GLOBAL.id("GAMIFICATION_EVM_HOLD_EVENT");

  private static final String   SETTING_LAST_TIME_CHECK_KEY = "lastRewardTimeCheck";

  @Autowired
  private EvmTransactionService evmTransactionService;

  @Autowired
  private EvmTriggerService     evmTriggerService;

  @Autowired
  private EvmBlockchainService  evmBlockchainService;

  @Autowired
  private RuleService           ruleService;

  @Autowired
  private WalletAccountService  walletAccountService;

  @Autowired
  private SettingService        settingService;

  public void scanForContractTransactions(RuleDTO rule) {
    String trigger = rule.getEvent().getTrigger();
    String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
    String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS).toLowerCase();
    long networkId = Long.parseLong(rule.getEvent().getProperties().get(Utils.NETWORK_ID));
    List<String> walletsAddresses = evmTriggerService.getWalletAddresses(rule.getProgram().getSpaceId());
    BigInteger minAmount = new BigInteger(rule.getEvent().getProperties().get(Utils.MIN_AMOUNT));
    if (CollectionUtils.isNotEmpty(walletsAddresses)) {
      walletsAddresses.forEach(walletAddress -> {
        List<EvmTransaction> evmTransactions = new ArrayList<>();
        Event event = TRANSFER_EVENT;
        long duration = Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION));
        long toBlock = evmBlockchainService.getLastBlock(blockchainNetwork);
        long fromBlock = toBlock - (duration / BLOCK_TIME_AVERAGE);
        long lastRewardTime = getLastRewardTime(walletAddress, rule.getId());
        if (evmBlockchainService.isERC1155(blockchainNetwork, contractAddress)) {
          event = TRANSFERSINGLE_EVENT;
          evmTransactions = evmBlockchainService.getEvmTransactions(fromBlock, toBlock, contractAddress, blockchainNetwork, event);

        }
        List<EvmTransaction> toAddressEvmTransactions = evmTransactions.stream()
                                                                       .filter(transaction -> (StringUtils.equals(transaction.getToAddress(),
                                                                                                                  walletAddress)))
                                                                       .collect(Collectors.toList());
        boolean isBalanceOfEnough = evmBlockchainService.isBalanceEnough(contractAddress,
                                                                         blockchainNetwork,
                                                                         walletAddress,
                                                                         minAmount,
                                                                         toAddressEvmTransactions);
        if (isBalanceOfEnough) {
          List<EvmTransaction> transactions = evmTransactionService.getFilteredTransactionsByWalletAddress(contractAddress,
                                                                                                           networkId,
                                                                                                           walletAddress,
                                                                                                           Utils.convertDateStringToTimestamp(rule.getCreatedDate()),
                                                                                                           lastRewardTime,
                                                                                                           trigger);
          if (trigger.equals(Utils.HOLD_TOKEN_EVENT)) {
            if (CollectionUtils.isEmpty(transactions)) {
              handleWithEvmTansactions(blockchainNetwork,
                                       contractAddress,
                                       walletAddress,
                                       networkId,
                                       event,
                                       fromBlock,
                                       toBlock,
                                       duration,
                                       evmTransactions,
                                       toAddressEvmTransactions,
                                       rule,
                                       trigger);
            } else {
              handleEvmTrigger(rule,
                               transactions.get(transactions.size() - 1),
                               trigger,
                               contractAddress,
                               networkId,
                               blockchainNetwork,
                               walletAddress);
            }
          } else {
            if (CollectionUtils.isNotEmpty(transactions)) {
               transactions.forEach(transaction -> { try { handleEvmTrigger(rule,
               transaction, trigger, contractAddress, networkId, blockchainNetwork,
               walletAddress); } catch (Exception e) {
               LOG.warn("Error broadcasting EVM event for transaction {} and trigger {}",
               transaction.getTransactionHash(), trigger, e); } });
            }
          }
        }
      });
    }
  }

  public void handleWithEvmTansactions(String blockchainNetwork,
                                       String contractAddress,
                                       String walletAddress,
                                       Long networkId,
                                       Event event,
                                       long fromBlock,
                                       long toBlock,
                                       long duration,
                                       List<EvmTransaction> evmTransactions,
                                       List<EvmTransaction> toAddressEvmTransactions,
                                       RuleDTO rule,
                                       String trigger) {
    if (evmTransactions.isEmpty()) {
      evmTransactions = evmBlockchainService.getEvmTransactions(fromBlock, toBlock, contractAddress, blockchainNetwork, event);
      toAddressEvmTransactions = evmTransactions.stream()
                                                .filter(transaction -> (StringUtils.equals(transaction.getToAddress(),
                                                                                           walletAddress)))
                                                .collect(Collectors.toList());

    }
    List<EvmTransaction> fromAddressEvmTransactions = evmTransactions.stream()
                                                                     .filter(transaction -> (StringUtils.equals(transaction.getFromAddress(),
                                                                                                               walletAddress)))
                                                                     .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(fromAddressEvmTransactions)) {
      if (evmBlockchainService.isERC1155(blockchainNetwork, contractAddress)) {
        List<String> tokenIds = new ArrayList<>();
        toAddressEvmTransactions.forEach(toAddressTransaction -> {
          if (!tokenIds.contains(toAddressTransaction.getTokenId().toString())) {
            EvmTransaction transaction = new EvmTransaction();
            tokenIds.add(toAddressTransaction.getTokenId().toString());
            transaction.setTransactionHash("");
            transaction.setAmount(new BigInteger(rule.getEvent().getProperties().get(Utils.MIN_AMOUNT)));
            transaction.setToAddress(walletAddress);
            transaction.setTransactionDate(System.currentTimeMillis() - duration);
            transaction.setTokenId(toAddressTransaction.getTokenId());
            transaction.setNetworkId(networkId);
            transaction.setContractAddress(contractAddress);
            handleTriggerForHoldEvent(rule, transaction, walletAddress);
          }
        });
      } else {
        EvmTransaction transaction = new EvmTransaction();
        transaction.setTransactionHash("");
        transaction.setAmount(new BigInteger(rule.getEvent().getProperties().get(Utils.MIN_AMOUNT)));
        transaction.setToAddress(walletAddress);
        transaction.setTransactionDate(System.currentTimeMillis() - duration);
        transaction.setNetworkId(networkId);
        transaction.setContractAddress(contractAddress);
        handleTriggerForHoldEvent(rule, transaction, walletAddress);
      }
    } else {
      if (evmBlockchainService.isERC1155(blockchainNetwork, contractAddress)) {
        List<String> tokenIds = new ArrayList<>();
        evmTransactions.forEach(transaction -> {
          if (!tokenIds.contains(transaction.getTokenId().toString())) {
            tokenIds.add(transaction.getTokenId().toString());
            transaction.setNetworkId(networkId);
            transaction.setContractAddress(contractAddress);
            transaction.setTransactionDate(System.currentTimeMillis());
            handleEvmTrigger(rule, transaction, trigger, contractAddress, networkId, blockchainNetwork, walletAddress);
          }
        });
      } else {
        handleEvmTrigger(rule,
                         evmTransactions.get(evmTransactions.size() - 1),
                         trigger,
                         contractAddress,
                         networkId,
                         blockchainNetwork,
                         walletAddress);
      }
    }

  }

  public List<RuleDTO> getEnabledEvmRules() {
    RuleFilter ruleFilter = new RuleFilter(true);
    ruleFilter.setEventType(Utils.CONNECTOR_NAME);
    ruleFilter.setStatus(EntityStatusType.ENABLED);
    ruleFilter.setProgramStatus(EntityStatusType.ENABLED);
    ruleFilter.setDateFilterType(DateFilterType.STARTED);
    List<RuleDTO> rules = ruleService.getRules(ruleFilter, 0, -1);
    return rules.stream()
                .filter(r -> !r.getEvent().getProperties().isEmpty()
                    && StringUtils.isNotBlank(r.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS)))
                .toList();
  }

  public List<RuleDTO> getEvmRules() {
    RuleFilter ruleFilter = new RuleFilter(true);
    ruleFilter.setEventType(Utils.CONNECTOR_NAME);
    ruleFilter.setDateFilterType(DateFilterType.STARTED);
    List<RuleDTO> rules = ruleService.getRules(ruleFilter, 0, -1);
    return rules.stream()
                .filter(r -> !r.getEvent().getProperties().isEmpty()
                    && StringUtils.isNotBlank(r.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS)))
                .toList();
  }

  public List<RuleDTO> getHoldEventEvmRules() {
    RuleFilter ruleFilter = new RuleFilter(true);
    ruleFilter.setEventType(Utils.CONNECTOR_NAME);
    ruleFilter.setDateFilterType(DateFilterType.STARTED);
    List<RuleDTO> rules = ruleService.getRules(ruleFilter, 0, -1);
    return rules.stream()
                .filter(r -> !r.getEvent().getProperties().isEmpty()
                    && StringUtils.isNotBlank(r.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS))
                    && r.getEvent().getTrigger().equals(Utils.HOLD_TOKEN_EVENT))
                .toList();
  }

  public void handleTriggerForHoldEvent(RuleDTO rule, EvmTransaction transaction, String walletAddress) {
    Long duration = Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION));
    String trigger = rule.getEvent().getTrigger();
    String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS);
    String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
    Long networkId = Long.parseLong(rule.getEvent().getProperties().get(Utils.NETWORK_ID));
    EvmTrigger evmTrigger = newEvmTrigger(transaction,
                                          trigger,
                                          contractAddress,
                                          blockchainNetwork,
                                          networkId,
                                          duration,
                                          walletAddress,
                                          null);
     evmTriggerService.handleTriggerAsync(evmTrigger);
     saveLastRewardTime(walletAddress, rule.getId());
  }

  private void handleEvmTrigger(RuleDTO rule,
                                EvmTransaction transaction,
                                String trigger,
                                String contractAddress,
                                Long networkId,
                                String blockchainNetwork,
                                String walletAddress) {
    Boolean isSendTokenEvent = trigger.equals(Utils.SEND_TOKEN_EVENT);
    Boolean isReceiveTokenEvent = trigger.equals(Utils.RECEIVE_TOKEN_EVENT);
    if (isSendTokenEvent || isReceiveTokenEvent) {
      EvmTrigger evmTrigger;
      if (isSendTokenEvent) {
        evmTrigger = newEvmTrigger(transaction,
                                   trigger,
                                   contractAddress,
                                   blockchainNetwork,
                                   networkId,
                                   null,
                                   transaction.getFromAddress(),
                                   transaction.getToAddress());
      } else {
        evmTrigger = newEvmTrigger(transaction,
                                   trigger,
                                   contractAddress,
                                   blockchainNetwork,
                                   networkId,
                                   null,
                                   transaction.getToAddress(),
                                   transaction.getFromAddress());
      }
      evmTriggerService.handleTriggerAsync(evmTrigger);
      saveLastRewardTime(walletAddress, rule.getId());
    }
    if (trigger.equals(Utils.HOLD_TOKEN_EVENT)
        && Utils.isValidDurationHoldingToken(transaction, Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION)))) {
      handleTriggerForHoldEvent(rule, transaction, walletAddress);
    }
  }

  private EvmTrigger newEvmTrigger(EvmTransaction transaction,
                                   String trigger,
                                   String contractAddress,
                                   String blockchainNetwork,
                                   Long networkId,
                                   Long duration,
                                   String walletAddress,
                                   String targetAddress) {
    EvmTrigger evmTrigger = new EvmTrigger();
    Map<String, String> funcParams = new HashMap<String, String>();
    evmTrigger.setTrigger(trigger);
    evmTrigger.setType(Utils.CONNECTOR_NAME);
    evmTrigger.setTransactionHash(transaction.getTransactionHash());
    evmTrigger.setContractAddress(contractAddress);
    evmTrigger.setBlockchainNetwork(blockchainNetwork);
    evmTrigger.setAmount(transaction.getAmount());
    evmTrigger.setNetworkId(networkId.toString());
    evmTrigger.setSentDate(transaction.getTransactionDate());
    evmTrigger.setDuration(duration);
    evmTrigger.setWalletAddress(walletAddress);
    evmTrigger.setTargetAddress(targetAddress);
    funcParams.put("owner", walletAddress);
    if (trigger.equals(Utils.HOLD_TOKEN_EVENT) && !transaction.getTokenId().toString().isEmpty()) {
      funcParams.put("tokenId", transaction.getTokenId().toString());
      evmTrigger.setTokenBalance(evmBlockchainService.balanceOf(contractAddress, blockchainNetwork, funcParams));
    }
    return evmTrigger;
  }

  public long getLastRewardTime(String walletAddress, Long ruleId) {
    long lastRewardTime = 0;
    SettingValue<?> settingValue = settingService.get(SETTING_CONTEXT,
                                                      SETTING_SCOPE,
                                                      SETTING_LAST_TIME_CHECK_KEY + ruleId.toString() + "#" + walletAddress);
    if (settingValue != null && settingValue.getValue() != null) {
      lastRewardTime = Long.parseLong(settingValue.getValue().toString());
    }
    return lastRewardTime;
  }

  public void saveLastRewardTime(String walletAddress, Long ruleId) {
    settingService.set(SETTING_CONTEXT,
                       SETTING_SCOPE,
                       SETTING_LAST_TIME_CHECK_KEY + ruleId.toString() + "#" + walletAddress,
                       SettingValue.create(System.currentTimeMillis()));
  }
}
