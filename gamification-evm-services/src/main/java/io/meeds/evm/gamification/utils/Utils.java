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
package io.meeds.evm.gamification.utils;

import java.util.HashMap;
import java.util.Map;

public class Utils {

  public static final String CONNECTOR_NAME   = "evm";

  public static final String TRANSFER_TOKEN_EVENT = "transferToken";

  public static final String WALLET_ADDRESS   = "walletAddress";

  public static final String CONTRACT_ADDRESS = "contractAddress";

  public static final String BLOCKCHAIN_NETWORK = "blockchainNetwork";

  public static final String NAME = "tokenName";

  public static final String SYMBOL = "tokenSymbol";

  public static final String DECIMALS = "tokenDecimals";

  public static final String MIN_AMOUNT = "minAmount";

  public static final String RECIPIENT_ADDRESS = "recipientAddress";

  public static final String TRANSACTION_HASH = "transactionHash";

  private Utils() {

  }

  public static Map<String, String> stringToMap(String mapAsString) {
    Map<String, String> map = new HashMap<>();
    mapAsString = mapAsString.substring(1, mapAsString.length() - 1);
    String[] pairs = mapAsString.split(", ");
    for (String pair : pairs) {
      String[] keyValue = pair.split(": ");
      String key = keyValue[0].trim();
      String value = keyValue[1].trim();
      map.put(key, value);
    }
    return map;
  }
}
