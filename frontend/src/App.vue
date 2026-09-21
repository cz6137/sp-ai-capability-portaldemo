<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import AppFooter from './components/AppFooter.vue'
const route = useRoute()
const standalone = computed(() => route.name === 'login')
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="!standalone" />
    <main class="app-main"><RouterView /></main>
    <AppFooter v-if="!standalone" />
  </div>
</template>
