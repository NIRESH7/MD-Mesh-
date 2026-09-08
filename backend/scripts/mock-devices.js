/**
 * Registers sample devices so the admin console can be exercised without an APK.
 * Usage: node scripts/mock-devices.js
 */
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../../.env') });
require('dotenv').config({ path: path.join(__dirname, '../.env') });

const deviceService = require('../src/services/deviceService');

const samples = [
  {
    unique_id: 'DEMO-TAB-001',
    device_name: 'Warehouse Tab 1',
    device_model: 'Samsung Galaxy Tab A9',
    ip_address: '192.168.1.21',
    installed_apps: [
      { app_name: 'WhatsApp', app_package: 'com.whatsapp', is_system_app: 0 },
      { app_name: 'Chrome', app_package: 'com.android.chrome', is_system_app: 0 },
      { app_name: 'Gmail', app_package: 'com.google.android.gm', is_system_app: 0 },
      { app_name: 'Settings', app_package: 'com.android.settings', is_system_app: 1 }
    ]
  },
  {
    unique_id: 'DEMO-PHONE-002',
    device_name: 'Floor Phone 2',
    device_model: 'Pixel 7',
    ip_address: '192.168.1.34',
    installed_apps: [
      { app_name: 'WhatsApp', app_package: 'com.whatsapp', is_system_app: 0 },
      { app_name: 'Instagram', app_package: 'com.instagram.android', is_system_app: 0 },
      { app_name: 'Maps', app_package: 'com.google.android.apps.maps', is_system_app: 0 }
    ]
  },
  {
    unique_id: 'DEMO-TAB-003',
    device_name: 'Reception Tab',
    device_model: 'Lenovo Tab M10',
    ip_address: '192.168.1.40',
    installed_apps: [
      { app_name: 'Chrome', app_package: 'com.android.chrome', is_system_app: 0 },
      { app_name: 'Files', app_package: 'com.google.android.documentsui', is_system_app: 1 }
    ]
  }
];

async function run() {
  for (const sample of samples) {
    const result = await deviceService.syncDevice(sample);
    console.log(`${sample.device_name} -> device_id ${result.device_id}`);
  }
  process.exit(0);
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
