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
    <div class="subtitle-1 font-weight-bold mb-2">
      {{ titleTriggerProps }}
    </div>
    <a
      :href="explorerLink"
      target="_blank"
      class="text-color">
      <div class="text-font-size align-self-start">
        {{ contractAddress }}
      </div>
    </a>
    <div class="subtitle-1 font-weight-bold mb-2 mt-4">
      {{ $t('gamification.event.form.recipientAddress') }}
    </div>
    <div class="text-font-size align-self-start">
      {{ recipientAddress }}
    </div>
    <div class="subtitle-1 font-weight-bold mb-2 mt-4">
      {{ $t('gamification.event.form.minAmount') }}
    </div>
    <div class="text-font-size align-self-start">
      {{ minAmount }}
    </div>
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
  computed: {
    contractAddress() {
      return this.properties?.contractAddress;
    },
    titleTriggerProps() {
      return this.$t(`gamification.event.detail.display.${this.trigger}`);
    },
    blockchainNetwork() {
      return this.properties?.blockchainNetwork;
    },
    explorerLink() {
      const url = this.blockchainNetwork?.substring(this.blockchainNetwork.indexOf('//') + 2, this.blockchainNetwork.indexOf('.g.alchemy.com'));
      switch (url) {
      case 'polygon-mainnet': return `https://polygonscan.com/address/${this.contractAddress}`;
      case 'polygon-mumbai': return `https://mumbai.polygonscan.com/address/${this.contractAddress}`;
      case 'eth-mainnet': return `https://etherscan.io/address/${this.contractAddress}`;
      case 'eth-sepolia': return `https://sepolia.etherscan.io/address/${this.contractAddress}`;
      }
      return '';
    },
    minAmount() {
      return this.properties?.minAmount;
    },
    recipientAddress() {
      return this.properties?.recipientAddress;
    }
  },
};
</script>
