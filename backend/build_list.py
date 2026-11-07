#!/usr/bin/env python3
"""Builds Volta's signed server list from public OpenVPN sources, keeping only
servers that pass strict live checks.

Sources:
  * VPN Gate academic project CSV API, or its GitHub mirror (9xN/auto-ovpn) when
    vpngate.net is unreachable. Servers whose operator says "Academic Use Only" are
    skipped (Volta is ad-supported), as are servers that keep logs permanently.
  * PublicVPNList API (only when PUBLICVPNLIST_KEY is set: an individually approved key,
    as its license forbids automating the 24-hour reCAPTCHA keys)
  * RiseupVPN (riseup.net, audited no-log nonprofit) via its official LEAP API. Its
    configs carry no client key: each phone fetches its own anonymous certificate from
    Riseup when connecting, exactly like Riseup's own app (tier NOLOG).
  * Volta's own servers from OWN_SERVERS (listed first as tier PRIVATE)

Every public candidate is connected through a real OpenVPN tunnel and must pass ALL of:
  * tunnel up within 20 s
  * negotiated data cipher is AES-GCM / ChaCha20 / AES-CBC with a real HMAC (no BF, DES, none)
  * TLS 1.2+ control channel
  * exit IP geo-location matches the advertised country (two geo services)
  * our real IP appears in no forwarded header (X-Forwarded-For, Via, Forwarded...)
  * HTTPS to a known site validates against the system CA store (no interception)
  * download >= MIN_MBPS and TLS connect time <= MAX_CONNECT_MS through the tunnel

Caps: at most 5 servers for each priority country (TOP_COUNTRIES + PRIORITY_COUNTRIES),
2 for every other country.

Local-site check: for countries in GEO_SITES, sites that only answer inside that
country are fetched directly from the builder and through the tunnel; a server is
marked as unlocking a site only when the direct fetch fails and the tunnel succeeds.

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
VPNGATE_MIRROR = "https://raw.githubusercontent.com/9xN/auto-ovpn/main/json/data.json"
PUBLICVPNLIST_API = "https://publicvpnlist.com/api/v1"
RISEUP_PROVIDER = "https://riseup.net/provider.json"

# Most requested VPN locations; everything else is capped lower.
TOP_COUNTRIES = ["US", "GB", "DE", "NL", "FR", "CA", "JP", "SG", "KR", "TR"]
# Gulf, Middle East and the largest expatriate home countries: always worth 5 slots.
PRIORITY_COUNTRIES = [
    "AE", "SA", "QA", "KW", "BH", "OM",                      # Gulf
    "EG", "JO", "LB", "SY", "IQ", "YE", "PS", "MA", "TN", "DZ",  # Middle East / North Africa
    "IN", "CN", "MX", "RU", "UA", "PH", "VE", "PK", "BD",    # largest diasporas
]
CAP_TOP, CAP_OTHER = 5, 2
# Sites known to refuse foreign visitors (checked live; see module docstring).
GEO_SITES = {
    "RU": ["https://www.kremlin.ru", "https://government.ru"],
    "MX": ["https://www.sat.gob.mx"],
    "VE": ["https://www.cne.gob.ve"],
    "IN": ["https://www.irctc.co.in"],
    "PH": ["https://www.philsys.gov.ph"],
    "SA": ["https://www.my.gov.sa", "https://www.absher.sa"],
    "QA": ["https://www.hukoomi.gov.qa"],
    "KW": ["https://www.e.gov.kw"],
    "EG": ["https://www.egypt.gov.eg"],
    "SY": ["https://www.egov.sy"],
    "UA": ["https://www.diia.gov.ua"],
}
EXCLUDE_ACADEMIC_ONLY = True
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
    """Rows as dicts with VPN Gate's lower-cased field names, from the API or its mirror."""
    try:
        text = http_get(VPNGATE_API).decode("utf-8", "replace")
        lines = [l for l in text.splitlines() if l and not l.startswith("*")]
        header = lines[0].lstrip("#").lower().split(",")
        rows = [dict(zip(header, f)) for f in csv.reader(io.StringIO("\n".join(lines[1:]))) if len(f) >= 15]
        if not rows:
            raise ValueError("empty")
    except Exception as e:
        print(f"VPN Gate API unavailable ({e}); using GitHub mirror", flush=True)
        rows = [r for block in json.loads(http_get(VPNGATE_MIRROR)) if isinstance(block, dict)
                for r in block.get("servers", [])]
    servers = []
    for r in rows:
        operator = (r.get("operator") or "") + " " + (r.get("message") or "")
        if EXCLUDE_ACADEMIC_ONLY and "academic use only" in operator.lower():
            continue
        if "perman" in (r.get("logtype") or "").lower():
            continue
        try:
            config = base64.b64decode(r.get("openvpn_configdata_base64") or "").decode("utf-8", "replace")
        except Exception:
            continue
        if "remote " not in config:
            continue
        servers.append({
            "id": "vg-" + r.get("hostname", r.get("ip", "")), "ip": r.get("ip", ""),
            "score": num(r.get("score"), 0), "country": r.get("countrylong", ""),
            "cc": (r.get("countryshort") or "").upper(), "source": "VPN Gate",
            "config": sanitize(config),
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


def fetch_riseup() -> list[dict]:
    """Riseup gateways as OpenVPN configs (without client key; see module docstring)."""
    import hashlib
    provider = json.loads(http_get(RISEUP_PROVIDER))
    ca = http_get(provider["ca_cert_uri"]).decode()
    # Pin the CA to the fingerprint Riseup publishes, so a tampered CA is never shipped.
    der = ssl.PEM_cert_to_DER_cert(ca)
    want = provider["ca_cert_fingerprint"].split(":", 1)[1].strip().lower()
    if hashlib.sha256(der).hexdigest() != want:
        raise ValueError("Riseup CA fingerprint mismatch")
    api = provider["api_uri"].rstrip("/") + "/" + str(provider["api_version"])
    eip = json.loads(http_get(api + "/config/eip-service.json"))
    locations = eip.get("locations", {})
    servers = []
    for g in eip.get("gateways", []):
        ovpn = next((t for t in g["capabilities"]["transport"] if t["type"] == "openvpn"), None)
        cc = (locations.get(g.get("location"), {}).get("country_code") or "").upper()
        if not ovpn or len(cc) != 2:
            continue
        port = "1194" if "1194" in ovpn["ports"] else ovpn["ports"][0]
        remotes = "".join(f"remote {g['ip_address']} {port} {proto}\n"
                          for proto in ("udp", "tcp") if proto in ovpn["protocols"])
        config = ("client\ndev tun\n" + remotes +
                  "nobind\npersist-key\nremote-cert-tls server\ntls-version-min 1.2\n"
                  "tls-cipher TLS-ECDHE-ECDSA-WITH-AES-256-GCM-SHA384\n"
                  "cipher AES-256-GCM\ndata-ciphers AES-256-GCM\nauth SHA512\n"
                  f"<ca>\n{ca.strip()}\n</ca>\n")
        servers.append({
            "id": "riseup-" + g["host"], "ip": g["ip_address"], "score": 10**8,
            "country": g.get("location") or cc, "cc": cc, "source": "Riseup",
            "cert_source": "riseup", "cert_url": api + "/cert", "config": config,
        })
    return servers


def riseup_client_cert(url: str) -> str:
    """A fresh anonymous Riseup client key + certificate, as inline OpenVPN blocks."""
    pem = http_get(url, timeout=30).decode()
    key = re.search(r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", pem, re.S)
    cert = re.search(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", pem, re.S)
    return f"<key>\n{key.group(0)}\n</key>\n<cert>\n{cert.group(0)}\n</cert>\n"


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
    return CAP_TOP if cc in TOP_COUNTRIES or cc in PRIORITY_COUNTRIES else CAP_OTHER


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
            if server.get("cert_source") == "riseup":
                fh.write(riseup_client_cert(server["cert_url"]))
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
            unlocks = [site for site in GEO_SITES.get(server["cc"], [])
                       if not reachable(site) and site_ok(curl("-L", "-o", "/dev/null", "-w", "%{http_code}", site))]
            tier = "NOLOG" if server.get("cert_source") == "riseup" else "VERIFIED"
            return ({**server, "verified": True, "tier": tier, "mbps": mbps,
                     "connect_ms": connect_ms, "cipher": cipher,
                     "unlocks": [u.split("//")[1].removeprefix("www.") for u in unlocks]},
                    f"{mbps} Mbps, {cipher}" + (f", unlocks {len(unlocks)} local site(s)" if unlocks else ""))
        finally:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
            logfh.close()
            subprocess.run(["ip", "rule", "del", "table", str(table)], stderr=subprocess.DEVNULL)
            subprocess.run(["ip", "route", "flush", "table", str(table)], stderr=subprocess.DEVNULL)


def site_ok(code: str) -> bool:
    return code[:1] in ("2", "3")


_direct: dict[str, bool] = {}


def reachable(site: str) -> bool:
    """Does the site answer the builder directly (i.e. NOT geo-fenced from here)? Cached."""
    if site not in _direct:
        code = subprocess.run(["curl", "-s", "-L", "--max-time", "15", "-o", "/dev/null",
                               "-w", "%{http_code}", "-A", "Mozilla/5.0", site],
                              capture_output=True, text=True).stdout
        _direct[site] = site_ok(code)
    return _direct[site]


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
    try:
        pool += fetch_riseup()
    except Exception as e:
        print(f"Riseup unavailable: {e}", flush=True)
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
