import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync, chmodSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const VERSION = 'v3.1.20260828';
const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

export function resolveAwgSource({
  override = process.env.NIMBO_AWG_CORE_DIR,
  root = repositoryRoot,
  home = homedir(),
  exists = existsSync,
} = {}) {
  if (override) return resolve(override);
  const local = resolve(root, 'tools/native/awg-core');
  if (exists(resolve(local, 'go.mod'))) return local;
  return resolve(home, 'AndroidStudioProjects/Nimbo/tools/native/awg-core');
}

export const TARGETS = {
  'x86_64-pc-windows-msvc': ['windows-x64', 'windows', 'amd64'],
  'i686-pc-windows-msvc': ['windows-x86', 'windows', '386'],
  'aarch64-pc-windows-msvc': ['windows-arm64', 'windows', 'arm64'],
  'x86_64-unknown-linux-gnu': ['linux-x64', 'linux', 'amd64'],
  'aarch64-unknown-linux-gnu': ['linux-arm64', 'linux', 'arm64'],
};

export function build(target, source) {
  const spec = TARGETS[target];
  if (!spec) throw new Error(`Unsupported AWG target: ${target}`);
  if (!existsSync(resolve(source, 'go.mod'))) throw new Error('Set NIMBO_AWG_CORE_DIR to the shared tools/native/awg-core module');
  const [platform, goos, goarch] = spec;
  const dir = resolve(dirname(fileURLToPath(import.meta.url)), '../src-tauri/resources/awg', platform);
  mkdirSync(dir, { recursive: true });
  const output = resolve(dir, goos === 'windows' ? 'nimbo-awg.exe' : 'nimbo-awg');
  const temporary = `${output}.partial`;
  const result = spawnSync(process.env.GO || 'go', ['build', '-mod=readonly', '-trimpath', '-buildvcs=false', '-ldflags=-s -w', '-o', temporary, './cmd/nimbo-awg'], {
    cwd: source, stdio: 'inherit', windowsHide: true,
    env: { ...process.env, CGO_ENABLED: '0', GOOS: goos, GOARCH: goarch },
  });
  if (result.error || result.status !== 0) throw new Error(`AWG build failed for ${target}`);
  const bytes = readFileSync(temporary);
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  renameSync(temporary, output);
  if (goos === 'linux') chmodSync(output, 0o755);
  writeFileSync(`${output}.manifest.json`, JSON.stringify({ version: VERSION, target, sha256 }, null, 2) + '\n');
  console.log(`Staged AWG ${VERSION}: ${platform}`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const source = resolveAwgSource();
  const args = process.argv.slice(2);
  const targets = args.length ? args : Object.keys(TARGETS);
  try { for (const target of targets) build(target, source); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
