/*
 * This file is part of the Meeds project (https://meeds.io/).
 * Copyright (C) 2020 - 2024 Meeds Association
 * contact@meeds.io
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
 */

export function getTokenDetailsByAddress(paramsObj) {
  const formData = new FormData();
  if (paramsObj) {
    Object.keys(paramsObj).forEach(key => {
      const value = paramsObj[key];
      if (window.Array && Array.isArray && Array.isArray(value)) {
        value.forEach(val => formData.append(key, val));
      } else {
        formData.append(key, value);
      }
    });
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`/gamification-evm/rest/gamification/connectors/evm/tokens?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp?.ok) {
      return resp.json();
    } else {
      throw new Error('Error when getting erc20 token details');
    }
  });
}

export function getNetworks() {
  return fetch('/gamification-evm/rest/gamification/connectors/evm/networks', {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp?.ok) {
      return resp.json();
    } else {
      throw new Error('Error when getting blockchain networks');
    }
  });
}