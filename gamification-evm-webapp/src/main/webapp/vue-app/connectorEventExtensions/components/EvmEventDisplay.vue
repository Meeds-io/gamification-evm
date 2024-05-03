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
      {{ $t('gamification.event.detail.display.contractAddress') }}
    </div>
    <a
      :href="explorerLink"
      target="_blank"
      class="text-color d-flex flex-row">
      <v-avatar size="24" class="border-color">
        <v-img :src="networkImageUrl" />
      </v-avatar>
      <div class="text-font-size my-auto ms-2 text-truncate">
        {{ contractAddress }}
      </div>
    </a>
    <div v-if="targetAddress" class="subtitle-1 font-weight-bold mb-2 mt-4">
      {{ addressLabel }}
    </div>
    <div class="text-font-size align-self-start">
      {{ targetAddress }}
    </div>
    <div v-if="minAmount" class="subtitle-1 font-weight-bold mb-2 mt-4">
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
    targetAddress() {
      return this.properties?.targetAddress;
    },
    networkImageUrl() {
      if (this.networkId === 137 || this.networkId === 80002) {
        return '/gamification-evm/images/polygon.png';
      } else {
        return '/gamification-evm/images/EVM.png';
      }
    },
    addressLabel() {
      return this.trigger === 'sendToken' ? this.$t('gamification.event.form.recipientAddress') : this.$t('gamification.event.form.senderAddress');
    },
  },
};
</script>
