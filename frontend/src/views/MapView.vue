<template>
  <div class="map-container">
    <div id="map"></div>

    <div class="panel">
      <h3>🤖 机器人列表</h3>

      <div
        v-for="robot in robots"
        :key="robot.id"
        class="robot-item"
        :class="{ active: selectedIds.includes(robot.id) }"
        @click="toggleRobot(robot)"
      >
        <span class="dot" :style="{ background: robot.color }"></span>
        <div>
          <div>{{ robot.name }}</div>
          <div class="coord">
            📍 {{ format(robot.x) }}, {{ format(robot.y) }}
          </div>
          <div class="coord" v-if="robot.planPath.length">
            🎯 {{ format(robot.planPath[1][1]) }},
            {{ format(robot.planPath[1][0]) }}
          </div>
        </div>
      </div>

      <div class="tip">
        ①选择机器人 → ②点击地图 → ③拖动🏁修改
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue"
import L from "leaflet"
import "leaflet/dist/leaflet.css"

// ===== 机器人数据 =====
const robots = ref([
  { id: "R1", name: "机器人1", x: 0, y: 0, color:"#2196F3", planPath: [] },
  { id: "R2", name: "机器人2", x: 2, y: 2, color:"#4CAF50", planPath: [] },
  { id: "R3", name: "机器人3", x: 4, y: 4, color:"#FF9800", planPath: [] }
])

// 初始默认选择机器人1
const selectedIds = ref(["R1"])

let map
let markers = {}
let polylines = {}
let endMarkers = {}

// ===== 工具 =====
function format(v){
  return v != null ? v.toFixed(2) : "-"
}

function robotIcon(color){
  return L.divIcon({
    html:`<div style="
      width:18px;height:18px;
      border-radius:50%;
      background:${color};
      border:3px solid white;
    "></div>`,
    iconSize:[18,18],
    iconAnchor:[9,9]
  })
}

function endIcon(){
  return L.divIcon({
    html:`<div style="font-size:22px;">🏁</div>`,
    iconSize:[22,22],
    iconAnchor:[6,22]
  })
}

// ===== 选择机器人 =====
function toggleRobot(robot){
  const i = selectedIds.value.indexOf(robot.id)
  if(i===-1) selectedIds.value.push(robot.id)
  else selectedIds.value.splice(i,1)
}

// ===== 初始化机器人 =====
function addRobots(){
  console.log("🤖 开始添加机器人标记...")
  robots.value.forEach(r=>{
    const pos = [r.y, r.x]
    console.log(`添加 ${r.id}: [${pos[0]}, ${pos[1]}]`)

    markers[r.id] = L.marker(pos,{
      icon: robotIcon(r.color)
    }).addTo(map)
  })
  console.log("✅ 所有机器人标记已添加")
  console.log("markers 对象:", Object.keys(markers))
}

// ===== 点击地图 =====
function setupClick(){
  console.log("🔍 setupClick 执行，地图对象:", map)
  console.log("🔍 选中的机器人ID:", selectedIds.value)
  console.log("🔍 markers 对象:", markers)
  
  // 只使用 Leaflet 的点击事件，不用 DOM 事件
  map.off("click") // 先清除任何已存在的监听
  
  map.on("click", e => {
    console.log("✅ Leaflet 地图被点击了！")
    console.log("事件对象:", e)
    console.log("点击坐标:", e.latlng)
    console.log("当前选中机器人数:", selectedIds.value.length)
    
    if(!selectedIds.value.length) {
      console.warn("⚠️ 未选择机器人，操作取消")
      alert("请先选择一个机器人！")
      return
    }

    const latlng = e.latlng
    const target = [latlng.lat, latlng.lng]
    console.log("目标坐标:", target)

    selectedIds.value.forEach(id => {
      console.log(`\n处理机器人 ${id}...`)
      
      const robot = robots.value.find(r => r.id === id)
      if(!robot) {
        console.warn(`机器人 ${id} 不存在`)
        return
      }

      if(!markers[id]) {
        console.error(`marker ${id} 不存在!`)
        console.log("现有 markers:", Object.keys(markers))
        return
      }

      console.log(`✅ 为 ${id} 设置目标`)

      const start = markers[id].getLatLng()
      const startPos = [start.lat, start.lng]
      console.log(`起点: [${startPos[0]}, ${startPos[1]}]`)
      console.log(`目标: [${target[0]}, ${target[1]}]`)

      robot.planPath = [startPos, target]

      // 清除旧
      if(polylines[id]) {
        console.log(`清除旧路径 ${id}`)
        map.removeLayer(polylines[id])
      }
      if(endMarkers[id]) {
        console.log(`清除旧终点 ${id}`)
        map.removeLayer(endMarkers[id])
      }

      // 新路径
      polylines[id] = L.polyline(robot.planPath, {
        color: robot.color,
        weight: 4,
        dashArray: "6,6"
      }).addTo(map)
      console.log(`✅ 添加了路径 ${id}`)

      // 终点
      const m = L.marker(target, {
        icon: endIcon(),
        draggable: true
      }).addTo(map)

      endMarkers[id] = m
      console.log(`✅ 添加了终点标记 ${id}`)

      m.on("dragstart", () => {
        console.log(`拖动开始 ${id}`)
        map.dragging.disable()
      })

      m.on("drag", ev => {
        const p = ev.target.getLatLng()
        robot.planPath[1] = [p.lat, p.lng]
        polylines[id].setLatLngs(robot.planPath)
      })

      m.on("dragend", () => {
        console.log(`拖动结束 ${id}`)
        map.dragging.enable()
      })
    })
  })
  
  console.log("✅ 点击监听器已注册完成")
}

// 处理 DOM 级别的点击事件
function handleMapClickEvent(latlng) {
  console.log("🔄 DOM 级别的点击处理, 坐标:", latlng)
  
  if(!selectedIds.value.length) {
    console.warn("⚠️ 未选择机器人，操作取消")
    return
  }

  const target = [latlng.lat, latlng.lng]
  selectedIds.value.forEach(id => {
    const robot = robots.value.find(r => r.id === id)
    if(!robot || !markers[id]) return

    const start = markers[id].getLatLng()
    const startPos = [start.lat, start.lng]
    robot.planPath = [startPos, target]

    if(polylines[id]) map.removeLayer(polylines[id])
    if(endMarkers[id]) map.removeLayer(endMarkers[id])

    polylines[id] = L.polyline(robot.planPath, {
      color: robot.color,
      weight: 4,
      dashArray: "6,6"
    }).addTo(map)

    const m = L.marker(target, {
      icon: endIcon(),
      draggable: true
    }).addTo(map)

    endMarkers[id] = m
    m.on("dragstart", () => map.dragging.disable())
    m.on("drag", ev => {
      const p = ev.target.getLatLng()
      robot.planPath[1] = [p.lat, p.lng]
      polylines[id].setLatLngs(robot.planPath)
    })
    m.on("dragend", () => map.dragging.enable())
  })
}

// ===== 初始化地图（你自己的地图）=====
onMounted(async()=>{

  try{
    console.log("📍 开始初始化地图...")
    
    // 读取 YAML
    const txt = await fetch('/maps/final.yaml').then(r=>r.text())
    console.log("📄 YAML内容:", txt)

    let resolution = 1
    let origin = [0,0]

    txt.split("\n").forEach(l=>{
      if(l.includes("resolution")) resolution = parseFloat(l.split(":")[1])
      if(l.includes("origin")){
        origin = l.match(/\[(.*)\]/)[1].split(",").map(Number)
      }
    })

    console.log("🔧 分辨率:", resolution, "起点:", origin)

    map = L.map("map",{ 
      crs:L.CRS.Simple, 
      minZoom:-5,
      dragging: true,
      touchZoom: true,
      doubleClickZoom: true,
      scrollWheelZoom: true,
      tap: true
    })

    console.log("🗺️ 地图对象创建成功")
    
    // 直接在地图容器上添加点击事件监听器作为最后的备选
    const mapDiv = document.getElementById("map")
    mapDiv.addEventListener("click", (e) => {
      console.warn("⚠️ 直接 DOM 点击事件被触发!")
      if(!selectedIds.value.length) return
      
      // 转换 DOM 坐标到 Leaflet 坐标
      const rect = mapDiv.getBoundingClientRect()
      const x = e.clientX - rect.left
      const y = e.clientY - rect.top
      
      const point = L.point(x, y)
      const latlng = map.containerPointToLatLng(point)
      
      console.log("计算得到的坐标:", latlng)
      handleMapClickEvent(latlng)
    }, true) // 使用 capture 模式以确保事件能被捕获

    const img = new Image()
    img.src = "/maps/final.png"

    img.onload = ()=>{
      console.log("🖼️ 地图图片加载成功")
      const w = img.width
      const h = img.height

      const bounds = [
        [origin[1], origin[0]],
        [origin[1]+h*resolution, origin[0]+w*resolution]
      ]

      console.log("📐 图片尺寸:", w, "x", h, "边界:", bounds)

      const imageLayer = L.imageOverlay("/maps/final.png",bounds).addTo(map)
      
      // 关键修复1：将图层放在后面，不要拦截点击事件
      imageLayer.bringToBack()
      
      // 关键修复2：禁用 ImageOverlay 元素的指针事件
      const imgElement = imageLayer.getElement()
      if(imgElement) {
        imgElement.style.pointerEvents = "none"
        console.log("✅ ImageOverlay 的指针事件已禁用")
      }
      
      map.fitBounds(bounds)

      addRobots()
      
      // 延迟一下再绑定事件，确保 Leaflet 完全初始化
      setTimeout(() => {
        setupClick()
        console.log("✅ 延迟后地图初始化完成")
      }, 100)
    }

    img.onerror = () => {
      console.error("❌ 图片加载失败，使用 fallback")
      // fallback
      map = L.map("map").setView([31.23,121.47],17)
      L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png").addTo(map)

      addRobots()
      setupClick()
    }

  }catch(error){
    console.error("❌ 初始化地图失败:", error)
    // fallback
    map = L.map("map").setView([31.23,121.47],17)
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png").addTo(map)

    addRobots()
    setupClick()
  }

})
</script>

<style scoped>
.map-container{
  width:100vw;
  height:100vh;
  position: relative;
}

#map{
  width:100%;
  height:100%;
  pointer-events: auto !important;
}

.panel{
  position:absolute;
  right:20px;
  top:20px;
  background:white;
  padding:12px;
  border-radius:10px;
  z-index:1000;
  pointer-events:auto;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.robot-item{
  display:flex;
  gap:8px;
  padding:6px;
  cursor:pointer;
  border-radius: 4px;
  transition: background 0.2s;
}

.robot-item:hover{
  background:#f5f5f5;
}

.robot-item.active{
  background:#e3f2fd;
  border-left: 3px solid #2196F3;
  padding-left: 3px;
}

.dot{
  width:10px;
  height:10px;
  border-radius:50%;
  flex-shrink: 0;
  margin-top: 4px;
}

.coord{
  font-size:11px;
  color:#666;
}

.tip{
  margin-top:10px;
  font-size:12px;
  color:#888;
  padding: 6px;
  background: #fafafa;
  border-radius: 3px;
}
</style>