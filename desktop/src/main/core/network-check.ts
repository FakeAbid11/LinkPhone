import { execFile } from 'node:child_process';
import type {
  NetworkCheckItem,
  NetworkCheckResult,
  NetworkCheckStatus,
} from '../../shared/types';

/**
 * Everything the Connection tab can learn about *why* a phone cannot reach this
 * PC. The four checks answer three questions Windows will answer and one it
 * cannot:
 *
 *   - is the network profile blocking inbound traffic?
 *   - is there a firewall rule that actually covers this port and this profile?
 *   - has anything other than this PC ever reached the port? (the decisive one)
 *   - is the router isolating its own clients? (unanswerable — see `isolation`)
 */

export interface CommandOutput {
  code: number;
  stdout: string;
  stderr: string;
}

/**
 * Injected rather than imported at the call site, so the tests never spawn a
 * process and never need to mock a Node builtin. Same shape as `UpdateClient`.
 */
export interface CommandRunner {
  run(file: string, args: string[], timeoutMs: number): Promise<CommandOutput>;
}

const POWERSHELL = 'powershell.exe';
/** Windows Firewall cmdlets are slow; long enough, short enough to stay a button. */
const TIMEOUT_MS = 6_000;

// Both scripts are constants. The port is never interpolated into a PowerShell
// string — it is compared in JavaScript after parsing — so there is no command
// injection surface to validate away.
//
// Neither script contains a double quote, deliberately: execFile escapes an
// embedded `"` as `\"`, which PowerShell's own parser reads differently, so a
// quoted string here is the difference between a working button and a command
// that silently fails. Concatenation replaces interpolation, and PowerShell
// treats single quotes literally.
const PROFILE_SCRIPT = [
  'Get-NetConnectionProfile',
  "| ForEach-Object { $_.InterfaceAlias + '|' + $_.NetworkCategory }",
].join(' ');

const RULE_SCRIPT = [
  "Get-NetFirewallRule -DisplayName 'LinkPhone' -ErrorAction SilentlyContinue",
  '| ForEach-Object {',
  '$f = $_ | Get-NetFirewallPortFilter;',
  "$_.Enabled.ToString() + '|' + $_.Direction + '|' + $_.Action + '|' + $_.Profile",
  "+ '|' + $f.Protocol + '|' + $f.LocalPort }",
].join(' ');

export const powershellRunner: CommandRunner = {
  run(file, args, timeoutMs) {
    return new Promise((resolve) => {
      execFile(
        file,
        args,
        { windowsHide: true, timeout: timeoutMs, maxBuffer: 1024 * 1024 },
        (error, stdout, stderr) => {
          // A missing executable reports a string code (ENOENT); a non-zero exit
          // reports a number. Both must resolve, because a rejected promise here
          // would surface as a broken button rather than a failed check.
          const raw = (error as NodeJS.ErrnoException | null)?.code;
          const code = typeof raw === 'number' ? raw : error ? 1 : 0;
          resolve({
            code,
            stdout: String(stdout ?? ''),
            stderr: String(stderr ?? ''),
          });
        },
      );
    });
  },
};

export interface NetworkProfile {
  alias: string;
  category: string;
}

export interface FirewallRuleSummary {
  enabled: boolean;
  direction: string;
  action: string;
  profile: string;
  protocol: string;
  localPort: string;
}

/** One `Alias|Category` line per connected network. */
export function parseConnectionProfiles(output: string): NetworkProfile[] {
  const profiles: NetworkProfile[] = [];
  for (const line of lines(output)) {
    const parts = line.split('|');
    if (parts.length < 2) continue;
    const alias = parts[0].trim();
    const category = parts[1].trim();
    if (!alias || !category) continue;
    profiles.push({ alias, category });
  }
  return profiles;
}

/**
 * Empty output is the normal "no such rule" answer: the cmdlet is run with
 * `-ErrorAction SilentlyContinue`, so a missing rule exits 0 and prints nothing.
 */
export function parseFirewallRules(output: string): FirewallRuleSummary[] {
  const rules: FirewallRuleSummary[] = [];
  for (const line of lines(output)) {
    const parts = line.split('|');
    if (parts.length < 6) continue;
    rules.push({
      enabled: parts[0].trim().toLowerCase() === 'true',
      direction: parts[1].trim(),
      action: parts[2].trim(),
      profile: parts[3].trim(),
      protocol: parts[4].trim(),
      localPort: parts[5].trim(),
    });
  }
  return rules;
}

/** A rule scoped to `Domain, Private` still applies on a Private network. */
export function ruleCoversProfile(rule: FirewallRuleSummary, category: string): boolean {
  const wanted = category.trim().toLowerCase();
  if (!wanted) return false;
  return rule.profile
    .split(',')
    .map((part) => part.trim().toLowerCase())
    .some((part) => part === 'any' || part === wanted);
}

export function ruleCoversPort(rule: FirewallRuleSummary, port: number): boolean {
  const protocol = rule.protocol.trim().toUpperCase();
  // `Get-NetFirewallPortFilter` returns the protocol name, but a numeric 6 is
  // what the older netsh output carries.
  if (!(protocol === 'TCP' || protocol === '6' || protocol === 'ANY')) return false;
  const local = rule.localPort.trim();
  if (!local || local.toLowerCase() === 'any') return true;
  if (local.includes('-')) {
    const [from, to] = local.split('-').map((part) => Number(part.trim()));
    return Number.isFinite(from) && Number.isFinite(to) && port >= from && port <= to;
  }
  return Number(local) === port;
}

/**
 * The addresses that reached this PC from somewhere else. Anything matching one
 * of this PC's own addresses is a local browser probe, not a phone.
 *
 * `bareAddress` in server.ts does the same normalisation but is module-private,
 * so it is duplicated here deliberately rather than exported for one caller.
 */
export function foreignPeers(peers: Iterable<string>, localAddresses: string[]): string[] {
  const local = new Set(localAddresses.map(normalizeAddress));
  const seen = new Set<string>();
  const result: string[] = [];
  for (const raw of peers) {
    const address = normalizeAddress(raw);
    if (!address) continue;
    if (address === '127.0.0.1' || address === '::1') continue;
    if (local.has(address)) continue;
    if (seen.has(address)) continue;
    seen.add(address);
    result.push(address);
  }
  return result;
}

export interface NetworkCheckInput {
  /** 0 when the server is not listening. */
  port: number;
  serverRunning: boolean;
  profiles: NetworkProfile[];
  profilesFailed?: string;
  rules: FirewallRuleSummary[];
  rulesFailed?: string;
  inboundPeers: string[];
  lastInboundAt?: number;
  localAddresses: string[];
}

export function evaluate(input: NetworkCheckInput): NetworkCheckResult {
  const items = [
    profileItem(input),
    firewallItem(input),
    inboundItem(input),
    isolationItem(),
  ];
  return {
    at: Date.now(),
    supported: true,
    verdict: rollUp(items),
    port: input.port,
    items,
  };
}

export interface RunNetworkCheckOptions extends Omit<NetworkCheckInput, 'port'> {
  port: number;
  now?: number;
  runner?: CommandRunner;
  platform?: NodeJS.Platform;
}

export async function runNetworkCheck(options: RunNetworkCheckOptions): Promise<NetworkCheckResult> {
  const now = options.now ?? Date.now();
  const port = safePort(options.port);
  const platform = options.platform ?? process.platform;

  if (platform !== 'win32') {
    // Never spawn on a platform that has neither cmdlet; report it instead.
    return {
      at: now,
      supported: false,
      verdict: 'unknown',
      port,
      items: [
        {
          id: 'profiles',
          status: 'unknown',
          title: 'This check needs Windows',
          detail:
            'The firewall and network-profile checks read Windows settings, so they only run on Windows.',
        },
      ],
    };
  }

  const runner = options.runner ?? powershellRunner;
  const [profileRun, ruleRun] = await Promise.all([
    safeRun(runner, PROFILE_SCRIPT),
    safeRun(runner, RULE_SCRIPT),
  ]);

  const result = evaluate({
    port,
    serverRunning: options.serverRunning,
    profiles: profileRun.ok ? parseConnectionProfiles(profileRun.stdout) : [],
    profilesFailed: profileRun.ok ? undefined : profileRun.error,
    rules: ruleRun.ok ? parseFirewallRules(ruleRun.stdout) : [],
    rulesFailed: ruleRun.ok ? undefined : ruleRun.error,
    inboundPeers: options.inboundPeers,
    lastInboundAt: options.lastInboundAt,
    localAddresses: options.localAddresses,
  });
  return { ...result, at: now };
}

/** 1..65535, or 0 meaning "there is no port to check". */
function safePort(value: number): number {
  return Number.isInteger(value) && value >= 1 && value <= 65_535 ? value : 0;
}

interface ScriptRun {
  ok: boolean;
  stdout: string;
  error?: string;
}

async function safeRun(runner: CommandRunner, script: string): Promise<ScriptRun> {
  try {
    const output = await runner.run(
      POWERSHELL,
      ['-NoProfile', '-NonInteractive', '-Command', script],
      TIMEOUT_MS,
    );
    if (output.code !== 0) {
      return { ok: false, stdout: '', error: oneLine(output.stderr, output.code) };
    }
    return { ok: true, stdout: output.stdout };
  } catch (error) {
    return {
      ok: false,
      stdout: '',
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

function profileItem(input: NetworkCheckInput): NetworkCheckItem {
  if (input.profilesFailed) {
    return {
      id: 'profiles',
      status: 'unknown',
      title: 'Windows did not report the network profile',
      detail: input.profilesFailed,
    };
  }
  if (input.profiles.length === 0) {
    return {
      id: 'profiles',
      status: 'unknown',
      title: 'No connected network',
      detail: 'Windows reports no network profile. Connect the PC to Wi-Fi or Ethernet.',
    };
  }
  const blocking = input.profiles.filter((profile) => category(profile) === 'public');
  if (blocking.length > 0) {
    return {
      id: 'profiles',
      status: 'bad',
      title: 'This network is Public',
      detail:
        'Windows Firewall blocks inbound connections on Public networks. Marked Public: ' +
        `${list(blocking)}. Set the network to Private, or add the rule for Public only if you ` +
        'accept the exposure.',
    };
  }
  const recognised = input.profiles.filter((profile) =>
    ['private', 'domain', 'domainauthenticated'].includes(category(profile)),
  );
  if (recognised.length !== input.profiles.length) {
    const odd = input.profiles.filter((profile) => !recognised.includes(profile));
    return {
      id: 'profiles',
      status: 'warn',
      title: 'Unrecognised network category',
      detail: `Windows reported ${list(odd)}. Only Private and Domain networks allow inbound traffic by default.`,
    };
  }
  return {
    id: 'profiles',
    status: 'ok',
    title: 'The network is not Public',
    detail: `Windows allows inbound connections on ${list(input.profiles)}.`,
  };
}

function firewallItem(input: NetworkCheckInput): NetworkCheckItem {
  if (!input.serverRunning || input.port === 0) {
    return {
      id: 'firewall',
      status: 'unknown',
      title: 'LinkPhone is not listening',
      detail: 'The server is not running, so there is no port for a rule to cover.',
    };
  }
  if (input.rulesFailed) {
    return {
      id: 'firewall',
      status: 'unknown',
      title: 'Windows did not report the firewall rule',
      detail: input.rulesFailed,
    };
  }
  if (input.rules.length === 0) {
    return {
      id: 'firewall',
      status: 'bad',
      title: 'No firewall rule for LinkPhone',
      detail:
        `No rule named LinkPhone exists, so nothing allows the phone in on TCP ${input.port}. ` +
        'Windows may have created a rule under a different name when it first asked for ' +
        'permission; this check only looks for the name the command below creates.',
    };
  }
  const enabled = input.rules.filter((rule) => rule.enabled);
  if (enabled.length === 0) {
    return {
      id: 'firewall',
      status: 'bad',
      title: 'The LinkPhone rule is disabled',
      detail: 'The rule exists but is turned off, so Windows ignores it.',
    };
  }
  const inbound = enabled.filter((rule) => rule.direction.trim().toLowerCase() === 'inbound');
  if (inbound.length === 0) {
    return {
      id: 'firewall',
      status: 'bad',
      title: 'The LinkPhone rule is outbound',
      detail: 'An outbound rule controls traffic leaving this PC; it does not let the phone in.',
    };
  }
  const allowing = inbound.filter((rule) => rule.action.trim().toLowerCase() === 'allow');
  if (allowing.length === 0) {
    return {
      id: 'firewall',
      status: 'bad',
      title: 'The LinkPhone rule blocks this traffic',
      detail: 'The rule exists but its action is Block, so it does the opposite of what is needed.',
    };
  }
  const covering = allowing.filter((rule) => ruleCoversPort(rule, input.port));
  if (covering.length === 0) {
    const scope = allowing.map((rule) => `${rule.protocol} ${rule.localPort}`).join(', ');
    return {
      id: 'firewall',
      status: 'bad',
      title: `The rule does not cover TCP ${input.port}`,
      detail: `The enabled rule covers ${scope}, so traffic to TCP ${input.port} is still blocked.`,
    };
  }
  const applicable = covering.filter((rule) =>
    input.profiles.some((profile) => ruleCoversProfile(rule, profile.category)),
  );
  if (input.profiles.length > 0 && applicable.length === 0) {
    const scope = covering.map((rule) => rule.profile).join(', ');
    return {
      id: 'firewall',
      status: 'warn',
      title: 'The rule does not apply to this network',
      detail:
        `The rule covers ${scope}, but ${list(input.profiles)} is on a profile it does not ` +
        'include. Windows applies a rule to the profile the network is on.',
    };
  }
  const scope = applicable.length > 0 ? applicable : covering;
  return {
    id: 'firewall',
    status: 'ok',
    title: `TCP ${input.port} is allowed in`,
    detail: `An enabled inbound rule allows TCP ${input.port} on ${scope.map((rule) => rule.profile).join(', ')}.`,
  };
}

function inboundItem(input: NetworkCheckInput): NetworkCheckItem {
  if (!input.serverRunning) {
    return {
      id: 'inbound',
      status: 'unknown',
      title: 'Nothing to measure',
      detail: 'The server is not running, so nothing can have reached it.',
    };
  }
  const peers = foreignPeers(input.inboundPeers, input.localAddresses);
  if (peers.length > 0) {
    return {
      id: 'inbound',
      status: 'ok',
      title: 'Traffic reaches this PC',
      detail:
        `${peers.join(', ')} connected to the port. The network delivers traffic, so a failure ` +
        'at this point is above TCP — check the pairing code rather than the router.',
      at: input.lastInboundAt,
    };
  }
  return {
    id: 'inbound',
    status: 'bad',
    title: 'Nothing has reached this PC',
    detail:
      `No device other than this PC has connected to TCP ${input.port} since LinkPhone ` +
      'started. The packet is being dropped before it arrives: that is the network, not the app.',
  };
}

function isolationItem(): NetworkCheckItem {
  return {
    id: 'isolation',
    status: 'unknown',
    title: 'Device-to-device traffic (cannot be checked)',
    detail:
      'No Windows API reports whether the router forwards traffic between its own clients. If ' +
      "the other checks pass and nothing has reached this PC, this is the remaining cause — run " +
      "ping against the phone's address, which the phone's pairing screen prints.",
  };
}

function rollUp(items: NetworkCheckItem[]): NetworkCheckStatus {
  if (items.some((item) => item.status === 'bad')) return 'bad';
  if (items.some((item) => item.status === 'warn')) return 'warn';
  if (items.every((item) => item.status === 'unknown')) return 'unknown';
  // `unknown` never downgrades: an unverifiable item must not make a working
  // setup look broken.
  return 'ok';
}

function lines(output: string): string[] {
  return output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}

function category(profile: NetworkProfile): string {
  return profile.category.trim().toLowerCase();
}

function list(profiles: NetworkProfile[]): string {
  return profiles.map((profile) => profile.alias).join(', ');
}

function normalizeAddress(value: string): string {
  let address = String(value ?? '').trim();
  // Node reports IPv4 peers as ::ffff:a.b.c.d on a dual-stack listener.
  address = address.replace(/^::ffff:/i, '');
  address = address.replace(/^\[/, '').replace(/\]$/, '');
  const zone = address.indexOf('%');
  if (zone >= 0) address = address.slice(0, zone);
  return address;
}

function oneLine(stderr: string, code: number): string {
  const text = stderr.replace(/\s+/g, ' ').trim();
  return (text || `PowerShell exited with code ${code}`).slice(0, 200);
}
