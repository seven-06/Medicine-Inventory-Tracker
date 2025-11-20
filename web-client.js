// Web UI client that uses MedicineAPI (Supabase-backed) to store medicines
// Assumes `supabase-config.js` and `medicine.js` (MedicineAPI) are loaded first

async function initApp() {
    const API_BASE = 'http://localhost:8001/api/medicines';
    try {
        const res = await fetch(API_BASE);
        window.medicines = await res.json();
    } catch (e) {
        console.error('Failed to load medicines from backend, falling back to empty list.', e);
        window.medicines = [];
    }
    renderTable();
    updateStats();
    checkLowStock();

    // wire buttons already present in index.html
    document.getElementById('search').addEventListener('input', filterTable);
    document.getElementById('filter').addEventListener('change', filterTable);
}

function toClientModel(row) {
    // Supabase returns columns that should match the expected fields.
    // Ensure we have consistent keys: id, name, batch, quantity, price, expiry, minStock
    return {
        id: row.id,
        name: row.name,
        batch: row.batch,
        qty: row.quantity ?? row.qty ?? 0,
        price: parseFloat(row.price ?? 0),
        expiry: row.expiry,
        minStock: row.min_stock ?? row.minStock ?? row.minstock ?? 0
    };
}

async function addMedicine() {
    const medicine = {
        // DB column names expected: name, batch, quantity, price, expiry, min_stock
        name: document.getElementById('name').value.trim(),
        batch: document.getElementById('batch').value.trim(),
        quantity: parseInt(document.getElementById('qty').value),
        price: parseFloat(document.getElementById('price').value),
        expiry: document.getElementById('expiry').value,
        min_stock: parseInt(document.getElementById('minStock').value)
    };

    if (!medicine.name || !medicine.batch || !medicine.expiry || !medicine.quantity || !medicine.price || !medicine.min_stock) {
        showNotification('Please fill all fields!', 'error');
        return;
    }

    try {
    const r = await fetch(API_BASE, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(medicine) });
        if (r.status === 201) {
            const created = await r.json();
            window.medicines.unshift(toClientModel(created));
        } else {
            // fallback refresh
            const list = await (await fetch('/api/medicines')).json();
            window.medicines = list;
        }
        renderTable();
        updateStats();
        checkLowStock();
        document.querySelectorAll('.form-grid input').forEach(i => i.value = '');
        showNotification('Medicine added!', 'success');
    } catch (err) {
        showNotification('Failed to add medicine. See console.', 'error');
    }
}

function getStatus(med) {
    const days = Math.ceil((new Date(med.expiry) - new Date()) / (1000 * 60 * 60 * 24));
    if (days < 0) return {text: 'Expired', class: 'expired'};
    if (days <= 30) return {text: 'Expiring', class: 'expiring'};
    if (med.qty <= med.minStock) return {text: 'Low', class: 'low'};
    return {text: 'Good', class: 'good'};
}

function renderTable(filtered = window.medicines) {
    const tbody = document.getElementById('tbody');
    tbody.innerHTML = (filtered || []).map(med => {
        const status = getStatus(med);
        return `<tr>
                    <td><strong>${escapeHtml(med.name)}</strong></td>
                    <td>${escapeHtml(med.batch)}</td>
                    <td><input type="number" value="${med.qty}" min="0" style="width:60px;padding:4px;border:1px solid #ddd;border-radius:4px" onchange="updateQty(${med.id}, this.value)"></td>
                    <td>$${(med.price||0).toFixed(2)}</td>
                    <td>${new Date(med.expiry).toLocaleDateString()}</td>
                    <td><span class="status ${status.class}">${status.text}</span></td>
                    <td>
                        <button class="action-btn edit" onclick="editMedicine(${med.id})"><i class="fas fa-edit"></i></button>
                        <button class="action-btn delete" onclick="deleteMedicine(${med.id})"><i class="fas fa-trash"></i></button>
                    </td>
                </tr>`;
    }).join('');
}

function escapeHtml(str){ return String(str||'').replace(/[&<>\"']/g, (c)=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":"&#39;" })[c]); }

async function updateQty(id, newQty) {
    try {
    await fetch(API_BASE, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: id, quantity: parseInt(newQty) }) });
        // optimistic update in local array
        const med = window.medicines.find(m => m.id === id);
        if (med) med.qty = parseInt(newQty);
        renderTable();
        updateStats();
        checkLowStock();
        showNotification('Quantity updated!', 'success');
    } catch (err) {
        showNotification('Failed to update quantity.', 'error');
    }
}

async function deleteMedicine(id) {
    if (!confirm('Delete this medicine?')) return;
    try {
    await fetch(API_BASE + '?id=' + encodeURIComponent(id), { method: 'DELETE' });
        window.medicines = window.medicines.filter(m => m.id !== id);
        renderTable();
        updateStats();
        checkLowStock();
        showNotification('Medicine deleted!', 'success');
    } catch (err) {
        showNotification('Failed to delete medicine.', 'error');
    }
}

function editMedicine(id) {
    const med = window.medicines.find(m => m.id === id);
    if (!med) return;
    document.getElementById('name').value = med.name;
    document.getElementById('batch').value = med.batch;
    document.getElementById('qty').value = med.qty;
    document.getElementById('price').value = med.price;
    document.getElementById('expiry').value = med.expiry;
    document.getElementById('minStock').value = med.minStock;
    // We'll delete existing DB row so adding will create/replace
    // Alternatively, user can change and then save will create a new record and we can remove the old one
    deleteMedicine(id);
}

function filterTable() {
    const search = document.getElementById('search').value.toLowerCase();
    const filter = document.getElementById('filter').value;
    let filtered = window.medicines.filter(m => 
        (m.name||'').toLowerCase().includes(search) || (m.batch||'').toLowerCase().includes(search)
    );
    if (filter === 'low') filtered = filtered.filter(m => m.qty <= m.minStock);
    else if (filter === 'expiring') filtered = filtered.filter(m => {
        const days = (new Date(m.expiry) - new Date()) / (1000 * 60 * 60 * 24);
        return days <= 30 && days >= 0;
    });
    else if (filter === 'expired') filtered = filtered.filter(m => new Date(m.expiry) < new Date());
    renderTable(filtered);
}

function updateStats() {
    document.getElementById('total').textContent = window.medicines.length;
    document.getElementById('lowStock').textContent = window.medicines.filter(m => m.qty <= m.minStock).length;
    document.getElementById('expiring').textContent = window.medicines.filter(m => {
        const days = (new Date(m.expiry) - new Date()) / (1000 * 60 * 60 * 24);
        return days <= 30 && days >= 0;
    }).length;
}

function checkLowStock() {
    const lowStock = window.medicines.filter(m => m.qty <= m.minStock);
    if (lowStock.length > 0) {
        const list = document.getElementById('lowStockList');
        list.innerHTML = lowStock.map(m => `<li><strong>${escapeHtml(m.name)}</strong> - ${m.qty}/${m.minStock} left</li>`).join('');
        document.getElementById('alertModal').style.display = 'block';
    }
}

function exportCSV() {
    const headers = ['Name', 'Batch', 'Quantity', 'Price', 'Expiry', 'Min Stock', 'Status'];
    const csv = [
        headers.join(','),
        ...window.medicines.map(m => [
            `"${m.name}"`,
            m.batch,
            m.qty,
            m.price,
            m.expiry,
            m.minStock,
            getStatus(m).text
        ].join(','))
    ].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `inventory_${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
    showNotification('Exported to CSV!', 'success');
}

function clearAll() {
    if (!confirm('Delete ALL medicines?')) return;
    // Delete each row via backend
    const deletes = (window.medicines || []).map(m => fetch(API_BASE + '?id=' + encodeURIComponent(m.id), { method: 'DELETE' }));
    Promise.all(deletes).then(() => {
        window.medicines = [];
        renderTable();
        updateStats();
        showNotification('All medicines cleared!', 'success');
    }).catch(err => {
        console.error(err);
        showNotification('Failed to clear all medicines.', 'error');
    });
}

function showNotification(message, type) {
    const div = document.createElement('div');
    div.style.cssText = `
        position: fixed; top: 20px; right: 20px; padding: 15px; border-radius: 8px;
        color: white; font-weight: 600; z-index: 1000;
        background: ${type === 'success' ? '#00b894' : '#ff6b6b'};
        animation: slideIn 0.3s ease;
    `;
    div.textContent = message;
    document.body.appendChild(div);
    setTimeout(() => div.remove(), 3000);
}

// Initialize once DOM is ready
if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initApp);
else initApp();
