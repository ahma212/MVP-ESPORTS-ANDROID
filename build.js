import fs from 'fs';
import path from 'path';

const ROOT_DIR = process.cwd();
const DIST_DIR = path.join(ROOT_DIR, 'dist');
const PUBLIC_DIR = path.join(ROOT_DIR, 'public');

try {
  if (!fs.existsSync(DIST_DIR)) {
    fs.mkdirSync(DIST_DIR, { recursive: true });
  }

  // Copy index.html to dist
  const indexSource = fs.existsSync(path.join(ROOT_DIR, 'index.html'))
    ? path.join(ROOT_DIR, 'index.html')
    : path.join(PUBLIC_DIR, 'index.html');

  if (fs.existsSync(indexSource)) {
    fs.copyFileSync(indexSource, path.join(DIST_DIR, 'index.html'));
  }

  // Copy public contents if any
  if (fs.existsSync(PUBLIC_DIR)) {
    fs.cpSync(PUBLIC_DIR, DIST_DIR, { recursive: true, force: true });
  }

  // Copy APK if available
  const possibleApkSources = [
    path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk'),
    path.join(ROOT_DIR, '.build-outputs', 'app-debug.apk')
  ];

  for (const apkSrc of possibleApkSources) {
    if (fs.existsSync(apkSrc)) {
      if (!fs.existsSync(PUBLIC_DIR)) fs.mkdirSync(PUBLIC_DIR, { recursive: true });
      fs.copyFileSync(apkSrc, path.join(PUBLIC_DIR, 'app-debug.apk'));
      fs.copyFileSync(apkSrc, path.join(DIST_DIR, 'app-debug.apk'));
      break;
    }
  }

  console.log('Build completed successfully: dist/ created.');
} catch (err) {
  console.error('Build step warning:', err);
}
