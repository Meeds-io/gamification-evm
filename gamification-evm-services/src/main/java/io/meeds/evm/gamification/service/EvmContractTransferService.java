package io.meeds.evm.gamification.service;

import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.model.EvmTrigger;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.constant.DateFilterType;
import io.meeds.gamification.constant.EntityStatusType;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.model.filter.RuleFilter;
import io.meeds.gamification.service.RuleService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.meeds.evm.gamification.utils.Utils.EVM_SAVE_ACTION_EVENT;

@Service
public class EvmContractTransferService {

  private static final Log      LOG = ExoLogger.getLogger(EvmContractTransferService.class);

  @Autowired
  private EvmTransactionService evmTransactionService;

  @Autowired
  private ListenerService       listenerService;

  @Autowired
  private EvmTriggerService     evmTriggerService;

  @Autowired
  private EvmBlockchainService  evmBlockchainService;

  @Autowired
  private RuleService           ruleService;

  public void listenEvmContractTransfer(RuleDTO rule) {
    String trigger = rule.getEvent().getTrigger();
    String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
    String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS).toLowerCase();
    Long networkId = Long.parseLong(rule.getEvent().getProperties().get(Utils.NETWORK_ID));
    Long lastIdProcced = getLastIdProcced(rule, contractAddress, networkId);
    List<EvmTransaction> transactions = evmTransactionService.getTransactionsByContractAddressAndNetworkIdFromId(contractAddress,
                                                                                                                 networkId,
                                                                                                                 lastIdProcced);
    if (CollectionUtils.isNotEmpty(transactions)) {
      transactions.forEach(transaction -> {
        try {
          handleEvmTrigger(rule, transaction, trigger, contractAddress, networkId, blockchainNetwork);
        } catch (Exception e) {
          LOG.warn("Error broadcasting EVM event for transaction {} and trigger {}",
                   transaction.getTransactionHash(),
                   trigger,
                   e);
        }
      });
    }
  }

  public List<RuleDTO> getFilteredEVMRules() {
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

  private Long getLastIdProcced(RuleDTO rule, String contractAddress, Long networkId) {
    Long lastIdProcced = 0l;
    if (StringUtils.isBlank(rule.getEvent().getProperties().get(Utils.LAST_ID_PROCCED))) {
      EvmTransaction lastTransaction = evmTransactionService.getTransactionByContractAddressAndNetworkIdOrderByIdDesc(contractAddress, networkId);
      if (lastTransaction != null) {
        lastIdProcced = lastTransaction.getId();
      }
      broadcastEvmActionEvent(lastIdProcced.toString(), rule.getId().toString());
    } else {
      lastIdProcced = Long.parseLong(rule.getEvent().getProperties().get(Utils.LAST_ID_PROCCED));
    }
    return lastIdProcced;
  }

  private void broadcastEvmActionEvent(String transactionId, String ruleId) {
    try {
      Map<String, String> gam = new HashMap<>();
      gam.put("ruleId", ruleId);
      gam.put("transactionId", transactionId);
      listenerService.broadcast(EVM_SAVE_ACTION_EVENT, gam, "");
    } catch (Exception e) {
      LOG.error("Cannot broadcast evm event", e);
    }
  }

  private void handleEvmTrigger(RuleDTO rule,
                                EvmTransaction transaction,
                                String trigger,
                                String contractAddress,
                                Long networkId,
                                String blockchainNetwork) {
    if (trigger.equals(Utils.SEND_TOKEN_EVENT) || trigger.equals(Utils.RECEIVE_TOKEN_EVENT)
        || (trigger.equals(Utils.HOLD_TOKEN_EVENT)
            && isValidHoldingToken(transaction,
                                   Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION)),
                                   contractAddress,
                                   blockchainNetwork))) {
      EvmTrigger evmTrigger = newEvmTrigger(transaction, rule.getId(), trigger, contractAddress, blockchainNetwork, networkId);
      evmTriggerService.handleTriggerAsync(evmTrigger);
      broadcastEvmActionEvent(transaction.getId().toString(), rule.getId().toString());
    }
  }

  private EvmTrigger newEvmTrigger(EvmTransaction transaction,
                                   Long ruleId,
                                   String trigger,
                                   String contractAddress,
                                   String blockchainNetwork,
                                   Long networkId) {
    EvmTrigger evmTrigger = new EvmTrigger();
    evmTrigger.setTrigger(trigger);
    evmTrigger.setType(Utils.CONNECTOR_NAME);
    evmTrigger.setTransactionHash(transaction.getTransactionHash());
    evmTrigger.setTransactionId(transaction.getId());
    evmTrigger.setRuleId(ruleId);
    evmTrigger.setContractAddress(contractAddress);
    evmTrigger.setBlockchainNetwork(blockchainNetwork);
    evmTrigger.setAmount(transaction.getAmount());
    evmTrigger.setNetworkId(networkId.toString());
    evmTrigger.setSentDate(transaction.getSentDate());
    if (trigger.equals(Utils.SEND_TOKEN_EVENT)) {
      evmTrigger.setWalletAddress(transaction.getFromAddress());
      evmTrigger.setTargetAddress(transaction.getToAddress());
    } else {
      if (trigger.equals(Utils.RECEIVE_TOKEN_EVENT)) {
        evmTrigger.setTargetAddress(transaction.getFromAddress());
      }
      evmTrigger.setWalletAddress(transaction.getToAddress());
    }
    if (trigger.equals(Utils.HOLD_TOKEN_EVENT)) {
      evmTrigger.setTokenBalance(evmBlockchainService.erc20BalanceOf(transaction.getToAddress(), contractAddress, blockchainNetwork));
    }
    return evmTrigger;
  }

  private Boolean isValidHoldingToken(EvmTransaction transaction,
                                      Long desiredDuration,
                                      String contractAddress,
                                      String blockchainNetwork) {
    Long holdingDuration = System.currentTimeMillis() - transaction.getSentDate();
    Boolean validDuration = holdingDuration.compareTo(desiredDuration) >= 0;
    String tokenHolder = transaction.getToAddress();
    Boolean amountHeld = true;
    if (!validDuration) {
      return false;
    }
    List<EvmTransaction> transferTransactions = evmTransactionService.getTransactionsByFromAddress(tokenHolder);
    if (CollectionUtils.isNotEmpty(transferTransactions)) {
      BigInteger balanceOf = evmBlockchainService.erc20BalanceOf(tokenHolder, contractAddress, blockchainNetwork);
      if (balanceOf.compareTo(transaction.getAmount()) < 0) {
        amountHeld = false;
      }
    }
    return validDuration && amountHeld;
  }
}
