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

import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.plugin.EventPlugin;

import java.util.List;
import java.util.Map;

public class EvmEventPlugin extends EventPlugin{
  public static final String EVENT_TYPE = "evm";

  @Override
  public String getEventType() {
    return EVENT_TYPE;
  }

  public List<String> getTriggers() {
    return List.of(Utils.TRANSFER_TOKEN_EVENT);
  }

  @Override
  public boolean isValidEvent(Map<String, String> eventProperties, String triggerDetails) {
    String desiredContractAddress = eventProperties.get(Utils.CONTRACT_ADDRESS);
    String desiredTokenName = eventProperties.get(Utils.NAME);
    String desiredTokenSymbol = eventProperties.get(Utils.SYMBOL);
    String desiredNetwork = eventProperties.get(Utils.BLOCKCHAIN_NETWORK);
    Map<String, String> triggerDetailsMop = Utils.stringToMap(triggerDetails);
    return (desiredContractAddress != null && desiredContractAddress.equals(triggerDetailsMop.get(Utils.CONTRACT_ADDRESS)))
        && (desiredNetwork != null && desiredNetwork.equals(triggerDetailsMop.get((Utils.BLOCKCHAIN_NETWORK))))
        && (desiredTokenName != null && desiredTokenName.equals(triggerDetailsMop.get((Utils.NAME))))
        && (desiredTokenSymbol != null && desiredTokenSymbol.equals(triggerDetailsMop.get((Utils.SYMBOL))));
  }

}
