export type Rol = "SUPER_ADMIN" | "ADMINISTRADOR_COMERCIAL" | "TRABAJADOR";

export type Me = {
  id: number;
  email: string;
  full_name: string;
  role: Rol;
  organization_id: number | null;
  organization_name: string | null;
};

export type Category = { id: number; name: string };
export type Unidad = "UND" | "M" | "M2" | "KG" | "ROLLO" | "L";
export type Product = {
  id: number;
  sku: string;
  barcode: string | null;
  name: string;
  category_id: number | null;
  unit: Unidad;
  min_stock: string;
  active: boolean;
};
export type Warehouse = { id: number; code: string; name: string; active: boolean };
export type StockRow = {
  product_id: number;
  sku: string;
  product_name: string;
  unit: string;
  warehouse_id: number;
  warehouse_code: string;
  quantity: string;
  min_stock: string;
  below_min: boolean;
  avg_cost: string;
  total_value: string;
};
export type MovementKind = "ENTRADA" | "SALIDA" | "AJUSTE" | "REVERSION";
export type Movement = {
  id: number;
  kind: MovementKind;
  product_id: number;
  warehouse_id: number;
  user_id: number;
  quantity: string;
  unit_cost: string | null;
  balance_after: string;
  avg_cost_after: string;
  reference: string | null;
  notes: string | null;
  reverses_id: number | null;
  created_at: string;
};
export type UserRow = {
  id: number;
  email: string;
  full_name: string;
  role: Rol;
  active: boolean;
  organization_id: number | null;
};
export type Organization = { id: number; name: string; slug: string; active: boolean; created_at: string };

export const ROL_LABEL: Record<Rol, string> = {
  SUPER_ADMIN: "Super administrador",
  ADMINISTRADOR_COMERCIAL: "Administrador comercial",
  TRABAJADOR: "Trabajador",
};
