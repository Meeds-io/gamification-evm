package io.meeds.evm.gamification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ERC20Token {

  private String name;

  private String symbol;

  private BigInteger totalSupply;

  private BigInteger decimals;
}
