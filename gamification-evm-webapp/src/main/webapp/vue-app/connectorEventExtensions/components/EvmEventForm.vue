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
    <v-card-text class="px-0">
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
      <v-card-text class="px-0">
        {{ $t('gamification.event.form.contractAddress') }}
      </v-card-text>
      <v-text-field
        v-if="!token"
        ref="contractAddress"
        v-model="contractAddress"
        :placeholder="$t('gamification.event.form.contractAddress.placeholder')"
        :loading="loading"
        class="pa-0"
        type="text"
        outlined
        required
        dense
        @keyup.enter="retrieveTokenDetails"
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
                @click="retrieveTokenDetails">
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
            @click="resetToken()">
            <v-icon size="18" class="icon-default-color mx-auto">fa-edit</v-icon>
          </v-btn>
        </div>
        <evm-connector-token-details
          :token="token"
          :network-id="networkId"
          :network="networks[networks.indexOf(this.selected)]" />
      </div>
      <span v-if="isInValidAddressFormat" class="error--text">{{ $t('gamification.event.detail.invalidContractAddress.error') }}</span>
      <span v-else-if="isInvalidAddress" class="error--text">{{ $t('gamification.event.detail.invalidTokenContractAddress.error') }}</span>
      <span v-else-if="emptyToken">{{ $t('gamification.event.detail.verifyToken.message') }}</span>
      <div v-if="token">
        <div v-if="!isHoldEvent">
          <v-card-text class="px-0">
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
        </div>
        <v-card-text class="px-0">
          {{ minAmountTitle }}
        </v-card-text>
        <v-text-field
          ref="minAmount"
          v-model="minAmount"
          :placeholder="minAmountPlaceholder"
          class="pa-0"
          type="text"
          outlined
          dense
          @change="selectedAmount" />
        <div v-if="isHoldEvent">
          <v-card-text class="px-0">
            {{ $t('gamification.event.form.duration') }}
          </v-card-text>
          <div class="d-flex flex-row">
            <v-card
              flat
              class="d-flex flex-grow-1">
              <v-text-field
                v-model="durationNumber"
                class="mt-0 pt-0 me-2"
                type="number"
                outlined
                dense
                required />
            </v-card>
            <select
              v-model="durationFilter"
              class="d-flex flex-grow-0 flex-shrink-0 ignore-vuetify-classes my-0"
              @change="resetDates">
              <option value="DAYS">
                {{ $t('gamification.event.form.duration.days') }}
              </option>
              <option value="WEEKS">
                {{ $t('gamification.event.form.duration.weeks') }}
              </option>
              <option value="MONTHS">
                {{ $t('gamification.event.form.duration.months') }}
              </option>
            </select>
          </div>
        </div>
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
      token: null,
      isValidTokenAddress: true,
      loading: false,
      loadingNetworks: false,
      networks: [],
      selectedNetwork: null,
      selected: null,
      eventProperties: null,
      networkId: null,
      validTargetAddress: true,
      targetAddress: null,
      durationFilter: 'DAYS',
      durationNumber: 0,
      averageDaysInAMonth: 30.44,
      dayInMilliseconds: 1000 * 60 * 60 * 24
    };
  },
  computed: {
    tokenName() {
      return this.token?.name;
    },
    tokenSymbol() {
      return this.token?.symbol;
    },
    isERC1155Token() {
      return this.token?.type === 'ERC-1155';
    },
    networkVerificationMessage() {
      return this.$t('gamification.event.form.contractAddress.tooltip', { 0: this.selected?.name });
    },
    isInValidAddressFormat() {
      return !this.typing && this.contractAddress && !this.isValidAddress;
    },
    isInvalidAddress() {
      return !this.typing && this.contractAddress && !this.isValidTokenAddress;
    },
    emptyToken() {
      return !this.typing && this.contractAddress && !this.token;
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
    isHoldEvent() {
      return this.trigger === 'holdToken';
    },
    minAmountTitle() {
      return this.isHoldEvent ? this.$t('gamification.event.form.minBalance') : this.$t('gamification.event.form.minAmount');
    },
    minAmountPlaceholder() {
      return this.isHoldEvent ? this.$t('gamification.event.form.minBalance.placeholder') : this.$t('gamification.event.form.minAmount.placeholder');
    },
    tokenType() {
      return this.token?.type;
    }
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
        this.token = null;
      }
    },
    durationNumber() {
      this.changeDuration();
    },
    durationFilter() {
      this.changeDuration();
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
            this.isValidTokenAddress = true;
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
    retrieveTokenDetails() {
      if (this.isValidAddress) {
        this.loading = true;
        return this.$evmConnectorService.getTokenDetailsByAddress({contractAddress: this.contractAddress, blockchainNetwork: this.selected?.providerUrl})
          .then(token => {
            this.token = token;
            if (this.isERC20(token.type)) {
              this.eventProperties = { contractAddress: this.contractAddress,
                blockchainNetwork: this.selected?.providerUrl,
                networkId: this.selected?.networkId,
                tokenName: token.name,
                tokenSymbol: token.symbol,
                tokenDecimals: token.decimals,
                tokenType: token.type
              };
            } else if (this.isERC721(token.type)) {
              this.eventProperties = { contractAddress: this.contractAddress,
                blockchainNetwork: this.selected?.providerUrl,
                networkId: this.selected?.networkId,
                tokenName: token.name,
                tokenSymbol: token.symbol,
                tokenType: token.type
              };
            } else if (this.isERC1155(token.type)) {
              this.eventProperties = { contractAddress: this.contractAddress,
                blockchainNetwork: this.selected?.providerUrl,
                networkId: this.selected?.networkId,
                tokenType: token.type
              };
            }
            document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
          })
          .then(() => this.loading = false )
          .catch(() => {
            this.isValidTokenAddress = false;
            this.token = null;
            this.loading = false;
          });
      }
    },
    resetToken() {
      this.token = null;
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
            if (this.isERC20(this.properties.type)) {
              this.token = {
                name: this.properties?.tokenName,
                symbol: this.properties?.tokenSymbol,
                decimals: this.properties?.tokenDecimals,
                type: this.properties?.tokenType
              };
            } else if (this.isERC721(this.properties.type)) {
              this.token = {
                name: this.properties?.tokenName,
                symbol: this.properties?.tokenSymbol,
                type: this.properties?.tokenType
              };
            } else if (this.isERC1155(this.properties.type)) {
              this.token = {
                type: this.properties?.tokenType
              };
            }
            this.minAmount = this.properties?.minAmount;
            this.targetAddress = this.properties?.targetAddress;
            this.durationFilter = this.properties?.frequency;
            this.durationNumber = (this.properties?.duration / this.getFreqInMilliseconds(this.durationFilter)).toFixed();
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
        if (this.isERC20(this.tokenType)) {
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl,
            networkId: this.selected?.networkId,
            tokenName: this.token.name,
            tokenSymbol: this.token.symbol,
            tokenDecimals: this.token.decimals,
            tokenType: this.token.type,
            targetAddress: this.targetAddress,
            minAmount: minAmount
          };
        } else if (this.isERC721(this.tokenType)) {
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl,
            networkId: this.selected?.networkId,
            tokenName: this.token.name,
            tokenSymbol: this.token.symbol,
            tokenType: this.token.type,
            targetAddress: this.targetAddress,
            minAmount: minAmount
          };
        } else if (this.isERC1155(this.tokenType)) {
          this.eventProperties = {
            contractAddress: this.contractAddress,
            blockchainNetwork: this.selected?.providerUrl,
            networkId: this.selected?.networkId,
            tokenType: this.token.type,
            targetAddress: this.targetAddress,
            minAmount: minAmount
          };
        }
      } else {
        if (this.properties?.duration && this.properties?.frequency) {
          if (this.isERC20(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenDecimals: this.token.decimals,
              tokenType: this.token.type,
              minAmount: minAmount,
              duration: this.properties?.duration,
              frequency: this.properties?.frequency
            };
          } else if (this.isERC721(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenType: this.token.type,
              minAmount: minAmount,
              duration: this.properties?.duration,
              frequency: this.properties?.frequency
            };
          } else if (this.isERC1155(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenType: this.token.type,
              minAmount: minAmount,
              duration: this.properties?.duration,
              frequency: this.properties?.frequency
            };
          }
        } else {
          if (this.isERC20(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.type.name,
              tokenSymbol: this.type.symbol,
              tokenDecimals: this.type.decimals,
              tokenType: this.token.type,
              minAmount: minAmount
            };
          } else if (this.isERC721(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.type.name,
              tokenSymbol: this.type.symbol,
              tokenType: this.token.type,
              minAmount: minAmount
            };
          } else if (this.isERC1155(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenType: this.token.type,
              minAmount: minAmount
            };
          }
        }
      }
      document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
    },
    selectedTargetAddress(targetAddress) {
      this.validTargetAddress = this.checkContractAddress(targetAddress);
      if (this.validTargetAddress) {
        if (this.minAmount) {
          if (this.isERC20(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenDecimals: this.token.decimals,
              tokenType: this.token.type,
              targetAddress: targetAddress,
              minAmount: this.minAmount
            };
          } else if (this.isERC721(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenType: this.token.type,
              targetAddress: targetAddress,
              minAmount: this.minAmount
            };
          } else if (this.isERC1155(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenType: this.token.type,
              targetAddress: targetAddress,
              minAmount: this.minAmount
            };
          }

        } else {
          if (this.isERC20(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenDecimals: this.token.decimals,
              tokenType: this.token.type,
              targetAddress: targetAddress
            };
          } else if (this.isERC721(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenName: this.token.name,
              tokenSymbol: this.token.symbol,
              tokenType: this.token.type,
              targetAddress: targetAddress
            };
          } else if (this.isERC1155(this.tokenType)) {
            this.eventProperties = {
              contractAddress: this.contractAddress,
              blockchainNetwork: this.selected?.providerUrl,
              networkId: this.selected?.networkId,
              tokenType: this.token.type,
              targetAddress: targetAddress
            };
          }
        }
        document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
      }
    },
    durationToTimestamp(months, weeks, days) {
      let durationTimestamp = new Date();
      durationTimestamp.setMonth(durationTimestamp.getMonth() + months);
      durationTimestamp.setDate(durationTimestamp.getDate() + weeks * 7 + days);
      durationTimestamp = (durationTimestamp - new Date());
      if (this.isERC20(this.tokenType)) {
        this.eventProperties = {
          contractAddress: this.contractAddress,
          blockchainNetwork: this.selected?.providerUrl,
          networkId: this.selected?.networkId,
          tokenName: this.token.name,
          tokenSymbol: this.token.symbol,
          tokenDecimals: this.token.decimals,
          tokenType: this.token.type,
          minAmount: this.minAmount,
          duration: durationTimestamp,
          frequency: months !== 0 ? 'MONTHS' : weeks !== 0 ? 'WEEKS' : 'DAYS'
        };
      } else if (this.isERC721(this.tokenType)) {
        this.eventProperties = {
          contractAddress: this.contractAddress,
          blockchainNetwork: this.selected?.providerUrl,
          networkId: this.selected?.networkId,
          tokenName: this.token.name,
          tokenSymbol: this.token.symbol,
          tokenType: this.token.type,
          minAmount: this.minAmount,
          duration: durationTimestamp,
          frequency: months !== 0 ? 'MONTHS' : weeks !== 0 ? 'WEEKS' : 'DAYS'
        };
      } else if (this.isERC1155(this.tokenType)) {
        this.eventProperties = {
          contractAddress: this.contractAddress,
          blockchainNetwork: this.selected?.providerUrl,
          networkId: this.selected?.networkId,
          tokenType: this.token.type,
          minAmount: this.minAmount,
          duration: durationTimestamp,
          frequency: months !== 0 ? 'MONTHS' : weeks !== 0 ? 'WEEKS' : 'DAYS'
        };
      }

      document.dispatchEvent(new CustomEvent('event-form-filled', {detail: this.eventProperties}));
    },
    changeDuration() {
      if (this.durationFilter === 'MONTHS') {
        this.durationToTimestamp(parseInt(this.durationNumber), 0, 0);
      } else if (this.durationFilter === 'WEEKS') {
        this.durationToTimestamp(0, parseInt(this.durationNumber), 0);
      } else {
        this.durationToTimestamp(0, 0, parseInt(this.durationNumber));
      }
    },
    getFreqInMilliseconds(freq) {
      if (freq === 'DAYS') {
        return this.dayInMilliseconds;
      } else if (freq === 'WEEKS') {
        return this.dayInMilliseconds * 7;
      } else {
        return this.dayInMilliseconds * this.averageDaysInAMonth;
      }
    },
    isERC20(tokenType) {
      return tokenType === 'ERC-20';
    },
    isERC721(tokenType) {
      return tokenType === 'ERC-721';
    },
    isERC1155(tokenType) {
      return tokenType === 'ERC-1155';
    },
  }
};
</script>