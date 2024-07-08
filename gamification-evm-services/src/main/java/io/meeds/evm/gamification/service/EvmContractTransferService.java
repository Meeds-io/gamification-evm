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

  public void scanForContractTransactions(RuleDTO rule) {
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
    Boolean isSendTokenEvent = trigger.equals(Utils.SEND_TOKEN_EVENT);
    Boolean isReceiveTokenEvent = trigger.equals(Utils.RECEIVE_TOKEN_EVENT);
    if (isSendTokenEvent || isReceiveTokenEvent) {
      EvmTrigger evmTrigger;
      if (isSendTokenEvent) {
        evmTrigger = newEvmTrigger(transaction, rule.getId(), trigger, contractAddress, blockchainNetwork, networkId, null, transaction.getFromAddress(), transaction.getToAddress());
      } else {
        evmTrigger = newEvmTrigger(transaction, rule.getId(), trigger, contractAddress, blockchainNetwork, networkId, null, transaction.getToAddress(), transaction.getFromAddress());
      }
      evmTriggerService.handleTriggerAsync(evmTrigger);
      broadcastEvmActionEvent(transaction.getId().toString(), rule.getId().toString());
    }
    if (trigger.equals(Utils.HOLD_TOKEN_EVENT)
        && isValidDurationHoldingToken(transaction, Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION)))) {
      Long duration = Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION));
      EvmTrigger evmTriggerForReceiver = newEvmTrigger(transaction, rule.getId(), trigger, contractAddress, blockchainNetwork, networkId, duration, transaction.getToAddress(), null);
      EvmTrigger evmTriggerForSender = newEvmTrigger(transaction, rule.getId(), trigger, contractAddress, blockchainNetwork, networkId, duration, transaction.getFromAddress(), null);
      evmTriggerService.handleTriggerAsync(evmTriggerForReceiver);
      evmTriggerService.handleTriggerAsync(evmTriggerForSender);
      broadcastEvmActionEvent(transaction.getId().toString(), rule.getId().toString());
    }
  }

  private EvmTrigger newEvmTrigger(EvmTransaction transaction,
                                   Long ruleId,
                                   String trigger,
                                   String contractAddress,
                                   String blockchainNetwork,
                                   Long networkId,
                                   Long duration,
                                   String walletAddress,
                                   String targetAddress) {
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
    evmTrigger.setDuration(duration);
    evmTrigger.setWalletAddress(walletAddress);
    evmTrigger.setTargetAddress(targetAddress);
    if (trigger.equals(Utils.HOLD_TOKEN_EVENT)) {
      evmTrigger.setTokenBalance(evmBlockchainService.erc20BalanceOf(walletAddress, contractAddress, blockchainNetwork));
    }
    return evmTrigger;
  }

  private Boolean isValidDurationHoldingToken(EvmTransaction transaction, Long desiredDuration) {
    Long holdingDuration = System.currentTimeMillis() - transaction.getSentDate();
    return holdingDuration.compareTo(desiredDuration) >= 0;
  }
}
