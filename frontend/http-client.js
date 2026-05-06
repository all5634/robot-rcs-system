// http-client.js — 对接后端 A（机器人管理服务）
// BASE_URL 改成后端 A 的实际地址

const BASE_URL = 'http://172.16.26.190:8080'; // ← 改成后端 A 地址

const POLL_INTERVAL     = 2000;
const LOG_POLL_INTERVAL = 2000;
const CMD_POLL_INTERVAL = 1000;
const CMD_POLL_TIMEOUT  = 30000;
const HEARTBEAT_TIMEOUT = 15000; // 超过 15s 未心跳 → 可能离线

const STATUS_MAP = {
    WORKING: '运行中', IDLE: '空闲',
    OFFLINE: '离线',   ERROR: '故障',
    CHARGING: '充电中', ALARM: '告警',
};

// ── 心跳超时处理 + 字段归一化 ─────────────────────────────────
function normalizeRobot(detail, realtime) {
    const pos = detail?.position ?? null;

    // 心跳检测
    const now            = Date.now();
    const lastHb         = detail?.lastHeartbeat ?? null;
    const hbAgo          = lastHb != null ? now - lastHb : null;
    const heartbeatStale = hbAgo != null && hbAgo > HEARTBEAT_TIMEOUT;
    const heartbeatText  = hbAgo != null
        ? hbAgo < 60000 ? `${Math.floor(hbAgo / 1000)}s 前`
                        : `${Math.floor(hbAgo / 60000)}m 前`
        : '--';

    let status = realtime?.status ?? STATUS_MAP[detail?.status] ?? detail?.status ?? '空闲';
    if (heartbeatStale && status !== '故障') status = '可能离线';

    return {
        id:             realtime?.id ?? detail?.robotCode ?? detail?.robotId,
        robotId:        detail?.robotId ?? null,
        name:           realtime?.name ?? detail?.model ?? '--',
        status,
        online:         (detail?.online === true) && !heartbeatStale,
        battery:        detail?.battery ?? null,
        zone:           pos ? `(${Number(pos.x).toFixed(1)}, ${Number(pos.y).toFixed(1)})` : '--',
        position:       pos,
        speed:          null,
        currentTask:    realtime?.currentTask ?? null,
        lastHeartbeat:  lastHb,
        heartbeatText,
        heartbeatStale,
    };
}

async function request(method, path, body) {
    const options = { method, headers: { 'Content-Type': 'application/json' } };
    if (body !== undefined) options.body = JSON.stringify(body);
    const res  = await fetch(`${BASE_URL}${path}`, options);
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error(json?.message ?? `HTTP ${res.status}`);
    if (!json || json.code !== 0) throw new Error(json?.message ?? '请求失败');
    return json.data;
}

export class HttpClient {
    constructor(onUpdate, onLog) {
        this.onUpdate       = onUpdate;
        this.onLog          = onLog;
        this._lastLogIndex  = 0;
    }

    start() {
        this._pollRobots();
        this._pollLogs();
        setInterval(() => this._pollRobots(), POLL_INTERVAL);
        setInterval(() => this._pollLogs(),   LOG_POLL_INTERVAL);
    }

    async _pollRobots() {
        try {
            const [list, realtimeList] = await Promise.all([
                request('GET', '/api/v1/robots'),
                request('GET', '/api/v1/robots/realtime').catch(() => []),
            ]);
            if (!Array.isArray(list)) return;

            const rtMap = new Map(
                (Array.isArray(realtimeList) ? realtimeList : []).map(r => [r.id, r])
            );
            const details = await Promise.all(
                list.map(item => request('GET', `/api/v1/robots/${item.robotId}`).catch(() => item))
            );
            this.onUpdate(details.map(d => normalizeRobot(d, rtMap.get(d?.robotCode))));
        } catch(e) {
            this.onLog('error', `轮询失败: ${e.message}`);
        }
    }

    async _pollLogs() {
        try {
            const logs = await request('GET', '/api/v1/logs');
            if (!Array.isArray(logs)) return;
            if (this._lastLogIndex === 0) { this._lastLogIndex = logs.length; return; }
            if (logs.length > this._lastLogIndex) {
                logs.slice(this._lastLogIndex).forEach(({ type, msg }) => {
                    if (msg) this.onLog(type ?? 'info', msg);
                });
                this._lastLogIndex = logs.length;
            } else if (logs.length < this._lastLogIndex) {
                this._lastLogIndex = logs.length;
            }
        } catch(_) {}
    }

    async dispatchTask(robotId, x, y) {
        this.onLog('info', `[下发] ${robotId} → DISPATCH (${x}, ${y})`);
        try {
            const data = await request('POST', '/api/v1/robots/control', {
                type: 'DISPATCH', robotId, position: { x, y },
            });
            this.onLog('info', `指令已接受，commandId: ${data.commandId}`);
            this._trackCommand(robotId, data.commandId);
        } catch(e) { this.onLog('error', `下发失败: ${e.message}`); }
    }

    async stopRobot(robotId) {
        this.onLog('error', `🚨 [急停] 发送中: ${robotId}`);
        try {
            const data = await request('POST', '/api/v1/robots/control', { type: 'STOP', robotId });
            this.onLog('error', `🚨 [急停] 已接受，commandId: ${data.commandId}`);
            this._trackCommand(robotId, data.commandId);
        } catch(e) { this.onLog('error', `急停失败: ${e.message}`); }
    }

    async resumeRobot(robotId) {
        this.onLog('info', `[恢复] 发送中: ${robotId}`);
        try {
            const data = await request('POST', '/api/v1/robots/control', { type: 'RESUME', robotId });
            this.onLog('info', `[恢复] 已接受，commandId: ${data.commandId}`);
            this._trackCommand(robotId, data.commandId);
        } catch(e) { this.onLog('error', `恢复失败: ${e.message}`); }
    }

    _trackCommand(robotId, commandId) {
        const start = Date.now();
        const short = String(commandId).slice(0, 8);
        const timer = setInterval(async () => {
            if (Date.now() - start > CMD_POLL_TIMEOUT) {
                clearInterval(timer);
                this.onLog('warn', `指令 ${short}... 追踪超时（30s）`);
                return;
            }
            try {
                const data = await request('GET', `/api/v1/robots/${robotId}/commands/${commandId}`);
                if (data?.status === 'SUCCESS') {
                    clearInterval(timer);
                    this.onLog('info', `✅ 指令 ${short}... 执行成功`);
                } else if (data?.status === 'FAILED') {
                    clearInterval(timer);
                    this.onLog('error', `❌ 指令 ${short}... 执行失败`);
                }
            } catch(e) {
                clearInterval(timer);
                this.onLog('warn', `指令状态查询失败: ${e.message}`);
            }
        }, CMD_POLL_INTERVAL);
    }

    async registerRobot(payload) {
        return request('POST', '/api/v1/robots', payload);
    }

    async deleteRobot(robotId) {
        return request('DELETE', `/api/v1/robots/${robotId}`);
    }

    // mock 占位，后端 B 接口文档出来后移到 task-client.js
    async createTask(robotId, commandType, priority, params) {
        return new Promise(resolve =>
            setTimeout(() => resolve({ taskId: 'mock-' + Date.now() }), 300)
        );
    }
}
