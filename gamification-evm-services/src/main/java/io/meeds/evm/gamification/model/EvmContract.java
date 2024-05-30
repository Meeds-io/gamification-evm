package io.meeds.evm.gamification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvmContract {

  private String     name;

  private String     symbol;

  private BigInteger decimals;
}
