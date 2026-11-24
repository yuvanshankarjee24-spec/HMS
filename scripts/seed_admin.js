/**
 * Standalone Admin Seed Script for Smart Hostel Management System
 * Reads ADMIN_USERNAME and ADMIN_PASSWORD from .env and verifies/displays
 * seed credentials.
 * The Spring Boot backend automatically runs AdminSeedRunner on startup
 * to synchronize these exact credentials into the database.
 */

const fs = require('fs');
const path = require('path');

const envPath = path.resolve(__dirname, '..', '.env');

function loadEnv() {
  if (!fs.existsSync(envPath)) {
    console.warn('[WARN] .env file not found at:', envPath);
    return {};
  }
  const lines = fs.readFileSync(envPath, 'utf-8').split(/\r?\n/);
  const env = {};
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx !== -1) {
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      env[key] = val;
    }
  }
  return env;
}

const env = loadEnv();
const adminUsername = env.ADMIN_USERNAME || '1234567890';
const adminPassword = env.ADMIN_PASSWORD || '1234567890';

console.log('==============================================');
console.log('Smart Hostel Management - Admin Seed Configuration');
console.log('==============================================');
console.log(`ADMIN_USERNAME : ${adminUsername}`);
console.log(`ADMIN_PASSWORD : ${adminPassword}`);
console.log(`Target Role    : ADMIN`);
console.log(`Status         : ACTIVE (0 failed attempts, unlocked)`);
console.log(`First-login PWD: No forced change required (exempt)`);
console.log('----------------------------------------------');
console.log('AdminSeedRunner in Spring Boot will apply these credentials to the database on boot.');
console.log('==============================================\n');
