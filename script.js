class MedicineInventory {
    constructor() {
        this.medicines = JSON.parse(localStorage.getItem('medicines')) || [];
        this.init();
    }

    init() {
        this.bindEvents();
        this.renderTable();
        this.updateStats();
        this.checkLowStock();
    }

    bindEvents() {
        
        document.getElementById('medicineForm').addEventListener('submit', (e) => this.addMedicine(e));
        
        
        document.getElementById('searchInput').addEventListener('input', () => this.filterMedicines());
        
        
        document.getElementById('filterSelect').addEventListener('change', () => this.filterMedicines());
        
        
        document.getElementById('exportBtn').addEventListener('click', () => this.exportToCSV());
        
        
        document.getElementById('clearBtn').addEventListener('click', () => this.clearAll());
        
        
        document.getElementById('closeAlert').addEventListener('click', () => this.closeAlertModal());
    }

    addMedicine(e) {
        e.preventDefault();
        
        const medicine = {
            id: Date.now(),
            name: document.getElementById('medicineName').value.trim(),
            batch: document.getElementById('batchNumber').value.trim(),
            quantity: parseInt(document.getElementById('quantity').value),
            price: parseFloat(document.getElementById('price').value),
            expiry: document.getElementById('expiryDate').value,
            minStock: parseInt(document.getElementById('minStock').value),
            addedDate: new Date().toISOString()
        };

        this.medicines.unshift(medicine);
        this.saveData();
        this.renderTable();
        this.updateStats();
        this.checkLowStock();
        
        
        document.getElementById('medicineForm').reset();
        this.showNotification('Medicine added successfully!', 'success');
    }

    deleteMedicine(id) {
        if (confirm('Are you sure you want to delete this medicine?')) {
            this.medicines = this.medicines.filter(medicine => medicine.id !== id);
            this.saveData();
            this.renderTable();
            this.updateStats();
            this.checkLowStock();
            this.showNotification('Medicine deleted successfully!', 'success');
        }
    }

    updateQuantity(id, newQuantity) {
        const medicine = this.medicines.find(m => m.id === id);
        if (medicine) {
            medicine.quantity = parseInt(newQuantity);
            this.saveData();
            this.renderTable();
            this.updateStats();
            this.checkLowStock();
            this.showNotification('Quantity updated successfully!', 'success');
        }
    }

    getStatus(medicine) {
        const today = new Date();
        const expiry = new Date(medicine.expiry);
        const daysUntilExpiry = Math.ceil((expiry - today) / (1000 * 60 * 60 * 24));

        if (daysUntilExpiry < 0) return { text: 'Expired', class: 'expired' };
        if (daysUntilExpiry <= 30) return { text: 'Expiring', class: 'expiring' };
        if (medicine.quantity <= medicine.minStock) return { text: 'Low', class: 'low' };
        return { text: 'Good', class: 'good' };
    }

    renderTable(filteredMedicines = this.medicines) {
        const tbody = document.getElementById('medicinesBody');
        tbody.innerHTML = '';

        filteredMedicines.forEach(medicine => {
            const status = this.getStatus(medicine);
            const row = `
                <tr>
                    <td><strong>${medicine.name}</strong></td>
                    <td>${medicine.batch}</td>
                    <td>
                        <input type="number" value="${medicine.quantity}" min="0" 
                               style="width: 80px; padding: 5px; border: 1px solid #ddd; border-radius: 5px;"
                               onchange="inventory.updateQuantity(${medicine.id}, this.value)">
                    </td>
                    <td>$${medicine.price.toFixed(2)}</td>
                    <td>${new Date(medicine.expiry).toLocaleDateString()}</td>
                    <td><span class="status ${status.class}">${status.text}</span></td>
                    <td>
                        <button class="action-btn edit-btn" onclick="inventory.editMedicine(${medicine.id})" title="Edit">
                            <i class="fas fa-edit"></i>
                        </button>
                        <button class="action-btn delete-btn" onclick="inventory.deleteMedicine(${medicine.id})" title="Delete">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                </tr>
            `;
            tbody.innerHTML += row;
        });
    }

    filterMedicines() {
        const searchTerm = document.getElementById('searchInput').value.toLowerCase();
        const filter = document.getElementById('filterSelect').value;

        let filtered = this.medicines.filter(medicine => 
            medicine.name.toLowerCase().includes(searchTerm) || 
            medicine.batch.toLowerCase().includes(searchTerm)
        );

        if (filter === 'low') {
            filtered = filtered.filter(m => m.quantity <= m.minStock);
        } else if (filter === 'expiring') {
            const today = new Date();
            filtered = filtered.filter(m => {
                const expiry = new Date(m.expiry);
                return (expiry - today) / (1000 * 60 * 60 * 24) <= 30;
            });
        } else if (filter === 'expired') {
            filtered = filtered.filter(m => new Date(m.expiry) < new Date());
        }

        this.renderTable(filtered);
    }

    updateStats() {
        const total = this.medicines.length;
        const lowStock = this.medicines.filter(m => m.quantity <= m.minStock).length;
        const expiring = this.medicines.filter(m => {
            const days = (new Date(m.expiry) - new Date()) / (1000 * 60 * 60 * 24);
            return days <= 30 && days >= 0;
        }).length;

        document.getElementById('totalMedicines').textContent = total;
        document.getElementById('lowStock').textContent = lowStock;
        document.getElementById('expiringSoon').textContent = expiring;
    }

    checkLowStock() {
        const lowStockItems = this.medicines.filter(m => m.quantity <= m.minStock);
        if (lowStockItems.length > 0) {
            this.showLowStockAlert(lowStockItems);
        }
    }

    showLowStockAlert(items) {
        const modal = document.getElementById('alertModal');
        const list = document.getElementById('lowStockList');
        
        list.innerHTML = items.map(item => 
            `<li><strong>${item.name}</strong> - ${item.quantity}/${item.minStock} left</li>`
        ).join('');
        
        modal.style.display = 'block';
    }

    closeAlertModal() {
        document.getElementById('alertModal').style.display = 'none';
    }

    exportToCSV() {
        const headers = ['Name', 'Batch', 'Quantity', 'Price', 'Expiry', 'Min Stock', 'Status'];
        const csvContent = [
            headers.join(','),
            ...this.medicines.map(m => {
                const status = this.getStatus(m);
                return [
                    `"${m.name}"`,
                    m.batch,
                    m.quantity,
                    m.price,
                    m.expiry,
                    m.minStock,
                    status.text
                ].join(',');
            })
        ].join('\n');

        const blob = new Blob([csvContent], { type: 'text/csv' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `medicine_inventory_${new Date().toISOString().split('T')[0]}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
        
        this.showNotification('Inventory exported successfully!', 'success');
    }

    clearAll() {
        if (confirm('Are you sure you want to delete ALL medicines? This action cannot be undone!')) {
            this.medicines = [];
            this.saveData();
            this.renderTable();
            this.updateStats();
            this.showNotification('All medicines cleared!', 'warning');
        }
    }

    saveData() {
        localStorage.setItem('medicines', JSON.stringify(this.medicines));
    }

    editMedicine(id) {
        const medicine = this.medicines.find(m => m.id === id);
        if (medicine) {
            document.getElementById('medicineName').value = medicine.name;
            document.getElementById('batchNumber').value = medicine.batch;
            document.getElementById('quantity').value = medicine.quantity;
            document.getElementById('price').value = medicine.price;
            document.getElementById('expiryDate').value = medicine.expiry;
            document.getElementById('minStock').value = medicine.minStock;
            
            
            setTimeout(() => {
                document.getElementById('medicineForm').dispatchEvent(new Event('submit'));
            }, 100);
            
            this.deleteMedicine(id); 
        }
    }

    showNotification(message, type = 'success') {

        const notification = document.createElement('div');
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            border-radius: 10px;
            color: white;
            font-weight: 600;
            z-index: 10000;
            background: ${type === 'success' ? '#00b894' : '#fdcb6e'};
            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
            animation: slideIn 0.3s ease;
        `;
        notification.textContent = message;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.remove();
        }, 3000);
    }
}


const inventory = new MedicineInventory();

if (inventory.medicines.length === 0) {
    const sampleMedicines = [
        {
            id: 1,
            name: "Paracetamol 500mg",
            batch: "PAR2025A",
            quantity: 85,
            price: 2.50,
            expiry: "2026-06-15",
            minStock: 20,
            addedDate: new Date().toISOString()
        },
        {
            id: 2,
            name: "Amoxicillin 500mg",
            batch: "AMX2025B",
            quantity: 12,
            price: 8.75,
            expiry: "2026-03-20",
            minStock: 15,
            addedDate: new Date().toISOString()
        },
        {
            id: 3,
            name: "Ibuprofen 400mg",
            batch: "IBU2025C",
            quantity: 150,
            price: 3.20,
            expiry: "2025-12-10",
            minStock: 30,
            addedDate: new Date().toISOString()
        }
    ];
    inventory.medicines = sampleMedicines;
    inventory.saveData();
    inventory.renderTable();
    inventory.updateStats();
}


window.onclick = function(event) {
    const modal = document.getElementById('alertModal');
    if (event.target === modal) {
        modal.style.display = 'none';
    }
}


const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from { transform: translateX(100%); opacity: 0; }
        to { transform: translateX(0); opacity: 1; }
    }
`;
document.head.appendChild(style);