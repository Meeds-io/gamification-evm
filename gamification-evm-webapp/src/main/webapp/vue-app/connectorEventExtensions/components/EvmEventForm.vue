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
    <v-card-text class="px-0 dark-grey-color font-weight-bold">
      {{ $t('gamification.event.form.contractAddress') }}
    </v-card-text>
    <v-card-text class="ps-0 py-0">
      <input
        ref="contractAddress"
        v-model="contractAddress"
        placeholder="Enter the contract address"
        type="text"
        class="ignore-vuetify-classes full-width"
        required
        @input="handleAddress"
        @change="checkContractAddress(contractAddress)">
    </v-card-text>
    <v-list-item-action-text v-if="!isValidAddress" class="d-flex py-0 me-0 me-sm-8">
      <span class="error--text">{{ $t('gamification.event.detail.invalidContractAddress.error') }}</span>
    </v-list-item-action-text>
  </div>
</template>
<script>
export default {
  props: {
    properties: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      contractAddress: null,
      startTypingKeywordTimeout: 0,
      startSearchAfterInMilliseconds: 300,
      endTypingKeywordTimeout: 50,
      isValidAddress: true
    };
  },
  created() {
    if (this.properties?.contractAddress){
      this.contractAddress = this.properties?.contractAddress;
    } else {
      document.dispatchEvent(new CustomEvent('event-form-unfilled'));
    }
  },
  methods: {
    handleAddress() {
      if (this.contractAddress) {
        this.startTypingKeywordTimeout = Date.now() + this.startSearchAfterInMilliseconds;
        if (!this.typing) {
          this.typing = true;
          this.waitForEndTyping();
        }
      }
    },
    waitForEndTyping() {
      window.setTimeout(() => {
        if (Date.now() > this.startTypingKeywordTimeout) {
          this.typing = false;
          if (this.checkContractAddress(this.contractAddress) && this.contractAddress !== this.properties?.contractAddress) {
            const eventProperties = {
              contractAddress: this.contractAddress
            };
            document.dispatchEvent(new CustomEvent('event-form-filled', {detail: eventProperties}));
          } else {
            document.dispatchEvent(new CustomEvent('event-form-unfilled'));
          }
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
    checkContractAddress(contractAddress) {
      const addressUrlRegex = /^(0x)?[0-9a-f]{40}$/i;
      this.isValidAddress = addressUrlRegex.test(contractAddress);
      return this.isValidAddress;
    },
  },
};
</script>