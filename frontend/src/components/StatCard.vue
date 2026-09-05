<template>
  <div class="page-card stat-card">
    <div class="stat-top"><span class="label">{{ label }}</span><span class="icon" :style="{ color }" aria-hidden="true">{{ icon }}</span></div>
    <div class="stat-bottom"><div class="value">{{ value }}</div><span class="stat-arrow" aria-hidden="true">↗</span></div>
  </div>
</template>
<script setup lang="ts">
defineProps<{ icon: string; label: string; value: string | number; color?: string }>()
</script>
<style scoped>
.stat-card { padding:18px 20px; height:100%; }
.stat-top,.stat-bottom { display:flex; align-items:center; justify-content:space-between; }
.icon { width:28px; height:28px; border-radius:10px; display:grid; place-items:center; font-size:21px; background:var(--lingxi-hover); }
.value { font-size:31px; font-weight:600; letter-spacing:-1px; line-height:1.2; margin-top:12px; font-variant-numeric:tabular-nums; }
.label { color:var(--lingxi-muted); font-size:11px; }.stat-arrow { color:var(--lingxi-muted); font-size:15px; margin-top:14px; }
@media(max-width:760px) { .stat-card { padding:14px; }.value { font-size:27px; }.label { font-size:10px; } }
</style>
