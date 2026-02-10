// ==================== CONFIGURATION ====================
const API_BASE_URL = 'http://localhost:5000/api';
const YTMUSIC_TRENDING_ENDPOINT = 'http://localhost:5000/debug/ytmusic/trending';
let isSearching = false;
let isYtmusicLoading = false;
let appInitialized = false; // Guard against multiple initializations

// ==================== INITIALIZATION ====================
function initializeApp() {
    if (appInitialized) {
        console.log('App already initialized, skipping');
        return;
    }

    appInitialized = true;
    console.log('Music API Debugger Initializing...');

    const requiredElements = {
        'searchForm': 'Search form',
        'searchBtn': 'Search button',
        'searchInput': 'Search input',
        'resultsTable': 'Results table',
        'resultsBody': 'Results body',
        'errorMessage': 'Error message div',
        'noResults': 'No results div',
        'loadingIndicator': 'Loading indicator',
        'apiDebugInfo': 'API debug info div',
        'rawResponse': 'Raw response div',
        'endpointDisplay': 'Endpoint display div',
        'ytmusic-trending': 'YT Music trending container'
    };

    let allElementsFound = true;
    Object.entries(requiredElements).forEach(([id, name]) => {
        const el = document.getElementById(id);
        if (!el) {
            console.error(`Missing DOM element: ${name} (id="${id}")`);
            allElementsFound = false;
        }
    });

    if (!allElementsFound) {
        console.error('Some required DOM elements are missing. Check HTML file.');
        return;
    }

    const searchForm = document.getElementById('searchForm');
    if (searchForm && !searchForm._submitListenerAttached) {
        searchForm.addEventListener('submit', function (e) {
            e.preventDefault();
            handleSearch();
        });
        searchForm._submitListenerAttached = true;
    }

    loadYtMusicTrending();
    console.log('Music API Debugger Ready');
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

// ==================== MAIN SEARCH HANDLER ====================
async function handleSearch() {
    if (isSearching) {
        console.warn('Search already in progress');
        return;
    }

    isSearching = true;

    try {
        const searchInput = document.getElementById('searchInput');
        if (!searchInput) {
            showError('Search input element not found');
            return;
        }

        const query = searchInput.value.trim();
        if (!query) {
            showError('Please enter a song name');
            return;
        }

        clearAllResults();
        showLoading(true);

        const url = `${API_BASE_URL}/search?query=${encodeURIComponent(query)}&limit=10&filter=songs`;
        displayEndpoint(url);
        displayApiDebugInfo('GET', url, { query, limit: 10, filter: 'songs' });

        const startTime = performance.now();
        const response = await fetch(url, { redirect: 'manual' });
        const responseTime = (performance.now() - startTime).toFixed(2);

        if (response.status >= 300 && response.status < 400) {
            const location = response.headers.get('Location') || 'unknown';
            throw new Error(`API returned redirect (${response.status}) to: ${location}. Expected JSON response.`);
        }

        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            const responseText = await response.text();
            throw new Error(
                `API returned non-JSON response (Content-Type: ${contentType}). ` +
                `Body: ${responseText.substring(0, 200)}`
            );
        }

        let data;
        try {
            data = await response.json();
        } catch (parseError) {
            throw new Error(`Failed to parse API response as JSON: ${parseError.message}`);
        }

        displayRawResponse(data, response.status, responseTime);

        if (!response.ok) {
            throw new Error(data.error || `API returned status ${response.status}`);
        }

        const results = extractResults(data);
        if (results.length > 0) {
            displayResults(results);
        } else {
            showNoResults();
        }
    } catch (error) {
        showError(`Error: ${error.message}`);
        displayRawResponse({ error: error.message }, 'error', 0);
    } finally {
        showLoading(false);
        isSearching = false;
    }
}

// ==================== UI HELPERS ====================
function displayEndpoint(url) {
    const endpointDisplay = document.getElementById('endpointDisplay');
    if (!endpointDisplay) return;

    endpointDisplay.innerHTML = '';

    const urlDiv = document.createElement('div');
    urlDiv.className = 'endpoint-url';

    const label = document.createElement('strong');
    label.textContent = 'GET ';

    const urlText = document.createElement('code');
    urlText.textContent = url;

    urlDiv.appendChild(label);
    urlDiv.appendChild(urlText);
    endpointDisplay.appendChild(urlDiv);
}

function displayApiDebugInfo(method, url, params) {
    const apiDebugInfo = document.getElementById('apiDebugInfo');
    if (!apiDebugInfo) return;

    apiDebugInfo.innerHTML = '';

    const items = [
        { label: 'Endpoint Type', value: 'Standard' },
        { label: 'HTTP Method', value: method },
        { label: 'URL', value: url },
        { label: 'Parameters', value: JSON.stringify(params, null, 2) },
        { label: 'Timestamp', value: new Date().toISOString() }
    ];

    items.forEach(item => {
        const div = document.createElement('div');
        div.className = 'debug-item';
        const label = document.createElement('strong');
        label.textContent = item.label + ': ';
        const value = document.createElement('span');
        value.textContent = item.value;
        div.appendChild(label);
        div.appendChild(value);
        apiDebugInfo.appendChild(div);
    });
}

function displayRawResponse(data, status, responseTime) {
    const rawResponse = document.getElementById('rawResponse');
    if (!rawResponse) return;

    rawResponse.innerHTML = '';

    const statusDiv = document.createElement('div');
    statusDiv.style.marginBottom = '10px';
    statusDiv.style.color = '#4ec9b0';
    statusDiv.textContent = `Status: ${status} | Response Time: ${responseTime}ms`;
    rawResponse.appendChild(statusDiv);

    const preEl = document.createElement('pre');
    preEl.textContent = JSON.stringify(data, null, 2);
    rawResponse.appendChild(preEl);
}

function extractResults(data) {
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.results)) return data.results;
    if (data && data.data && Array.isArray(data.data.results)) return data.data.results;
    if (data && data.data && Array.isArray(data.data)) return data.data;
    return [];
}

function displayResults(results) {
    const resultsTable = document.getElementById('resultsTable');
    const resultsBody = document.getElementById('resultsBody');

    if (!resultsTable || !resultsBody) {
        return;
    }

    resultsBody.innerHTML = '';

    const fragment = document.createDocumentFragment();

    results.forEach((song, index) => {
        const row = document.createElement('tr');
        const cells = [
            String(index + 1),
            escapeHtml(song.title || 'N/A'),
            escapeHtml(formatArtists(song.artists)),
            escapeHtml(song.album || 'N/A'),
            escapeHtml(formatDuration(song.duration || song.duration_seconds))
        ];

        cells.forEach((cellText) => {
            const cell = document.createElement('td');
            cell.textContent = cellText;
            if (cellText === 'Not Available' || cellText === 'N/A') {
                cell.className = 'na';
            }
            row.appendChild(cell);
        });

        fragment.appendChild(row);
    });

    resultsBody.appendChild(fragment);
    resultsTable.classList.remove('hidden');
}

function formatArtists(artists) {
    if (!artists) return 'N/A';
    if (Array.isArray(artists)) {
        return artists
            .map(a => (typeof a === 'string' ? a : a.name || ''))
            .filter(Boolean)
            .join(', ') || 'N/A';
    }
    return String(artists);
}

function formatDuration(duration) {
    if (typeof duration === 'string' && duration.includes(':')) {
        return duration;
    }
    if (typeof duration === 'number' && duration > 0) {
        const mins = Math.floor(duration / 60);
        const secs = Math.floor(duration % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
    return 'N/A';
}

function showLoading(isLoading) {
    const loader = document.getElementById('loadingIndicator');
    if (!loader) return;
    if (isLoading) loader.classList.remove('hidden');
    else loader.classList.add('hidden');
}

function showError(message) {
    const err = document.getElementById('errorMessage');
    if (!err) {
        return;
    }
    err.textContent = message;
    err.classList.remove('hidden');
}

function showNoResults() {
    const nr = document.getElementById('noResults');
    if (!nr) return;
    nr.classList.remove('hidden');
}

function clearAllResults() {
    const errEl = document.getElementById('errorMessage');
    if (errEl) {
        errEl.textContent = '';
        errEl.classList.add('hidden');
    }

    const noResultsEl = document.getElementById('noResults');
    if (noResultsEl) {
        noResultsEl.classList.add('hidden');
    }

    const tblEl = document.getElementById('resultsTable');
    if (tblEl) {
        tblEl.classList.add('hidden');
    }

    const bodyEl = document.getElementById('resultsBody');
    if (bodyEl) {
        bodyEl.innerHTML = '';
    }

    const debugEl = document.getElementById('apiDebugInfo');
    if (debugEl) {
        debugEl.innerHTML = '<p class="placeholder">Making request...</p>';
    }

    const rawEl = document.getElementById('rawResponse');
    if (rawEl) {
        rawEl.innerHTML = '<pre class="placeholder">Waiting for response...</pre>';
    }

    const endpointEl = document.getElementById('endpointDisplay');
    if (endpointEl) {
        endpointEl.innerHTML = '<p class="placeholder">Making request...</p>';
    }
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ==================== YT MUSIC TRENDING (DEBUG) ====================
async function loadYtMusicTrending() {
    if (isYtmusicLoading) {
        return;
    }

    const container = document.getElementById('ytmusic-trending');
    if (!container) {
        return;
    }

    container.textContent = 'Loading YT Music trending...';
    isYtmusicLoading = true;

    try {
        const response = await fetch(YTMUSIC_TRENDING_ENDPOINT, { redirect: 'manual' });
        const contentType = response.headers.get('content-type') || '';

        if (!contentType.includes('application/json')) {
            const responseText = await response.text();
            throw new Error(
                `Non-JSON response from backend. Content-Type: ${contentType}. ` +
                `Body: ${responseText.substring(0, 200)}`
            );
        }

        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || `Backend error (${response.status})`);
        }

        const items = Array.isArray(data.trending) ? data.trending : [];
        if (!items.length) {
            container.textContent = 'No trending items found.';
            return;
        }

        renderYtMusicTrending(container, items);
    } catch (error) {
        container.textContent = error.message || 'Failed to load YT Music trending.';
    } finally {
        isYtmusicLoading = false;
    }
}

function renderYtMusicTrending(container, items) {
    container.innerHTML = '';

    items.forEach((item, index) => {
        const wrapper = document.createElement('div');
        wrapper.className = 'track';

        const image = document.createElement('img');
        image.src = item.thumbnail || '';
        image.alt = item.title || 'Trending track';
        image.width = 80;
        image.height = 80;
        image.style.objectFit = 'cover';
        image.style.borderRadius = '6px';

        const title = document.createElement('h4');
        title.textContent = `${index + 1}. ${item.title || 'Untitled'}`;

        const artist = document.createElement('p');
        artist.textContent = formatArtists(item.artists);

        wrapper.appendChild(image);
        wrapper.appendChild(title);
        wrapper.appendChild(artist);

        const divider = document.createElement('hr');
        container.appendChild(wrapper);
        container.appendChild(divider);
    });
}
