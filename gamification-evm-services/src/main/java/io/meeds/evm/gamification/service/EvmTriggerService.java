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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.meeds.evm.gamification.model.EvmTrigger;
import io.meeds.evm.gamification.utils.Utils;
import jakarta.annotation.PostConstruct;

import jakarta.annotation.PreDestroy;
import org.exoplatform.wallet.service.WalletAccountService;
import org.exoplatform.wallet.model.Wallet;
import io.meeds.gamification.model.EventDTO;
import io.meeds.gamification.service.ConnectorService;
import io.meeds.gamification.service.EventService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvmTriggerService {

  private static final Log   LOG                        = ExoLogger.getLogger(EvmTriggerService.class);

  public static final String GAMIFICATION_GENERIC_EVENT = "exo.gamification.generic.action";

  @Autowired
  private ConnectorService connectorService;

  @Autowired
  private IdentityManager identityManager;

  @Autowired
  private ListenerService listenerService;

  @Autowired
  private EventService eventService;

  @Autowired
  private WalletAccountService walletAccountService;

  private ExecutorService executorService;

  public EvmTriggerService(ConnectorService connectorService,
                               IdentityManager identityManager,
                               ListenerService listenerService,
                               EventService eventService) {
    this.connectorService = connectorService;
    this.identityManager = identityManager;
    this.listenerService = listenerService;
    this.eventService = eventService;
  }

  @PostConstruct
  public void initialize() {
    QueuedThreadPool threadFactory = new QueuedThreadPool(5, 1, 1);
    threadFactory.setName("Gamification - Evm connector");
    executorService = Executors.newCachedThreadPool(threadFactory);
  }

  @PreDestroy
  public void stop() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  /**
   * Handle evm trigger asynchronously
   *
   * @param evmTrigger evm retrieved trigger
   */
  public void handleTriggerAsync(EvmTrigger evmTrigger) {
    executorService.execute(() -> handleTriggerAsyncInternal(evmTrigger));
  }

  @ExoTransactional
  public void handleTriggerAsyncInternal(EvmTrigger evmTrigger) {
    processEvent(evmTrigger);
  }

  private void processEvent(EvmTrigger evmTrigger) {
    Wallet wallet = walletAccountService.getWalletByAddress(evmTrigger.getWalletAddress());
    String receiverId = wallet.getId();
    if (StringUtils.isNotBlank(receiverId)) {
      Identity socialIdentity = identityManager.getOrCreateUserIdentity(receiverId);
      if (socialIdentity != null) {
        String eventDetails = "{" + Utils.WALLET_ADDRESS + ": " + evmTrigger.getWalletAddress() + ", "
                              + Utils.TRANSACTION_HASH + ": " + evmTrigger.getTransactionHash() +  ", "
                              + Utils.CONTRACT_ADDRESS + ": " + evmTrigger.getContractAddress() + ", "
                              + Utils.BLOCKCHAIN_NETWORK + ": " + evmTrigger.getBlockchainNetwork() +  "}";
        broadcastEvmEvent(evmTrigger.getTrigger(),
                          receiverId,
                          evmTrigger.getTransactionHash(),
                          evmTrigger.getType(),
                          eventDetails);
      }
    }
  }

  private void broadcastEvmEvent(String ruleTitle,
                                 String receiverId,
                                 String objectId,
                                 String objectType,
                                 String eventDetails) {
    try {
      List<EventDTO> events = eventService.getEventsByTitle(ruleTitle, 0, -1);
      if (CollectionUtils.isNotEmpty(events)) {
        Map<String, String> gam = new HashMap<>();
        gam.put("senderId", receiverId);
        gam.put("receiverId", receiverId);
        gam.put("objectId", objectId);
        gam.put("objectType", objectType);
        gam.put("ruleTitle", ruleTitle);
        gam.put("eventDetails", eventDetails);
        listenerService.broadcast(GAMIFICATION_GENERIC_EVENT, gam, "");
        LOG.info("Evm action {} broadcasted for user {}", ruleTitle, receiverId);
      }
    } catch (Exception e) {
      LOG.error("Cannot broadcast evm gamification event", e);
    }
  }

}
