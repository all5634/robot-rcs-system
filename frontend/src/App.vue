<template>
  <div id="app">
    <RouterView />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
}
</style>
```

---

**2. 点开 `src/views/` 文件夹**

如果里面有文件（比如HomeView.vue），**全部删掉**。

---

**3. 在 `src/views/` 里新建一个文件**，命名为：
```
MapView.vue