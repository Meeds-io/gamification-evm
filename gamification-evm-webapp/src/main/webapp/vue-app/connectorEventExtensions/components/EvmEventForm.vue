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
      {{ $t('gamification.event.form.networks') }}
    </v-card-text>
    <v-progress-circular
      v-if="loadingNetworks"
      indeterminate
      color="primary"
      size="20"
      class="ms-3 my-auto" />
    <v-chip-group
      v-model="selectedNetwork"
      :show-arrows="false"
      active-class="primary white--text">
      <evm-connector-network-item
        v-for="network in networks"
        :key="network.id"
        :network="network" />
    </v-chip-group>
    <template v-if="selected">
      <v-card-text class="px-0 dark-grey-color font-weight-bold">
        {{ $t('gamification.event.form.contractAddress') }}
      </v-card-text>
      <v-text-field
        v-if="!erc20Token"
        ref="contractAddress"
        v-model="contractAddress"
        :placeholder="$t('gamification.event.form.contractAddress.placeholder')"
        :loading="loading"
        class="pa-0"
        type="text"
        outlined
        required
        dense
        @input="handleAddress"
        @change="checkContractAddress(contractAddress)">
        <template #append-outer>
          <v-tooltip
            bottom>
            <template #activator="{ on, attrs }">
              <v-btn
                height="18px"
                width="18px"
                icon
                class="position-relative-1"
                dark
                v-bind="attrs"
                v-on="on"
                @click="retrieveERC20Token">
                <v-icon
                  :color="isValidAddress ? 'success' : 'info'"
                  class="text-header-title">
                  fas fa-check
                </v-icon>
              </v-btn>
            </template>
            <span>{{ networkVerificationMessage }}</span>
          </v-tooltip>
        </template>
      </v-text-field>
      <div v-else class="d-flex">
        <v-text-field
          ref="contractAddress"
          v-model="contractAddress"
          class="pa-0"
          type="text"
          outlined
          required
          dense
          readonly />
        <v-chip
          class="mx-2 mt-1"
          color="indigo darken-3"
          outlined>
          <span class="font-weight-bold"> {{ tokenName }} ({{ tokenSymbol }}) </span>
        </v-chip>
      </div>
      <span v-if="!isValidAddress && contractAddress && !typing" class="error--text">{{ $t('gamification.event.detail.invalidContractAddress.error') }}</span>
      <span v-else-if="!isValidERC20Address && contractAddress && !typing" class="error--text">{{ $t('gamification.event.detail.invalidERC20ContractAddress.error') }}</span>
    </template>
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
      isValidAddress: false,
      typing: false,
      erc20Token: null,
      isValidERC20Address: true,
      loading: false,
      loadingNetworks: false,
      networks: [],
      selectedNetwork: null,
      selected: null,
      eventProperties: null
    };
  },
  computed: {
    tokenName() {
      return this.erc20Token?.name;
    },
    tokenSymbol() {
      return this.erc20Token?.symbol;
    },
    networkVerificationMessage() {
      return this.$t('gamification.event.form.contractAddress.tooltip', { 0: this.selected?.name });
    }
  },
  created() {
    this.retrieveNetworks();
  },
  watch: {
    selectedNetwork() {
      this.selected = this.networks[this.selectedNetwork];
      this.handleAddress();
      this.erc20Token = null;
    }
  },
  methods: {
    submitEventProperties() {
      if (this.checkContractAddress(this.eventProperties.contractAddress) && this.erc20Token) {
        document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
      } else {
        document.dispatchEvent(new CustomEvent('event-form-unfilled'));
      }
    },
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
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl
          };
          this.checkContractAddress(this.contractAddress);
          this.isValidERC20Address = true;
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
    retrieveERC20Token() {
      this.loading = true;
      return this.$evmConnectorService.getTokenDetailsByAddress(this.eventProperties)
        .then(token => {
          this.erc20Token = token;
        })
        .then(() => this.loading = false )
        .catch(() => {
          this.isValidERC20Address = false;
          this.erc20Token = null;
          this.loading = false;
        })
        .finally(() => {
          this.submitEventProperties();
        });
    },
    retrieveNetworks() {
      this.loadingNetworks = true;
      return this.$evmConnectorService.getNetworks()
        .then(data => {
          this.networks = data;
        }).finally(() => {
          if (this.properties) {
            this.contractAddress = this.properties?.contractAddress;
            this.selected = this.networks.find(network => network.providerUrl === this.properties.blockchainNetwork);
            this.selectedNetwork = this.networks.indexOf(this.selected);
            this.isValidAddress = true;
          }
          document.dispatchEvent(new CustomEvent('event-form-unfilled'));
          this.loadingNetworks = false;
        });
    }
  },
};
</script>