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

import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.storage.EvmTransactionStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EvmTransactionService {

  private static final Logger       LOG = LoggerFactory.getLogger(EvmTransactionService.class);

  @Autowired
  private EvmTransactionStorage evmTransactionStorage;

  public void saveTransaction(EvmTransaction transaction) {
    evmTransactionStorage.saveEvmTransaction(transaction);
  }

  public List<EvmTransaction> getTransferredTokensTransactions(String contractAddress, long networkId) {
    return evmTransactionStorage.getEvmTransactionsByContractAddressAndNetworkId(contractAddress, networkId);
  }

  public List<EvmTransaction> getTransactionsByFromAddress(String fromAddress) {
    return evmTransactionStorage.getEvmTransactionsByFromAddress(fromAddress);
  }

  public List<EvmTransaction> getTransactionsByFromAId(Long id) {
    return evmTransactionStorage.getEvmTransactionsFromId(id);
  }

}
