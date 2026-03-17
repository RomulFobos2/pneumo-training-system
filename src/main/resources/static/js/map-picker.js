/**
 * Компонент выбора координат на Яндекс Карте через модальное окно.
 *
 * Использование:
 *   openMapPicker(latInput, lngInput, nameInput)
 *   где latInput и lngInput — DOM-элементы <input>, в которые запишутся координаты.
 *   nameInput (опционально) — DOM-элемент <input>, в который запишется название места (обратное геокодирование).
 *
 * Требует:
 *   - Яндекс Карты API v2.1 (подключается в blocks/map-picker.html)
 *   - Bootstrap 5 (модальное окно)
 *   - HTML-разметка модалки #mapPickerModal (blocks/map-picker.html)
 */

var _mapPickerState = {
    map: null,
    placemark: null,
    selectedCoords: null,
    latInput: null,
    lngInput: null,
    nameInput: null,
    initialized: false
};

/**
 * Открыть модальное окно с картой для выбора координат.
 * @param {HTMLInputElement} latInput — поле широты
 * @param {HTMLInputElement} lngInput — поле долготы
 * @param {HTMLInputElement} [nameInput] — поле для названия места (опционально)
 */
function openMapPicker(latInput, lngInput, nameInput) {
    _mapPickerState.latInput = latInput;
    _mapPickerState.lngInput = lngInput;
    _mapPickerState.nameInput = nameInput || null;
    _mapPickerState.selectedCoords = null;

    var confirmBtn = document.getElementById('mapPickerConfirmBtn');
    confirmBtn.disabled = true;

    document.getElementById('mapPickerCoords').textContent = 'Кликните на карту для выбора точки';
    document.getElementById('mapPickerAddress').textContent = '';

    var modal = new bootstrap.Modal(document.getElementById('mapPickerModal'));
    modal.show();

    // Инициализация карты после открытия модалки (нужна задержка для корректного рендера)
    document.getElementById('mapPickerModal').addEventListener('shown.bs.modal', function onShown() {
        document.getElementById('mapPickerModal').removeEventListener('shown.bs.modal', onShown);
        _initOrUpdateMap(latInput, lngInput);
    });
}

/**
 * Инициализировать карту или обновить центр/маркер при повторном открытии.
 */
function _initOrUpdateMap(latInput, lngInput) {
    var existingLat = parseFloat(latInput.value);
    var existingLng = parseFloat(lngInput.value);
    var hasExisting = !isNaN(existingLat) && !isNaN(existingLng);

    // Центр: существующие координаты или Москва
    var centerLat = hasExisting ? existingLat : 55.751244;
    var centerLng = hasExisting ? existingLng : 37.618423;
    var zoom = hasExisting ? 15 : 10;

    if (!_mapPickerState.initialized) {
        // Первая инициализация
        ymaps.ready(function () {
            _mapPickerState.map = new ymaps.Map('mapPickerContainer', {
                center: [centerLat, centerLng],
                zoom: zoom,
                controls: ['zoomControl', 'searchControl', 'typeSelector', 'geolocationControl']
            });

            // Клик по карте — установить маркер
            _mapPickerState.map.events.add('click', function (e) {
                var coords = e.get('coords');
                _setMapPickerMarker(coords);
            });

            // Если есть существующие координаты — поставить маркер
            if (hasExisting) {
                _setMapPickerMarker([centerLat, centerLng]);
            }

            _mapPickerState.initialized = true;
        });
    } else {
        // Повторное открытие — обновить центр и маркер
        _mapPickerState.map.setCenter([centerLat, centerLng], zoom);

        // Удалить старый маркер
        if (_mapPickerState.placemark) {
            _mapPickerState.map.geoObjects.remove(_mapPickerState.placemark);
            _mapPickerState.placemark = null;
        }

        // Если есть координаты — поставить маркер
        if (hasExisting) {
            _setMapPickerMarker([centerLat, centerLng]);
        }
    }
}

/**
 * Установить/переместить маркер на карте и обновить UI.
 * @param {number[]} coords — [latitude, longitude]
 */
function _setMapPickerMarker(coords) {
    var lat = coords[0];
    var lng = coords[1];

    _mapPickerState.selectedCoords = coords;

    // Удалить старый маркер
    if (_mapPickerState.placemark) {
        _mapPickerState.map.geoObjects.remove(_mapPickerState.placemark);
    }

    // Создать новый маркер
    _mapPickerState.placemark = new ymaps.Placemark(coords, {}, {
        preset: 'islands#redDotIcon',
        draggable: true
    });

    // Перетаскивание маркера
    _mapPickerState.placemark.events.add('dragend', function () {
        var newCoords = _mapPickerState.placemark.geometry.getCoordinates();
        _mapPickerState.selectedCoords = newCoords;
        _updateMapPickerUI(newCoords);
    });

    _mapPickerState.map.geoObjects.add(_mapPickerState.placemark);

    _updateMapPickerUI(coords);

    // Активировать кнопку подтверждения
    document.getElementById('mapPickerConfirmBtn').disabled = false;
}

/**
 * Обновить отображение координат и адреса в модалке.
 */
function _updateMapPickerUI(coords) {
    var lat = coords[0].toFixed(6);
    var lng = coords[1].toFixed(6);

    document.getElementById('mapPickerCoords').textContent = 'Широта: ' + lat + '  |  Долгота: ' + lng;

    // Обратное геокодирование — показать адрес
    var addressDiv = document.getElementById('mapPickerAddress');
    addressDiv.textContent = 'Определение адреса...';

    ymaps.geocode(coords, { results: 1 }).then(function (res) {
        var firstGeoObject = res.geoObjects.get(0);
        if (firstGeoObject) {
            addressDiv.textContent = firstGeoObject.getAddressLine();
        } else {
            addressDiv.textContent = '';
        }
    }).catch(function () {
        addressDiv.textContent = '';
    });
}

// Обработчик кнопки «Подтвердить»
document.addEventListener('DOMContentLoaded', function () {
    var confirmBtn = document.getElementById('mapPickerConfirmBtn');
    if (confirmBtn) {
        confirmBtn.addEventListener('click', function () {
            if (_mapPickerState.selectedCoords && _mapPickerState.latInput && _mapPickerState.lngInput) {
                _mapPickerState.latInput.value = _mapPickerState.selectedCoords[0].toFixed(6);
                _mapPickerState.lngInput.value = _mapPickerState.selectedCoords[1].toFixed(6);

                // Записать название места в дополнительное поле (если передано)
                if (_mapPickerState.nameInput) {
                    var addressText = document.getElementById('mapPickerAddress').textContent || '';
                    if (addressText && addressText !== 'Определение адреса...') {
                        _mapPickerState.nameInput.value = addressText;
                    }
                }
            }

            // Закрыть модалку
            var modal = bootstrap.Modal.getInstance(document.getElementById('mapPickerModal'));
            if (modal) {
                modal.hide();
            }
        });
    }
});
