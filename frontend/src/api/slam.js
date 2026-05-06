// src/api/slam.js

import axios from "axios"

// ✅ 你的后端地址
const BASE_URL = "http://172.16.25.178:8080/api/v1/scheduler/slam"

// 创建 axios 实例
const request = axios.create({
  baseURL: BASE_URL,
  timeout: 8000
})

// 👉 请求拦截（可扩展 token）
request.interceptors.request.use(config => {
  return config
})

// 👉 响应拦截（统一处理）
request.interceptors.response.use(
  res => res.data,
  err => {
    console.error("❌ 接口错误:", err)
    return Promise.reject(err)
  }
)


// =============================
// 📌 地图相关
// =============================

// 获取地图（OccupancyGrid）
export function getMap() {
  return request.get("/map")
}

// 更新地图
export function updateMap(data) {
  return request.post("/map", data)
}

// 重置地图
export function resetMap() {
  return request.post("/map/reset")
}

// 获取地图状态
export function getMapStatus() {
  return request.get("/map/status")
}


// =============================
// 📌 障碍物
// =============================

// 获取障碍物
export function getObstacles() {
  return request.get("/obstacles")
}

// 添加障碍物
export function addObstacle(data) {
  return request.post("/obstacles", data)
}

// 修改障碍物
export function updateObstacle(id, data) {
  return request.put(`/obstacles/${id}`, data)
}

// 删除障碍物
export function deleteObstacle(id) {
  return request.delete(`/obstacles/${id}`)
}


// =============================
// 📌 路径规划（A*）
// =============================

export function planPath(startX, startY, goalX, goalY) {
  return request.post("/path/plan", {
    startX,
    startY,
    goalX,
    goalY
  })
}