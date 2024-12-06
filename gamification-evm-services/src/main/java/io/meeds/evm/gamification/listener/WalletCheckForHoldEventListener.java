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
package io.meeds.evm.gamification.listener;

import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.meeds.evm.gamification.service.EvmContractTransferService;
import io.meeds.evm.gamification.service.EvmTransactionService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.wallet.model.Wallet;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.listener.Listener;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.meeds.wallet.utils.WalletUtils.NEW_ADDRESS_ASSOCIATED_EVENT;
import static io.meeds.wallet.utils.WalletUtils.MODIFY_ADDRESS_ASSOCIATED_EVENT;
import static io.meeds.evm.gamification.utils.Utils.BLOCK_TIME_AVERAGE;

@Component
public class WalletCheckForHoldEventListener extends Listener<Wallet, String> {
  private static final List<String>  SUPPORTED_EVENTS = Arrays.asList(NEW_ADDRESS_ASSOCIATED_EVENT,
                                                                      MODIFY_ADDRESS_ASSOCIATED_EVENT);

  @Autowired
  private ListenerService            listenerService;

  @Autowired
  private EvmContractTransferService evmContractTransferService;

  @Autowired
  private EvmBlockchainService       evmBlockchainService;

  @Autowired
  private EvmTransactionService      evmTransactionService;

  @PostConstruct
  public void init() {
    for (String eventName : SUPPORTED_EVENTS) {
      listenerService.addListener(eventName, this);
    }
  }

  @Override
  @ExoTransactional
  public void onEvent(Event<Wallet, String> event) {
    List<RuleDTO> holdEventEvmRules = evmContractTransferService.getHoldEventEvmRules();
    if (CollectionUtils.isNotEmpty(holdEventEvmRules)) {
      Wallet wallet = event.getSource();
      String walletAddress = wallet.getAddress();
      List<RuleDTO> rules = new ArrayList<>();
      holdEventEvmRules.forEach(holdEventEvmRule -> {
        boolean isRuleExists = false;
        if (rules != null) {
          isRuleExists = rules.stream()
                              .anyMatch(rule -> rule.getEvent()
                                                    .getProperties()
                                                    .get(Utils.NETWORK_ID)
                                                    .compareTo(holdEventEvmRule.getEvent()
                                                                               .getProperties()
                                                                               .get(Utils.NETWORK_ID)) == 0
                                  && StringUtils.equals(rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS),
                                                        holdEventEvmRule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS)));
        }
        if (!isRuleExists) {
          BigInteger minAmount = new BigInteger(holdEventEvmRule.getEvent().getProperties().get(Utils.MIN_AMOUNT));
          BigInteger base = new BigInteger("10");
          Integer tokenDecimals = Integer.parseInt(holdEventEvmRule.getEvent().getProperties().get(Utils.TOKEN_DECIMALS));
          BigInteger desiredMinAmount = base.pow(tokenDecimals).multiply(minAmount);
          String contractAddress = holdEventEvmRule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS).toLowerCase();
          String blockchainNetwork = holdEventEvmRule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
          Long networkId = Long.parseLong(holdEventEvmRule.getEvent().getProperties().get(Utils.NETWORK_ID));
          Long duration = Long.parseLong(holdEventEvmRule.getEvent().getProperties().get(Utils.DURATION));
          BigInteger walletBalance = evmBlockchainService.erc20BalanceOf(walletAddress, contractAddress, blockchainNetwork);
          if (walletBalance.compareTo(desiredMinAmount) >= 0) {
            EvmTransaction lastTransaction = evmTransactionService.getLastScannedTransactionByWalletAddress(contractAddress,
                                                                                                            networkId,
                                                                                                            walletAddress);
            if (lastTransaction != null) {
              if (Utils.isValidDurationHoldingToken(lastTransaction, duration)) {
                evmContractTransferService.handleTriggerForHoldEvent(holdEventEvmRule, lastTransaction, walletAddress);
                rules.add(holdEventEvmRule);
              }
            } else {
              long toBlock = evmBlockchainService.getLastBlock(blockchainNetwork);
              long fromBlock = toBlock - (duration / BLOCK_TIME_AVERAGE);
              List<EvmTransaction> evmTransactions = evmBlockchainService.getEvmTransactions(fromBlock,
                                                                                             toBlock,
                                                                                             contractAddress,
                                                                                             blockchainNetwork);
              evmTransactions = evmTransactions.stream()
                                               .filter(transaction -> StringUtils.equals(transaction.getFromAddress(), walletAddress))
                                               .collect(Collectors.toList());
              if (CollectionUtils.isEmpty(evmTransactions)) {
                EvmTransaction transaction = new EvmTransaction();
                transaction.setTransactionHash("");
                transaction.setAmount(desiredMinAmount);
                transaction.setToAddress(walletAddress);
                transaction.setTransactionDate(System.currentTimeMillis() - duration);
                evmContractTransferService.handleTriggerForHoldEvent(holdEventEvmRule, transaction, walletAddress);
                rules.add(holdEventEvmRule);
              }
            }
          }
        }
      });
    }
  }
}
