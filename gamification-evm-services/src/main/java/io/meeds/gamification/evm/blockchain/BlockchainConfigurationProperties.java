package io.meeds.gamification.evm.blockchain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gamification.evm.blockchain")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainConfigurationProperties {

  private String polygonNetworkUrl = "https://polygon-mumbai.g.alchemy.com/v2/5grQcqG3YrkpuxDbfhXE8HgPv04_iwSK";

  private String meedAddress       = "0x334D85047da64738c065d36E10B2AdEb965000d0";

}
