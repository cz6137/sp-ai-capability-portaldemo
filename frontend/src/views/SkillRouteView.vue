<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import CapabilityView from './CapabilityView.vue'

const route = useRoute()
const legacyAliases: Record<string, string> = {}
const slug = computed(() => legacyAliases[String(route.params.id)] || String(route.params.id))
</script>

<template><CapabilityView :slug="slug" /></template>
