/**
 * Simulation — клиентский движок симуляции мнемосхемы.
 * Таймер, переключение элементов, проверка шагов, tooltip.
 */
var Simulation = (function () {

    var svgEl;
    var elementState = {};   // {name: true/false}
    var elementGroups = {};  // {name: SVG group}
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
        'LOCK': 'Блокиратор'
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
        var stateText = isOn ? 'ВКЛ' : 'ВЫКЛ';
        var stateClass = isOn ? 'sim-tooltip-on' : 'sim-tooltip-off';

        tooltipEl.innerHTML =
            '<div class="sim-tooltip-name">' + (el.name || '—') + '</div>' +
            '<div class="sim-tooltip-type">' + typeName + '</div>' +
            '<div class="sim-tooltip-state ' + stateClass + '">' + stateText + '</div>' +
            '<div class="sim-tooltip-hint">Нажмите для переключения</div>';

        tooltipEl.style.display = 'block';
        positionTooltip(evt);
    }

    function positionTooltip(evt) {
        if (!tooltipEl || tooltipEl.style.display === 'none') return;
        var x = evt.clientX + 14;
        var y = evt.clientY + 14;
        // Не выходить за правый/нижний край
        var rect = tooltipEl.getBoundingClientRect();
        if (x + rect.width > window.innerWidth) x = evt.clientX - rect.width - 10;
        if (y + rect.height > window.innerHeight) y = evt.clientY - rect.height - 10;
        tooltipEl.style.left = x + 'px';
        tooltipEl.style.top = y + 'px';
    }

    function hideTooltip() {
        if (tooltipEl) tooltipEl.style.display = 'none';
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

        svgEl.innerHTML = defsHtml +
            '<rect width="' + w + '" height="' + h + '" fill="#f8f9fa"/>';

        // Соединения
        (data.connections || []).forEach(function (conn) {
            var src = (data.elements || []).find(function (e) { return e.id === conn.sourceElementId; });
            var tgt = (data.elements || []).find(function (e) { return e.id === conn.targetElementId; });
            if (!src || !tgt) return;

            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', src.posX + (src.width || 60) / 2);
            line.setAttribute('y1', src.posY + (src.height || 60) / 2);
            line.setAttribute('x2', tgt.posX + (tgt.width || 60) / 2);
            line.setAttribute('y2', tgt.posY + (tgt.height || 60) / 2);
            line.setAttribute('class', 'connection-line');
            svgEl.appendChild(line);
        });

        // Элементы
        (data.elements || []).forEach(function (el) {
            var isOn = elementState.hasOwnProperty(el.name) ? elementState[el.name] : el.initialState;
            var state = isOn ? 'on' : 'off';

            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', 'element-group');
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + ((el.width || 60) / 2) + ',' + ((el.height || 60) / 2) + ')' : ''));

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

            var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            label.setAttribute('x', (el.width || 60) / 2);
            label.setAttribute('y', (el.height || 60) + 14);
            label.setAttribute('text-anchor', 'middle');
            label.setAttribute('font-size', '11');
            label.setAttribute('fill', '#333');
            label.textContent = el.name || '';
            group.appendChild(label);

            // Tooltip при наведении
            group.addEventListener('mouseenter', function (evt) { showTooltip(el, evt); });
            group.addEventListener('mousemove', function (evt) { positionTooltip(evt); });
            group.addEventListener('mouseleave', function () { hideTooltip(); });

            // Клик — переключение
            group.addEventListener('click', function () {
                hideTooltip();
                toggleElement(el.name, group, use, el);
            });

            svgEl.appendChild(group);
            elementGroups[el.name] = { group: group, use: use, el: el };
        });
    }

    // ========== Переключение элемента ==========

    function toggleElement(name, group, use, el) {
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
                // Подсветить ошибочный элемент
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
