<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
  <div class="d-flex pt-4 align-center">
    <div class="d-flex flex-column pe-4 pb-5">
      <v-img
        :src="tokenImageUrl"
        height="40"
        width="40" />
      <v-avatar
        size="24"
        class=" border-color grey lighten-2 mt-n12 ms-auto me-n2">
        <v-img :src="networkImageUrl" />
      </v-avatar>
    </div>
    <div
      v-if="!isERC1155Token"
      class="d-flex flex-column">
      <span class="font-weight-bold d-flex justify-start"> {{ tokenSymbol }} </span>
      <span class="d-flex justify-start text-sub-title caption text-truncate"> {{ tokenName }} </span>
    </div>
    <div v-else>
      <span class="font-weight-bold d-flex justify-start"> {{ $t('gamification.event.form.nft') }} </span>
    </div>
    <div class="ml-auto grey-background rounded">
      <span class="caption primary--text font-weight-bold px-3"> {{ tokenType }} </span>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    token: {
      type: Object,
      default: null
    },
    network: {
      type: Object,
      default: null,
    },
  },
  computed: {
    tokenName() {
      return this.token?.name;
    },
    tokenSymbol() {
      return this.token?.symbol;
    },
    tokenType() {
      return this.token?.type;
    },
    isERC20Token() {
      return this.tokenType === 'ERC-20';
    },
    isERC1155Token() {
      return this.tokenType === 'ERC-1155';
    },
    tokenImageUrl() {
      if (this.isERC20Token) {
        return '/gamification-evm/images/Token.webp';
      } else {
        return '/gamification-evm/images/NFT.webp';
      }
    },
    networkName() {
      return this.network?.name;
    },
    networkImageUrl() {
      if (this.networkName === 'Polygon') {
        return '/gamification-evm/images/polygonLogo.svg';
      } else {
        return '/gamification-evm/images/ethereumLogo.svg';
      }
    }
  }
};
</script>