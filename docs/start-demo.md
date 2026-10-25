# Start the seeded application

Run PowerShell from the repository root. Existing demo data is retained in
`inventory_walkthrough` and its four configured inventory shard databases.

1. Services first:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1 -Part services
   ```

   This reuses MongoDB on 27019 if it is running, otherwise starts the isolated
   replica set. It starts Memurai/Redis on 6379 if necessary. If Windows denies
   service-start permissions, run `Start-Service Memurai` in Administrator
   PowerShell, then retry. The standalone MongoDB service on 27017 is not used.

2. Backend, in a terminal kept open:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1 -Part backend
   ```

   Wait for Spring Boot to report it has started. The API is on
   `http://127.0.0.1:8081`. The script selects the existing seeded core and shard
   databases and cache namespace. Normal demo traffic has rate limiting disabled;
   the separate benchmark runner still tests the limiter on/off.

3. Frontend, in another terminal kept open:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/start-demo.ps1 -Part frontend
   ```

   Open **http://localhost:3000**. The script points frontend requests at 8081,
   avoiding the Vite configuration's default proxy to 8080.

Stop the frontend and backend using Ctrl+C in their respective terminals. These
commands do not reset or reseed data. If seeding a fresh instance is needed:

```powershell
python scripts/seed-demo.py --base-url http://127.0.0.1:8081
```

The seed is repeatable and preserves completed demo actions and user-edited
descriptions. See [the demo guide](../evaluation/demo/README.md) for records and
ready-to-show operations.
