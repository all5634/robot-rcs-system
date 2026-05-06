// mock-server.js

export const initialRobots = [
    { id: 'R-001', name: '巡逻机器人', status: '待机', battery: 85, zone: null, currentTask: null },
    { id: 'R-002', name: '搬运机器人', status: '待机', battery: 45, zone: null, currentTask: null },
    { id: 'R-003', name: '清洁机器人', status: '待机', battery: 92, zone: null, currentTask: null },
    { id: 'R-004', name: '配送机器人', status: '待机', battery: 67, zone: null, currentTask: null },
];

const ZONES = ['A区-装载点', 'B区-走廊', 'C区-分拣中心', 'D区-成品库01', 'E区-充电站', 'F区-备料仓'];

/**
 * 模拟后端推送类
 *
 * 对接真实后端时：
 *   把 start() 里的 setInterval 替换为 WebSocket / SSE 监听即可，
 *   dispatchTask / stopRobot / resumeRobot 改为发送 HTTP / WS 指令。
 */
export class MockServer {
    constructor(onUpdate, onLog) {
        this.robots   = initialRobots.map(r => ({ ...r }));
        this.onUpdate = onUpdate;
        this.onLog    = onLog;
        // 任务倒计时：robotId -> 剩余 tick（每 500ms -1）
        this.taskCountdown = {};
    }

    // ── 1. 紧急停止 ────────────────────────────────────
    stopRobot(robotId) {
        const robot = this.robots.find(r => r.id === robotId);
        if (robot) {
            delete this.taskCountdown[robotId];
            robot.status      = '故障';
            robot.currentTask = null;
            this.onLog('error', `🚨 [急停] 机器人 ${robotId} 已紧急锁定！`);
            this.onUpdate(this.robots);
        }
    }

    // ── 2. 解除锁定 ────────────────────────────────────
    resumeRobot(robotId) {
        const robot = this.robots.find(r => r.id === robotId);
        if (robot && robot.status === '故障') {
            robot.status = '待机';
            this.onLog('info', `✅ [恢复] 机器人 ${robotId} 锁定已解除，可以下发任务。`);
            this.onUpdate(this.robots);
        } else if (robot) {
            this.onLog('warn', `${robotId} 当前非锁定状态，无需解除。`);
        }
    }

    // ── 3. 下发任务（接收地区名称字符串） ─────────────────
    dispatchTask(robotId, zone, taskId = 'Manual-Task') {
        const robot = this.robots.find(r => r.id === robotId);
        if (!robot) return;

        if (robot.status === '故障') {
            this.onLog('warn', `拒绝指令：${robotId} 处于故障锁定状态！`);
            return;
        }

        robot.zone        = zone;
        robot.currentTask = `${taskId} → ${zone}`;
        robot.status      = '运行中';

        // 模拟行驶时间（8~15 秒后到达）
        const ticks = Math.floor(16 + Math.random() * 14); // 500ms * ticks
        this.taskCountdown[robotId] = ticks;

        this.onLog('info', `[系统] ${robotId} 开始执行任务: ${taskId} → ${zone}`);
        this.onUpdate(this.robots);
    }

    // ── 4. 自动调度引擎 ────────────────────────────────
    autoDispatcher() {
        this.robots.forEach(robot => {
            if (robot.status === '待机' && robot.battery > 20) {
                const zone   = ZONES[Math.floor(Math.random() * ZONES.length)];
                const taskId = `Auto-T${Math.floor(Math.random() * 900 + 100)}`;
                this.onLog('info', `🤖 [自动调度] 发现空闲设备 ${robot.id}，下发系统任务 ${taskId}`);
                this.dispatchTask(robot.id, zone, taskId);
            }
        });
    }

    // ── 5. 物理步进（电量消耗 + 任务完成判断） ────────────
    simulateStep() {
        let changed = false;

        this.robots.forEach(r => {
            if (r.status === '运行中') {
                // 电量缓慢消耗
                r.battery = parseFloat(Math.max(0, r.battery - 0.05).toFixed(2));

                // 倒计时到 0 → 任务完成
                if (this.taskCountdown[r.id] !== undefined) {
                    this.taskCountdown[r.id]--;
                    if (this.taskCountdown[r.id] <= 0) {
                        delete this.taskCountdown[r.id];
                        this.onLog('info', `✅ ${r.id} 已到达 ${r.zone}，任务完成`);
                        r.status      = '待机';
                        r.currentTask = null;
                    }
                }
                changed = true;
            }
        });

        if (changed) this.onUpdate(this.robots);
    }

    // ── 6. 注册机器人（mock）─────────────────────────────────
    async registerRobot(robotCode, model, capabilities = 'move') {
        if (this.robots.find(r => r.id === robotCode)) {
            throw new Error(`${robotCode} 已存在`);
        }
        this.robots.push({
            id: robotCode, robotId: robotCode, name: model,
            status: '待机', battery: 100, zone: null, currentTask: null,
        });
        this.onLog('info', `✅ [注册] ${robotCode} (${model}) 注册成功`);
        this.onUpdate(this.robots);
    }

    // ── 6. 创建任务（mock，对接任务调度接口后替换）─────────────
    createTask(robotId, commandType, priority, params) {
        const taskId = `TASK-${Math.floor(Math.random() * 9000 + 1000)}`;
        this.onLog('info', `[任务] ${taskId} 已创建 → ${commandType} P${priority} 分配给 ${robotId}`);
        // mock：直接作为 dispatchTask 执行
        if (commandType === 'MOVE_TO' && params.x != null && params.y != null) {
            const zone = `(${params.x}, ${params.y})`;
            this.dispatchTask(robotId, zone, taskId);
        }
        return Promise.resolve({ taskId });
    }

    // ── 7. 删除机器人（mock）─────────────────────────────────
    async deleteRobot(robotId) {
        const idx = this.robots.findIndex(r => (r.robotId ?? r.id) === robotId);
        if (idx === -1) throw new Error('机器人不存在');
        const [removed] = this.robots.splice(idx, 1);
        delete this.taskCountdown[removed.id];
        this.onLog('warn', `🗑 [删除] ${removed.id} 已移除`);
        this.onUpdate(this.robots);
    }

    // ── 8. 启动 ───────────────────────────────────────────────
    start() {
        // 物理步进，每 500ms 一次
        setInterval(() => this.simulateStep(), 500);
        // 自动调度，每 8 秒尝试分配空闲机器人
        setInterval(() => this.autoDispatcher(), 8000);
    }
}
