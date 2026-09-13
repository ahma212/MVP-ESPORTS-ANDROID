import http from 'http';
import fs from 'fs';
import path from 'path';

const PORT = 3000;
const ROOT_DIR = process.cwd();
const PUBLIC_DIR = path.join(ROOT_DIR, 'public');
const DIST_DIR = path.join(ROOT_DIR, 'dist');
const APK_SOURCES = [
  path.join(PUBLIC_DIR, 'app-debug.apk'),
  path.join(DIST_DIR, 'app-debug.apk'),
  path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
  path.join(ROOT_DIR, '.build-outputs', 'app-debug.apk')
];

function getApkPath(): string | null {
  for (const src of APK_SOURCES) {
    if (fs.existsSync(src)) return src;
  }
  return null;
}

// Ensure dist directory exists
try {
  if (!fs.existsSync(DIST_DIR)) {
    fs.mkdirSync(DIST_DIR, { recursive: true });
  }
  const indexSource = fs.existsSync(path.join(ROOT_DIR, 'index.html'))
    ? path.join(ROOT_DIR, 'index.html')
    : path.join(PUBLIC_DIR, 'index.html');
  if (fs.existsSync(indexSource) && !fs.existsSync(path.join(DIST_DIR, 'index.html'))) {
    fs.copyFileSync(indexSource, path.join(DIST_DIR, 'index.html'));
  }
} catch (e) {
  console.error('Directory preparation warning:', e);
}

const MIME_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.apk': 'application/vnd.android.package-archive',
  '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url || '/', `http://localhost:${PORT}`);
  let pathname = parsedUrl.pathname;

  if (pathname === '/health' || pathname === '/api/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', timestamp: new Date().toISOString() }));
    return;
  }

  const targetApk = getApkPath();

  if (pathname === '/app-debug.apk' || pathname === '/download') {
    if (!targetApk) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('APK not found. Please build using ./gradlew assembleDebug');
      return;
    }

    const stat = fs.statSync(targetApk);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': stat.size,
      'Content-Disposition': 'attachment; filename="app-debug.apk"',
      'Cache-Control': 'no-cache'
    });

    if (req.method === 'HEAD') {
      res.end();
      return;
    }

    const readStream = fs.createReadStream(targetApk);
    readStream.pipe(res);
    return;
  }

  // Serve static files
  let filePath = path.join(ROOT_DIR, pathname === '/' ? 'index.html' : pathname);
  if (!fs.existsSync(filePath)) {
    filePath = path.join(DIST_DIR, pathname === '/' ? 'index.html' : pathname);
  }
  if (!fs.existsSync(filePath)) {
    filePath = path.join(PUBLIC_DIR, pathname === '/' ? 'index.html' : pathname);
  }

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    const content = fs.readFileSync(filePath);
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(content);
    return;
  }

  // Fallback to index.html
  const fallbackIndex = fs.existsSync(path.join(ROOT_DIR, 'index.html'))
    ? path.join(ROOT_DIR, 'index.html')
    : path.join(DIST_DIR, 'index.html');

  if (fs.existsSync(fallbackIndex)) {
    const content = fs.readFileSync(fallbackIndex, 'utf-8');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(content);
    return;
  }

  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(`<!DOCTYPE html>
<html>
<head><title>MVP ESPORTS PK Live Analyzer</title></head>
<body style="background:#0E0E10;color:#fff;font-family:sans-serif;text-align:center;padding:40px;">
  <h1>MVP ESPORTS PK Live Analyzer</h1>
  <p>Dev server running successfully on port ${PORT}.</p>
</body>
</html>`);
});

server.on('error', (err) => {
  console.error('Server error:', err);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`MVP Esports Live Analyzer server listening on http://0.0.0.0:${PORT}`);
});
