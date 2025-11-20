import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class Medicine {
    private String name;
    private int quantity;
    private LocalDate expiryDate;

    public Medicine(String name, int quantity, LocalDate expiryDate) {
        this.name = name;
        this.quantity = quantity;
        this.expiryDate = expiryDate;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isInStock() {
        return quantity > 0;
    }

    public boolean isExpired() {
        return LocalDate.now().isAfter(expiryDate);
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    @Override
    public String toString() {
        return name + " | Qty: " + quantity + " | Expiry: " + expiryDate +
                " | " + (isExpired() ? "Expired" : "Valid");
    }
}

class MedicineInventory {
    private List<Medicine> medicines = new ArrayList<>();

    public void addMedicine(Medicine med) {
        medicines.add(med);
    }

    public void listMedicines() {
        for (Medicine med : medicines) {
            System.out.println(med);
        }
    }

    public void showExpiredMedicines() {
        System.out.println("Expired Medicines:");
        for (Medicine med : medicines) {
            if (med.isExpired()) {
                System.out.println(med);
            }
        }
    }

    public void showInStockMedicines() {
        System.out.println("Medicines In Stock:");
        for (Medicine med : medicines) {
            if (med.isInStock()) {
                System.out.println(med);
            }
        }
    }
}

public class MedicineTrackerApp {
    // Default Supabase credentials
    private static final String DEFAULT_SUPABASE_URL = "https://zprmdczzcvzpxibhtezp.supabase.co";
    private static final String DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inpwcm1kY3p6Y3Z6cHhpYmh0ZXpwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc2MTM3NjExMywiZXhwIjoyMDc2OTUyMTEzfQ.QrtKnR_qmTNG_vLdM59jhkdM7mLVOgEYeB0LMdfpcBM";

    public static void main(String[] args) {
        MedicineInventory inventory = new MedicineInventory();
        Scanner scanner = new Scanner(System.in);

        // Use environment variables if set, otherwise use defaults
        String supabaseUrl = System.getenv("SUPABASE_URL");
        String supabaseKey = System.getenv("SUPABASE_SERVICE_KEY");
        
        // Set default values if not provided in environment
        if (supabaseUrl == null || supabaseUrl.trim().isEmpty()) {
            supabaseUrl = DEFAULT_SUPABASE_URL;
        }
        if (supabaseKey == null || supabaseKey.trim().isEmpty()) {
            supabaseKey = DEFAULT_SUPABASE_KEY;
        }
        
        SupabaseClient supabase = new SupabaseClient(supabaseUrl, supabaseKey, "medicines");
        System.out.println("Connected to Supabase database.");

        System.out.println("Welcome to Medicine Inventory Tracker!");

        while (true) {
            System.out.println("\nEnter medicine details:");

            System.out.print("Name (or type 'exit' to finish): ");
            String name = scanner.nextLine();
            if (name.equalsIgnoreCase("exit")) {
                break;
            }

            int quantity = 0;
            while (true) {
                System.out.print("Quantity: ");
                String qtyInput = scanner.nextLine();
                try {
                    quantity = Integer.parseInt(qtyInput);
                    if (quantity < 0) {
                        System.out.println("Quantity cannot be negative. Try again.");
                        continue;
                    }
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number. Try again.");
                }
            }

            LocalDate expiryDate = null;
            while (true) {
                System.out.print("Expiry date (YYYY-MM-DD): ");
                String expiryInput = scanner.nextLine();
                try {
                    expiryDate = LocalDate.parse(expiryInput);
                    break;
                } catch (DateTimeParseException e) {
                    System.out.println("Invalid date format. Try again.");
                }
            }

            Medicine med = new Medicine(name, quantity, expiryDate);
            inventory.addMedicine(med);
            System.out.println("Medicine added successfully.");
            if (supabase != null) {
                try {
                    boolean ok = supabase.addMedicine(med.getName(), med.getQuantity(), med.getExpiryDate().toString(), 0.0, 0);
                    System.out.println(ok ? "Stored to Supabase." : "Supabase store failed.");
                } catch (Exception e) {
                    System.out.println("Supabase write failed: " + e.getMessage());
                }
            }
        }

        System.out.println("\nAll Medicines:");
        if (supabase != null) {
            try {
                String json = supabase.getAllMedicinesRaw();
                System.out.println("Supabase records (raw JSON):\n" + json);
            } catch (Exception e) {
                System.out.println("Failed to fetch from Supabase: " + e.getMessage());
                inventory.listMedicines();
            }
        } else {
            inventory.listMedicines();
        }

        inventory.showExpiredMedicines();
        inventory.showInStockMedicines();

        scanner.close();
    }
}
