// WS 分帧乱码验证驱动（D12 对应 Java 版修复验证）
//
// 与 ws_big_payload.js 的区别：官方 sekiroWeb.js 经浏览器/undici 发送时不会分帧，
// 无法触发服务端逐分片解码缺陷。本驱动用原生 TCP 手工构造 WebSocket 帧，
// 把 3MB 中文 provider 响应按指定字节偏移 K 切成 Text(FIN=0) + Continuation(FIN=1)
// 两帧发送，且 K 落在多字节汉字内部，直接检验服务端跨分片 UTF-8 聚合是否正确。
//
// 运行：node ws_fragment_test.js [port]   （默认 5620）
//
// 判定：每个切分点 2 轮，HTTP 响应 data 无 U+FFFD 且 SHA256 与原始载荷一致 => PASS

const net = require('net');
const crypto = require('crypto');
const http = require('http');

const PORT = process.argv[2] || '5620';
const GROUP = 'ws-frag';
const CLIENT_ID = 'frag-1';
const CHARS = 1048576; // 1M 汉字 ≈ 3MB UTF-8
const SPLITS = [21, 22, 23, 24, 36, 64, 100, 1500]; // 字节切分点（落在汉字内部）
const RUNS_PER_SPLIT = 2;

// ---- 3MB 中文载荷 ----
const unit = '中文字符串测试';
let big = '';
while (big.length < CHARS) big += unit;
big = big.slice(0, CHARS);
const bigBuf = Buffer.from(big, 'utf8');
const expectHash = crypto.createHash('sha256').update(bigBuf).digest('hex');
console.log(`payload: ${big.length} chars, ${bigBuf.length} utf8 bytes`);
console.log(`split points: ${SPLITS.join(', ')} bytes, ${RUNS_PER_SPLIT} runs each`);

// ---- 原生 TCP WebSocket provider（客户端帧必须掩码）----
let currentSplit = SPLITS[0];

function buildMaskedFrame(opcode, fin, payload) {
    const mask = crypto.randomBytes(4);
    const len = payload.length;
    const header = Buffer.alloc(2 + (len < 126 ? 0 : len < 65536 ? 2 : 8) + 4);
    header[0] = (fin ? 0x80 : 0x00) | opcode;
    let off = 1;
    if (len < 126) {
        header[1] = 0x80 | len; off = 2;
    } else if (len < 65536) {
        header[1] = 0x80 | 126; header.writeUInt16BE(len, 2); off = 4;
    } else {
        header[1] = 0x80 | 127; header.writeBigUInt64BE(BigInt(len), 2); off = 10;
    }
    mask.copy(header, off);
    const masked = Buffer.from(payload);
    for (let i = 0; i < masked.length; i++) masked[i] ^= mask[i & 3];
    return Buffer.concat([header, masked]);
}

function wsConnect(port, path, onText) {
    return new Promise((resolve, reject) => {
        const key = crypto.randomBytes(16).toString('base64');
        const sock = net.connect(port, '127.0.0.1', () => {
            sock.write(
                `GET ${path} HTTP/1.1\r\n` +
                `Host: 127.0.0.1:${port}\r\n` +
                `Upgrade: websocket\r\nConnection: Upgrade\r\n` +
                `Sec-WebSocket-Key: ${key}\r\n` +
                `Sec-WebSocket-Version: 13\r\n\r\n`);
        });
        sock.setTimeout(0);
        let buf = Buffer.alloc(0);
        let msg = Buffer.alloc(0);   // 跨分片聚合的消息负载
        let fragOpcode = -1;
        let upgraded = false;

        sock.on('data', (chunk) => {
            buf = Buffer.concat([buf, chunk]);
            if (!upgraded) {
                const idx = buf.indexOf('\r\n\r\n');
                if (idx < 0) return;
                const resp = buf.slice(0, idx).toString('latin1');
                if (!/HTTP\/1\.1 101/.test(resp)) {
                    reject(new Error('upgrade failed: ' + resp.split('\r\n')[0]));
                    return;
                }
                upgraded = true;
                buf = buf.slice(idx + 4);
                resolve({ sock });
            }
            // 解析服务端帧（服务端帧不掩码）
            while (true) {
                if (buf.length < 2) return;
                const fin = (buf[0] & 0x80) !== 0;
                const opcode = buf[0] & 0x0f;
                const masked = (buf[1] & 0x80) !== 0;
                let len = buf[1] & 0x7f;
                let off = 2;
                if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
                else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
                if (masked) off += 4;
                if (buf.length < off + len) return;
                const payload = buf.slice(off, off + len);
                buf = buf.slice(off + len);

                if (opcode === 0x9) { // ping -> pong
                    sock.write(buildMaskedFrame(0xA, true, payload));
                    continue;
                }
                if (opcode === 0x8) { sock.end(); continue; }
                if (opcode === 0x1 || opcode === 0x0) {
                    if (opcode === 0x1) { msg = Buffer.alloc(0); fragOpcode = 0x1; }
                    msg = Buffer.concat([msg, payload]);
                    if (fin && fragOpcode === 0x1) {
                        msg = Buffer.alloc(0); fragOpcode = -1;
                        onText(payload.toString('utf8'));
                    }
                }
            }
        });
        sock.on('error', reject);
    });
}

// 发送分片响应：Text(FIN=0, K 字节) + Continuation(FIN=1, 余下)
function sendFragmented(sock, text) {
    const bytes = Buffer.from(text, 'utf8');
    const k = Math.min(currentSplit, bytes.length - 1);
    sock.write(buildMaskedFrame(0x1, false, bytes.slice(0, k)));
    sock.write(buildMaskedFrame(0x0, true, bytes.slice(k)));
}

// ---- HTTP invoke ----
function invoke(action, timeoutMs) {
    return new Promise((resolvePromise, rejectPromise) => {
        const t0 = Date.now();
        const req = http.get(
            `http://127.0.0.1:${PORT}/business/invoke?group=${GROUP}&action=${action}`,
            { timeout: timeoutMs },
            (res) => {
                const chunks = [];
                res.on('data', (c) => chunks.push(c));
                res.on('end', () => resolvePromise({
                    status: res.statusCode, body: Buffer.concat(chunks), ms: Date.now() - t0,
                }));
            });
        req.on('error', rejectPromise);
        req.on('timeout', () => req.destroy(new Error('client timeout')));
    });
}

function checkResponse(r, label) {
    const text = r.body.toString('utf8');
    const bad = (text.match(/\uFFFD/g) || []).length;
    let parsed;
    try { parsed = JSON.parse(text); } catch (e) {
        console.log(`[${label}] FAIL: http=${r.status} ${r.ms}ms body=${r.body.length}B JSON error: ${e.message}`);
        return false;
    }
    const data = parsed.data == null ? '' : String(parsed.data);
    const hash = crypto.createHash('sha256').update(Buffer.from(data, 'utf8')).digest('hex');
    const ok = bad === 0 && hash === expectHash && parsed.status === 0;
    console.log(`[${label}] http=${r.status} ${r.ms}ms body=${r.body.length}B U+FFFD=${bad} ` +
        `sha256=${hash.slice(0, 16)}... => ${ok ? 'PASS' : 'FAIL'}`);
    return ok;
}

(async () => {
    let ready = false;
    await wsConnect(PORT, `/business/register?group=${GROUP}&clientId=${CLIENT_ID}`, (text) => {
        try {
            const req = JSON.parse(text);
            const resp = { status: 0, data: big, __sekiro_seq__: req['__sekiro_seq__'] };
            sendFragmented(sock, JSON.stringify(resp));
        } catch (e) { console.log('dispatch error:', e.message); }
    }).then((s) => { global.sock = s.sock; ready = true; });

    for (let i = 0; i < 20 && !ready; i++) await new Promise((r) => setTimeout(r, 200));

    // 等 provider 注册生效
    for (let i = 0; i < 20; i++) {
        try {
            const r = await invoke('fragBig', 30000);
            if (r.status === 200) break;
        } catch (e) { /* retry */ }
        await new Promise((r) => setTimeout(r, 500));
    }

    let pass = 0, total = 0;
    for (const k of SPLITS) {
        currentSplit = k;
        for (let n = 1; n <= RUNS_PER_SPLIT; n++) {
            total++;
            try {
                const r = await invoke('fragBig', 60000);
                if (checkResponse(r, `split=${k} run${n}`)) pass++;
            } catch (e) {
                console.log(`[split=${k} run${n}] FAIL: ${e.message}`);
            }
            await new Promise((r) => setTimeout(r, 200));
        }
    }
    console.log(`\nRESULT: ${pass}/${total} PASS`);
    global.sock.end();
    process.exit(pass === total ? 0 : 1);
})().catch((e) => { console.error('fatal:', e.message); process.exit(2); });
