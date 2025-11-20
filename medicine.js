// Supabase-based API client for medicines
// Requires `supabase-config.js` (global constants: SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_TABLE)

class MedicineAPI {
    static get base() {
        if (typeof SUPABASE_URL === 'undefined' || typeof SUPABASE_ANON_KEY === 'undefined') {
            console.warn('Supabase config not found. Please fill supabase-config.js');
        }
        return `${SUPABASE_URL}/rest/v1`;
    }

    static headers() {
        return {
            'Content-Type': 'application/json',
            'apikey': SUPABASE_ANON_KEY,
            'Authorization': `Bearer ${SUPABASE_ANON_KEY}`
        };
    }

    static async request(path = '', method = 'GET', data, preferReturn = false) {
        const url = `${this.base}/${SUPABASE_TABLE}${path ? path : ''}`;
        const opts = { method, headers: this.headers() };
        if (data) opts.body = JSON.stringify(data);
        if (preferReturn) opts.headers = { ...opts.headers, 'Prefer': 'return=representation' };

        try {
            const res = await fetch(url, opts);
            const text = await res.text();
            let json = null;
            try { json = text ? JSON.parse(text) : null; } catch (e) { json = text; }
            if (!res.ok) {
                console.error('Supabase error', res.status, json);
                throw new Error(json && json.message ? json.message : `HTTP ${res.status}`);
            }
            return json;
        } catch (err) {
            console.error(`Error ${method} ${path}:`, err);
            throw err;
        }
    }

    // CRUD
    static getAllMedicines()        { return this.request('?select=*', 'GET'); }
    static getMedicineById(id)      { return this.request(`?select=*&id=eq.${id}`, 'GET'); }
    static addMedicine(data)        { return this.request('', 'POST', data, true); }
    static updateMedicine(id, data) { return this.request(`?id=eq.${id}`, 'PATCH', data, true); }
    static deleteMedicine(id)       { return this.request(`?id=eq.${id}`, 'DELETE', null, false); }

    // convenience
    static async updateStock(id, qty) {
        return this.updateMedicine(id, { quantity: qty });
    }

    static async getExpiringMedicines() {
        // expiring in next 30 days and not expired
        const now = new Date().toISOString();
        const future = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString();
        const filter = `?select=*&expiry=gte.${now}&expiry=lte.${future}`;
        return this.request(filter, 'GET');
    }
}

// Export for Node/CommonJS if used in tests
if (typeof module !== 'undefined' && module.exports)
    module.exports = { MedicineAPI };
