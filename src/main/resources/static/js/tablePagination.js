/**
 * Клиентская пагинация таблиц.
 * Использование: initPagination('tableId', { perPage: 20 })
 */
(function () {
    'use strict';

    window.initPagination = function (tableId, options) {
        var opts = Object.assign({ perPage: 20 }, options || {});
        var table = document.getElementById(tableId);
        if (!table) return;

        var tbody = table.querySelector('tbody');
        if (!tbody) return;

        var allRows = [];
        var currentPage = 1;
        var wrapper = null;

        function collectRows() {
            allRows = Array.from(tbody.querySelectorAll('tr')).filter(function (tr) {
                return !tr.classList.contains('pagination-empty-row');
            });
        }

        function totalPages() {
            return Math.max(1, Math.ceil(allRows.length / opts.perPage));
        }

        function showPage(page) {
            currentPage = Math.max(1, Math.min(page, totalPages()));
            var start = (currentPage - 1) * opts.perPage;
            var end = start + opts.perPage;

            allRows.forEach(function (row, i) {
                row.style.display = (i >= start && i < end) ? '' : 'none';
            });

            renderControls();
        }

        function renderControls() {
            if (!wrapper) {
                wrapper = document.createElement('div');
                wrapper.className = 'pagination-wrapper d-flex justify-content-between align-items-center mt-3';
                table.parentNode.insertBefore(wrapper, table.nextSibling);
            }

            var total = totalPages();
            if (allRows.length <= opts.perPage) {
                wrapper.style.display = 'none';
                return;
            }
            wrapper.style.display = '';

            var start = (currentPage - 1) * opts.perPage + 1;
            var end = Math.min(currentPage * opts.perPage, allRows.length);

            var html = '<div class="text-muted small">Показано ' + start + '–' + end + ' из ' + allRows.length + '</div>';
            html += '<nav><ul class="pagination pagination-sm mb-0">';

            html += '<li class="page-item' + (currentPage === 1 ? ' disabled' : '') + '">';
            html += '<a class="page-link" href="#" data-page="' + (currentPage - 1) + '">&laquo;</a></li>';

            var pages = getPageNumbers(currentPage, total);
            for (var i = 0; i < pages.length; i++) {
                var p = pages[i];
                if (p === '...') {
                    html += '<li class="page-item disabled"><span class="page-link">…</span></li>';
                } else {
                    html += '<li class="page-item' + (p === currentPage ? ' active' : '') + '">';
                    html += '<a class="page-link" href="#" data-page="' + p + '">' + p + '</a></li>';
                }
            }

            html += '<li class="page-item' + (currentPage === total ? ' disabled' : '') + '">';
            html += '<a class="page-link" href="#" data-page="' + (currentPage + 1) + '">&raquo;</a></li>';
            html += '</ul></nav>';

            wrapper.innerHTML = html;

            wrapper.querySelectorAll('a[data-page]').forEach(function (link) {
                link.addEventListener('click', function (e) {
                    e.preventDefault();
                    showPage(parseInt(this.getAttribute('data-page')));
                });
            });
        }

        function getPageNumbers(current, total) {
            if (total <= 7) {
                var arr = [];
                for (var i = 1; i <= total; i++) arr.push(i);
                return arr;
            }
            var pages = [1];
            if (current > 3) pages.push('...');
            for (var j = Math.max(2, current - 1); j <= Math.min(total - 1, current + 1); j++) {
                pages.push(j);
            }
            if (current < total - 2) pages.push('...');
            pages.push(total);
            return pages;
        }

        // Публичный API для пересчёта после сортировки
        table._paginationRefresh = function () {
            collectRows();
            showPage(1);
        };

        collectRows();
        if (allRows.length > opts.perPage) {
            showPage(1);
        }
    };
})();
