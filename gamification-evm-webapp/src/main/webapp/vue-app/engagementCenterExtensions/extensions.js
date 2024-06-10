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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
export function init() {
  extensionRegistry.registerExtension('engagementCenterActions', 'user-actions', {
    type: 'evm',
    options: {
      rank: 60,
      icon: 'fab fa-ethereum',
      iconColorClass: 'text-color',
      match: (actionLabel) => [
        'sendToken',
        'receiveToken'
      ].includes(actionLabel),
      getLink: realization => {
        if (realization.objectType === 'evm' && realization.objectId !== '') {
          const transactionDetails = realization.objectId.split('#');
          const networkId = parseInt(transactionDetails[0]);
          const transactionHash = transactionDetails[1];
          switch (networkId) {
          case 1:
            realization.link = `https://etherscan.io/tx/${transactionHash}`;
            break;
          case 137:
            realization.link = `https://polygonscan.com/tx/${transactionHash}`;
            break;
          case 80002:
            realization.link = `https://amoy.polygonscan.com/tx/${transactionHash}`;
            break;
          case 11155111:
            realization.link = `https://sepolia.etherscan.io/tx/${transactionHash}`;
            break;
          default:
            realization.link = '';
          }
          return realization.link;
        }
      },
      isExtensible: true
    }
  });
}