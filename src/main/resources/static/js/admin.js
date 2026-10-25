// Admin panel: Wikidata sync progress (polls /api/admin/sync) and hiding places
(function () {
    var section = document.getElementById('sync');
    var statusUrl = section.dataset.statusUrl;
    var csrfHeader = section.dataset.csrfHeader;
    var csrfToken = section.dataset.csrfToken;
    var syncAllButton = document.getElementById('sync-all');
    var stateBox = document.getElementById('sync-state');
    var countriesBox = document.getElementById('sync-countries');

    var POLL_MS = 1000;
    var STEPS = ['SEARCH', 'CANDIDATES', 'DETAILS'];
    var STEP_LABELS = { SEARCH: 'Search', CANDIDATES: 'Candidates', DETAILS: 'Details' };
    var STAGES = [['WIKIDATA', 'Wikidata'], ['PHOTOS', 'Photos on Commons'], ['SAVING', 'Saving']];

    var wasRunning = false;
    var timer = null;

    function request(method, url, body) {
        var headers = { 'Accept': 'application/json' };
        headers[csrfHeader] = csrfToken;
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        return fetch(url, { method: method, headers: headers, body: body === undefined ? undefined : JSON.stringify(body) })
            .then(function (response) {
                if (response.status === 204) {
                    return null;
                }
                return response.json().then(function (data) {
                    if (!response.ok) {
                        throw new Error(data.message || response.statusText);
                    }
                    return data;
                });
            });
    }

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    // 98 -> "1:38"
    function duration(seconds) {
        seconds = Math.max(0, Math.round(seconds));
        var s = seconds % 60;
        return Math.floor(seconds / 60) + ':' + (s < 10 ? '0' : '') + s;
    }

    function ago(iso) {
        var minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
        if (minutes < 1) {
            return 'just now';
        }
        if (minutes < 60) {
            return minutes + ' min ago';
        }
        var hours = Math.round(minutes / 60);
        if (hours < 24) {
            return hours + (hours === 1 ? ' hour ago' : ' hours ago');
        }
        var days = Math.round(hours / 24);
        return days + (days === 1 ? ' day ago' : ' days ago');
    }

    function places(count) {
        return count + (count === 1 ? ' place' : ' places');
    }

    // Colors of the three request bars: done, running, waiting or failed
    function bars(country) {
        var step = country.step;
        if (step === 'DONE') {
            return ['done', 'done', 'done'];
        }
        if (step === 'FAILED') {
            var failedAt = Math.max(0, STEPS.indexOf(country.failedStep));
            return STEPS.map(function (s, i) { return i < failedAt ? 'done' : i === failedAt ? 'fail' : 'wait'; });
        }
        var current = STEPS.indexOf(step);
        if (current >= 0) {
            return STEPS.map(function (s, i) { return i < current ? 'done' : i === current ? 'run' : 'wait'; });
        }
        // Not in the current run: the latest saved result
        var last = country.lastSync;
        if (!last) {
            return ['wait', 'wait', 'wait'];
        }
        return last.succeeded ? ['done', 'done', 'done'] : ['fail', 'fail', 'fail'];
    }

    function countryState(country) {
        switch (country.step) {
            case 'WAITING': return ['Waiting', 'wait'];
            case 'SEARCH':
            case 'CANDIDATES':
            case 'DETAILS': return [STEP_LABELS[country.step] + '…', 'run'];
            case 'DONE': return [places(country.placeCount), 'done'];
            case 'FAILED': return ['Failed, kept as it was', 'fail'];
        }
        var last = country.lastSync;
        if (!last) {
            return ['No sync record yet', 'wait'];
        }
        return last.succeeded ? [places(last.placeCount), 'done'] : ['Failed last time', 'fail'];
    }

    function countryCard(country, running) {
        var state = countryState(country);
        var failed = state[1] === 'fail';
        var card = el('div', 'sync-country sync-country-' + state[1]);

        var head = el('div', 'sync-country-head');
        head.appendChild(el('span', 'sync-country-name', country.name));
        head.appendChild(el('span', 'sync-country-status', state[0]));
        card.appendChild(head);

        var steps = el('div', 'sync-steps');
        bars(country).forEach(function (bar, i) {
            var step = el('div', 'sync-step');
            step.appendChild(el('div', 'sync-bar sync-bar-' + bar));
            step.appendChild(el('span', 'sync-step-label', STEP_LABELS[STEPS[i]]));
            steps.appendChild(step);
        });
        card.appendChild(steps);

        if (!running) {
            var foot = el('div', 'sync-country-foot');
            var last = country.lastSync;
            var note = el('span', 'sync-country-note');
            if (last && !last.succeeded) {
                note.textContent = last.message || 'Failed';
                note.title = last.message || '';
            } else if (last) {
                note.textContent = 'Last: ' + ago(last.syncedAt);
            }
            foot.appendChild(note);

            var button = el('button', 'btn btn-small ' + (failed ? 'btn-sun' : 'btn-line'));
            button.type = 'button';
            button.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
                'stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
                '<path d="M21 12a9 9 0 1 1-2.6-6.4M21 4v5h-5"/></svg>';
            button.appendChild(document.createTextNode(failed ? 'Retry' : 'Sync'));
            button.setAttribute('aria-label', (failed ? 'Retry ' : 'Sync ') + country.name);
            button.addEventListener('click', function () {
                start(statusUrl + '/' + encodeURIComponent(country.name));
            });
            foot.appendChild(button);
            card.appendChild(foot);
        }
        return card;
    }

    function runningPanel(status) {
        var inRun = status.countries.filter(function (c) { return c.step; });
        var finished = inRun.filter(function (c) { return c.step === 'DONE' || c.step === 'FAILED'; }).length;
        var stageIndex = STAGES.map(function (s) { return s[0]; }).indexOf(status.stage);
        // Wikidata takes most of the time: 90% of the bar, photos and saving the rest
        var percent = stageIndex === 0 ? 90 * finished / Math.max(1, inRun.length) : 90 + 5 * stageIndex;

        var panel = el('div', 'sync-running');
        var head = el('div', 'sync-running-head');
        var title = el('div', 'sync-running-title');
        title.innerHTML = '<svg class="spin" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
            'stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M21 12a9 9 0 1 1-9-9"/></svg>';
        title.appendChild(document.createTextNode(inRun.length === 1
            ? 'Syncing ' + inRun[0].name
            : 'Syncing: ' + finished + ' of ' + inRun.length + ' countries'));
        head.appendChild(title);
        head.appendChild(el('span', 'sync-running-time',
            'Started ' + duration((Date.now() - new Date(status.startedAt).getTime()) / 1000) + ' ago'));
        panel.appendChild(head);

        var bar = el('div', 'sync-progress');
        var fill = el('div', 'sync-progress-fill');
        fill.style.width = percent + '%';
        bar.appendChild(fill);
        panel.appendChild(bar);

        var stages = el('ol', 'sync-stages');
        STAGES.forEach(function (stage, i) {
            var state = i < stageIndex ? 'done' : i === stageIndex ? 'run' : 'wait';
            var item = el('li', 'sync-stage sync-stage-' + state);
            item.appendChild(el('span', 'sync-stage-number', String(i + 1)));
            item.appendChild(document.createTextNode(stage[1]));
            if (i === 0) {
                item.appendChild(el('span', 'sync-stage-note', finished + ' / ' + inRun.length));
            }
            stages.appendChild(item);
        });
        panel.appendChild(stages);
        return panel;
    }

    function resultLine(status) {
        if (status.error) {
            return el('div', 'notice notice-error', status.error);
        }
        if (!status.result) {
            return null;
        }
        var r = status.result;
        var text = 'Done in ' + duration((new Date(status.finishedAt) - new Date(status.startedAt)) / 1000) + ': ' +
            places(r.places) + ', ' + r.created + ' new, ' + r.removed + ' removed.';
        var failed = status.countries
            .filter(function (c) { return c.step === 'FAILED'; })
            .map(function (c) { return c.name; });
        if (failed.length === 1) {
            text += ' ' + failed[0] + ' failed and was left as it was.';
        } else if (failed.length > 1) {
            text += ' ' + failed.join(', ') + ' failed and were left as they were.';
        }
        return el('div', 'notice ' + (failed.length ? 'notice-warning' : 'notice-success'), text);
    }

    function render(status) {
        stateBox.innerHTML = '';
        var line = status.running ? runningPanel(status) : resultLine(status);
        if (line) {
            stateBox.appendChild(line);
        }
        countriesBox.innerHTML = '';
        status.countries.forEach(function (country) {
            countriesBox.appendChild(countryCard(country, status.running));
        });
        syncAllButton.disabled = status.running;
    }

    function poll() {
        request('GET', statusUrl).then(function (status) {
            render(status);
            if (status.running) {
                wasRunning = true;
                timer = setTimeout(poll, POLL_MS);
            } else if (wasRunning) {
                // Numbers and the place list changed
                window.location.reload();
            }
        }).catch(function (error) {
            stateBox.innerHTML = '';
            stateBox.appendChild(el('div', 'notice notice-error', 'Could not load the sync state: ' + error.message));
            timer = setTimeout(poll, POLL_MS * 5);
        });
    }

    function start(url) {
        syncAllButton.disabled = true;
        request('POST', url).then(function (status) {
            render(status);
            clearTimeout(timer);
            wasRunning = true;
            timer = setTimeout(poll, POLL_MS);
        }).catch(function (error) {
            stateBox.innerHTML = '';
            stateBox.appendChild(el('div', 'notice notice-error', error.message));
            poll();
        });
    }

    syncAllButton.addEventListener('click', function () {
        start(statusUrl);
    });

    // Hide / Show: the counters and filters change, so the page is reloaded
    document.querySelectorAll('[data-hide]').forEach(function (button) {
        button.addEventListener('click', function () {
            button.disabled = true;
            request('PATCH', button.dataset.url, { hidden: button.dataset.hidden === 'true' })
                .then(function () { window.location.reload(); })
                .catch(function (error) {
                    button.disabled = false;
                    alert('Could not change the place: ' + error.message);
                });
        });
    });

    poll();
})();
