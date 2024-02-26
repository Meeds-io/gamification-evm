package io.meeds.gamification.evm.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jakarta.annotation.PostConstruct;

import org.exoplatform.wallet.service.WalletAccountService;
import org.exoplatform.wallet.model.Wallet;
import io.meeds.gamification.evm.model.EvmTrigger;
import io.meeds.gamification.evm.service.EvmTriggerService;
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
import org.picocontainer.Startable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import static io.meeds.gamification.evm.utils.Utils.*;

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
        String eventDetails = "{" + WALLET_ADDRESS + ": " + evmTrigger.getWalletAddress() + ", " + TRANSACTION_HASH + ": "
                + evmTrigger.getTransactionHash() + "}";
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
