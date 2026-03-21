/**
 * Simulation — клиентский движок симуляции мнемосхемы.
 * Таймер, переключение элементов, проверка шагов, tooltip,
 * окрашивание труб, значения датчиков, направление потока.
 */
var Simulation = (function () {

    var svgEl;
    var elementState = {};   // {name: true/false}
    var elementGroups = {};  // {name: {group, use, el, valueText?}}
    var connectionLines = []; // [{line, src, tgt}]
    var timerInterval = null;
    var tooltipEl = null;

    /** Русские названия типов элементов */
    var TYPE_LABELS = {
        'VALVE': 'Пневмоклапан',
        'PUMP': 'Насос',
        'SWITCH': 'Переключатель',
        'SENSOR_PRESSURE': 'Датчик давления',
        'SENSOR_TEMPERATURE': 'Датчик температуры',
        'HEATER': 'Нагреватель',
        'LOCK': 'Блокиратор',
        'LABEL': 'Надпись'
    };

    /** Диапазоны значений датчиков при ВКЛ */
    var SENSOR_RANGES = {
        'SENSOR_PRESSURE': { min: 0.5, max: 6.0, unit: 'МПа' },
        'SENSOR_TEMPERATURE': { min: 15, max: 85, unit: '°C' }
    };

    function init() {
        svgEl = document.getElementById('simSvg');
        if (!svgEl || typeof simSchemaData === 'undefined') return;

        // Парсить текущее состояние
        if (typeof simCurrentState === 'string') {
            try { elementState = JSON.parse(simCurrentState); } catch (e) { elementState = {}; }
        } else if (typeof simCurrentState === 'object' && simCurrentState !== null) {
            elementState = simCurrentState;
        }

        createTooltip();
        renderSchema();
        renderStepProgress();
        startTimer();

        document.getElementById('btnCheckStep').addEventListener('click', checkStep);
    }

    // ========== Tooltip ==========

    function createTooltip() {
        tooltipEl = document.createElement('div');
        tooltipEl.className = 'sim-tooltip';
        tooltipEl.style.display = 'none';
        document.body.appendChild(tooltipEl);
    }

    function showTooltip(el, evt) {
        var isOn = elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
        var typeName = TYPE_LABELS[el.elementType] || el.elementType;

        if (el.elementType === 'LABEL') {
            tooltipEl.innerHTML =
                '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
                '<div class="sim-tooltip-type">' + typeName + '</div>';
        } else {
            var stateText = isOn ? 'ВКЛ' : 'ВЫКЛ';
            var stateClass = isOn ? 'sim-tooltip-on' : 'sim-tooltip-off';
            var sensorHtml = '';

            // Значение датчика
            if (SENSOR_RANGES[el.elementType]) {
                var range = SENSOR_RANGES[el.elementType];
                var val = isOn ? getSensorValue(el.name, range) : 0;
                sensorHtml = '<div class="sim-tooltip-value">' + val.toFixed(2) + ' ' + range.unit + '</div>';
            }

            tooltipEl.innerHTML =
                '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
                '<div class="sim-tooltip-type">' + typeName + '</div>' +
                sensorHtml +
                '<div class="sim-tooltip-state ' + stateClass + '">' + stateText + '</div>' +
                '<div class="sim-tooltip-hint">Нажмите для переключения</div>';
        }

        tooltipEl.style.display = 'block';
        positionTooltip(evt);
    }

    function positionTooltip(evt) {
        if (!tooltipEl || tooltipEl.style.display === 'none') return;
        var x = evt.clientX + 14;
        var y = evt.clientY + 14;
        var rect = tooltipEl.getBoundingClientRect();
        if (x + rect.width > window.innerWidth) x = evt.clientX - rect.width - 10;
        if (y + rect.height > window.innerHeight) y = evt.clientY - rect.height - 10;
        tooltipEl.style.left = x + 'px';
        tooltipEl.style.top = y + 'px';
    }

    function hideTooltip() {
        if (tooltipEl) tooltipEl.style.display = 'none';
    }

    // ========== Значения датчиков (pseudo-random, стабильные для одного имени) ==========

    var sensorCache = {};

    function getSensorValue(name, range) {
        if (sensorCache[name] !== undefined) return sensorCache[name];
        // Генерируем стабильное псевдослучайное значение по имени
        var hash = 0;
        for (var i = 0; i < name.length; i++) {
            hash = ((hash << 5) - hash) + name.charCodeAt(i);
            hash |= 0;
        }
        var norm = (Math.abs(hash) % 10000) / 10000;
        var val = range.min + norm * (range.max - range.min);
        sensorCache[name] = val;
        return val;
    }

    // ========== Рендер схемы ==========

    function renderSchema() {
        var data = simSchemaData;
        var w = data.width || 1200;
        var h = data.height || 800;

        svgEl.setAttribute('width', w);
        svgEl.setAttribute('height', h);
        svgEl.setAttribute('viewBox', '0 0 ' + w + ' ' + h);

        var defsHtml = '';
        if (typeof SchemaEditor !== 'undefined' && SchemaEditor.getSymbolDefs) {
            defsHtml = SchemaEditor.getSymbolDefs();
        }

        // Добавляем маркер стрелки для направления потока
        var arrowDefs =
            '<defs>' +
            '<marker id="arrow-active" viewBox="0 0 10 6" refX="10" refY="3" markerWidth="8" markerHeight="6" orient="auto">' +
            '<path d="M0,0 L10,3 L0,6 Z" fill="#27ae60"/>' +
            '</marker>' +
            '<marker id="arrow-inactive" viewBox="0 0 10 6" refX="10" refY="3" markerWidth="8" markerHeight="6" orient="auto">' +
            '<path d="M0,0 L10,3 L0,6 Z" fill="#adb5bd"/>' +
            '</marker>' +
            '</defs>';

        svgEl.innerHTML = defsHtml + arrowDefs +
            '<rect width="' + w + '" height="' + h + '" fill="#f8f9fa"/>';

        connectionLines = [];
        var elementsById = {};
        (data.elements || []).forEach(function (el) { elementsById[el.id] = el; });

        // Соединения (трубы) с направлением и окрашиванием
        (data.connections || []).forEach(function (conn) {
            var src = elementsById[conn.sourceElementId];
            var tgt = elementsById[conn.targetElementId];
            if (!src || !tgt) return;

            var x1 = src.posX + (src.width || 60) / 2;
            var y1 = src.posY + (src.height || 60) / 2;
            var x2 = tgt.posX + (tgt.width || 60) / 2;
            var y2 = tgt.posY + (tgt.height || 60) / 2;

            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', x1);
            line.setAttribute('y1', y1);
            line.setAttribute('x2', x2);
            line.setAttribute('y2', y2);
            line.setAttribute('class', 'connection-line');
            line.setAttribute('stroke-width', '3');
            line.setAttribute('fill', 'none');
            svgEl.appendChild(line);

            connectionLines.push({ line: line, src: src, tgt: tgt });
        });

        // Элементы
        (data.elements || []).forEach(function (el) {
            var isOn = elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
            var state = isOn ? 'on' : 'off';
            var isLabel = el.elementType === 'LABEL';
            var isSensor = el.elementType === 'SENSOR_PRESSURE' || el.elementType === 'SENSOR_TEMPERATURE';

            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', 'element-group' + (isLabel ? ' label-group' : ''));
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + ((el.width || 60) / 2) + ',' + ((el.height || 60) / 2) + ')' : ''));

            if (isLabel) {
                // Надпись — отображаем имя как текст, без иконки
                var labelText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                labelText.setAttribute('x', (el.width || 80) / 2);
                labelText.setAttribute('y', (el.height || 30) / 2 + 5);
                labelText.setAttribute('text-anchor', 'middle');
                labelText.setAttribute('font-size', '13');
                labelText.setAttribute('font-weight', '600');
                labelText.setAttribute('fill', '#495057');
                labelText.textContent = el.name || 'Текст';
                group.appendChild(labelText);
            } else {
                var border = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                border.setAttribute('class', 'element-border');
                border.setAttribute('width', el.width || 60);
                border.setAttribute('height', el.height || 60);
                border.setAttribute('fill', 'transparent');
                border.setAttribute('stroke', '#dee2e6');
                border.setAttribute('stroke-width', '1');
                border.setAttribute('rx', '4');
                group.appendChild(border);

                var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
                use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);
                use.setAttribute('width', el.width || 60);
                use.setAttribute('height', el.height || 60);
                group.appendChild(use);

                // Имя элемента под иконкой
                var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                label.setAttribute('x', (el.width || 60) / 2);
                label.setAttribute('y', (el.height || 60) + 14);
                label.setAttribute('text-anchor', 'middle');
                label.setAttribute('font-size', '11');
                label.setAttribute('fill', '#333');
                label.textContent = el.name || '';
                group.appendChild(label);

                // Значение датчика под именем
                var valueText = null;
                if (isSensor) {
                    valueText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                    valueText.setAttribute('x', (el.width || 60) / 2);
                    valueText.setAttribute('y', (el.height || 60) + 26);
                    valueText.setAttribute('text-anchor', 'middle');
                    valueText.setAttribute('font-size', '10');
                    valueText.setAttribute('font-weight', '700');
                    var range = SENSOR_RANGES[el.elementType];
                    if (isOn && range) {
                        var val = getSensorValue(el.name, range);
                        valueText.textContent = val.toFixed(2) + ' ' + range.unit;
                        valueText.setAttribute('fill', '#0d6efd');
                    } else {
                        valueText.textContent = '0,00';
                        valueText.setAttribute('fill', '#adb5bd');
                    }
                    group.appendChild(valueText);
                }

                // Tooltip при наведении
                group.addEventListener('mouseenter', function (evt) { showTooltip(el, evt); });
                group.addEventListener('mousemove', function (evt) { positionTooltip(evt); });
                group.addEventListener('mouseleave', function () { hideTooltip(); });

                // Клик — переключение (не для LABEL)
                group.addEventListener('click', function () {
                    hideTooltip();
                    toggleElement(el.name, group, use, el, valueText);
                });

                elementGroups[el.name] = { group: group, use: use, el: el, valueText: valueText };
            }

            svgEl.appendChild(group);
        });

        // Обновить цвета труб
        updatePipeColors();
    }

    // ========== Окрашивание труб ==========

    function updatePipeColors() {
        connectionLines.forEach(function (conn) {
            var srcOn = isElementOn(conn.src);
            var tgtOn = isElementOn(conn.tgt);

            // Труба активна если ОБА конца включены (или это LABEL/датчик — считаем нейтральными)
            var srcActive = isNeutralElement(conn.src) || srcOn;
            var tgtActive = isNeutralElement(conn.tgt) || tgtOn;
            var active = srcActive && tgtActive;

            if (active) {
                conn.line.setAttribute('stroke', '#27ae60');
                conn.line.setAttribute('stroke-width', '4');
                conn.line.setAttribute('marker-end', 'url(#arrow-active)');
            } else {
                conn.line.setAttribute('stroke', '#adb5bd');
                conn.line.setAttribute('stroke-width', '3');
                conn.line.setAttribute('marker-end', 'url(#arrow-inactive)');
            }
        });
    }

    function isElementOn(el) {
        if (!el) return false;
        return elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
    }

    function isNeutralElement(el) {
        return el.elementType === 'LABEL' || el.elementType === 'SENSOR_PRESSURE' || el.elementType === 'SENSOR_TEMPERATURE';
    }

    // ========== Переключение элемента ==========

    function toggleElement(name, group, use, el, valueText) {
        showFeedback('', '');

        fetch('/employee/specialist/mnemo/toggleElement/' + simSessionId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ elementName: name })
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data.expired) {
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                return;
            }
            elementState[name] = data.newState;
            var state = data.newState ? 'on' : 'off';
            use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);

            // Обновить значение датчика
            if (valueText && SENSOR_RANGES[el.elementType]) {
                var range = SENSOR_RANGES[el.elementType];
                if (data.newState) {
                    var val = getSensorValue(el.name, range);
                    valueText.textContent = val.toFixed(2) + ' ' + range.unit;
                    valueText.setAttribute('fill', '#0d6efd');
                } else {
                    valueText.textContent = '0,00';
                    valueText.setAttribute('fill', '#adb5bd');
                }
            }

            // Обновить цвета труб
            updatePipeColors();

            // Подпись состояния
            showFeedback(el.name + ': ' + (data.newState ? 'ВКЛ' : 'ВЫКЛ'), 'info');

            // Visual feedback
            group.classList.add('toggled');
            setTimeout(function () { group.classList.remove('toggled'); }, 300);
        })
        .catch(function () {
            showFeedback('Ошибка связи с сервером', 'error');
        });
    }

    // ========== Проверка шага ==========

    function checkStep() {
        var btn = document.getElementById('btnCheckStep');
        btn.disabled = true;
        btn.innerHTML = '<i class="bi bi-hourglass-split"></i> Проверка...';

        fetch('/employee/specialist/mnemo/checkStep/' + simSessionId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-circle"></i> Проверить шаг';

            if (data.status === 'completed') {
                showFeedback('Симуляция успешно завершена!', 'success');
                setTimeout(function () {
                    window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                }, 1500);
            } else if (data.status === 'advance') {
                simCurrentStep = data.nextStep;
                simCompletedSteps = data.nextStep - 1;
                document.getElementById('currentStepNum').textContent = data.nextStep;
                document.getElementById('instructionText').textContent = data.nextInstruction || '';
                renderStepProgress();
                showFeedback('Шаг пройден! Переход к шагу ' + data.nextStep, 'success');
            } else if (data.status === 'wrong') {
                highlightError(data.failedElement);
                showFeedback('Ошибка: элемент «' + data.failedElement + '» — ожидалось ' +
                    (data.expected ? 'ВКЛ' : 'ВЫКЛ') + ', текущее ' +
                    (data.actual ? 'ВКЛ' : 'ВЫКЛ'), 'error');
            } else if (data.status === 'expired') {
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
            }
        })
        .catch(function () {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-check-circle"></i> Проверить шаг';
            showFeedback('Ошибка связи с сервером', 'error');
        });
    }

    // ========== Подсветка ошибочного элемента ==========

    function highlightError(elementName) {
        var info = elementGroups[elementName];
        if (!info) return;
        var border = info.group.querySelector('.element-border');
        if (!border) return;
        border.setAttribute('stroke', '#dc3545');
        border.setAttribute('stroke-width', '3');
        border.setAttribute('stroke-dasharray', '5,3');
        setTimeout(function () {
            border.setAttribute('stroke', '#dee2e6');
            border.setAttribute('stroke-width', '1');
            border.removeAttribute('stroke-dasharray');
        }, 3000);
    }

    // ========== Прогресс шагов ==========

    function renderStepProgress() {
        var container = document.getElementById('stepProgress');
        container.innerHTML = '';
        for (var i = 1; i <= simTotalSteps; i++) {
            var badge = document.createElement('span');
            badge.className = 'sim-step-badge';
            badge.textContent = i;
            if (i < simCurrentStep) {
                badge.classList.add('done');
            } else if (i === simCurrentStep) {
                badge.classList.add('current');
            } else {
                badge.classList.add('pending');
            }
            container.appendChild(badge);
        }
    }

    // ========== Таймер ==========

    function startTimer() {
        var timerEl = document.getElementById('simTimer');
        if (!timerEl) return;

        var endTimeStr = timerEl.getAttribute('data-end-time');
        if (!endTimeStr) return;

        var endTime = new Date(endTimeStr).getTime();

        function update() {
            var now = Date.now();
            var diff = endTime - now;

            if (diff <= 0) {
                timerEl.textContent = '00:00';
                timerEl.classList.add('warning');
                clearInterval(timerInterval);
                window.location.href = '/employee/specialist/mnemo/result/' + simSessionId;
                return;
            }

            var minutes = Math.floor(diff / 60000);
            var seconds = Math.floor((diff % 60000) / 1000);
            timerEl.textContent = pad(minutes) + ':' + pad(seconds);

            if (diff < 60000) {
                timerEl.classList.add('warning');
            }
        }

        update();
        timerInterval = setInterval(update, 1000);
    }

    function pad(n) { return n < 10 ? '0' + n : '' + n; }

    // ========== Feedback ==========

    function showFeedback(msg, type) {
        var el = document.getElementById('simFeedback');
        if (!msg) {
            el.classList.remove('show', 'success', 'error', 'info');
            return;
        }
        el.textContent = msg;
        el.className = 'sim-feedback show ' + type;
        if (type === 'success' || type === 'info') {
            setTimeout(function () { el.classList.remove('show'); }, 3000);
        }
    }

    return { init: init };
})();

document.addEventListener('DOMContentLoaded', function () {
    if (typeof simSessionId !== 'undefined') {
        Simulation.init();
    }
});
