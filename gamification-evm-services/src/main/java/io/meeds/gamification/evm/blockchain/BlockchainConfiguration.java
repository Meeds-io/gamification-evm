package io.meeds.gamification.evm.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class BlockchainConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(BlockchainConfiguration.class);

    @Autowired
    private BlockchainConfigurationProperties blockchainProperties;

    @Bean("polygonNetwork")
    public Web3j getPolygonNetworkWeb3j() {
        Web3j web3j = Web3j.build(new HttpService(blockchainProperties.getPolygonNetworkUrl()));
        return web3j;
    }

}
