// ==================== CONFIGURATION ====================
const API_BASE_URL = 'http://localhost:5000/api';
let isSearching = false;
let appInitialized = false;  // Guard against multiple initializations

// ==================== INITIALIZATION ====================
function initializeApp() {
    // Prevent double initialization
    if (appInitialized) {
        console.log('⚠️ App already initialized, skipping');
        return;
    }
    appInitialized = true;
    console.log('🎵 Music API Debugger Initializing...');
    console.log('📌 Document Ready State:', document.readyState);
    console.log('🌍 Backend Enrichment: DISABLED');
    console.log('📊 Data Source: YouTube Music API (raw results only)');
    
    // Verify all required DOM elements exist
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
        'lastfmBtn': 'Last.fm load button',
        'lastfmLoading': 'Last.fm loading indicator',
        'lastfmError': 'Last.fm error message',
        'trending-results': 'Last.fm results container'
    };
    
    let allElementsFound = true;
    Object.entries(requiredElements).forEach(([id, name]) => {
        const el = document.getElementById(id);
        if (!el) {
            console.error(`❌ Missing DOM element: ${name} (id="${id}")`);
            allElementsFound = false;
        }
    });
    
    if (!allElementsFound) {
        console.error('❌ Some required DOM elements are missing. Check HTML file.');
        return;
    }
    
    // Get references to elements (DO NOT clone/replace - keeps listener binding intact)
    const searchForm = document.getElementById('searchForm');
    
    // Attach form submit listener EXACTLY ONCE - this handles both button clicks and Enter key
    // Single source of truth for search submission - no multiple listener issue possible
    
    if (searchForm && !searchForm._submitListenerAttached) {
        searchForm.addEventListener('submit', function(e) {
            e.preventDefault();
            console.log('📝 Form submitted');
            handleSearch();
        });
        searchForm._submitListenerAttached = true;
        console.log('✅ Form submit listener attached');
    }

    initializeLastFmSection();
    
    console.log('✅ Music API Debugger Ready');
    console.log(`📍 Backend API Base URL: ${API_BASE_URL}`);
}

// Run initialization
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

// ==================== MAIN SEARCH HANDLER ====================
async function handleSearch() {
    // Prevent concurrent searches
    if (isSearching) {
        console.warn('⚠️ Search already in progress');
        return;
    }
    
    isSearching = true;
    
    try {
        console.log('🔍 Starting search...');
        
        // Get search query
        const searchInput = document.getElementById('searchInput');
        if (!searchInput) {
            console.error('❌ Search input not found');
            showError('Search input element not found');
            return;
        }
        
        const query = searchInput.value.trim();
        if (!query) {
            showError('Please enter a song name');
            return;
        }
        
        console.log(`📝 Query: "${query}"`);
        
        // Reset UI BEFORE showing loading (clears all previous state)
        clearAllResults();
        showLoading(true);
        
        // Build request
        const url = `${API_BASE_URL}/search?query=${encodeURIComponent(query)}&limit=10&filter=songs`;
        
        console.log(`📡 Fetching from: ${url}`);
        
        // Display the exact API endpoint URL for debugging and verification
        displayEndpoint(url);
        
        // Log request details
        displayApiDebugInfo('GET', url, { query, limit: 10, filter: 'songs' }, false);
        
        // Make API call with redirect: "manual" to prevent browser navigation
        const startTime = performance.now();
        const response = await fetch(url, { redirect: 'manual' });
        const endTime = performance.now();
        const responseTime = (endTime - startTime).toFixed(2);
        
        console.log(`📥 Response Status: ${response.status} ${response.statusText}`);
        console.log(`⏱️ Response Time: ${responseTime}ms`);
        
        // CRITICAL: Check for redirects (3xx status codes) - prevent navigation
        if (response.status >= 300 && response.status < 400) {
            const location = response.headers.get('Location') || 'unknown';
            const errorMsg = `API returned redirect (${response.status}) to: ${location}. Expected JSON response.`;
            console.error(`❌ ${errorMsg}`);
            throw new Error(errorMsg);
        }
        
        // CRITICAL: Validate Content-Type before attempting JSON parse
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            // Response is not JSON - read as text and show error
            const responseText = await response.text();
            console.error(`❌ Expected JSON but got: ${contentType}`);
            console.error(`📄 Response body (first 500 chars): ${responseText.substring(0, 500)}`);
            
            const errorMsg = `API returned non-JSON response (Content-Type: ${contentType}). Expected application/json.`;
            throw new Error(errorMsg);
        }
        
        // Now safe to parse JSON
        let data;
        try {
            data = await response.json();
        } catch (parseError) {
            console.error(`❌ Failed to parse JSON:`, parseError);
            throw new Error(`Failed to parse API response as JSON: ${parseError.message}`);
        }
        
        console.log('📋 Response Data:', data);
        
        // Display raw response
        displayRawResponse(data, response.status, responseTime);
        
        // Check for API errors
        if (!response.ok) {
            const errorMsg = data.error || `API returned status ${response.status}`;
            throw new Error(errorMsg);
        }
        
        // Extract and display results
        const results = extractResults(data);
        console.log(`✨ Extracted ${results.length} results`);
        
        if (results.length > 0) {
            console.log('🎭 Displaying raw streaming results');
            displayResults(results);
            console.log('✅ Results displayed successfully');
        } else {
            showNoResults();
            console.log('⚠️ No results found');
        }
        
    } catch (error) {
        console.error('❌ Error:', error.message);
        showError(`Error: ${error.message}`);
        displayRawResponse({ error: error.message }, 'error', 0);
        
    } finally {
        showLoading(false);
        isSearching = false;
        console.log('✓ Search completed, handler ready for next search');
    }
}

/**
 * Display the exact API endpoint URL being called
 * Shows the full request URL as-is for debugging and verification
 */
function displayEndpoint(url) {
    const endpointDisplay = document.getElementById('endpointDisplay');
    if (!endpointDisplay) return;
    
    // Clear previous content
    endpointDisplay.innerHTML = '';
    
    // Create endpoint display
    const urlDiv = document.createElement('div');
    urlDiv.className = 'endpoint-url';
    
    const label = document.createElement('strong');
    label.textContent = 'GET ';
    
    const urlText = document.createElement('code');
    urlText.textContent = url;
    
    urlDiv.appendChild(label);
    urlDiv.appendChild(urlText);
    endpointDisplay.appendChild(urlDiv);
    
    console.log('🔗 Endpoint displayed: ' + url);
}

/**
 * Display API debug information
 * Shows debug info for the raw streaming API request
 */
function displayApiDebugInfo(method, url, params, isEnriched = false) {
    const apiDebugInfo = document.getElementById('apiDebugInfo');
    if (!apiDebugInfo) return;
    
    // Clear previous content
    apiDebugInfo.innerHTML = '';
    
    // Create and append each debug item
    const items = [
        { label: 'Backend Processing', value: 'Python Flask (raw streaming API results)' },
        { label: 'HTTP Method', value: method },
        { label: 'URL', value: url },
        { label: 'Parameters', value: JSON.stringify(params, null, 2) },
        { label: 'Timestamp', value: new Date().toISOString() },
        { label: 'Metadata Source', value: 'YouTube Music API (raw)' },
        { label: 'Enriched Fields', value: 'None' },
        { label: 'Data Honesty', value: 'Empty arrays/null for missing data (NOT "Unknown")' }
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
    
    console.log('📋 Debug info displayed');
}

/**
 * Display raw JSON response
 * Clear previous response before showing new one
 */
function displayRawResponse(data, status, responseTime) {
    const rawResponse = document.getElementById('rawResponse');
    if (!rawResponse) return;
    
    // Clear previous content
    rawResponse.innerHTML = '';
    
    // Create status line
    const statusDiv = document.createElement('div');
    statusDiv.style.marginBottom = '10px';
    statusDiv.style.color = '#4ec9b0';
    statusDiv.textContent = `Status: ${status} | Response Time: ${responseTime}ms`;
    rawResponse.appendChild(statusDiv);
    
    // Create JSON display
    const preEl = document.createElement('pre');
    preEl.textContent = JSON.stringify(data, null, 2);
    rawResponse.appendChild(preEl);
    
    console.log('📝 Raw response displayed');
}

/**
 * Extract results from various API response formats
 */
function extractResults(data) {
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.results)) return data.results;
    if (data && data.data && Array.isArray(data.data.results)) return data.data.results;
    if (data && data.data && Array.isArray(data.data)) return data.data;
    return [];
}

/**
 * Display raw results in table format
 * Uses DocumentFragment for efficient batch DOM insertion
 */
function displayResults(results) {
    console.log(`🎬 Rendering ${results.length} raw results...`);
    
    const resultsTable = document.getElementById('resultsTable');
    const resultsBody = document.getElementById('resultsBody');
    
    if (!resultsTable || !resultsBody) {
        console.error('❌ Results table elements not found');
        return;
    }
    
    // Clear any previous rows
    resultsBody.innerHTML = '';
    
    // Use DocumentFragment for efficient batch insertion (single DOM reflow)
    const fragment = document.createDocumentFragment();
    
    // Create rows for each result
    results.forEach((song, index) => {
        const row = document.createElement('tr');
        
        // Create cells (raw streaming data only)
        const cells = [
            String(index + 1),  // #
            escapeHtml(song.title || 'N/A'),  // Song Name
            escapeHtml(formatArtists(song.artists)),  // Artist(s)
            escapeHtml(song.album || 'N/A'),  // Album
            escapeHtml(formatDuration(song.duration || song.duration_seconds))  // Duration
        ];
        
        cells.forEach((cellText, i) => {
            const cell = document.createElement('td');
            cell.textContent = cellText;
            // Apply NA styling for missing data
            if (cellText === 'Not Available' || cellText === 'N/A') {
                cell.className = 'na';
            }
            row.appendChild(cell);
        });
        
        fragment.appendChild(row);
    });
    
    // Single DOM insertion via fragment
    resultsBody.appendChild(fragment);
    
    // Show the table
    resultsTable.classList.remove('hidden');
    console.log(`✅ ${results.length} rows rendered successfully`);
}

function formatArtists(artists) {
    if (!artists) return 'N/A';
    if (Array.isArray(artists)) return artists.map(a => typeof a === 'string' ? a : a.name || '').filter(Boolean).join(', ') || 'N/A';
    return String(artists);
}

function formatDuration(duration) {
    // Handle if already a formatted string (e.g., "4:28")
    if (typeof duration === 'string' && duration.includes(':')) {
        return duration;
    }
    // Handle numeric seconds
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
    console.error(`⚠️ Error message: ${message}`);
    const err = document.getElementById('errorMessage');
    if (!err) {
        console.error('❌ Error message element not found');
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

/**
 * Clear all previous results and error states
 * This is called BEFORE each new search to ensure clean state
 */
function clearAllResults() {
    console.log('🧹 Clearing previous results and errors...');
    
    // Clear error message
    const errEl = document.getElementById('errorMessage');
    if (errEl) {
        errEl.textContent = '';
        errEl.classList.add('hidden');
    }
    
    // Clear no results message
    const noResultsEl = document.getElementById('noResults');
    if (noResultsEl) {
        noResultsEl.classList.add('hidden');
    }
    
    // Hide and clear results table
    const tblEl = document.getElementById('resultsTable');
    if (tblEl) {
        tblEl.classList.add('hidden');
    }
    
    // Clear table body rows
    const bodyEl = document.getElementById('resultsBody');
    if (bodyEl) {
        bodyEl.innerHTML = '';
    }
    
    // Clear debug info
    const debugEl = document.getElementById('apiDebugInfo');
    if (debugEl) {
        debugEl.innerHTML = '<p class="placeholder">Making request...</p>';
    }
    
    // Clear raw response
    const rawEl = document.getElementById('rawResponse');
    if (rawEl) {
        rawEl.innerHTML = '<pre class="placeholder">Waiting for response...</pre>';
    }
    
    // Clear endpoint display
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

// ==================== LAST.FM TOP TRACKS ====================
const LASTFM_ENDPOINT = `${API_BASE_URL}/trending/lastfm`;
let isLastfmLoading = false;

function initializeLastFmSection() {
    const button = document.getElementById('lastfmBtn');
    if (!button) {
        return;
    }
}

async function loadTrendingTracks() {
    if (isLastfmLoading) {
        return;
    }

    const loadingEl = document.getElementById('lastfmLoading');
    const errorEl = document.getElementById('lastfmError');
    const resultsEl = document.getElementById('trending-results');

    if (!loadingEl || !errorEl || !resultsEl) {
        return;
    }

    errorEl.textContent = '';
    errorEl.classList.add('hidden');
    resultsEl.innerHTML = '';

    const url = `${LASTFM_ENDPOINT}?country=India&limit=20`;

    loadingEl.classList.remove('hidden');
    isLastfmLoading = true;

    try {
        const response = await fetch(url, { redirect: 'manual' });
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

        const tracks = Array.isArray(data.tracks) ? data.tracks : [];
        if (!tracks.length) {
            resultsEl.textContent = 'No top tracks found.';
            return;
        }

        tracks.forEach((track) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'track';

            const image = document.createElement('img');
            image.src = track.image || '';
            image.alt = track.track_name || 'Track image';
            image.width = 80;
            image.height = 80;
            image.style.objectFit = 'cover';
            image.style.borderRadius = '6px';

            const title = document.createElement('h4');
            title.textContent = `${track.rank}. ${track.track_name}`;

            const artist = document.createElement('p');
            artist.textContent = track.artist_name;

            const plays = document.createElement('p');
            plays.textContent = `Plays: ${track.playcount}`;

            wrapper.appendChild(image);
            wrapper.appendChild(title);
            wrapper.appendChild(artist);
            wrapper.appendChild(plays);

            const divider = document.createElement('hr');
            resultsEl.appendChild(wrapper);
            resultsEl.appendChild(divider);
        });
    } catch (error) {
        errorEl.textContent = error.message || 'Failed to load Last.fm top tracks.';
        errorEl.classList.remove('hidden');
    } finally {
        loadingEl.classList.add('hidden');
        isLastfmLoading = false;
    }
}

// Run initialization
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeApp);
} else {
    initializeApp();
}

// ==================== MAIN SEARCH HANDLER ====================
async function handleSearch() {
    // Prevent concurrent searches
    if (isSearching) {
        console.warn('⚠️ Search already in progress');
        return;
    }
    
    isSearching = true;
    
    try {
        console.log('🔍 Starting search...');
        
        // Get search query
        const searchInput = document.getElementById('searchInput');
        if (!searchInput) {
            console.error('❌ Search input not found');
            showError('Search input element not found');
            return;
        }
        
        const query = searchInput.value.trim();
        if (!query) {
            showError('Please enter a song name');
            return;
        }
        
        console.log(`📝 Query: "${query}"`);
        
        // Reset UI BEFORE showing loading (clears all previous state)
        clearAllResults();
        showLoading(true);
        
        // Build request
        const url = `${API_BASE_URL}/search?query=${encodeURIComponent(query)}&limit=10&filter=songs`;
        
        console.log(`📡 Fetching from: ${url}`);
        
        // Display the exact API endpoint URL for debugging and verification
        displayEndpoint(url);
        
        // Log request details
        displayApiDebugInfo('GET', url, { query, limit: 10, filter: 'songs' }, false);
        
        // Make API call with redirect: "manual" to prevent browser navigation
        const startTime = performance.now();
        const response = await fetch(url, { redirect: 'manual' });
        const endTime = performance.now();
        const responseTime = (endTime - startTime).toFixed(2);
        
        console.log(`📥 Response Status: ${response.status} ${response.statusText}`);
        console.log(`⏱️ Response Time: ${responseTime}ms`);
        
        // CRITICAL: Check for redirects (3xx status codes) - prevent navigation
        if (response.status >= 300 && response.status < 400) {
            const location = response.headers.get('Location') || 'unknown';
            const errorMsg = `API returned redirect (${response.status}) to: ${location}. Expected JSON response.`;
            console.error(`❌ ${errorMsg}`);
            throw new Error(errorMsg);
        }
        
        // CRITICAL: Validate Content-Type before attempting JSON parse
        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            // Response is not JSON - read as text and show error
            const responseText = await response.text();
            console.error(`❌ Expected JSON but got: ${contentType}`);
            console.error(`📄 Response body (first 500 chars): ${responseText.substring(0, 500)}`);
            
            const errorMsg = `API returned non-JSON response (Content-Type: ${contentType}). Expected application/json.`;
            throw new Error(errorMsg);
        }
        
        // Now safe to parse JSON
        let data;
        try {
            data = await response.json();
        } catch (parseError) {
            console.error(`❌ Failed to parse JSON:`, parseError);
            throw new Error(`Failed to parse API response as JSON: ${parseError.message}`);
        }
        
        console.log('📋 Response Data:', data);
        
        // Display raw response
        displayRawResponse(data, response.status, responseTime);
        
        // Check for API errors
        if (!response.ok) {
            const errorMsg = data.error || `API returned status ${response.status}`;
            throw new Error(errorMsg);
        }
        
        // Extract and display results
        const results = extractResults(data);
        console.log(`✨ Extracted ${results.length} results`);
        
        if (results.length > 0) {
            displayResults(results);
            console.log('✅ Results displayed successfully');
        } else {
            showNoResults();
            console.log('⚠️ No results found');
        }
        
    } catch (error) {
        console.error('❌ Error:', error.message);
        showError(`Error: ${error.message}`);
        displayRawResponse({ error: error.message }, 'error', 0);
        
    } finally {
        showLoading(false);
        isSearching = false;
        console.log('✓ Search completed, handler ready for next search');
    }
}

/**
 * Display the exact API endpoint URL being called
 * Shows the full request URL as-is for debugging and verification
 */
function displayEndpoint(url) {
    const endpointDisplay = document.getElementById('endpointDisplay');
    if (!endpointDisplay) return;
    
    // Clear previous content
    endpointDisplay.innerHTML = '';
    
    // Create endpoint display
    const urlDiv = document.createElement('div');
    urlDiv.className = 'endpoint-url';
    
    const label = document.createElement('strong');
    label.textContent = 'GET ';
    
    const urlText = document.createElement('code');
    urlText.textContent = url;
    
    urlDiv.appendChild(label);
    urlDiv.appendChild(urlText);
    endpointDisplay.appendChild(urlDiv);
    
    console.log('🔗 Endpoint displayed: ' + url);
}

/**
 * Display API debug information
 * Uses textContent and manual DOM building to avoid innerHTML vulnerabilities
 */
function displayApiDebugInfo(method, url, params, isEnriched = false) {
    const apiDebugInfo = document.getElementById('apiDebugInfo');
    if (!apiDebugInfo) return;
    
    // Clear previous content
    apiDebugInfo.innerHTML = '';
    
    // Create and append each debug item
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
    
    console.log('📋 Debug info displayed');
}

/**
 * Display raw JSON response
 * Clear previous response before showing new one
 */
function displayRawResponse(data, status, responseTime) {
    const rawResponse = document.getElementById('rawResponse');
    if (!rawResponse) return;
    
    // Clear previous content
    rawResponse.innerHTML = '';
    
    // Create status line
    const statusDiv = document.createElement('div');
    statusDiv.style.marginBottom = '10px';
    statusDiv.style.color = '#4ec9b0';
    statusDiv.textContent = `Status: ${status} | Response Time: ${responseTime}ms`;
    rawResponse.appendChild(statusDiv);
    
    // Create JSON display
    const preEl = document.createElement('pre');
    preEl.textContent = JSON.stringify(data, null, 2);
    rawResponse.appendChild(preEl);
    
    console.log('📝 Raw response displayed');
}

/**
 * Extract results from various API response formats
 */
function extractResults(data) {
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data.results)) return data.results;
    if (data && data.data && Array.isArray(data.data.results)) return data.data.results;
    if (data && data.data && Array.isArray(data.data)) return data.data;
    return [];
}



function showLoading(isLoading) {
    const loader = document.getElementById('loadingIndicator');
    if (!loader) return;
    if (isLoading) loader.classList.remove('hidden');
    else loader.classList.add('hidden');
}

function showError(message) {
    console.error(`⚠️ Error message: ${message}`);
    const err = document.getElementById('errorMessage');
    if (!err) {
        console.error('❌ Error message element not found');
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

/**
 * Clear all previous results and error states
 * This is called BEFORE each new search to ensure clean state
 */
function clearAllResults() {
    console.log('🧹 Clearing previous results and errors...');
    
    // Clear error message
    const errEl = document.getElementById('errorMessage');
    if (errEl) {
        errEl.textContent = '';
        errEl.classList.add('hidden');
    }
    
    // Clear no results message
    const noResultsEl = document.getElementById('noResults');
    if (noResultsEl) {
        noResultsEl.classList.add('hidden');
    }
    
    // Hide and clear results table
    const tblEl = document.getElementById('resultsTable');
    if (tblEl) {
        tblEl.classList.add('hidden');
    }
    
    // Clear table body rows
    const bodyEl = document.getElementById('resultsBody');
    if (bodyEl) {
        bodyEl.innerHTML = '';
    }
    
    // Clear debug info
    const debugEl = document.getElementById('apiDebugInfo');
    if (debugEl) {
        debugEl.innerHTML = '<p class="placeholder">Making request...</p>';
    }
    
    // Clear raw response
    const rawEl = document.getElementById('rawResponse');
    if (rawEl) {
        rawEl.innerHTML = '<pre class="placeholder">Waiting for response...</pre>';
    }
    
    // Clear endpoint display
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

