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

import io.meeds.evm.gamification.entity.TransactionDetailsEntity;
import io.meeds.evm.gamification.model.TransactionDetails;

public class EntityMapper {

  private EntityMapper() {
    // Util class
  }

  public static TransactionDetails fromEntity(TransactionDetailsEntity transactionDetails) {
    if (transactionDetails == null) {
      return null;
    }
    return new TransactionDetails(transactionDetails.getId(),
                                  transactionDetails.getTransactionHash(),
                                  transactionDetails.getNetworkId(),
                                  transactionDetails.getFromAddress(),
                                  transactionDetails.getToAddress(),
                                  transactionDetails.getContractAddress(),
                                  transactionDetails.getSentDate(),
                                  transactionDetails.getAmount(),
                                  transactionDetails.getTreatedTransactionStatus());
  }

  public static TransactionDetailsEntity toEntity(TransactionDetails transactionDetails) {
    if (transactionDetails == null) {
      return null;
    }
    TransactionDetailsEntity transactionDetailsEntity = new TransactionDetailsEntity();
    transactionDetailsEntity.setId(transactionDetails.getId());
    transactionDetailsEntity.setTransactionHash(transactionDetails.getTransactionHash());
    transactionDetailsEntity.setNetworkId(transactionDetails.getNetworkId());
    transactionDetailsEntity.setFromAddress(transactionDetails.getFromAddress());
    transactionDetailsEntity.setToAddress(transactionDetails.getToAddress());
    transactionDetailsEntity.setContractAddress(transactionDetails.getContractAddress());
    transactionDetailsEntity.setSentDate(transactionDetails.getSentDate());
    transactionDetailsEntity.setAmount(transactionDetails.getAmount());
    transactionDetailsEntity.setTreatedTransactionStatus(transactionDetails.getTreatedTransactionStatus());
    return transactionDetailsEntity;
  }
}
