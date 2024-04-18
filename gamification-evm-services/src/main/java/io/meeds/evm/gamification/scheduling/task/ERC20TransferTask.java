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

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.meeds.common.ContainerTransactional;
import io.meeds.evm.gamification.model.TokenTransferEvent;
import io.meeds.evm.gamification.service.BlockchainService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.constant.DateFilterType;
import io.meeds.gamification.constant.EntityStatusType;
import io.meeds.evm.gamification.model.EvmTrigger;
import io.meeds.evm.gamification.service.EvmTriggerService;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.model.filter.RuleFilter;
import io.meeds.gamification.service.RuleService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.api.settings.SettingValue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ERC20TransferTask {
  private static final Logger LOG = LoggerFactory.getLogger(ERC20TransferTask.class);

  private static final Scope   SETTING_SCOPE               = Scope.APPLICATION.id("GAMIFICATION_EVM");

  private static final Context SETTING_CONTEXT             = Context.GLOBAL.id("GAMIFICATION_EVM");

  private static final String  SETTING_LAST_TIME_CHECK_KEY = "transferredTokenTransactionsCheck";

  @Autowired
  private SettingService    settingService;

  @Autowired
  private BlockchainService blockchainService;

  @Autowired
  private EvmTriggerService evmTriggerService;

  @Autowired
  private RuleService       ruleService;

  @ContainerTransactional
  @Scheduled(cron = "0 * * * * *")
  public synchronized void listenTokenTransfer() {
    LOG.info("Start listening erc20 token transfers");
    try {
        RuleFilter ruleFilter = new RuleFilter(true);
        ruleFilter.setEventType(Utils.CONNECTOR_NAME);
        ruleFilter.setStatus(EntityStatusType.ENABLED);
        ruleFilter.setProgramStatus(EntityStatusType.ENABLED);
        ruleFilter.setDateFilterType(DateFilterType.STARTED);
        List<RuleDTO> rules = ruleService.getRules(ruleFilter, 0, -1);
        List<RuleDTO> filteredRules = rules.stream()
                .filter(r -> !r.getEvent().getProperties().isEmpty()
                        && StringUtils.isNotBlank(r.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS)))
                .toList();
        if (CollectionUtils.isNotEmpty(filteredRules)) {
          filteredRules.forEach(rule -> {
            BigInteger minAmount;
            String recipientAddress;
            BigInteger base = new BigInteger("10");
            String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
            String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS);
            String tokenName = rule.getEvent().getProperties().get(Utils.NAME);
            String tokenSymbol = rule.getEvent().getProperties().get(Utils.SYMBOL);
            Integer tokenDecimals = Integer.parseInt(rule.getEvent().getProperties().get(Utils.DECIMALS));
            long lastBlock = blockchainService.getLastBlock(blockchainNetwork);
            long lastCheckedBlock = getLastCheckedBlock(contractAddress);
            if (lastCheckedBlock == 0) {
              // If this is the first time that it's started, save the last block as
              // last checked one
              saveLastCheckedBlock(lastBlock, contractAddress);
              return;
            }
            Set<TokenTransferEvent> events = blockchainService.getTransferredTokensTransactions(lastCheckedBlock + 1,
                                                                                                lastBlock,
                                                                                                contractAddress,
                                                                                                blockchainNetwork);
            if(!CollectionUtils.isEmpty(events) && StringUtils.isNotBlank(rule.getEvent().getProperties().get(Utils.MIN_AMOUNT))) {
              minAmount = base.pow(tokenDecimals).multiply(new BigInteger(rule.getEvent().getProperties().get(Utils.MIN_AMOUNT)));
              events = events.stream()
                             .filter(event -> event.getAmount().compareTo(minAmount) > 0)
                             .collect(Collectors.toSet());
            }
            if(!CollectionUtils.isEmpty(events) && StringUtils.isNotBlank(rule.getEvent().getProperties().get(Utils.RECIPIENT_ADDRESS))) {
              recipientAddress = rule.getEvent().getProperties().get(Utils.RECIPIENT_ADDRESS);
              events = events.stream()
                             .filter(event -> recipientAddress.toUpperCase().equals(event.getTo().toUpperCase()))
                             .collect(Collectors.toSet());
            }
            if (!CollectionUtils.isEmpty(events)) {
              events.forEach(event -> {
                try {
                  EvmTrigger evmTrigger = new EvmTrigger();
                  evmTrigger.setTrigger(Utils.TRANSFER_TOKEN_EVENT);
                  evmTrigger.setType(Utils.CONNECTOR_NAME);
                  evmTrigger.setWalletAddress(event.getTo());
                  evmTrigger.setTransactionHash(event.getTransactionHash());
                  evmTrigger.setContractAddress(contractAddress);
                  evmTrigger.setBlockchainNetwork(blockchainNetwork);
                  evmTrigger.setTokenName(tokenName);
                  evmTrigger.setTokenSymbol(tokenSymbol);
                  evmTriggerService.handleTriggerAsync(evmTrigger);
                } catch (Exception e) {
                  LOG.warn("Error broadcasting event '" + event, e);
                }
              });
            }
            saveLastCheckedBlock(lastBlock, contractAddress);
            LOG.info("End listening erc20 token transfers");
          });
        }
    } catch (Exception e) {
      LOG.error("An error occurred while listening erc20 token transfers", e);
    }
  }

  @ContainerTransactional
  public long getLastCheckedBlock(String contractAddress) {
    long lastCheckedBlock = 0;
    SettingValue<?> settingValue = settingService.get(SETTING_CONTEXT, SETTING_SCOPE, SETTING_LAST_TIME_CHECK_KEY + contractAddress);
    if (settingValue != null && settingValue.getValue() != null) {
      lastCheckedBlock = Long.parseLong(settingValue.getValue().toString());
    }
    return lastCheckedBlock;
  }

  @ContainerTransactional
  public void saveLastCheckedBlock(long lastBlock, String contractAddress) {
    settingService.set(SETTING_CONTEXT, SETTING_SCOPE, SETTING_LAST_TIME_CHECK_KEY + contractAddress, SettingValue.create(lastBlock));
  }
}
