(function () {
    const DEFAULT_FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128'];
    const FALLBACK_SCRIPT_SRC = 'https://unpkg.com/html5-qrcode@2.3.8/html5-qrcode.min.js';
    let fallbackScriptPromise = null;

    function ensureFallbackScript() {
        if (window.Html5Qrcode && window.Html5QrcodeSupportedFormats) {
            return Promise.resolve();
        }
        if (fallbackScriptPromise) {
            return fallbackScriptPromise;
        }
        fallbackScriptPromise = new Promise(function (resolve, reject) {
            const existing = document.querySelector('script[data-maidav-html5-qrcode="true"]');
            if (existing) {
                existing.addEventListener('load', function () { resolve(); }, {once: true});
                existing.addEventListener('error', function () { reject(new Error('No se pudo cargar html5-qrcode.')); }, {once: true});
                return;
            }
            const script = document.createElement('script');
            script.src = FALLBACK_SCRIPT_SRC;
            script.async = true;
            script.dataset.maidavHtml5Qrcode = 'true';
            script.addEventListener('load', function () { resolve(); }, {once: true});
            script.addEventListener('error', function () { reject(new Error('No se pudo cargar html5-qrcode.')); }, {once: true});
            document.head.appendChild(script);
        });
        return fallbackScriptPromise;
    }

    function formatListForFallback() {
        if (!window.Html5QrcodeSupportedFormats) {
            return undefined;
        }
        const supported = window.Html5QrcodeSupportedFormats;
        return DEFAULT_FORMATS
            .map(function (format) {
                const key = format.toUpperCase();
                return supported[key];
            })
            .filter(function (value) {
                return value !== undefined && value !== null;
            });
    }

    async function requestCameraStream() {
        const attempts = [
            {video: {facingMode: {ideal: 'environment'}}, audio: false},
            {video: {facingMode: 'environment'}, audio: false},
            {video: true, audio: false}
        ];
        let lastError = null;
        for (const constraints of attempts) {
            try {
                return await navigator.mediaDevices.getUserMedia(constraints);
            } catch (error) {
                lastError = error;
            }
        }
        throw lastError || new Error('No se pudo acceder a la camara.');
    }

    function createScanner(config) {
        const state = {
            stream: null,
            frame: null,
            active: false,
            detector: null,
            html5QrCode: null,
            mode: null,
            lastScan: '',
            lastScanAt: 0
        };

        const settings = Object.assign({
            formats: DEFAULT_FORMATS,
            duplicateWindowMs: 2500,
            fallbackFps: 10,
            fallbackQrbox: {width: 280, height: 180},
            fallbackAspectRatio: 1.777778
        }, config || {});

        function setStatus(message, isError) {
            if (typeof settings.onStatus === 'function') {
                settings.onStatus(message, Boolean(isError), state.mode);
            }
        }

        function setMode(nextMode) {
            state.mode = nextMode;
            if (settings.videoElement) {
                settings.videoElement.classList.toggle('hidden', nextMode !== 'native');
            }
            if (settings.fallbackElement) {
                settings.fallbackElement.classList.toggle('hidden', nextMode !== 'fallback');
            }
            if (typeof settings.onModeChange === 'function') {
                settings.onModeChange(nextMode);
            }
        }

        async function stopFallbackScanner() {
            if (!state.html5QrCode) {
                return;
            }
            const reader = state.html5QrCode;
            state.html5QrCode = null;
            try {
                await reader.stop();
            } catch (_) {
            }
            try {
                await reader.clear();
            } catch (_) {
            }
        }

        async function stopNativeScanner() {
            if (state.frame) {
                cancelAnimationFrame(state.frame);
                state.frame = null;
            }
            if (settings.videoElement) {
                try {
                    settings.videoElement.pause();
                } catch (_) {
                }
                settings.videoElement.srcObject = null;
            }
            if (state.stream) {
                state.stream.getTracks().forEach(function (track) {
                    track.stop();
                });
                state.stream = null;
            }
        }

        async function stop() {
            state.active = false;
            await stopNativeScanner();
            await stopFallbackScanner();
            setMode(null);
        }

        function handleDetectedValue(rawValue) {
            const value = String(rawValue || '').trim();
            if (!value) {
                return;
            }
            const now = Date.now();
            if (value === state.lastScan && (now - state.lastScanAt) < settings.duplicateWindowMs) {
                return;
            }
            state.lastScan = value;
            state.lastScanAt = now;
            if (typeof settings.onDetected === 'function') {
                settings.onDetected(value, state.mode);
            }
        }

        function scanNativeFrame() {
            if (!state.active || !state.detector || !settings.videoElement) {
                return;
            }
            if (settings.videoElement.readyState < 2) {
                state.frame = requestAnimationFrame(scanNativeFrame);
                return;
            }
            state.detector.detect(settings.videoElement)
                .then(function (codes) {
                    if (!state.active || !Array.isArray(codes) || !codes.length) {
                        return;
                    }
                    handleDetectedValue(codes[0].rawValue || '');
                })
                .catch(function () {
                    setStatus('No se pudo leer el codigo. Acerque mas la camara.', true);
                })
                .finally(function () {
                    if (state.active) {
                        state.frame = requestAnimationFrame(scanNativeFrame);
                    }
                });
        }

        async function startNative() {
            if (!window.BarcodeDetector || !settings.videoElement) {
                throw new Error('native-unavailable');
            }
            if (!state.detector) {
                state.detector = new window.BarcodeDetector({
                    formats: settings.formats
                });
            }
            state.stream = await requestCameraStream();
            settings.videoElement.srcObject = state.stream;
            await settings.videoElement.play();
            setMode('native');
            setStatus('Apunte la camara al codigo.', false);
            state.active = true;
            scanNativeFrame();
        }

        async function startFallback() {
            if (!settings.fallbackElement || !settings.fallbackElement.id) {
                throw new Error('fallback-container-missing');
            }
            await ensureFallbackScript();
            setMode('fallback');
            state.active = true;
            state.html5QrCode = new window.Html5Qrcode(settings.fallbackElement.id, {
                formatsToSupport: formatListForFallback()
            });
            await state.html5QrCode.start(
                {facingMode: 'environment'},
                {
                    fps: settings.fallbackFps,
                    qrbox: settings.fallbackQrbox,
                    aspectRatio: settings.fallbackAspectRatio
                },
                function (decodedText) {
                    handleDetectedValue(decodedText);
                },
                function () {
                }
            );
            setStatus('Apunte la camara al codigo.', false);
        }

        async function start() {
            if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                throw new Error('La camara no esta disponible en este navegador o contexto. Abra la pagina por HTTPS y revise permisos.');
            }

            await stop();
            state.lastScan = '';
            state.lastScanAt = 0;
            setStatus('Preparando camara...', false);

            try {
                await startNative();
            } catch (nativeError) {
                await stop();
                try {
                    await startFallback();
                } catch (fallbackError) {
                    await stop();
                    if (!window.BarcodeDetector) {
                        throw new Error('Este navegador no tiene escaneo nativo y no se pudo activar el lector alternativo.');
                    }
                    throw fallbackError && fallbackError.message
                        ? fallbackError
                        : nativeError;
                }
            }
        }

        return {
            start: start,
            stop: stop,
            getMode: function () {
                return state.mode;
            }
        };
    }

    window.MaidavBarcodeScanner = {
        create: createScanner
    };
})();
