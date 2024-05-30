/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.evm.gamification.listener;

import io.meeds.evm.gamification.utils.Utils;
import io.meeds.gamification.model.RuleDTO;
import io.meeds.gamification.service.RuleService;
import jakarta.annotation.PostConstruct;
import org.exoplatform.commons.api.persistence.ExoTransactional;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

import org.exoplatform.services.listener.ListenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.List;

import static io.meeds.evm.gamification.utils.Utils.EVM_SAVE_ACTION_EVENT;

@Component
public class EvmRuleUpdateListener extends Listener<Map<String, String>, String> {

  private static final List<String> SUPPORTED_EVENTS = Arrays.asList(EVM_SAVE_ACTION_EVENT);

  @Autowired
  private RuleService               ruleService;

  @Autowired
  private ListenerService           listenerService;

  @PostConstruct
  public void init() {
    for (String eventName : SUPPORTED_EVENTS) {
      listenerService.addListener(eventName, this);
    }
  }

  @Override
  @ExoTransactional
  public void onEvent(Event<Map<String, String>, String> event) throws ObjectNotFoundException {
    Long lastIdToSave = Long.parseLong(event.getSource().get(Utils.TRANSACTION_ID));
    Long ruleId = Long.parseLong(event.getSource().get(Utils.RULE_ID));
    RuleDTO rule = ruleService.findRuleById(ruleId);
    if (rule == null) {
      throw new ObjectNotFoundException(String.format("Rule with id %s wasn't found", rule.getId()));
    }
    Map<String, String> map = rule.getEvent().getProperties();
    map.put(Utils.LAST_ID_PROCCED, lastIdToSave.toString());
    rule.getEvent().setProperties(map);
    ruleService.updateRule(rule);
  }
}
