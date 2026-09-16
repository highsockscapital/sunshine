// Contract-test the embedded setup script without touching any installed packages.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const source = fs.readFileSync(path.join(__dirname,
    '../app/src/main/java/com/highsockscapital/sunshine/termux/TermuxRuntimePackages.kt'), 'utf8');
const script = source.match(/val installScript: String = """([\s\S]*?)"""\.trimIndent\(\)/)?.[1];
assert.ok(script, 'Embedded package script must exist');
const mocks = `
count=0
mock_command() {
    count=$((count + 1))
    printf '%s\\n' "$*"
    if [ "$count" = "$FAIL_AT" ]; then return 42; fi
}
apt-get() { mock_command apt-get "$@"; }
node() { mock_command node "$@"; }
openssl() { mock_command openssl "$@"; }
`;
function run(failAt) {
    const result = spawnSync('bash', ['-c', mocks + script], {
        encoding: 'utf8', env: { ...process.env, FAIL_AT: String(failAt) },
    });
    assert.ifError(result.error);
    return { status: result.status, lines: result.stdout.trim().split('\n') };
}
const success = run(0);
assert.equal(success.status, 0);
assert.equal(success.lines.length, 6);
assert.match(success.lines[0], /^apt-get .*Error-Mode=any update$/);
assert.match(success.lines[1], /upgrade -y --with-new-pkgs --no-remove$/);
assert.match(success.lines[2], /install -y --no-remove nodejs openssl clang make pkg-config python git$/);
assert.equal(success.lines[3], 'node --version');
assert.equal(success.lines[4], 'openssl version');
assert.match(success.lines[5], /node:crypto.*node:tls/);
for (let step = 1; step <= 6; step++) {
    const failure = run(step);
    assert.equal(failure.status, 42, `Step ${step} must propagate failure`);
    assert.equal(failure.lines.length, step, `Nothing should run after step ${step} fails`);
}
console.log('PASS: package ordering, runtime probes, and fail-fast handling at all 6 steps');
