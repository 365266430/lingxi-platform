<template>
  <div class="nf">
    <h1>404</h1>
    <p>页面走丢了…</p>
    <el-button type="primary" @click="$router.push('/dashboard')">回到首页</el-button>
  </div>
</template>

<style scoped>
.nf { height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; }
h1 { font-size: 72px; margin: 0; color: #4f6ef2; }
</style>
