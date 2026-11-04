#!/usr/bin/env python3
"""Builds Volta's signed server list from VPN Gate.

Steps: fetch -> sanitize configs -> keep best per country -> (optional) live
verification through a real OpenVPN tunnel -> sign with ECDSA P-256 -> write JSON.

Live verification (--verify, Linux + root + openvpn installed) checks for every server:
  * exit IP geo-location matches the advertised country
  * no X-Forwarded-For / Via / Forwarded header leaks our real IP
  * TLS to well-known sites is not intercepted (certificate issuer sanity check)
  * measured download speed

Usage:
  python build_list.py --key keys/private.pem --out public/servers.json [--verify]
"""
import argparse
import base64
import csv
import io
import json
import os
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
PER_COUNTRY = 6
MAX_VERIFY = 40

BLOCKED = {
    "script-security", "up", "down", "route-up", "route-pre-down", "ipchange",
    "tls-verify", "client-connect", "client-disconnect", "learn-address",
    "auth-user-pass-verify", "plugin", "setenv", "setenv-safe", "management",
    "management-hold", "log", "log-append", "status", "writepid", "daemon",
    "chroot", "user", "group", "cd", "redirect-private", "dhcp-option",
    "block-outside-dns", "pull-filter", "route-nopull", "allow-pull-fqdn",
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


def fetch_vpngate() -> list[dict]:
    req = urllib.request.Request(VPNGATE_API, headers={"User-Agent": "VoltaVPN-builder"})
    text = urllib.request.urlopen(req, timeout=60).read().decode("utf-8", "replace")
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
            "id": f[0], "ip": f[1], "score": int(f[2] or 0), "ping": int(f[3] or 999),
            "mbps": round(int(f[4] or 0) / 1e6, 1), "country": f[5], "cc": f[6],
            "verified": False, "config": sanitize(config),
        })
    return servers


def remote_of(config: str) -> tuple[str, int, str]:
    host, port, proto = "", 1194, "udp"
    for line in config.splitlines():
        p = line.split()
        if not p:
            continue
        if p[0] == "remote" and len(p) >= 2:
            host = p[1]
            if len(p) >= 3:
                port = int(p[2])
        elif p[0] == "proto" and len(p) >= 2:
            proto = p[1]
    return host, port, proto


def tcp_alive(config: str) -> bool:
    host, port, proto = remote_of(config)
    if not proto.startswith("tcp"):
        return True  # UDP can't be probed cheaply; live verify covers it.
    try:
        with socket.create_connection((host, port), timeout=4):
            return True
    except OSError:
        return False


def best_per_country(servers: list[dict]) -> list[dict]:
    by_cc: dict[str, list[dict]] = {}
    for s in servers:
        by_cc.setdefault(s["cc"], []).append(s)
    out = []
    for lst in by_cc.values():
        lst.sort(key=lambda s: (-s["score"], s["ping"]))
        out += lst[:PER_COUNTRY]
    return out


# ---------- live verification (Linux, root) ----------

def run_in_tunnel(server: dict, real_ip: str) -> dict | None:
    """Brings up a tunnel that only carries traffic sourced from its own address
    (policy routing), so the CI runner's own connection is never disturbed."""
    with tempfile.TemporaryDirectory() as d:
        cfg = os.path.join(d, "c.ovpn")
        up = os.path.join(d, "up.sh")
        with open(cfg, "w") as fh:
            fh.write(server["config"])
            fh.write("route-nopull\nconnect-retry-max 1\n"
                     "data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC\n"
                     f"script-security 2\nup {up}\n")
        with open(up, "w") as fh:
            fh.write("#!/bin/sh\nip rule add from $ifconfig_local table 123\n"
                     "ip route add default dev $dev table 123\n"
                     f"echo $ifconfig_local > {d}/ip\n")
        os.chmod(up, 0o755)
        proc = subprocess.Popen(["openvpn", "--config", cfg], stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL)
        try:
            ip_file = os.path.join(d, "ip")
            for _ in range(25):
                if os.path.exists(ip_file):
                    break
                time.sleep(1)
            else:
                return None
            src = open(ip_file).read().strip()

            def curl(*args: str) -> str:
                return subprocess.run(["curl", "-s", "--max-time", "15", "--interface", src, *args],
                                      capture_output=True, text=True).stdout

            geo = json.loads(curl("https://ipinfo.io/json") or "{}")
            headers = curl("https://httpbin.org/headers")
            # Any header carrying our real IP (X-Forwarded-For, Via, Forwarded...) is a leak.
            leak = real_ip in headers
            t0 = time.time()
            size = len(curl("https://speed.cloudflare.com/__down?bytes=5000000"))
            mbps = round(size * 8 / 1e6 / max(time.time() - t0, 0.1), 1)
            ok_geo = geo.get("country", "").upper() == server["cc"].upper()
            ok_tls = verify_tls_via(src)
            if ok_geo and not leak and ok_tls and size > 1_000_000:
                return {**server, "verified": True, "mbps": mbps}
            return None
        finally:
            proc.terminate()
            proc.wait(timeout=10)
            subprocess.run(["ip", "rule", "del", "table", "123"], stderr=subprocess.DEVNULL)


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
    real_ip = urllib.request.urlopen("https://api.ipify.org", timeout=15).read().decode()
    verified = []
    for s in servers[:MAX_VERIFY]:
        r = run_in_tunnel(s, real_ip)
        print(("PASS " if r else "FAIL ") + f"{s['cc']} {s['ip']}", flush=True)
        if r:
            verified.append(r)
    return verified


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

    servers = [s for s in best_per_country(fetch_vpngate()) if tcp_alive(s["config"])]
    servers.sort(key=lambda s: (-s["score"], s["ping"]))
    if args.verify:
        verified = verify_all(servers)
        ids = {v["id"] for v in verified}
        servers = verified + [s for s in servers if s["id"] not in ids]
    if not servers:
        sys.exit("no servers - refusing to publish an empty list")

    payload = json.dumps({"generated": int(time.time()), "servers": servers},
                         separators=(",", ":")).encode()
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w") as fh:
        json.dump({"payload": base64.b64encode(payload).decode(), "sig": sign(payload, args.key)}, fh)
    print(f"wrote {len(servers)} servers ({sum(s['verified'] for s in servers)} verified)")


if __name__ == "__main__":
    main()
