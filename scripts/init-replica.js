// Executed inside the Compose network; this hostname must be reachable by backend.
try {
  rs.status();
} catch (error) {
  if (error.code !== 94) throw error;
  rs.initiate({ _id: 'inventory-rs', members: [{ _id: 0, host: 'mongodb:27017' }] });
}
let writable = false;
for (let attempt = 0; attempt < 60; attempt++) {
  if (db.hello().isWritablePrimary) { writable = true; break; }
  sleep(500);
}
if (!writable) throw new Error('MongoDB replica set did not elect a primary');
