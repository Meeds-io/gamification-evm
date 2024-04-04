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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.evm.gamification.plugin;

import io.meeds.gamification.plugin.ConnectorPlugin;
import org.exoplatform.commons.api.settings.ExoFeatureService;

import static io.meeds.evm.gamification.utils.Utils.CONNECTOR_NAME;

public class EvmConnectorPlugin extends ConnectorPlugin {

  private final ExoFeatureService featureService;

  public EvmConnectorPlugin(ExoFeatureService featureService) {
    this.featureService = featureService;
  }

  @Override
  public String getConnectorName() {
    return CONNECTOR_NAME;
  }

  @Override
  public boolean enabled(String username) {
    return featureService.isFeatureActiveForUser("EVMConnector", username);
  }
}
