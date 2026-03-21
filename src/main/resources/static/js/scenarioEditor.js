/**
 * ScenarioEditor — редактор шагов сценария симуляции.
 * Управление списком шагов + визуальный редактор ожидаемого состояния через SVG-схему.
 */
var ScenarioEditor = (function () {

    var steps = [];         // [{stepNumber, instructionText, expectedState (JSON string)}]
    var schemaElements = []; // [{id, name, elementType, posX, posY, width, height, initialState, rotation}]
    var schemaConnections = [];
    var schemaWidth = 1200;
    var schemaHeight = 800;

    var editingStepIndex = -1;
    var tempExpectedState = {}; // {elementId: true/false}

    function init() {
        document.getElementById('btnAddStep').addEventListener('click', addStep);
        document.getElementById('btnSaveSteps').addEventListener('click', saveSteps);
        document.getElementById('stateEditorClose').addEventListener('click', closeStateEditor);
        document.getElementById('stateEditorCancel').addEventListener('click', closeStateEditor);
        document.getElementById('stateEditorApply').addEventListener('click', applyState);

        document.getElementById('stateEditorOverlay').addEventListener('click', function (e) {
            if (e.target === this) closeStateEditor();
        });

        loadSteps();
    }

    // ========== Steps CRUD ==========

    function loadSteps() {
        fetch('/employee/chief/scenarios/loadSteps/' + scenarioId)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                steps = data.map(function (s) {
                    return {
                        stepNumber: s.stepNumber,
                        instructionText: s.instructionText || '',
                        expectedState: s.expectedState || '{}'
                    };
                });
                renderSteps();
            });
    }

    function addStep() {
        steps.push({
            stepNumber: steps.length + 1,
            instructionText: '',
            expectedState: '{}'
        });
        renderSteps();
        var cards = document.querySelectorAll('.step-card');
        if (cards.length > 0) {
            var last = cards[cards.length - 1];
            last.scrollIntoView({ behavior: 'smooth', block: 'center' });
            last.querySelector('textarea').focus();
        }
    }

    function removeStep(index) {
        if (!confirm('Удалить шаг ' + (index + 1) + '?')) return;
        steps.splice(index, 1);
        renumberSteps();
        renderSteps();
    }

    function moveStep(fromIndex, toIndex) {
        if (toIndex < 0 || toIndex >= steps.length) return;
        var item = steps.splice(fromIndex, 1)[0];
        steps.splice(toIndex, 0, item);
        renumberSteps();
        renderSteps();
    }

    function renumberSteps() {
        steps.forEach(function (s, i) { s.stepNumber = i + 1; });
    }

    function renderSteps() {
        var container = document.getElementById('stepsContainer');
        var noMsg = document.getElementById('noStepsMessage');
        container.innerHTML = '';

        if (steps.length === 0) {
            noMsg.style.display = 'block';
            return;
        }
        noMsg.style.display = 'none';

        steps.forEach(function (step, index) {
            var card = document.createElement('div');
            card.className = 'step-card';
            card.setAttribute('draggable', 'true');
            card.dataset.index = index;

            var stateObj = parseState(step.expectedState);
            var stateKeys = Object.keys(stateObj);
            var stateBadgesHtml = '';
            if (stateKeys.length > 0) {
                stateBadgesHtml = '<div class="step-state-badges mt-2">';
                stateKeys.forEach(function (key) {
                    var val = stateObj[key];
                    var cls = val ? 'bg-success' : 'bg-secondary';
                    stateBadgesHtml += '<span class="badge ' + cls + '">' + escapeHtml(key) + ': ' + (val ? 'ВКЛ' : 'ВЫКЛ') + '</span>';
                });
                stateBadgesHtml += '</div>';
            }

            card.innerHTML =
                '<div class="step-actions">' +
                '  <button class="btn btn-outline-secondary btn-sm btn-move-up" title="Вверх"><i class="bi bi-arrow-up"></i></button>' +
                '  <button class="btn btn-outline-secondary btn-sm btn-move-down" title="Вниз"><i class="bi bi-arrow-down"></i></button>' +
                '  <button class="btn btn-outline-danger btn-sm btn-remove" title="Удалить"><i class="bi bi-trash"></i></button>' +
                '</div>' +
                '<div class="d-flex align-items-start gap-3">' +
                '  <span class="drag-handle" title="Перетащить"><i class="bi bi-grip-vertical"></i></span>' +
                '  <span class="step-number">' + step.stepNumber + '</span>' +
                '  <div class="flex-grow-1">' +
                '    <textarea class="form-control mb-2 step-instruction" rows="2" placeholder="Инструкция для оператора...">' +
                       escapeHtml(step.instructionText) +
                '    </textarea>' +
                '    <button class="btn btn-outline-info btn-sm btn-set-state">' +
                '      <i class="bi bi-cpu"></i> Задать ожидаемое состояние' +
                '      <span class="badge bg-light text-dark ms-1">' + stateKeys.length + '</span>' +
                '    </button>' +
                    stateBadgesHtml +
                '  </div>' +
                '</div>';

            // Event listeners
            card.querySelector('.btn-move-up').addEventListener('click', function () { moveStep(index, index - 1); });
            card.querySelector('.btn-move-down').addEventListener('click', function () { moveStep(index, index + 1); });
            card.querySelector('.btn-remove').addEventListener('click', function () { removeStep(index); });
            card.querySelector('.btn-set-state').addEventListener('click', function () { openStateEditor(index); });
            card.querySelector('.step-instruction').addEventListener('input', function () {
                steps[index].instructionText = this.value;
            });

            // Drag-and-drop reordering
            card.addEventListener('dragstart', function (e) {
                e.dataTransfer.setData('text/plain', index);
                card.classList.add('dragging');
            });
            card.addEventListener('dragend', function () { card.classList.remove('dragging'); });
            card.addEventListener('dragover', function (e) {
                e.preventDefault();
                card.classList.add('drag-over');
            });
            card.addEventListener('dragleave', function () { card.classList.remove('drag-over'); });
            card.addEventListener('drop', function (e) {
                e.preventDefault();
                card.classList.remove('drag-over');
                var from = parseInt(e.dataTransfer.getData('text/plain'));
                var to = index;
                if (from !== to) moveStep(from, to);
            });

            container.appendChild(card);
        });
    }

    // ========== Save/Load Steps ==========

    function saveSteps() {
        var indicator = document.getElementById('stepsSaveIndicator');
        indicator.textContent = 'Сохранение...';
        indicator.className = 'text-warning';

        var payload = steps.map(function (s, i) {
            return {
                stepNumber: i + 1,
                instructionText: s.instructionText,
                expectedState: s.expectedState
            };
        });

        fetch('/employee/chief/scenarios/saveSteps/' + scenarioId, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data.success) {
                indicator.textContent = 'Сохранено';
                indicator.className = 'text-success';
            } else {
                indicator.textContent = 'Ошибка сохранения';
                indicator.className = 'text-danger';
            }
            setTimeout(function () { indicator.textContent = ''; }, 3000);
        })
        .catch(function () {
            indicator.textContent = 'Ошибка сети';
            indicator.className = 'text-danger';
        });
    }

    // ========== State Editor ==========

    function openStateEditor(stepIndex) {
        editingStepIndex = stepIndex;
        var step = steps[stepIndex];
        tempExpectedState = parseState(step.expectedState);

        document.getElementById('stateEditorStepNum').textContent = step.stepNumber;
        document.getElementById('stateEditorOverlay').classList.add('show');
        document.body.style.overflow = 'hidden';

        loadSchemaForStateEditor();
    }

    function closeStateEditor() {
        document.getElementById('stateEditorOverlay').classList.remove('show');
        document.body.style.overflow = '';
        editingStepIndex = -1;
    }

    function applyState() {
        if (editingStepIndex < 0) return;
        // Remove entries where state matches initialState (no change expected)
        var cleanState = {};
        Object.keys(tempExpectedState).forEach(function (key) {
            cleanState[key] = tempExpectedState[key];
        });
        steps[editingStepIndex].expectedState = JSON.stringify(cleanState);
        closeStateEditor();
        renderSteps();
    }

    function loadSchemaForStateEditor() {
        var sid = currentSchemaId || document.getElementById('schemaSelect').value;
        if (!sid) {
            alert('Сначала выберите мнемосхему в настройках сценария.');
            closeStateEditor();
            return;
        }

        fetch('/employee/chief/scenarios/loadSchemaElements/' + sid)
            .then(function (r) { return r.json(); })
            .then(function (data) {
                schemaElements = data.elements || [];
                schemaConnections = data.connections || [];
                schemaWidth = data.width || 1200;
                schemaHeight = data.height || 800;
                renderStateEditorSvg();
            })
            .catch(function () {
                alert('Не удалось загрузить данные схемы');
                closeStateEditor();
            });
    }

    function renderStateEditorSvg() {
        var svgEl = document.getElementById('stateEditorSvg');
        svgEl.setAttribute('width', schemaWidth);
        svgEl.setAttribute('height', schemaHeight);
        svgEl.setAttribute('viewBox', '0 0 ' + schemaWidth + ' ' + schemaHeight);

        // Get symbol defs from SchemaEditor if available
        var defsHtml = '';
        if (typeof SchemaEditor !== 'undefined' && SchemaEditor.getSymbolDefs) {
            defsHtml = SchemaEditor.getSymbolDefs();
        }

        svgEl.innerHTML = defsHtml +
            '<rect width="' + schemaWidth + '" height="' + schemaHeight + '" fill="#f8f9fa"/>';

        // Render connections
        schemaConnections.forEach(function (conn) {
            var sourceEl = schemaElements.find(function (e) { return e.id === conn.sourceElementId; });
            var targetEl = schemaElements.find(function (e) { return e.id === conn.targetElementId; });
            if (!sourceEl || !targetEl) return;

            var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', sourceEl.posX + (sourceEl.width || 60) / 2);
            line.setAttribute('y1', sourceEl.posY + (sourceEl.height || 60) / 2);
            line.setAttribute('x2', targetEl.posX + (targetEl.width || 60) / 2);
            line.setAttribute('y2', targetEl.posY + (targetEl.height || 60) / 2);
            line.setAttribute('class', 'connection-line');
            svgEl.appendChild(line);
        });

        // Render elements
        schemaElements.forEach(function (el) {
            var isOn = tempExpectedState.hasOwnProperty(el.name)
                ? tempExpectedState[el.name]
                : el.initialState;

            var group = document.createElementNS('http://www.w3.org/2000/svg', 'g');
            group.setAttribute('class', 'element-group' + (tempExpectedState.hasOwnProperty(el.name) && tempExpectedState[el.name] ? ' state-on' : ''));
            group.setAttribute('transform', 'translate(' + el.posX + ',' + el.posY + ')' +
                (el.rotation ? ' rotate(' + el.rotation + ',' + ((el.width || 60) / 2) + ',' + ((el.height || 60) / 2) + ')' : ''));
            group.style.cursor = 'pointer';

            // Border rect
            var border = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
            border.setAttribute('class', 'element-border');
            border.setAttribute('width', el.width || 60);
            border.setAttribute('height', el.height || 60);
            border.setAttribute('fill', 'transparent');
            border.setAttribute('stroke', tempExpectedState.hasOwnProperty(el.name) ? (tempExpectedState[el.name] ? '#198754' : '#dc3545') : '#dee2e6');
            border.setAttribute('stroke-width', tempExpectedState.hasOwnProperty(el.name) ? '2.5' : '1');
            border.setAttribute('rx', '4');
            group.appendChild(border);

            // Use symbol
            var state = isOn ? 'on' : 'off';
            var use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
            use.setAttribute('href', '#symbol-' + el.elementType + '-' + state);
            use.setAttribute('width', el.width || 60);
            use.setAttribute('height', el.height || 60);
            group.appendChild(use);

            // Label
            var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            label.setAttribute('x', (el.width || 60) / 2);
            label.setAttribute('y', (el.height || 60) + 14);
            label.setAttribute('text-anchor', 'middle');
            label.setAttribute('font-size', '11');
            label.setAttribute('fill', '#333');
            label.textContent = el.name || '';
            group.appendChild(label);

            // Click handler
            group.addEventListener('click', function () {
                toggleElementState(el.name, group, border, use, el);
            });

            svgEl.appendChild(group);
        });
    }

    function toggleElementState(elName, group, border, use, el) {
        if (tempExpectedState.hasOwnProperty(elName)) {
            if (tempExpectedState[elName]) {
                // Was ON -> set OFF
                tempExpectedState[elName] = false;
                border.setAttribute('stroke', '#dc3545');
                border.setAttribute('stroke-width', '2.5');
                group.classList.remove('state-on');
                use.setAttribute('href', '#symbol-' + el.elementType + '-off');
            } else {
                // Was OFF -> remove from state (back to initial)
                delete tempExpectedState[elName];
                border.setAttribute('stroke', '#dee2e6');
                border.setAttribute('stroke-width', '1');
                group.classList.remove('state-on');
                var initState = el.initialState ? 'on' : 'off';
                use.setAttribute('href', '#symbol-' + el.elementType + '-' + initState);
            }
        } else {
            // Not set -> set ON
            tempExpectedState[elName] = true;
            border.setAttribute('stroke', '#198754');
            border.setAttribute('stroke-width', '2.5');
            group.classList.add('state-on');
            use.setAttribute('href', '#symbol-' + el.elementType + '-on');
        }
    }

    // ========== Utils ==========

    function parseState(json) {
        if (!json || json === '{}') return {};
        try { return JSON.parse(json); }
        catch (e) { return {}; }
    }

    function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // ========== Public API ==========

    return {
        init: init
    };

})();

document.addEventListener('DOMContentLoaded', function () {
    if (typeof scenarioId !== 'undefined') {
        ScenarioEditor.init();
    }
});
