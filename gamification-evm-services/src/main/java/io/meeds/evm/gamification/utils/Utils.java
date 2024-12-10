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

import io.meeds.evm.gamification.model.EvmTransaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Utils {

  public static final String  CONNECTOR_NAME      = "evm";

  public static final String  SEND_TOKEN_EVENT    = "sendToken";

  public static final String  RECEIVE_TOKEN_EVENT = "receiveToken";

  public static final String  HOLD_TOKEN_EVENT    = "holdToken";

  public static final String  WALLET_ADDRESS      = "walletAddress";

  public static final String  CONTRACT_ADDRESS    = "contractAddress";

  public static final String  BLOCKCHAIN_NETWORK  = "blockchainNetwork";

  public static final String  NETWORK_ID          = "networkId";

  public static final String  DECIMALS            = "tokenDecimals";

  public static final String  MIN_AMOUNT          = "minAmount";

  public static final String  TARGET_ADDRESS      = "targetAddress";

  public static final String  DURATION            = "duration";

  public static final String  SENT_DATE           = "sentDate";

  public static final String  TOKEN_BALANCE       = "tokenBalance";

  public static final String  TOKEN_DECIMALS      = "tokenDecimals";

  public static final Integer BLOCK_TIME_AVERAGE  = 13;

  public static  final String ERC721_INTERFACE_ID = "0x80ac58cd";

  public static  final String ERC1155_INTERFACE_ID = "0xd9b67a26";

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

  public static long convertDateStringToTimestamp(String dateInString) {
    SimpleDateFormat formatter = new SimpleDateFormat("yyy-MM-dd'T'HH:mm:ss.SSSXXX");
    try {
      Date creationDate = formatter.parse(dateInString);
      return creationDate.getTime();
    } catch (ParseException e) {
      throw new RuntimeException("Invalid date format", e);
    }
  }

  public static Boolean isValidDurationHoldingToken(EvmTransaction transaction, Long desiredDuration) {
    Long holdingDuration = System.currentTimeMillis() - transaction.getTransactionDate();
    return holdingDuration.compareTo(desiredDuration) >= 0;
  }

  public static byte[] hexStringToByteArray(String hexString) {
    if (hexString.startsWith("0x")) {
      hexString = hexString.substring(2);
    }
    if (hexString.length() != 8) {
      throw new IllegalArgumentException("Hex string must be exactly 8 characters long for Bytes4");
    }
    byte[] byteArray = new byte[4];
    for (int i = 0; i < 4; i++) {
      byteArray[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
    }
    return byteArray;
  }
}
