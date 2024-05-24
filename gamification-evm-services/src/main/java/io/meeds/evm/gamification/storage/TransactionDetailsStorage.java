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

import io.meeds.evm.gamification.dao.TransactionDetailsDAO;
import io.meeds.evm.gamification.entity.TransactionDetailsEntity;
import io.meeds.evm.gamification.model.TransactionDetails;
import io.meeds.evm.gamification.utils.EntityMapper;
import io.meeds.evm.gamification.utils.TreatedTransactionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class TransactionDetailsStorage {

  @Autowired
  private TransactionDetailsDAO transactionDetailsDAO;

  public TransactionDetails saveTransactionDetails(TransactionDetails transactionDetails) {
    TransactionDetailsEntity transactionDetailsEntity = EntityMapper.toEntity(transactionDetails);
    transactionDetailsEntity = transactionDetailsDAO.save(transactionDetailsEntity);
    return EntityMapper.fromEntity(transactionDetailsEntity);
  }

  public List<TransactionDetails> getTransactionsDetailsByContractAddressAndNetworkId(String contractAddress, Long networkId) {
    Set<TransactionDetailsEntity> transactionsDetailsEntities =
                                                              transactionDetailsDAO.findTransactionsByContractAddressAndNetworkId(contractAddress,
                                                                                                                                  networkId);
    List<TransactionDetails> transactions = transactionsDetailsEntities.stream().map(td -> EntityMapper.fromEntity(td)).toList();
    return transactions;
  }

  public List<TransactionDetails> getTransactionsDetailsByFromAddress(String fromAddress) {
    Set<TransactionDetailsEntity> transactionsDetailsEntities = transactionDetailsDAO.findTransactionsByFromAddress(fromAddress);
    List<TransactionDetails> transactions = transactionsDetailsEntities.stream().map(td -> EntityMapper.fromEntity(td)).toList();
    return transactions;
  }

  public TransactionDetails getTransactionByTransactionHash(String transactionHash) {
    TransactionDetailsEntity transactionDetailsEntity = transactionDetailsDAO.findTransactionByTransactionHash(transactionHash);
    return EntityMapper.fromEntity(transactionDetailsEntity);
  }

  public void updateTransaction(Long transactionId, String trigger, TreatedTransactionStatus status) {
    TransactionDetailsEntity transactionDetailsEntity = transactionDetailsDAO.findById(transactionId).orElse(null);
    if (transactionDetailsEntity != null && transactionDetailsEntity.getTreatedTransactionStatus() != null) {
      Map<String, TreatedTransactionStatus> statuses = transactionDetailsEntity.getTreatedTransactionStatus();
      statuses.put(trigger, status);
      transactionDetailsEntity.setTreatedTransactionStatus(statuses);
      transactionDetailsDAO.save(transactionDetailsEntity);
    }
  }
}
