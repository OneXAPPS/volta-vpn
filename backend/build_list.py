#!/usr/bin/env python3
"""Builds Volta's signed server list from public OpenVPN sources, keeping only
servers that pass strict live checks.

Sources:
  * VPN Gate academic project CSV API (always)
  * PublicVPNList API (only when PUBLICVPNLIST_KEY is set: an individually approved key,
    as its license forbids automating the 24-hour reCAPTCHA keys)
  * Volta's own servers from OWN_SERVERS (listed first as tier PRIVATE)

Every public candidate is connected through a real OpenVPN tunnel and must pass ALL of:
  * tunnel up within 20 s
  * negotiated data cipher is AES-GCM / ChaCha20 / AES-CBC with a real HMAC (no BF, DES, none)
  * TLS 1.2+ control channel
  * exit IP geo-location matches the advertised country (two geo services)
  * our real IP appears in no forwarded header (X-Forwarded-For, Via, Forwarded...)
  * HTTPS to a known site validates against the system CA store (no interception)
  * download >= MIN_MBPS and TLS connect time <= MAX_CONNECT_MS through the tunnel

Caps: at most 5 servers for each of the TOP_COUNTRIES, 2 for every other country.

Usage:
  python build_list.py --key keys/private.pem --out public/servers.json [--verify]
"""
import argparse
import base64
import concurrent.futures
import csv
import io
import json
import os
import queue
import re
import socket
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.request

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

VPNGATE_API = "http://www.vpngate.net/api/iphone/"
PUBLICVPNLIST_API = "https://publicvpnlist.com/api/v1"

# Most requested VPN locations; everything else is capped lower.
TOP_COUNTRIES = ["US", "GB", "DE", "NL", "FR", "CA", "JP", "SG", "KR", "TR"]
CAP_TOP, CAP_OTHER = 5, 2
CANDIDATES_PER_SLOT = 3      # test 3 candidates for every slot we want to fill
MIN_MBPS = 3.0
MAX_CONNECT_MS = 1500
PARALLEL = 6
WEAK_CIPHERS = ("BF-", "DES", "RC2", "CAST", "NONE")

BLOCKED = {
    "script-security", "up", "down", "route-up", "route-pre-down", "ipchange",
    "tls-verify", "client-connect", "client-disconnect", "learn-address",
    "auth-user-pass-verify", "plugin", "setenv", "setenv-safe", "management",
    "management-hold", "log", "log-append", "status", "writepid", "daemon",
    "chroot", "user", "group", "cd", "redirect-private", "dhcp-option",
    "block-outside-dns", "pull-filter", "route-nopull", "allow-pull-fqdn", "verb",
}


def sanitize(config: str) -> str:
    out, in_block = [], False
    for raw in config.splitlines():
        line = raw.strip()
        if line.startswith("<") and not line.startswith("</"):
            in_block = True
        if in_block:
            out.append(line)
            if line.startswith("</"):
                in_block = False
            continue
        if not line or line[0] in "#;":
            continue
        if line.split()[0].lower() in BLOCKED:
            continue
        out.append(line)
    return "\n".join(out) + "\n"


def num(v, default):
    """Sources use '-', empty or null for unknown values."""
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return default


def http_get(url: str, headers: dict | None = None, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "VoltaVPN-builder", **(headers or {})})
    return urllib.request.urlopen(req, timeout=timeout).read()


# ---------- sources ----------

def fetch_vpngate() -> list[dict]:
    text = http_get(VPNGATE_API).decode("utf-8", "replace")
    rows = [l for l in text.splitlines() if l and not l.startswith("*") and not l.startswith("#")]
    servers = []
    for f in csv.reader(io.StringIO("\n".join(rows))):
        if len(f) < 15:
            continue
        try:
            config = base64.b64decode(f[-1]).decode("utf-8", "replace")
        except Exception:
            continue
        if "remote " not in config:
            continue
        servers.append({
            "id": "vg-" + f[0], "ip": f[1], "score": num(f[2], 0), "country": f[5],
            "cc": f[6].upper(), "source": "VPN Gate", "config": sanitize(config),
        })
    return servers


def fetch_publicvpnlist(key: str) -> list[dict]:
    """Best online OpenVPN records per country, configs downloaded with the approved key."""
    auth = {"Authorization": f"Bearer {key}"}
    url = (f"{PUBLICVPNLIST_API}/servers?protocol=openvpn&status=online&fresh_within=6h"
           f"&min_speed={MIN_MBPS}&sort=technical_quality_score&order=desc&per_page=300")
    records = json.loads(http_get(url, auth))["data"]
    per_cc: dict[str, int] = {}
    servers = []
    for r in records:
        cc = (r.get("country_code") or "").upper()
        if len(cc) != 2 or not r.get("config_download_url"):
            continue
        if per_cc.get(cc, 0) >= cap(cc) * CANDIDATES_PER_SLOT:
            continue
        try:
            config = http_get(r["config_download_url"], auth, timeout=30).decode("utf-8", "replace")
        except Exception:
            continue
        if "remote " not in config:
            continue
        per_cc[cc] = per_cc.get(cc, 0) + 1
        servers.append({
            "id": "pvl-" + str(r["id"]), "ip": r.get("ip") or r.get("hostname"),
            "score": num(r.get("technical_quality_score"), 0) * 1000,
            "country": r.get("country_name") or cc, "cc": cc,
            "source": "PublicVPNList", "config": sanitize(config),
        })
    return servers


def own_servers() -> list[dict]:
    """Volta's own no-log servers (see server/install.sh), from the OWN_SERVERS secret:
    a JSON list of {"cc", "country", "config"}. Always listed first, as tier PRIVATE."""
    raw = os.environ.get("OWN_SERVERS", "").strip()
    if not raw:
        return []
    out = []
    for i, s in enumerate(json.loads(raw)):
        host, _, _ = remote_of(s["config"])
        out.append({
            "id": f"volta-{s['cc'].lower()}-{i}", "ip": host, "score": 10**9, "mbps": 0,
            "country": s["country"], "cc": s["cc"].upper(), "source": "Volta",
            "verified": True, "tier": "PRIVATE", "config": s["config"],
        })
    return out


# ---------- selection ----------

def cap(cc: str) -> int:
    return CAP_TOP if cc in TOP_COUNTRIES else CAP_OTHER


def remote_of(config: str) -> tuple[str, int, str]:
    host, port, proto = "", 1194, "udp"
    for line in config.splitlines():
        p = line.split()
        if not p:
            continue
        if p[0] == "remote" and len(p) >= 2:
            host = p[1]
            if len(p) >= 3:
                port = num(p[2], 1194)
        elif p[0] == "proto" and len(p) >= 2:
            proto = p[1]
    return host, port, proto


def tcp_alive(config: str) -> bool:
    host, port, proto = remote_of(config)
    if not proto.startswith("tcp"):
        return True  # UDP can't be probed cheaply; the live check covers it.
    try:
        with socket.create_connection((host, port), timeout=4):
            return True
    except OSError:
        return False


def candidates(servers: list[dict]) -> list[dict]:
    """Per country, the best-scored few (3x the slots we may fill), de-duplicated by IP."""
    by_cc: dict[str, list[dict]] = {}
    for s in servers:
        by_cc.setdefault(s["cc"], []).append(s)
    out = []
    for cc, lst in by_cc.items():
        seen, picked = set(), []
        for s in sorted(lst, key=lambda s: -s["score"]):
            if s["ip"] in seen or not tcp_alive(s["config"]):
                continue
            seen.add(s["ip"])
            picked.append(s)
            if len(picked) >= cap(cc) * CANDIDATES_PER_SLOT:
                break
        out += picked
    return out


def apply_caps(passed: list[dict]) -> list[dict]:
    """Fastest first; at most 5 per top country and 2 per other country."""
    out, count = [], {}
    for s in sorted(passed, key=lambda s: (-s["mbps"], s["connect_ms"])):
        if count.get(s["cc"], 0) < cap(s["cc"]):
            count[s["cc"]] = count.get(s["cc"], 0) + 1
            out.append(s)
    return out


# ---------- live verification (Linux, root) ----------

_tables: "queue.Queue[int]" = queue.Queue()
for _t in range(PARALLEL):
    _tables.put(200 + _t)


def check_server(server: dict, real_ip: str) -> tuple[dict | None, str]:
    """Brings up a tunnel that only carries traffic sourced from its own address
    (policy routing, one table per worker), so the runner's own connection and the
    other parallel checks are never disturbed. Returns (passed server or None, reason)."""
    table = _tables.get()
    try:
        return _check(server, real_ip, table)
    except Exception as e:  # one bad server must never stop the whole run
        return None, f"error: {type(e).__name__}"
    finally:
        _tables.put(table)


def _check(server: dict, real_ip: str, table: int) -> tuple[dict | None, str]:
    with tempfile.TemporaryDirectory() as d:
        cfg, up, log = (os.path.join(d, n) for n in ("c.ovpn", "up.sh", "ovpn.log"))
        with open(cfg, "w") as fh:
            fh.write(server["config"])
            fh.write("route-nopull\nconnect-retry-max 1\nverb 3\n"
                     "data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-256-CBC:AES-128-CBC\n"
                     f"script-security 2\nup {up}\n")
        with open(up, "w") as fh:
            fh.write(f"#!/bin/sh\nip rule add from $ifconfig_local table {table}\n"
                     f"ip route add default dev $dev table {table}\n"
                     f"echo $ifconfig_local > {d}/ip\n")
        os.chmod(up, 0o755)
        logfh = open(log, "w")
        proc = subprocess.Popen(["openvpn", "--config", cfg], stdout=logfh, stderr=subprocess.STDOUT)
        try:
            ip_file = os.path.join(d, "ip")
            for _ in range(20):
                if os.path.exists(ip_file):
                    break
                time.sleep(1)
            else:
                return None, "tunnel did not come up in 20 s"
            src = open(ip_file).read().strip()
            time.sleep(0.5)
            text = open(log).read()

            cipher = re.search(r"Data Channel: cipher '([^']+)'", text)
            cipher = cipher.group(1).upper() if cipher else ""
            if not cipher or any(w in cipher for w in WEAK_CIPHERS):
                return None, f"weak or unknown data cipher ({cipher or '?'})"
            if "CBC" in cipher and re.search(r"auth '\[?null", text, re.I):
                return None, "CBC without HMAC"
            tls = re.search(r"Control Channel: (TLSv[\d.]+)", text)
            if tls and tls.group(1) in ("TLSv1", "TLSv1.1"):
                return None, f"old control channel {tls.group(1)}"

            def curl(*args: str) -> str:
                return subprocess.run(["curl", "-s", "--max-time", "15", "--interface", src, *args],
                                      capture_output=True, text=True).stdout

            country = exit_country(curl)
            if country != server["cc"]:
                return None, f"geo mismatch (exit country {country or '?'})"
            if real_ip in curl("https://httpbin.org/headers"):
                return None, "real IP leaked in forwarded headers"
            if not verify_tls_via(src):
                return None, "TLS interception or no HTTPS"
            connect = curl("-o", "/dev/null", "-w", "%{time_appconnect}", "https://www.cloudflare.com/cdn-cgi/trace")
            connect_ms = int(float(connect or 99) * 1000)
            if connect_ms > MAX_CONNECT_MS:
                return None, f"slow handshake ({connect_ms} ms)"
            t0 = time.time()
            size = len(curl("https://speed.cloudflare.com/__down?bytes=5000000"))
            mbps = round(size * 8 / 1e6 / max(time.time() - t0, 0.1), 1)
            if mbps < MIN_MBPS:
                return None, f"too slow ({mbps} Mbps)"
            return ({**server, "verified": True, "tier": "VERIFIED", "mbps": mbps,
                     "connect_ms": connect_ms, "cipher": cipher}, f"{mbps} Mbps, {cipher}")
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
            logfh.close()
            subprocess.run(["ip", "rule", "del", "table", str(table)], stderr=subprocess.DEVNULL)
            subprocess.run(["ip", "route", "flush", "table", str(table)], stderr=subprocess.DEVNULL)


def exit_country(curl) -> str:
    """Two independent geo-IP services; the first valid answer wins."""
    code = curl("https://ipinfo.io/country").strip().upper()
    if len(code) == 2 and code.isalpha():
        return code
    try:
        return json.loads(curl("https://api.country.is/")).get("country", "").upper()
    except ValueError:
        return ""


def verify_tls_via(src_ip: str) -> bool:
    """TLS handshake to a known site from the tunnel address must validate
    against the system CA store (a MITM proxy would fail this)."""
    try:
        ctx = ssl.create_default_context()
        with socket.create_connection(("www.cloudflare.com", 443), timeout=10,
                                      source_address=(src_ip, 0)) as sock:
            with ctx.wrap_socket(sock, server_hostname="www.cloudflare.com"):
                return True
    except Exception:
        return False


def verify_all(servers: list[dict]) -> list[dict]:
    real_ip = http_get("https://api.ipify.org", timeout=15).decode().strip()
    passed = []
    with concurrent.futures.ThreadPoolExecutor(PARALLEL) as pool:
        futures = {pool.submit(check_server, s, real_ip): s for s in servers}
        for f in concurrent.futures.as_completed(futures):
            s = futures[f]
            r, why = f.result()
            print(("PASS " if r else "FAIL ") + f"{s['cc']} {s['ip']} [{s['source']}]: {why}", flush=True)
            if r:
                passed.append(r)
    return passed


def sign(payload: bytes, key_path: str) -> str:
    with open(key_path, "rb") as fh:
        key = serialization.load_pem_private_key(fh.read(), password=None)
    return base64.b64encode(key.sign(payload, ec.ECDSA(hashes.SHA256()))).decode()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--key", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--verify", action="store_true")
    args = ap.parse_args()

    pool = fetch_vpngate()
    pvl_key = os.environ.get("PUBLICVPNLIST_KEY", "").strip()
    if pvl_key:
        try:
            pool += fetch_publicvpnlist(pvl_key)
        except Exception as e:
            print(f"PublicVPNList unavailable: {e}", flush=True)
    cands = candidates(pool)
    print(f"{len(pool)} public servers, {len(cands)} candidates", flush=True)

    if args.verify:
        public = apply_caps(verify_all(cands))
    else:  # local dry run: no tunnels, nothing is marked verified
        public = [{**s, "verified": False, "tier": "COMMUNITY", "mbps": 0, "connect_ms": 0}
                  for s in cands][:20]
    servers = own_servers() + public
    if not servers:
        sys.exit("no servers passed - refusing to publish an empty list")

    payload = json.dumps({"generated": int(time.time()), "servers": servers},
                         separators=(",", ":")).encode()
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w") as fh:
        json.dump({"payload": base64.b64encode(payload).decode(), "sig": sign(payload, args.key)}, fh)
    by_cc: dict[str, int] = {}
    for s in public:
        by_cc[s["cc"]] = by_cc.get(s["cc"], 0) + 1
    print(f"published {len(servers)} servers in {len(by_cc)} countries: {by_cc}")


if __name__ == "__main__":
    main()
