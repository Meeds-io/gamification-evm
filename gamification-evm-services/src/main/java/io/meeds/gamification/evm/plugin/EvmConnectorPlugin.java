package io.meeds.gamification.evm.plugin;

import io.meeds.gamification.plugin.ConnectorPlugin;
import io.meeds.gamification.service.ConnectorSettingService;

public class EvmConnectorPlugin extends ConnectorPlugin {

  private static final String           CONNECTOR_NAME = "evm";

  private final ConnectorSettingService connectorSettingService;

  public EvmConnectorPlugin(ConnectorSettingService connectorSettingsService) {
    this.connectorSettingService = connectorSettingsService;
  }

  @Override
  public String getConnectorName() {
    return CONNECTOR_NAME;
  }
    
}
