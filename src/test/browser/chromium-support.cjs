const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');

async function captureScreen(browser, screen, width, height, output, state = '') {
    const profile = path.join(path.dirname(screen), 'cdp-profile-' + Date.now());
    const child = spawn(browser, [
        '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--allow-file-access-from-files', '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0',
        '--user-data-dir=' + profile, 'about:blank'
    ], { stdio: 'ignore', windowsHide: true });
    let socket;
    let command;
    try {
        const portFile = path.join(profile, 'DevToolsActivePort');
        const deadline = Date.now() + 15000;
        let port;
        while (!port) {
            assert.ok(Date.now() < deadline, 'Browser debugging port became available');
            try {
                const candidate = fs.readFileSync(portFile, 'utf8').split('\n')[0].trim();
                if (/^\d+$/.test(candidate)) port = candidate;
            } catch (error) {
                // Edge can briefly lock this file while publishing its debugging port on Windows.
                if (!['ENOENT', 'EBUSY'].includes(error.code)) throw error;
            }
            if (port) break;
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        const tabs = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
        socket = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl);
        await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
        let sequence = 0;
        const pending = new Map();
        let loaded;
        socket.onmessage = event => {
            const message = JSON.parse(event.data);
            if (message.method === 'Page.loadEventFired' && loaded) loaded();
            if (!pending.has(message.id)) return;
            const request = pending.get(message.id);
            clearTimeout(request.timeout);
            pending.delete(message.id);
            message.error ? request.reject(new Error(message.error.message)) : request.resolve(message.result);
        };
        command = (method, params = {}) => new Promise((resolve, reject) => {
            const id = ++sequence;
            const timeout = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 10000);
            pending.set(id, { resolve, reject, timeout });
            socket.send(JSON.stringify({ id, method, params }));
        });
        await command('Page.enable');
        await command('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 });
        const load = new Promise((resolve, reject) => {
            const timeout = setTimeout(() => reject(new Error('Preview load timed out')), 15000);
            loaded = () => { clearTimeout(timeout); resolve(); };
        });
        await command('Page.navigate', { url: pathToFileURL(screen).href });
        await load;
        await command('Runtime.evaluate', { expression: 'new Promise(resolve => setTimeout(resolve, 350))', awaitPromise: true });
        if (state) await command('Runtime.evaluate', {
            expression: state === 'combo' ? "document.querySelector('.product-combobox__input').click()" : "document.getElementById('toggleSaleCatalogBtn').click()"
        });
        if (state === 'combo') {
            const popup = await command('Runtime.evaluate', {
                expression: "({open:!document.querySelector('.product-combobox__panel').classList.contains('hidden'),count:document.querySelectorAll('.product-combobox__option').length})", returnByValue: true
            });
            assert.equal(popup.result.value.open, true, 'Desktop picker is open in the visual preview');
            assert.equal(popup.result.value.count, 5, 'Desktop visual preview shows first five products');
        }
        const metrics = await command('Runtime.evaluate', {
            expression: '({width:innerWidth,scroll:document.documentElement.scrollWidth})', returnByValue: true
        });
        assert.equal(metrics.result.value.width, width, 'Exact emulated viewport');
        assert.ok(metrics.result.value.scroll <= width, 'No horizontal page overflow');
        const layout = await command('Page.getLayoutMetrics');
        const image = await command('Page.captureScreenshot', {
            format: 'png', captureBeyondViewport: true,
            clip: { x: 0, y: 0, width, height: Math.ceil(layout.cssContentSize.height), scale: 1 }
        });
        fs.writeFileSync(output, Buffer.from(image.data, 'base64'));
    } finally {
        if (command) await command('Browser.close').catch(() => child.kill());
        else child.kill();
        if (socket) socket.close();
    }
}

module.exports = { captureScreen };
