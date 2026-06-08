# Hikvision ISAPI Face Recognition Integration

This project includes a backend bridge and frontend page for Hikvision face-recognition terminals such as DS-K1T343EWX / DS-KIT343EWX.

## Runtime Flow

1. The browser calls the Spring backend.
2. The Spring backend connects to the camera over the local network using ISAPI digest authentication.
3. Connection testing uses:
   - `GET /ISAPI/System/deviceInfo`
4. Live recognition sync polls recent access-control events through:
   - `POST /ISAPI/AccessControl/AcsEvent?format=json`
5. Returned `employeeNoString` values are matched against `public.employees.employee_code`.
6. The frontend page displays live matched and unmatched recognition events.
7. New events are also upserted into `public.hikvision_face_events` when the Supabase migration has been applied.

## Local Test Setup

Set the backend environment values:

```env
HIKVISION_BASE_URL=http://192.168.1.64
HIKVISION_USERNAME=admin
HIKVISION_PASSWORD=your-camera-password
HIKVISION_POLL_INTERVAL_SECONDS=3
HIKVISION_LOOKBACK_MINUTES=60
```

Set the frontend backend URL:

```env
VITE_BACKEND_URL=http://localhost:8080
```

Start the backend and frontend, then open:

```text
/hikvision-face
```

Use **Save & Test** first. If the camera responds, use **Start Live** to begin polling recognition events.

## Hosted Backend Setup

When the backend is deployed outside the factory LAN, run the local bridge on a factory computer:

```bash
cd backend
python3 -m venv .venv-lan-bridges
.venv-lan-bridges/bin/pip install -r requirements-lan-bridges.txt
HIKVISION_BRIDGE_BACKEND_URL=https://your-render-backend.onrender.com \
HIKVISION_BRIDGE_TOKEN=change-this-long-random-token \
HIKVISION_USERNAME=admin \
HIKVISION_PASSWORD=your-camera-password \
.venv-lan-bridges/bin/python scripts/hikvision_lan_bridge.py --interval 3 --lookback-minutes 60
```

Set the same `HIKVISION_BRIDGE_TOKEN` value on Render. The backend accepts bridge pushes at:

```text
POST /api/hikvision/bridge/events
```

## Notes

- Camera and backend machine must be on the same reachable network.
- If the backend is deployed on Render, private camera URLs such as `http://10.10.4.101` are not reachable unless you add VPN/reverse-tunnel connectivity or run a local Hikvision bridge.
- HTTP is usually simpler for first local testing. HTTPS may require trusted camera certificates.
- The camera should have access-control event storage enabled and face/person records configured.
- Camera passwords are kept in backend memory when configured from the UI. Use environment variables for repeatable deployments.
- Apply `supabase/migrations/20260516000100_hikvision_face_events.sql` to persist camera recognition history.
