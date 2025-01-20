<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <div>
    <div class="text-header">
      {{ $t('gamification.event.detail.display') }}
    </div>
    <div v-sanitized-html="eventDetails" class="py-4"></div>
    <a
      :href="explorerLink"
      target="_blank"
      class="text-color">
      <evm-connector-token-details
        :token="token"
        :network-id="networkId" />
    </a>
  </div>
</template>
<script>
export default {
  props: {
    properties: {
      type: Object,
      default: null
    },
    trigger: {
      type: String,
      default: null
    },
  },
  data() {
    return {
      averageDaysInAMonth: 30.44,
      dayInMilliseconds: 1000 * 60 * 60 * 24,
      tokenType: null,
      token: null
    };
  },
  computed: {
    contractAddress() {
      return this.properties?.contractAddress;
    },
    blockchainNetwork() {
      return this.properties?.blockchainNetwork;
    },
    networkId() {
      return parseInt(this.properties?.networkId);
    },
    explorerLink() {
      switch (this.networkId) {
      case 1:
        return `https://etherscan.io/address/${this.contractAddress}`;
      case 137:
        return `https://polygonscan.com/address/${this.contractAddress}`;
      case 80002:
        return `https://amoy.polygonscan.com/address/${this.contractAddress}`;
      case 11155111:
        return `https://sepolia.etherscan.io/address/${this.contractAddress}`;
      default:
        return '';
      }
    },
    minAmount() {
      return this.properties?.minAmount;
    },
    duration() {
      return this.properties?.duration;
    },
    durationNumber() {
      const freq = this.properties?.frequency;
      const freqInMilliseconds = this.getFreqInMilliseconds(freq);
      return (this.duration / freqInMilliseconds).toFixed();
    },
    durationToDisplay() {
      switch (this.properties?.frequency) {
      case 'DAYS':
        return parseInt(this.durationNumber) === 1 ? `${this.durationNumber} day` : `${this.durationNumber} days`;
      case 'WEEKS':
        return parseInt(this.durationNumber) === 1 ? `${this.durationNumber} week` : `${this.durationNumber} weeks`;
      case 'MONTHS':
        return parseInt(this.durationNumber) === 1 ? `${this.durationNumber} month` : `${this.durationNumber} months`;
      default:
        return '';
      }
    },
    isHoldEvent() {
      return this.trigger === 'holdToken';
    },
    isSendEvent() {
      return this.trigger === 'sendToken';
    },
    minAmountTitle() {
      return this.isHoldEvent ? this.$t('gamification.event.form.minBalance') : this.$t('gamification.event.form.minAmount');
    },
    holdEventDetails() {
      return this.$t('gamification.event.detail.display.details.hold', {0: this.$t('gamification.event.detail.display.hold'), 1: '<span class="font-weight-bold">', 2: this.minAmount, 3: '</span>', 4: this.durationToDisplay});
    },
    sendEventDetails() {
      return this.$t('gamification.event.detail.display.details.sendAndReceive', {0: this.$t('gamification.event.detail.display.send') ,1: '<span class="font-weight-bold">', 2: this.minAmount, 3: '</span>'});
    },
    receiveEventDetails() {
      return this.$t('gamification.event.detail.display.details.sendAndReceive', {0: this.$t('gamification.event.detail.display.receive') ,1: '<span class="font-weight-bold">', 2: this.minAmount, 3: '</span>'});
    },
    eventDetails() {
      return this.isHoldEvent? this.holdEventDetails : this.isSendEvent? this.sendEventDetails : this.receiveEventDetails;
    }
  },
  mounted() {
    this.getToken();

  },
  methods: {
    getFreqInMilliseconds(freq) {
      if (freq === 'DAYS') {
        return this.dayInMilliseconds;
      } else if (freq === 'WEEKS') {
        return this.dayInMilliseconds * 7;
      } else {
        return this.dayInMilliseconds * this.averageDaysInAMonth;
      }
    },
    getToken() {
      if (this.properties?.tokenType) {
        if (this.properties?.tokenType === 'ERC-20' || this.properties?.tokenType === 'ERC-721') {
          this.token = { symbol: this.properties?.tokenSymbol,
            name: this.properties?.tokenName,
            type: this.properties?.tokenType };
        } else {
          this.token = { type: this.properties?.tokenType };
        }
      } else {
        this.$evmConnectorService.getTokenTypeByAddress({contractAddress: this.contractAddress, blockchainNetwork: this.blockchainNetwork})
          .then(tokenType => {
            if (tokenType === 'ERC-20' || tokenType === 'ERC-721') {
              this.token = { symbol: this.properties?.tokenSymbol,
                name: this.properties?.tokenName,
                type: tokenType };
            } else {
              this.token = { type: tokenType };
            }
          });
      }


    }
  }
};
</script>
