<template>
  <div class="page-card stat-card">
    <div class="icon" :style="{ background: color }">{{ icon }}</div>
    <div>
      <div class="value">{{ value }}</div>
      <div class="label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ icon: string; label: string; value: string | number; color?: string }>()
</script>

<style scoped>
.stat-card { display: flex; align-items: center; gap: 14px; }
.icon {
  width: 46px; height: 46px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; color: #fff;
}
.value { font-size: 24px; font-weight: 700; line-height: 1.2; }
.label { color: #94a3b8; font-size: 13px; }
</style>
