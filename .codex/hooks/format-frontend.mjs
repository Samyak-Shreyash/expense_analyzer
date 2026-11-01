// PostToolUse hook: run Prettier and ESLint --fix on edited files under frontend/src.
import { execFileSync } from 'node:child_process';
import path from 'node:path';

let input = '';
for await (const chunk of process.stdin) input += chunk;
const file = JSON.parse(input).tool_input?.file_path;
if (!file) process.exit(0);

const frontend = path.resolve(import.meta.dirname, '../../frontend');
const rel = path.relative(frontend, path.resolve(file));
if (!rel.startsWith('src' + path.sep) || !/\.(tsx?|jsx?|css)$/.test(rel)) process.exit(0);

const run = (args) =>
  execFileSync('npx', args, { cwd: frontend, shell: true, stdio: ['ignore', 'pipe', 'pipe'] });

try {
  run(['prettier', '--write', `"${rel}"`]);
  if (!rel.endsWith('.css')) run(['eslint', '--fix', `"${rel}"`]);
} catch (e) {
  // Exit 2 shows the remaining lint errors to Claude so it fixes them.
  process.stderr.write(String(e.stdout || '') + String(e.stderr || ''));
  process.exit(2);
}
