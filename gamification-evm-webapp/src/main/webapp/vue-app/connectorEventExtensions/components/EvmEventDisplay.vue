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
    explorerLink() {
      return `https://mumbai.polygonscan.com/address/${this.contractAddress}`;
    }
  },
};
</script>
