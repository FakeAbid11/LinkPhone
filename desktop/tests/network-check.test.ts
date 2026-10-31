import { describe, expect, it } from 'vitest';
import type { NetworkCheckItem, NetworkCheckResult } from '../src/shared/types';
import {
  evaluate,
  foreignPeers,
  parseConnectionProfiles,
  parseFirewallRules,
  ruleCoversPort,
  ruleCoversProfile,
  runNetworkCheck,
  type CommandRunner,
  type FirewallRuleSummary,
  type NetworkCheckInput,
} from '../src/main/core/network-check';

function rule(overrides: Partial<FirewallRuleSummary> = {}): FirewallRuleSummary {
  return {
    enabled: true,
    direction: 'Inbound',
    action: 'Allow',
    profile: 'Private',
    protocol: 'TCP',
    localPort: '48921',
    ...overrides,
  };
}

function input(overrides: Partial<NetworkCheckInput> = {}): NetworkCheckInput {
  return {
    port: 48921,
    serverRunning: true,
    profiles: [{ alias: 'Wi-Fi', category: 'Private' }],
    rules: [rule()],
    inboundPeers: ['192.168.0.119'],
    lastInboundAt: 1_700_000_000_000,
    localAddresses: ['192.168.0.111'],
    ...overrides,
  };
}

function item(result: NetworkCheckResult, id: NetworkCheckItem['id']): NetworkCheckItem {
  const found = result.items.find((entry) => entry.id === id);
  if (!found) throw new Error(`missing check: ${id}`);
  return found;
}

describe('PowerShell output parsing', () => {
  it('parses one profile per line and tolerates CRLF', () => {
    const profiles = parseConnectionProfiles('Wi-Fi|Public\r\nEthernet|Private\r\n');
    expect(profiles).toEqual([
      { alias: 'Wi-Fi', category: 'Public' },
      { alias: 'Ethernet', category: 'Private' },
    ]);
  });

  it('ignores blank and whitespace-only lines', () => {
    expect(parseConnectionProfiles('\r\n   \r\nWi-Fi|Private\r\n\r\n')).toHaveLength(1);
  });

  it('returns nothing when Windows reports no connected network', () => {
    expect(parseConnectionProfiles('')).toEqual([]);
  });

  it('keeps an alias that contains a space', () => {
    expect(parseConnectionProfiles('Wi-Fi 2|Public')[0].alias).toBe('Wi-Fi 2');
  });

  it('skips a line that is not pipe separated', () => {
    expect(parseConnectionProfiles('garbage')).toEqual([]);
  });

  it('parses an enabled inbound allow rule', () => {
    const [parsed] = parseFirewallRules('True|Inbound|Allow|Private|TCP|48921');
    expect(parsed).toEqual({
      enabled: true,
      direction: 'Inbound',
      action: 'Allow',
      profile: 'Private',
      protocol: 'TCP',
      localPort: '48921',
    });
  });

  it('returns nothing when no rule exists', () => {
    // The cmdlet runs with -ErrorAction SilentlyContinue, so a missing rule is
    // exit 0 with empty output rather than an error.
    expect(parseFirewallRules('')).toEqual([]);
  });

  it('parses a disabled rule as disabled', () => {
    expect(parseFirewallRules('False|Inbound|Allow|Private|TCP|48921')[0].enabled).toBe(false);
  });

  it('keeps a combined profile list intact', () => {
    expect(parseFirewallRules('True|Inbound|Allow|Domain, Private|TCP|48921')[0].profile).toBe(
      'Domain, Private',
    );
  });

  it('skips a rule line with too few fields', () => {
    expect(parseFirewallRules('True|Inbound|Allow')).toEqual([]);
  });
});

describe('firewall rule matching', () => {
  it('covers the exact port', () => {
    expect(ruleCoversPort(rule(), 48921)).toBe(true);
  });

  it('covers any port', () => {
    expect(ruleCoversPort(rule({ localPort: 'Any' }), 48921)).toBe(true);
  });

  it('covers a port inside a range', () => {
    expect(ruleCoversPort(rule({ localPort: '48920-48930' }), 48921)).toBe(true);
  });

  it('rejects a port outside the range and a different single port', () => {
    expect(ruleCoversPort(rule({ localPort: '48930-48940' }), 48921)).toBe(false);
    expect(ruleCoversPort(rule({ localPort: '8080' }), 48921)).toBe(false);
  });

  it('accepts the protocol name, the numeric 6 and Any, but not UDP', () => {
    expect(ruleCoversPort(rule({ protocol: '6' }), 48921)).toBe(true);
    expect(ruleCoversPort(rule({ protocol: 'Any' }), 48921)).toBe(true);
    expect(ruleCoversPort(rule({ protocol: 'UDP' }), 48921)).toBe(false);
  });

  it('covers a profile listed alongside others', () => {
    expect(ruleCoversProfile(rule({ profile: 'Domain, Private' }), 'Private')).toBe(true);
    expect(ruleCoversProfile(rule({ profile: 'Any' }), 'Public')).toBe(true);
  });

  it('does not cover a profile it does not list', () => {
    expect(ruleCoversProfile(rule({ profile: 'Private' }), 'Public')).toBe(false);
  });
});

describe('foreign peer filter', () => {
  it('drops loopback and this PC\u2019s own addresses', () => {
    expect(
      foreignPeers(['127.0.0.1', '::1', '192.168.0.111', '192.168.0.119'], ['192.168.0.111']),
    ).toEqual(['192.168.0.119']);
  });

  it('strips the IPv4-mapped prefix Node reports on a dual-stack listener', () => {
    expect(foreignPeers(['::ffff:192.168.0.119'], ['192.168.0.111'])).toEqual(['192.168.0.119']);
  });

  it('deduplicates and keeps the original order', () => {
    expect(foreignPeers(['192.168.0.120', '192.168.0.119', '192.168.0.120'], [])).toEqual([
      '192.168.0.120',
      '192.168.0.119',
    ]);
  });

  it('drops an empty peer', () => {
    expect(foreignPeers(['', '  '], [])).toEqual([]);
  });
});

describe('check evaluation', () => {
  it('passes when the profile, rule and inbound traffic all agree', () => {
    const result = evaluate(input());
    expect(result.verdict).toBe('ok');
    expect(result.items).toHaveLength(4);
    expect(item(result, 'profiles').status).toBe('ok');
    expect(item(result, 'firewall').status).toBe('ok');
    expect(item(result, 'inbound').status).toBe('ok');
  });

  it('treats isolation as unverifiable rather than as a failure', () => {
    const result = evaluate(input());
    expect(item(result, 'isolation').status).toBe('unknown');
    expect(result.verdict).toBe('ok');
  });

  it('fails a Public network and names the adapter', () => {
    const result = evaluate(input({ profiles: [{ alias: 'Wi-Fi', category: 'Public' }] }));
    expect(item(result, 'profiles').status).toBe('bad');
    expect(item(result, 'profiles').detail).toContain('Wi-Fi');
    expect(result.verdict).toBe('bad');
  });

  it('fails when no rule exists at all', () => {
    const result = evaluate(input({ rules: [] }));
    expect(item(result, 'firewall').status).toBe('bad');
    expect(item(result, 'firewall').title).toContain('No firewall rule');
  });

  it('distinguishes a disabled rule from a missing one', () => {
    const result = evaluate(input({ rules: [rule({ enabled: false })] }));
    expect(item(result, 'firewall').status).toBe('bad');
    expect(item(result, 'firewall').title).toContain('disabled');
    expect(item(result, 'firewall').detail).not.toContain('No rule named');
  });

  it('does not count an outbound rule', () => {
    const result = evaluate(input({ rules: [rule({ direction: 'Outbound' })] }));
    expect(item(result, 'firewall').status).toBe('bad');
    expect(item(result, 'firewall').title).toContain('outbound');
  });

  it('does not count a rule whose action is Block', () => {
    const result = evaluate(input({ rules: [rule({ action: 'Block' })] }));
    expect(item(result, 'firewall').status).toBe('bad');
    expect(item(result, 'firewall').title).toContain('blocks');
  });

  it('does not count a rule for another port', () => {
    const result = evaluate(input({ rules: [rule({ localPort: '8080' })] }));
    expect(item(result, 'firewall').status).toBe('bad');
    expect(item(result, 'firewall').detail).toContain('48921');
    expect(item(result, 'firewall').detail).toContain('8080');
  });

  it('warns when the rule exists but does not cover the current profile', () => {
    const result = evaluate(input({ profiles: [{ alias: 'Wi-Fi', category: 'Public' }] }));
    expect(item(result, 'firewall').status).toBe('warn');
  });

  it('fails when nothing other than this PC has ever connected', () => {
    const result = evaluate(input({ inboundPeers: [] }));
    expect(item(result, 'inbound').status).toBe('bad');
    expect(item(result, 'inbound').detail).toContain('48921');
    expect(result.verdict).toBe('bad');
  });

  it('does not count this PC\u2019s own address as proof', () => {
    const result = evaluate(input({ inboundPeers: ['192.168.0.111', '::ffff:192.168.0.111'] }));
    expect(item(result, 'inbound').status).toBe('bad');
  });

  it('reports the last foreign connection time', () => {
    const result = evaluate(input());
    expect(item(result, 'inbound').at).toBe(1_700_000_000_000);
  });

  it('leaves the firewall and inbound checks unknown when the server is stopped', () => {
    const result = evaluate(input({ serverRunning: false, port: 0 }));
    expect(item(result, 'firewall').status).toBe('unknown');
    expect(item(result, 'inbound').status).toBe('unknown');
  });

  it('degrades a failed command to unknown rather than to blocked', () => {
    const result = evaluate(input({ profilesFailed: 'Access is denied' }));
    expect(item(result, 'profiles').status).toBe('unknown');
    expect(item(result, 'profiles').detail).toBe('Access is denied');
    expect(result.verdict).not.toBe('bad');
  });

  it('reports no connected network as unknown, not as blocked', () => {
    const result = evaluate(input({ profiles: [] }));
    expect(item(result, 'profiles').status).toBe('unknown');
  });
});

describe('running the check', () => {
  it('returns unsupported off Windows without spawning anything', async () => {
    let called = false;
    const runner: CommandRunner = {
      async run() {
        called = true;
        return { code: 0, stdout: '', stderr: '' };
      },
    };

    const result = await runNetworkCheck({ ...input(), runner, platform: 'linux', now: 42 });

    expect(called).toBe(false);
    expect(result.supported).toBe(false);
    expect(result.verdict).toBe('unknown');
    expect(result.at).toBe(42);
  });

  it('runs both queries with -NoProfile and never puts the port in the script', async () => {
    const calls: { file: string; args: string[] }[] = [];
    const runner: CommandRunner = {
      async run(file, args) {
        calls.push({ file, args });
        const script = args.join(' ');
        if (script.includes('Get-NetConnectionProfile')) {
          return { code: 0, stdout: 'Wi-Fi|Private\r\n', stderr: '' };
        }
        return { code: 0, stdout: 'True|Inbound|Allow|Private|TCP|48921\r\n', stderr: '' };
      },
    };

    const result = await runNetworkCheck({ ...input(), runner, platform: 'win32', now: 7 });

    expect(calls).toHaveLength(2);
    for (const call of calls) {
      expect(call.file).toBe('powershell.exe');
      expect(call.args).toContain('-NoProfile');
      expect(call.args).toContain('-NonInteractive');
      // The injection guarantee: the port is compared after parsing, so it must
      // never reach a PowerShell string.
      expect(call.args.some((arg) => arg.includes('48921'))).toBe(false);
      // execFile escapes an embedded double quote as \" and PowerShell reads that
      // differently, so a script containing one would fail at runtime. Pinned
      // here because no unit test can run PowerShell itself.
      expect(call.args.some((arg) => arg.includes('"'))).toBe(false);
    }
    expect(result.verdict).toBe('ok');
    expect(result.at).toBe(7);
  });

  it('resolves rather than rejects when PowerShell exits non-zero', async () => {
    const runner: CommandRunner = {
      async run() {
        return { code: 1, stdout: '', stderr: 'Access is denied' };
      },
    };

    const result = await runNetworkCheck({ ...input(), runner, platform: 'win32' });

    expect(item(result, 'profiles').status).toBe('unknown');
    expect(item(result, 'firewall').status).toBe('unknown');
    expect(result.verdict).not.toBe('bad');
  });

  it('resolves rather than rejects when the runner throws', async () => {
    const runner: CommandRunner = {
      async run() {
        throw new Error('spawn powershell.exe ENOENT');
      },
    };

    const result = await runNetworkCheck({ ...input(), runner, platform: 'win32' });

    expect(item(result, 'profiles').detail).toContain('ENOENT');
  });
});
