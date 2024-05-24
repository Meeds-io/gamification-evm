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
package io.meeds.evm.gamification.entity;

import io.meeds.evm.gamification.utils.TreatedTransactionStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

@Entity(name = "TransactionDetails")
@Table(name = "EVM_TRANSACTION_DETAILS")
@Data
public class TransactionDetailsEntity implements Serializable {

  @Id
  @SequenceGenerator(name = "SEQ_EVM_TRANSACTION_DETAILS_ID", sequenceName = "SEQ_EVM_TRANSACTION_DETAILS_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_EVM_TRANSACTION_DETAILS_ID")
  @Column(name = "ID", nullable = false)
  private Long                                  id;

  @Column(name = "TRANSACTION_HASH", nullable = false)
  private String                                transactionHash;

  @Column(name = "NETWORK_ID", nullable = false)
  private Long                                  networkId;

  @Column(name = "FROM_ADDRESS", nullable = false)
  private String                                fromAddress;

  @Column(name = "TO_ADDRESS", nullable = false)
  private String                                toAddress;

  @Column(name = "CONTRACT_ADDRESS", nullable = false)
  private String                                contractAddress;

  @Column(name = "SENT_DATE", nullable = false)
  private Long                                  sentDate;

  @Column(name = "AMOUNT", nullable = false)
  private BigInteger                            amount;

  @ElementCollection(fetch = FetchType.EAGER)
  @MapKeyColumn(name = "\"TRIGGER\"")
  @Column(name = "TREATED_TRANSACTION_STATUS")
  @CollectionTable(name = "EVM_EVENT_STATUS", joinColumns = { @JoinColumn(name = "ID") })
  private Map<String, TreatedTransactionStatus> treatedTransactionStatus;
}
