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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package io.meeds.evm.gamification.plugin;

import io.meeds.gamification.service.EventService;
import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.plugin.EventPlugin;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.services.listener.ListenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Component
public class EvmEventPlugin extends EventPlugin {

  public static final String EVENT_TYPE = "evm";

  @Autowired
  private EventService       eventService;

  @Autowired
  ListenerService            listenerService;

  @PostConstruct
  public void init() {
    eventService.addPlugin(this);
  }

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }

  @Override
  public List<String> getTriggers() {
    return List.of(Utils.SEND_TOKEN_EVENT, Utils.RECEIVE_TOKEN_EVENT, Utils.HOLD_TOKEN_EVENT);
  }

  @Override
  public boolean isValidEvent(Map<String, String> eventProperties, String triggerDetails) {
    String desiredContractAddress = eventProperties.get(Utils.CONTRACT_ADDRESS).toLowerCase();
    String desiredTargetAddress = eventProperties.get(Utils.TARGET_ADDRESS);
    String minAmount = eventProperties.get(Utils.MIN_AMOUNT);
    String desiredNetwork = eventProperties.get(Utils.BLOCKCHAIN_NETWORK);
    String tokenDecimals = eventProperties.get(Utils.DECIMALS);
    Map<String, String> triggerDetailsMop = Utils.stringToMap(triggerDetails);
    if (!desiredNetwork.equals(triggerDetailsMop.get(Utils.BLOCKCHAIN_NETWORK))
        || !desiredContractAddress.equals(triggerDetailsMop.get(Utils.CONTRACT_ADDRESS).toLowerCase())) {
      return false;
    }
    boolean isValidFilters = true;
    if (StringUtils.isNotBlank(minAmount) && StringUtils.isNotBlank(tokenDecimals)) {
      isValidFilters = isValidMinAmount(minAmount,
                                        new BigInteger(triggerDetailsMop.get(Utils.MIN_AMOUNT)),
                                        Integer.parseInt(tokenDecimals));
    }
    if (StringUtils.isNotBlank(desiredTargetAddress)) {
      isValidFilters = isValidFilters && isValidTargetAddress(desiredTargetAddress, triggerDetailsMop.get(Utils.TARGET_ADDRESS));
    }
    return isValidFilters;
  }

  private boolean isValidMinAmount(String minAmount, BigInteger amountTransferred, Integer tokenDecimals) {
    BigInteger base = new BigInteger("10");
    BigInteger desiredMinAmount = base.pow(tokenDecimals).multiply(new BigInteger(minAmount));
    return amountTransferred.compareTo(desiredMinAmount) >= 0;
  }

  private boolean isValidTargetAddress(String desiredTargetAddress, String targetAddress) {
    return desiredTargetAddress.toLowerCase().equals(targetAddress.toLowerCase());
  }
}
