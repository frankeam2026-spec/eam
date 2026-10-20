<script setup lang="ts">
import { ElConfigProvider } from 'element-plus';
import { elementLocale } from './i18n';
</script>
<template>
  <ElConfigProvider :locale="elementLocale" :z-index="2000"><RouterView /></ElConfigProvider>
</template>
