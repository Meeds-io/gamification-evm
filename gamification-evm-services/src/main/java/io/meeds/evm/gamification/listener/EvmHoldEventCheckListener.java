package io.meeds.evm.gamification.listener;

import io.meeds.evm.gamification.model.EvmTransaction;
import io.meeds.evm.gamification.service.EvmBlockchainService;
import io.meeds.evm.gamification.service.EvmContractTransferService;
import io.meeds.evm.gamification.service.EvmTransactionService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;
import jakarta.annotation.PostConstruct;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.listener.Listener;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.meeds.evm.gamification.utils.Utils.EVM_HOLD_ACTION_EVENT;

@Component
public class EvmHoldEventCheckListener extends Listener<Map<String, String>, String> {
  private static final List<String>  SUPPORTED_EVENTS = Arrays.asList(EVM_HOLD_ACTION_EVENT);

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
  public void onEvent(Event<Map<String, String>, String> event) {
    List<RuleDTO> holdEventEvmRules = evmContractTransferService.getHoldEventEvmRules();
    String walletAddress = event.getSource().get(Utils.WALLET_ADDRESS).toLowerCase();
    if (CollectionUtils.isNotEmpty(holdEventEvmRules)) {
      holdEventEvmRules.forEach(holdEventEvmRule -> {
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
              }
            }
          }

      });
    }
  }
}
