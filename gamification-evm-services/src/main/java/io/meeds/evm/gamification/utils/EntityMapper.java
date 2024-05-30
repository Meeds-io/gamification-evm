/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.evm.gamification.utils;

import io.meeds.evm.gamification.entity.EvmTransactionEntity;
import io.meeds.evm.gamification.model.EvmTransaction;

public class EntityMapper {

  private EntityMapper() {
    // Util class
  }

  public static EvmTransaction fromEntity(EvmTransactionEntity evmTransactionEntity) {
    if (evmTransactionEntity == null) {
      return null;
    }
    return new EvmTransaction(evmTransactionEntity.getId(),
                              evmTransactionEntity.getTransactionHash(),
                              evmTransactionEntity.getNetworkId(),
                              evmTransactionEntity.getFromAddress(),
                              evmTransactionEntity.getToAddress(),
                              evmTransactionEntity.getContractAddress(),
                              evmTransactionEntity.getSentDate(),
                              evmTransactionEntity.getAmount());
  }

  public static EvmTransactionEntity toEntity(EvmTransaction evmTransaction) {
    if (evmTransaction == null) {
      return null;
    }
    EvmTransactionEntity evmTransactionEntity = new EvmTransactionEntity();
    evmTransactionEntity.setId(evmTransaction.getId());
    evmTransactionEntity.setTransactionHash(evmTransaction.getTransactionHash());
    evmTransactionEntity.setNetworkId(evmTransaction.getNetworkId());
    evmTransactionEntity.setFromAddress(evmTransaction.getFromAddress());
    evmTransactionEntity.setToAddress(evmTransaction.getToAddress());
    evmTransactionEntity.setContractAddress(evmTransaction.getContractAddress());
    evmTransactionEntity.setSentDate(evmTransaction.getSentDate());
    evmTransactionEntity.setAmount(evmTransaction.getAmount());
    return evmTransactionEntity;
  }
}
