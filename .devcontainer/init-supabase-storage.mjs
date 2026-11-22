import { createHmac } from 'node:crypto';

// Development bootstrap only: create the private bucket through Storage's API.
const base = process.env.STORAGE_API_URL;
const bucket = process.env.STORAGE_BUCKET;
const jwtSecret = process.env.AUTH_JWT_SECRET;
if (!base || !bucket || !jwtSecret) throw new Error('Missing Storage bootstrap configuration');
const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const payload = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ role: 'service_role', iat: now, exp: now + 300 })}`;
const token = `${payload}.${createHmac('sha256', jwtSecret).update(payload).digest('base64url')}`;
const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
const existing = await fetch(`${base}/bucket/${encodeURIComponent(bucket)}`, { headers, signal: AbortSignal.timeout(10000) });
if (existing.ok) {
    const info = await existing.json();
    if (info.public) throw new Error('Expected a private development bucket');
    console.log('Storage development bucket is ready');
} else {
    const error = await existing.json();
    // Storage's REST API can report NoSuchBucket with HTTP 400.
    if (existing.status !== 404 && error.code !== 'NoSuchBucket') {
        throw new Error(`Storage bucket lookup failed: ${existing.status} ${JSON.stringify(error)}`);
    }
    const response = await fetch(`${base}/bucket`, {
        method: 'POST', headers, signal: AbortSignal.timeout(10000),
        body: JSON.stringify({ id: bucket, name: bucket, public: false, file_size_limit: 10485760,
            allowed_mime_types: ['image/jpeg', 'image/png', 'image/webp'] }),
    });
    if (!response.ok) throw new Error(`Storage bucket creation failed: ${response.status} ${await response.text()}`);
    console.log('Created private Storage development bucket');
}
