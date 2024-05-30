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
package io.meeds.evm.gamification.scheduling.task;

import java.util.HashMap;
import java.util.List;
import java.math.BigInteger;
import java.util.Map;

import io.meeds.common.ContainerTransactional;
import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.meeds.evm.gamification.service.EvmTransactionService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.constant.DateFilterType;
import io.meeds.gamification.constant.EntityStatusType;
import io.meeds.evm.gamification.model.EvmTrigger;
import io.meeds.evm.gamification.service.EvmTriggerService;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.model.filter.RuleFilter;
import io.meeds.gamification.service.RuleService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.api.settings.SettingValue;

import org.exoplatform.services.listener.ListenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.meeds.evm.gamification.utils.Utils.EVM_SAVE_ACTION_EVENT;

@Component
public class ERC20TransferTask {
  private static final Logger   LOG                         = LoggerFactory.getLogger(ERC20TransferTask.class);

  private static final Scope    SETTING_SCOPE               = Scope.APPLICATION.id("GAMIFICATION_EVM");

  private static final Context  SETTING_CONTEXT             = Context.GLOBAL.id("GAMIFICATION_EVM");

  private static final String   SETTING_LAST_TIME_CHECK_KEY = "transferredTokenTransactionsCheck";

  @Autowired
  private SettingService        settingService;

  @Autowired
  private EvmBlockchainService  evmBlockchainService;

  @Autowired
  private EvmTriggerService     evmTriggerService;

  @Autowired
  private RuleService           ruleService;

  @Autowired
  private EvmTransactionService evmTransactionService;

  @Autowired
  private ListenerService       listenerService;

  @ContainerTransactional
  @Scheduled(cron = "0 * * * * *")
  public synchronized void listenTokenTransfer() {
    try {
      List<RuleDTO> filteredRules = getFilteredEVMRules();
      if (CollectionUtils.isNotEmpty(filteredRules)) {
        filteredRules.forEach(rule -> {
          Long lastIdProcced = 0l;
          String trigger = rule.getEvent().getTrigger();
          String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
          String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS).toLowerCase();
          Long networkId = Long.parseLong(rule.getEvent().getProperties().get(Utils.NETWORK_ID));
          if (StringUtils.isBlank(rule.getEvent().getProperties().get(Utils.LAST_ID_PROCCED))) {
            if (evmTransactionService.getTransactionByContractAddressAndNetworkIdOrderByIdDesc(contractAddress, networkId) != null) {
              lastIdProcced = evmTransactionService.getTransactionByContractAddressAndNetworkIdOrderByIdDesc(contractAddress, networkId).getId();
            }
            broadcastEvmActionEvent(lastIdProcced.toString(), rule.getId().toString());
          } else {
            lastIdProcced = Long.parseLong(rule.getEvent().getProperties().get(Utils.LAST_ID_PROCCED));
          }
          List<EvmTransaction> transactions = evmTransactionService.getTransactionsByContractAddressAndNetworkIdFromId(contractAddress, networkId, lastIdProcced);
          if (CollectionUtils.isNotEmpty(transactions)) {
            transactions.forEach(transaction -> {
              try {
                if (trigger.equals(Utils.SEND_TOKEN_EVENT) || trigger.equals(Utils.RECEIVE_TOKEN_EVENT)
                    || (trigger.equals(Utils.HOLD_TOKEN_EVENT)
                        && isValidHoldingToken(transaction,
                                               Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION)),
                                               contractAddress,
                                               blockchainNetwork))) {
                  EvmTrigger evmTrigger = new EvmTrigger();
                  evmTrigger.setTrigger(trigger);
                  evmTrigger.setType(Utils.CONNECTOR_NAME);
                  evmTrigger.setTransactionHash(transaction.getTransactionHash());
                  evmTrigger.setTransactionId(transaction.getId());
                  evmTrigger.setRuleId(rule.getId());
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
                  evmTriggerService.handleTriggerAsync(evmTrigger);
                  broadcastEvmActionEvent(transaction.getId().toString(), rule.getId().toString());
                }
              } catch (Exception e) {
                LOG.warn("Error broadcasting EVM event for transaction {} and trigger {}", transaction.getTransactionHash(), trigger, e);
              }
            });
          }
        });
      }
    } catch (Exception e) {
      LOG.error("An error occurred while rewarding for EVM events", e);
    }
  }

  @ContainerTransactional
  @Scheduled(cron = "0 * * * * *")
  public synchronized void saveTokenTransactions() {
    try {
      List<RuleDTO> filteredRules = getFilteredEVMRules();
      if (CollectionUtils.isNotEmpty(filteredRules)) {
        LOG.info("Start listening erc20 token transfers for {} configured rules", filteredRules.size());
        filteredRules.forEach(rule -> {
          String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
          String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS);
          String networkId = rule.getEvent().getProperties().get(Utils.NETWORK_ID);
          long lastBlock = evmBlockchainService.getLastBlock(blockchainNetwork);
          long lastCheckedBlock = getLastCheckedBlock(contractAddress, networkId);
          if (lastCheckedBlock == 0) {
            // If this is the first time that it's started, save the last block as
            // last checked one
            saveLastCheckedBlock(lastBlock, contractAddress, networkId);
            return;
          }
          evmBlockchainService.saveTokenTransactions(lastCheckedBlock + 1,
                                                  lastBlock,
                                                  contractAddress.toLowerCase(),
                                                  blockchainNetwork,
                                                  Long.parseLong(networkId));
          saveLastCheckedBlock(lastBlock, contractAddress, networkId);
        });
        LOG.info("End listening erc20 token transfers");
      }
    } catch (Exception e) {
      LOG.error("An error occurred while listening blockchain transactions", e);
    }
  }

  @ContainerTransactional
  public long getLastCheckedBlock(String contractAddress, String networkId) {
    long lastCheckedBlock = 0;
    SettingValue<?> settingValue = settingService.get(SETTING_CONTEXT,
                                                      SETTING_SCOPE,
                                                      SETTING_LAST_TIME_CHECK_KEY + networkId + "#" + contractAddress);
    if (settingValue != null && settingValue.getValue() != null) {
      lastCheckedBlock = Long.parseLong(settingValue.getValue().toString());
    }
    return lastCheckedBlock;
  }

  @ContainerTransactional
  public void saveLastCheckedBlock(long lastBlock, String contractAddress, String networkId) {
    settingService.set(SETTING_CONTEXT,
                       SETTING_SCOPE,
                       SETTING_LAST_TIME_CHECK_KEY + networkId + "#" + contractAddress,
                       SettingValue.create(lastBlock));
  }

  private List<RuleDTO> getFilteredEVMRules() {
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
}
