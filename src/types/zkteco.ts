export interface ZktecoDevice {
  id?: string;
  serial_no: string;
  device_name?: string | null;
  location?: string | null;
  enabled?: boolean | null;
  last_ip?: string | null;
  last_seen_at?: string | null;
  last_event_at?: string | null;
  last_push_table?: string | null;
  raw_options?: Record<string, unknown> | null;
  created_at?: string | null;
  updated_at?: string | null;
}

export interface ZktecoFingerprintEvent {
  id: string;
  event_uid: string;
  employee_pin: string;
  employee_code?: string | null;
  employee_id?: string | null;
  matched_employee_name?: string | null;
  matched_department?: string | null;
  match_status: "matched" | "unmatched";
  device_serial_no?: string | null;
  device_ip?: string | null;
  event_time: string;
  attendance_date: string;
  punch_time: string;
  verify_mode?: string | null;
  in_out_mode?: string | null;
  work_code?: string | null;
  reserved_fields?: string[] | null;
  raw_line: string;
  raw_payload?: Record<string, unknown> | null;
  received_at: string;
  created_at?: string | null;
}

export interface ZktecoStatus {
  devices: ZktecoDevice[];
  deviceCount: number;
  serverTime: string;
  mode: "adms_push" | string;
}
