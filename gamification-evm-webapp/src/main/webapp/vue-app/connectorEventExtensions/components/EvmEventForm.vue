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
        @keyup.enter="retrieveERC20Token"
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
      <div v-else>
        <div class="d-flex">
          <v-text-field
            ref="contractAddress"
            v-model="contractAddress"
            class="pa-0"
            type="text"
            outlined
            required
            dense
            readonly />
          <v-btn
            icon
            class="ms-2"
            @click="resetERC20Token()">
            <v-icon size="18" class="icon-default-color mx-auto">fa-edit</v-icon>
          </v-btn>
        </div>
        <v-tooltip
          bottom>
          <template #activator="{ on, attrs }">
            <a
              :href="explorerLink"
              target="_blank"
              class="text-color">
              <v-chip
                class="mt-3"
                color="primary"
                v-bind="attrs"
                v-on="on">
                <span class="mx-2 text-truncate"> {{ tokenName }} ({{ tokenSymbol }}) </span>
              </v-chip>
            </a>
          </template>
          <span>{{ $t('gamification.event.form.openExplorer') }}</span>
        </v-tooltip>
      </div>
      <span v-if="isInValidAddressFormat" class="error--text">{{ $t('gamification.event.detail.invalidContractAddress.error') }}</span>
      <span v-else-if="isInvalidERC20Address" class="error--text">{{ $t('gamification.event.detail.invalidERC20ContractAddress.error') }}</span>
      <span v-else-if="emptyERC20Token">{{ $t('gamification.event.detail.verifyToken.message') }}</span>
      <div v-if="erc20Token">
        <v-card-text class="px-0 dark-grey-color font-weight-bold">
          {{ addressLabel }}
        </v-card-text>
        <v-text-field
          ref="targetAddress"
          v-model="targetAddress"
          :placeholder="addressPlaceholder"
          class="pa-0"
          type="text"
          outlined
          dense
          @input="handleAddress"
          @change="selectedTargetAddress" />
        <span v-if="!validTargetAddress" class="error--text">{{ invalidTargetAddress }}</span>
        <v-card-text class="px-0 dark-grey-color font-weight-bold">
          {{ $t('gamification.event.form.minAmount') }}
        </v-card-text>
        <v-text-field
          ref="minAmount"
          v-model="minAmount"
          :placeholder="$t('gamification.event.form.minAmount.placeholder')"
          class="pa-0"
          type="text"
          outlined
          dense
          @change="selectedAmount" />
      </div>
    </template>
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
      eventProperties: null,
      networkId: null,
      validTargetAddress: true,
      targetAddress: null
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
    },
    isInValidAddressFormat() {
      return !this.typing && this.contractAddress && !this.isValidAddress;
    },
    isInvalidERC20Address() {
      return !this.typing && this.contractAddress && !this.isValidERC20Address;
    },
    emptyERC20Token() {
      return !this.typing && this.contractAddress && !this.erc20Token;
    },
    explorerLink() {
      const networkId = this.selected.networkId;
      switch (networkId) {
      case 1:
        return `https://etherscan.io/token/${this.contractAddress}`;
      case 137:
        return `https://polygonscan.com/token/${this.contractAddress}`;
      case 80002:
        return `https://amoy.polygonscan.com/token/${this.contractAddress}`;
      case 11155111:
        return `https://sepolia.etherscan.io/token/${this.contractAddress}`;
      default:
        return '';
      }
    },
    addressLabel() {
      return this.trigger === 'sendToken' ? this.$t('gamification.event.form.recipientAddress') : this.$t('gamification.event.form.senderAddress');
    },
    addressPlaceholder() {
      return this.trigger === 'sendToken' ? this.$t('gamification.event.form.recipientAddress.placeholder') : this.$t('gamification.event.form.senderAddress.placeholder');
    },
    invalidTargetAddress() {
      return this.trigger === 'sendToken' ? this.$t('gamification.event.detail.invalidRecipientAddress.error') : this.$t('gamification.event.detail.invalidSenderAddress.error');
    },
  },
  created() {
    this.retrieveNetworks();
  },
  watch: {
    selectedNetwork(newVal, oldVal) {
      this.selected = this.networks[this.selectedNetwork];
      this.networkId = this.selected.networkId;
      this.handleAddress();
      if ( oldVal !== null && newVal !== oldVal) {
        this.erc20Token = null;
      }
    }
  },
  methods: {
    handleAddress() {
      if (this.contractAddress || this.targetAddress) {
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
          if (this.contractAddress) {
            this.isValidAddress = this.checkContractAddress(this.contractAddress);
            this.isValidERC20Address = true;
          }
          if (this.targetAddress) {
            this.validTargetAddress = this.checkContractAddress(this.targetAddress);
          }
        } else {
          this.waitForEndTyping();
        }
      }, this.endTypingKeywordTimeout);
    },
    checkContractAddress(contractAddress) {
      const addressUrlRegex = /^(0x)?[0-9a-f]{40}$/i;
      return addressUrlRegex.test(contractAddress);
    },
    retrieveERC20Token() {
      if (this.isValidAddress) {
        this.loading = true;
        return this.$evmConnectorService.getTokenDetailsByAddress({contractAddress: this.contractAddress, blockchainNetwork: this.selected?.providerUrl})
          .then(token => {
            this.erc20Token = token;
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: token.name,
              tokenSymbol: token.symbol,
              tokenDecimals: token.decimals,
            };
            document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
          })
          .then(() => this.loading = false )
          .catch(() => {
            this.isValidERC20Address = false;
            this.erc20Token = null;
            this.loading = false;
          });
      }
    },
    resetERC20Token() {
      this.erc20Token = null;
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
            this.networkId = this.selected.networkId;
            this.selectedNetwork = this.networks.indexOf(this.selected);
            this.erc20Token = {
              name: this.properties?.tokenName,
              symbol: this.properties?.tokenSymbol,
              decimals: this.properties?.tokenDecimals
            };
            this.minAmount = this.properties?.minAmount;
            this.targetAddress = this.properties?.targetAddress;
            this.readOnly = true;
            this.isValidAddress = true;
          } else {
            document.dispatchEvent(new CustomEvent('event-form-unfilled'));
          }
          this.loadingNetworks = false;
        });
    },
    selectedAmount(minAmount) {
      if (this.targetAddress) {
        this.eventProperties = {
          contractAddress: this.contractAddress,
          blockchainNetwork: this.selected?.providerUrl,
          networkId: this.selected?.networkId,
          tokenName: this.erc20Token.name,
          tokenSymbol: this.erc20Token.symbol,
          tokenDecimals: this.erc20Token.decimals,
          targetAddress: this.targetAddress,
          minAmount: minAmount
        };
      } else {
        this.eventProperties = {
          contractAddress: this.contractAddress,
          blockchainNetwork: this.selected?.providerUrl,
          networkId: this.selected?.networkId,
          tokenName: this.erc20Token.name,
          tokenSymbol: this.erc20Token.symbol,
          tokenDecimals: this.erc20Token.decimals,
          minAmount: minAmount
        };
      }
      document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
    },
    selectedTargetAddress(targetAddress) {
      this.validTargetAddress = this.checkContractAddress(targetAddress);
      if (this.validTargetAddress) {
        if (this.minAmount) {
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl,
            networkId: this.selected?.networkId,
            tokenName: this.erc20Token.name,
            tokenSymbol: this.erc20Token.symbol,
            tokenDecimals: this.erc20Token.decimals,
            targetAddress: targetAddress,
            minAmount: this.minAmount
          };
        } else {
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl,
            networkId: this.selected?.networkId,
            tokenName: this.erc20Token.name,
            tokenSymbol: this.erc20Token.symbol,
            tokenDecimals: this.erc20Token.decimals,
            targetAddress: targetAddress
          };
        }
        document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
      }
    }
  }
};
</script>