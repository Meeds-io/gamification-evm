package io.meeds.gamification.evm.scheduling.task;

import java.util.List;
import java.util.Set;

import io.meeds.common.ContainerTransactional;
import io.meeds.gamification.evm.blockchain.BlockchainConfigurationProperties;
import io.meeds.gamification.evm.model.EvmTrigger;
import io.meeds.gamification.evm.service.EvmTriggerService;
import io.meeds.gamification.evm.service.BlockchainService;
import io.meeds.gamification.model.EventDTO;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.model.filter.RuleFilter;
import io.meeds.gamification.service.EventService;
import io.meeds.gamification.service.RuleService;
import org.apache.commons.collections.CollectionUtils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.services.listener.ListenerService;

import io.meeds.gamification.evm.constant.Constants.TokenTransferEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.meeds.gamification.evm.utils.Utils.*;

@Component
public class ERC20TransferTask {
  private static final Logger LOG = LoggerFactory.getLogger(ERC20TransferTask.class);

  private static final Scope   SETTING_SCOPE               = Scope.APPLICATION.id("GAMIFICATION_EVM");

  private static final Context SETTING_CONTEXT             = Context.GLOBAL.id("GAMIFICATION_EVM");

  private static final String  SETTING_LAST_TIME_CHECK_KEY = "transferredTokenTransactionsCheck";

  @Autowired
  private EventService                      eventService;

  @Autowired
  private SettingService                    settingService;

  @Autowired
  private ListenerService                   listenerService;

  @Autowired
  private BlockchainService                 blockchainService;

  @Autowired
  private EvmTriggerService                 evmTriggerService;

  @Autowired
  private BlockchainConfigurationProperties blockchainProperties;

  @Autowired
  private RuleService                       ruleService;

  @ContainerTransactional
  @Scheduled(cron = "5 * * * * *")
  public synchronized void listenTokenTransfer() {
      LOG.info("Start listening erc20 token transfers");
      try {
          RuleFilter ruleFilter = new RuleFilter(true);
          ruleFilter.setEventType(CONNECTOR_NAME);
          List<RuleDTO> rules = ruleService.getRules(ruleFilter, 0, -1);
          if (CollectionUtils.isNotEmpty(rules)) {
              long lastBlock = blockchainService.getLastBlock();
              long lastCheckedBlock = getLastCheckedBlock(blockchainProperties.getMeedAddress());
              if (lastCheckedBlock == 0) {
                  // If this is the first time that it's started, save the last block as
                  // last checked one
                  saveLastCheckedBlock(lastBlock, blockchainProperties.getMeedAddress());
                  return;
              }

              Set<TokenTransferEvent> events = blockchainService.getTransferredTokensTransactions(lastCheckedBlock + 1,
                      lastBlock,
                      blockchainProperties.getMeedAddress());
              if (!CollectionUtils.isEmpty(events)) {
                  events.forEach(event -> {
                      try {
                          EvmTrigger evmTrigger = new EvmTrigger();
                          evmTrigger.setTrigger(HOLD_TOKEN_EVENT);
                          evmTrigger.setType(CONNECTOR_NAME);
                          evmTrigger.setWalletAddress(event.getTo());
                          evmTrigger.setTransactionHash(event.getTransactionHash());
                          evmTriggerService.handleTriggerAsync(evmTrigger);
                      } catch (Exception e) {
                          LOG.warn("Error broadcasting event '" + event, e);
                      }
                  });
              }
              saveLastCheckedBlock(lastBlock, blockchainProperties.getMeedAddress());
              LOG.info("End listening erc20 token transfers");
          }
      } catch (Exception e) {
          LOG.error("An error occurred while listening erc20 token transfers", e);
      }
  }

    @ContainerTransactional
    public long getLastCheckedBlock(String contractAddress) {
        long lastCheckedBlock = 0;
        SettingValue<?> settingValue = settingService.get(SETTING_CONTEXT, SETTING_SCOPE, SETTING_LAST_TIME_CHECK_KEY + contractAddress);
        if (settingValue != null && settingValue.getValue() != null) {
            lastCheckedBlock = Long.parseLong(settingValue.getValue().toString());
        }
        return lastCheckedBlock;
    }

    @ContainerTransactional
    public void saveLastCheckedBlock(long lastBlock, String contractAddress) {
        settingService.set(SETTING_CONTEXT, SETTING_SCOPE, SETTING_LAST_TIME_CHECK_KEY + contractAddress, SettingValue.create(lastBlock));
    }
}
