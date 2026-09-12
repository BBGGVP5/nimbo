// Browser preview imports use the same wire model as Rust. Native imports are
// validated by the Rust parser and again by the runtime before launch.
export function isAwgInput(value: string): boolean {
  return /^(awg|amneziawg|wireguard|wg):\/\//i.test(value.trim()) || /^\s*\[Interface\]\s*$/im.test(value);
}

export function parseAwgInput(value: string): { name: string; config: { address: string; port: number; config: string } } {
  const fail = (): never => { throw new Error("Некорректная конфигурация AWG/WireGuard"); };
  if (value.length > 256 * 1024) fail();
  let ini = value.trim();
  let name = "AmneziaWG";
  const norm = (key: string) => key.toLowerCase().replace(/[_-]/g, "");
  try {
    if (/^(awg|amneziawg|wireguard|wg):\/\//i.test(ini)) {
      let body = ini.slice(ini.indexOf("://") + 3);
      const hash = body.indexOf("#");
      if (hash >= 0) { name = decodeURIComponent(body.slice(hash + 1)) || name; body = body.slice(0, hash); }
      const q = body.indexOf("?");
      const params = new Map<string, string>();
      if (q >= 0) {
        for (const pair of body.slice(q + 1).split("&").filter(Boolean)) {
          const equal = pair.indexOf("="); if (equal < 1) fail();
          const key = norm(decodeURIComponent(pair.slice(0, equal)));
          const val = decodeURIComponent(pair.slice(equal + 1));
          if (/[\r\n\0]/.test(val) || params.has(key)) fail();
          params.set(key, val);
        }
        body = body.slice(0, q);
      }
      if (name === "AmneziaWG") name = params.get("name") || name;
      let decoded = decodeURIComponent(body);
      if (!/^\s*\[Interface\]\s*$/im.test(decoded)) {
        try {
          const b64 = decoded.replace(/-/g, "+").replace(/_/g, "/");
          decoded = new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(b64), c => c.charCodeAt(0)));
        } catch { decoded = ""; }
      }
      if (/^\s*\[Interface\]\s*$/im.test(decoded)) ini = decoded;
      else {
        const get = (...keys: string[]) => keys.map(k => params.get(k)).find(Boolean);
        const iface: string[] = []; const peer: string[] = [];
        for (const [key, aliases] of [
          ["PrivateKey", ["privatekey", "privkey", "secretkey"]],
          ["Address", ["address", "ip", "localaddress", "localip"]],
          ["DNS", ["dns"]], ["MTU", ["mtu"]],
        ] as [string, string[]][]) {
          const val = get(...aliases); if (val) iface.push(`${key}=${val}`);
        }
        for (const key of ["Jc","Jmin","Jmax","S1","S2","S3","S4","H1","H2","H3","H4","I1","I2","I3","I4","I5",
          "header_protection_key","content_padding_addition","rekey_after_time","rekey_timeout","reject_after_time","keepalive_timeout","max_handshake_attempts","random_trailers","disable_cookies"]) {
          const val = params.get(norm(key)); if (val) iface.push(`${key}=${val}`);
        }
        peer.push(`PublicKey=${get("publickey","pubkey","peerpublickey","serverpublickey") || ""}`);
        peer.push(`Endpoint=${get("endpoint","server") || decodeURIComponent(body)}`);
        peer.push(`AllowedIPs=${get("allowedips","iprange","routes") || "0.0.0.0/0, ::/0"}`);
        const psk = get("presharedkey","psk","peerpresharedkey"); if (psk) peer.push(`PresharedKey=${psk}`);
        const keepalive = get("persistentkeepalive","keepalive","persistentkeepaliveinterval"); if (keepalive) peer.push(`PersistentKeepalive=${keepalive}`);
        ini = `[Interface]\n${iface.join("\n")}\n[Peer]\n${peer.join("\n")}\n`;
      }
    }
    let section = ""; const seen = new Set<string>();
    const iface = new Map<string, string>(); const peer = new Map<string, string>();
    for (const raw of ini.split(/\r?\n/)) {
      const line = raw.split("#")[0].trim();
      if (!line || line.startsWith(";")) continue;
      if (/^\[(Interface|Peer)\]$/i.test(line)) {
        section = line.toLowerCase(); if (seen.has(section)) fail(); seen.add(section); continue;
      }
      const equal = line.indexOf("="); if (equal < 1 || !section) fail();
      const key = norm(line.slice(0, equal).trim()); const val = line.slice(equal + 1).trim();
      const map = section === "[interface]" ? iface : peer;
      if (!val || map.has(key) || /[\0]/.test(val)) fail(); map.set(key, val);
    }
    for (const key of [iface.get("privatekey"), peer.get("publickey"), peer.get("presharedkey") || iface.get("privatekey")]) {
      if (!key || (!/^[0-9a-f]{64}$/i.test(key) && atob(key).length !== 32)) fail();
    }
    if (!iface.get("address") || !peer.get("allowedips")) fail();
    const endpoint = peer.get("endpoint")?.match(/^(?:\[([0-9a-f:]+)\]|([a-z0-9.-]+)):(\d+)$/i);
    if (!endpoint) return fail();
    const port = Number(endpoint[3]); if (port < 1 || port > 65535) fail();
    return { name, config: { address: endpoint[1] || endpoint[2], port, config: ini } };
  } catch { return fail(); }
}
