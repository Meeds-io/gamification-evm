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
package io.meeds.evm.gamification.storage;

import io.meeds.evm.gamification.dao.EvmTransactionDAO;
import io.meeds.evm.gamification.entity.EvmTransactionEntity;
import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.utils.EntityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EvmTransactionStorage {

  @Autowired
  private EvmTransactionDAO evmTransactionDAO;

  public EvmTransaction saveEvmTransaction(EvmTransaction evmTransaction) {
    EvmTransactionEntity evmTransactionEntity = EntityMapper.toEntity(evmTransaction);
    evmTransactionEntity = evmTransactionDAO.save(evmTransactionEntity);
    return EntityMapper.fromEntity(evmTransactionEntity);
  }

  public List<EvmTransaction> getEvmTransactionsByFromAddress(String fromAddress) {
    List<EvmTransactionEntity> evmTransactionsEntities = evmTransactionDAO.findByFromAddress(fromAddress);
    return evmTransactionsEntities.stream().map(td -> EntityMapper.fromEntity(td)).toList();
  }

  public List<EvmTransaction> getEvmTransactionsByContractAddressAndNetworkIdFromId(String contractAddress, Long networkId, Long id) {
    List<EvmTransactionEntity> evmTransactionsEntities = evmTransactionDAO.findByContractAddressAndNetworkIdAndIdGreaterThan(contractAddress, networkId, id);
    return evmTransactionsEntities.stream().map(td -> EntityMapper.fromEntity(td)).toList();
  }

  public EvmTransaction getEvmTransactionByContractAddressAndNetworkIdOrderByIdDesc(String contractAddress, Long networkId) {
    EvmTransactionEntity evmTransactionsEntity = evmTransactionDAO.findTopByContractAddressAndNetworkIdOrderByIdDesc(contractAddress, networkId);
    return EntityMapper.fromEntity(evmTransactionsEntity);
  }

}
