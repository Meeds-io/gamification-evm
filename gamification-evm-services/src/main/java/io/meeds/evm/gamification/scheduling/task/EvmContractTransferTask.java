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

import java.util.List;

import io.meeds.common.ContainerTransactional;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.meeds.evm.gamification.service.EvmContractTransferService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;

import org.apache.commons.collections4.CollectionUtils;
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
public class EvmContractTransferTask {
  private static final Logger        LOG                         = LoggerFactory.getLogger(EvmContractTransferTask.class);

  private static final Scope         SETTING_SCOPE               = Scope.APPLICATION.id("GAMIFICATION_EVM");

  private static final Context       SETTING_CONTEXT             = Context.GLOBAL.id("GAMIFICATION_EVM");

  private static final String        SETTING_LAST_TIME_CHECK_KEY = "transferredTokenTransactionsCheck";

  @Autowired
  private SettingService             settingService;

  @Autowired
  private EvmBlockchainService       evmBlockchainService;

  @Autowired
  private EvmContractTransferService evmContractTransferService;

  @ContainerTransactional
  @Scheduled(cron = "${gamification.evm.transactionScan.cron:0 * * * * *}")
  @Scheduled(cron = "0 * * * * *")
  public synchronized void scanForContractTransactions() {
    try {
      List<RuleDTO> filteredRules = evmContractTransferService.getEnabledEvmRules();
      if (CollectionUtils.isNotEmpty(filteredRules)) {
        filteredRules.forEach(rule -> {
          evmContractTransferService.scanForContractTransactions(rule);
        });
      }
    } catch (Exception e) {
      LOG.error("An error occurred while rewarding for EVM events", e);
    }
  }

  @ContainerTransactional
  @Scheduled(cron = "0 * * * * *")
  public synchronized void saveEVMContractTransactions() {
    try {
      List<RuleDTO> filteredRules = evmContractTransferService.getEnabledEvmRules();
      if (CollectionUtils.isNotEmpty(filteredRules)) {
        LOG.info("Start listening evm token transfers for {} configured rules", filteredRules.size());
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
        LOG.info("End listening evm token transfers");
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

}
