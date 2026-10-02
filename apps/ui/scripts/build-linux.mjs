import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, renameSync, rmSync, chmodSync, statSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ui = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const root = resolve(ui, '../..');
const machines = { 'x86_64-unknown-linux-gnu': 62, 'aarch64-unknown-linux-gnu': 183 };

export function stageLinuxHelper(source, destination, target, version) {
  const machine = machines[target];
  if (!machine) throw new Error('Unsupported Linux helper target');
  const bytes = readFileSync(source);
  if (bytes.length < 24 || bytes.toString('hex', 0, 4) !== '7f454c46' || bytes[4] !== 2 || bytes[5] !== 1 || bytes.readUInt16LE(18) !== machine) {
    throw new Error('Linux helper ELF does not match the requested architecture');
  }
  if (process.platform !== 'win32' && !(statSync(source).mode & 0o111)) throw new Error('Linux helper is not executable');
  const sha256 = createHash('sha256').update(bytes).digest('hex');
  mkdirSync(dirname(destination), { recursive: true });
  const temporary = destination + '.partial';
  const manifest = destination + '.manifest.json';
  try {
    writeFileSync(temporary, bytes, { mode: 0o755 });
    chmodSync(temporary, 0o755);
    renameSync(temporary, destination);
    writeFileSync(manifest + '.partial', JSON.stringify({ target, version, sha256 }, null, 2) + '\n');
    renameSync(manifest + '.partial', manifest);
  } finally {
    rmSync(temporary, { force: true });
    rmSync(manifest + '.partial', { force: true });
  }
  return { target, version, sha256 };
}

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', windowsHide: true });
  if (result.error || result.status !== 0) throw new Error(`${command} failed (${result.status ?? 'not started'})`);
}

export function buildLinux(args) {
  if (process.platform !== 'linux') throw new Error('Build Linux packages on Linux or in WSL');
  let target = process.env.CARGO_BUILD_TARGET;
  let explicitTarget = Boolean(target);
  const forwarded = [];
  let prepareOnly = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--prepare-only') prepareOnly = true;
    else if (args[i] === '--target') { target = args[++i]; explicitTarget = true; }
    else if (args[i].startsWith('--target=')) { target = args[i].slice('--target='.length); explicitTarget = true; }
    else forwarded.push(args[i]);
  }
  if (!target) {
    const host = spawnSync('rustc', ['-vV'], { encoding: 'utf8' });
    if (host.error || host.status !== 0) throw new Error('Cannot determine Rust host');
    target = /^host: (.+)$/m.exec(host.stdout)?.[1];
  }
  if (!machines[target]) throw new Error('Choose a supported x64 or ARM64 Linux target');
  run('cargo', ['build', '--locked', '-p', 'nimbo-svc', '--release', '--target', target], root);
  const targetDir = resolve(root, process.env.CARGO_TARGET_DIR || 'target');
  const source = resolve(targetDir, target, 'release/nimbo-svc');
  const version = JSON.parse(readFileSync(resolve(ui, 'package.json'), 'utf8')).version;
  const receipt = stageLinuxHelper(source, resolve(ui, 'src-tauri/resources/helper/linux/nimbo-svc'), target, version);
  console.log(`Staged Linux helper ${receipt.version} for ${target}: ${receipt.sha256}`);
  if (!prepareOnly) run(process.execPath, [resolve(ui, 'node_modules/@tauri-apps/cli/tauri.js'), 'build', '--bundles', 'appimage,deb,rpm', ...(explicitTarget ? ['--target', target] : []), ...forwarded], ui);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { buildLinux(process.argv.slice(2)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
