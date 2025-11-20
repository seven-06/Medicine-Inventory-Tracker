-- Supabase / Postgres schema for medicine inventory
-- Run these in the SQL editor in Supabase (or psql) to create the table.

-- Enable pgcrypto for gen_random_uuid() (if not already enabled)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Create medicines table
CREATE TABLE IF NOT EXISTS public.medicines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    batch text,
    quantity integer NOT NULL DEFAULT 0,
    price numeric(10,2) NOT NULL DEFAULT 0.00,
    expiry date,
    min_stock integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Optional: index on expiry for fast queries on expiring medicines
CREATE INDEX IF NOT EXISTS idx_medicines_expiry ON public.medicines (expiry);

-- Optional: unique constraint if you want to prevent duplicate batch entries for the same medicine
-- ALTER TABLE public.medicines ADD CONSTRAINT uq_medicine_name_batch UNIQUE (name, batch);

-- Trigger to update updated_at timestamp on row modification
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_set_updated_at ON public.medicines;
CREATE TRIGGER trg_set_updated_at
BEFORE UPDATE ON public.medicines
FOR EACH ROW
EXECUTE PROCEDURE public.set_updated_at();

-- Example: test insert (uncomment to run)
-- INSERT INTO public.medicines (name, batch, quantity, price, expiry, min_stock) VALUES
-- ('Paracetamol 500mg', 'PAR2025A', 85, 2.50, '2026-06-15', 20);
