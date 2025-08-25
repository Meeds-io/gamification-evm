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

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.meeds.wallet.model.Wallet;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.meeds.evm.gamification.service.EvmContractTransferService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.service.RuleService;
import io.meeds.wallet.service.WalletAccountService;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.meeds.evm.gamification.utils.Utils.*;
import static io.meeds.gamification.utils.Utils.POST_CREATE_RULE_EVENT;
import static io.meeds.gamification.utils.Utils.POST_UPDATE_RULE_EVENT;
import static io.meeds.gamification.utils.Utils.getUserIdentity;

@Component
public class RuleCheckForHoldEventListener extends Listener<Long, String> {
  private static final List<String>  SUPPORTED_EVENTS = Arrays.asList(POST_CREATE_RULE_EVENT, POST_UPDATE_RULE_EVENT);

  @Autowired
  private ListenerService            listenerService;

  @Autowired
  private RuleService                ruleService;

  @Autowired
  private SpaceService               spaceService;

  @Autowired
  private WalletAccountService       walletAccountService;

  @Autowired
  private EvmBlockchainService       evmBlockchainService;

  @Autowired
  private EvmContractTransferService evmContractTransferService;

  @PostConstruct
  public void init() {
    for (String eventName : SUPPORTED_EVENTS) {
      listenerService.addListener(eventName, this);
    }
  }

  @Override
  @ExoTransactional
  public void onEvent(Event<Long, String> event) {
    Long ruleId = event.getSource();
    RuleDTO rule = ruleService.findRuleById(ruleId);
    if (rule.getProgram() == null
        || !rule.getProgram().isEnabled()
        || rule.getEvent() == null
        || !StringUtils.equals(HOLD_TOKEN_EVENT, rule.getEvent().getTrigger())) {
      return;
    }
    String trigger = rule.getEvent().getTrigger();
    Long spaceId = rule.getProgram().getSpaceId();
    String contractAddress = rule.getEvent().getProperties().get(Utils.CONTRACT_ADDRESS).toLowerCase();
    String blockchainNetwork = rule.getEvent().getProperties().get(Utils.BLOCKCHAIN_NETWORK);
    Long networkId = Long.parseLong(rule.getEvent().getProperties().get(Utils.NETWORK_ID));
    Long duration = Long.parseLong(rule.getEvent().getProperties().get(Utils.DURATION));
    org.web3j.abi.datatypes.Event blockchainEvent;
    if (evmBlockchainService.isERC1155(blockchainNetwork, contractAddress)) {
      blockchainEvent = TRANSFERSINGLE_EVENT;
    } else if (evmBlockchainService.isERC721(blockchainNetwork, contractAddress)) {
      blockchainEvent = TRANSFER_EVENT_ER721;
    } else {
      blockchainEvent = TRANSFER_EVENT_ERC20;
    }
    if (spaceId == 0) {
      Set<Wallet> wallets = walletAccountService.listWallets();
      List<String> walletsAddresses = wallets.stream().map(Wallet::getAddress).collect(Collectors.toList());
      walletsAddresses.forEach(walletAddress -> {
        if (event.getEventName().equals(POST_CREATE_RULE_EVENT)) {
          evmContractTransferService.handleHoldEvent(blockchainNetwork,
                                                     contractAddress,
                                                     walletAddress,
                                                     networkId,
                                                     duration,
                                                     rule,
                                                     blockchainEvent,
                                                     trigger,
                                                     null);

        } else if (event.getEventName().equals(POST_UPDATE_RULE_EVENT)) {
          evmContractTransferService.saveLastRewardTime(walletAddress, rule.getId());
        }
      });

    } else {
      Space space = spaceService.getSpaceById(String.valueOf(spaceId));
      String[] members = space.getMembers();
      Arrays.stream(members).forEach(member -> {
        Long identityId = Long.parseLong(getUserIdentity(member).getId());
        String walletAddress = walletAccountService.getWalletByIdentityId(identityId).getAddress();
        if (event.getEventName().equals(POST_CREATE_RULE_EVENT)) {
          evmContractTransferService.handleHoldEvent(blockchainNetwork,
                                                     contractAddress,
                                                     walletAddress,
                                                     networkId,
                                                     duration,
                                                     rule,
                                                     blockchainEvent,
                                                     trigger,
                                                     null);
        } else if (event.getEventName().equals(POST_UPDATE_RULE_EVENT)) {
          evmContractTransferService.saveLastRewardTime(walletAddress, rule.getId());
        }
      });
    }
  }
}
