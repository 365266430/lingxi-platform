<template>
  <div class="tool-card">
    <div class="flex-between">
      <span class="tool-name">🔧 {{ name }}</span>
      <el-tag size="small" :type="result ? 'success' : 'warning'">{{ result ? '已执行' : '调用中…' }}</el-tag>
    </div>
    <pre v-if="argumentsText">{{ argumentsText }}</pre>
    <div v-if="result" class="tool-result msg-md" v-html="renderMarkdown(result)"></div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps<{ name: string; arguments?: string; result?: string }>()

const argumentsText = computed(() => {
  if (!props.arguments) return ''
  try {
    return JSON.stringify(JSON.parse(props.arguments), null, 2)
  } catch (e) {
    return props.arguments
  }
})
</script>

<style scoped>
.tool-result { margin-top: 6px; border-top: 1px dashed #dfe4f1; padding-top: 6px; max-height: 300px; overflow: auto; }
</style>
