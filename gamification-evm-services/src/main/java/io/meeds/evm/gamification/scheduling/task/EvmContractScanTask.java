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
import io.meeds.evm.gamification.service.EvmContractTransferService;
import io.meeds.gamification.model.RuleDTO;

import org.apache.commons.collections4.CollectionUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EvmContractScanTask {
  private static final Logger        LOG = LoggerFactory.getLogger(EvmContractSaveTask.class);

  @Autowired
  private EvmContractTransferService evmContractTransferService;

  @ContainerTransactional
  @Scheduled(cron = "${gamification.evm.transactionScan.cron:0 */2 * * * *}")
  public synchronized void scanForContractTransactions() {

    List<RuleDTO> enabledRules = evmContractTransferService.getEnabledEvmRules();
    if (CollectionUtils.isNotEmpty(enabledRules)) {
        enabledRules.forEach(rule -> {
        try {
          evmContractTransferService.scanForContractTransactions(rule);
        } catch (Exception e) {
          LOG.error("An error occurred while rewarding for {} rule", rule.getTitle(), e);
        }
      });
    }

  }

}
