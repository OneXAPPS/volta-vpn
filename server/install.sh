#!/usr/bin/env bash
# Volta private server: a no-log OpenVPN server for a free Oracle Cloud (or any Ubuntu 22.04+/Debian 12) VM.
#
#   curl -fsSL https://raw.githubusercontent.com/OneXAPPS/volta-vpn/main/server/install.sh | sudo bash
#   (options: sudo PROTO=tcp PORT=443 bash install.sh   -> harder to block, a bit slower)
#
# At the end it prints one JSON entry; add it to the GitHub secret OWN_SERVERS
# (a JSON list) and the next hourly run publishes it in the app as a VOLTA location.
set -euo pipefail

PROTO="${PROTO:-udp}"
PORT="${PORT:-1194}"
[ "$(id -u)" = 0 ] || { echo "Run as root (sudo)."; exit 1; }

PUBLIC_IP="$(curl -fsS https://api.ipify.org)"
COUNTRY="$(curl -fsS "https://api.country.is/${PUBLIC_IP}" | sed -n 's/.*"country":"\([A-Z]*\)".*/\1/p')"

apt-get update -q
DEBIAN_FRONTEND=noninteractive apt-get install -yq openvpn easy-rsa iptables-persistent

# --- PKI: modern elliptic-curve certificates, no passwords -------------------
PKI=/etc/openvpn/server/pki-dir
rm -rf "$PKI" && make-cadir "$PKI" && cd "$PKI"
cat > vars <<'EOF'
set_var EASYRSA_ALGO ec
set_var EASYRSA_CURVE prime256v1
set_var EASYRSA_DIGEST sha256
set_var EASYRSA_CA_EXPIRE 3650
set_var EASYRSA_CERT_EXPIRE 3650
set_var EASYRSA_BATCH 1
EOF
./easyrsa init-pki
EASYRSA_REQ_CN="Volta CA" ./easyrsa build-ca nopass
./easyrsa build-server-full server nopass
# One shared client identity for all app users (anonymous: nothing ties a user to it).
./easyrsa build-client-full volta nopass
openvpn --genkey secret /etc/openvpn/server/tc.key

cp pki/ca.crt pki/issued/server.crt pki/private/server.key /etc/openvpn/server/

# --- Server config: strong ciphers, no logs ----------------------------------
cat > /etc/openvpn/server/server.conf <<EOF
port $PORT
proto $PROTO
dev tun
ca ca.crt
cert server.crt
key server.key
dh none
ecdh-curve prime256v1
tls-crypt tc.key
tls-version-min 1.2
data-ciphers AES-256-GCM:CHACHA20-POLY1305
auth SHA256
topology subnet
server 10.8.0.0 255.255.255.0
push "redirect-gateway def1 bypass-dhcp"
push "block-ipv6"
push "dhcp-option DNS 1.1.1.1"
push "dhcp-option DNS 1.0.0.1"
duplicate-cn
keepalive 10 60
user nobody
group nogroup
persist-key
persist-tun
# No logs: nothing about users is written anywhere.
verb 0
mute 10
status /dev/null
log /dev/null
EOF
[ "$PROTO" = udp ] && echo "explicit-exit-notify 1" >> /etc/openvpn/server/server.conf

# --- Routing / firewall (Oracle images ship REJECT rules: insert ours first) --
echo 'net.ipv4.ip_forward=1' > /etc/sysctl.d/99-volta.conf
sysctl -p /etc/sysctl.d/99-volta.conf
WAN="$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}')"
iptables -I INPUT -p "$PROTO" --dport "$PORT" -j ACCEPT
iptables -I FORWARD -s 10.8.0.0/24 -j ACCEPT
iptables -I FORWARD -m state --state RELATED,ESTABLISHED -j ACCEPT
iptables -t nat -A POSTROUTING -s 10.8.0.0/24 -o "$WAN" -j MASQUERADE
netfilter-persistent save

systemctl enable --now openvpn-server@server
systemctl restart openvpn-server@server

# --- Client profile + the JSON line for OWN_SERVERS ---------------------------
CLIENT=/root/volta-client.ovpn
{
  echo "client"; echo "dev tun"; echo "proto $PROTO"; echo "remote $PUBLIC_IP $PORT"
  echo "resolv-retry infinite"; echo "nobind"; echo "remote-cert-tls server"
  echo "data-ciphers AES-256-GCM:CHACHA20-POLY1305"; echo "auth SHA256"; echo "verb 0"
  echo "<ca>"; cat pki/ca.crt; echo "</ca>"
  echo "<cert>"; sed -n '/BEGIN/,/END/p' pki/issued/volta.crt; echo "</cert>"
  echo "<key>"; cat pki/private/volta.key; echo "</key>"
  echo "<tls-crypt>"; cat /etc/openvpn/server/tc.key; echo "</tls-crypt>"
} > "$CLIENT"
chmod 600 "$CLIENT"

echo
echo "=== Volta server is running on $PUBLIC_IP ($COUNTRY) $PROTO/$PORT ==="
echo "Oracle Cloud: also open $PROTO port $PORT in the VCN Security List (Ingress rule, source 0.0.0.0/0)."
echo
echo "Add this entry to the GitHub secret OWN_SERVERS (inside a JSON list [ ... ]):"
python3 - "$CLIENT" "$COUNTRY" <<'PY'
import json, sys
names = {"DE": "Germany", "NL": "Netherlands", "GB": "United Kingdom", "US": "United States",
         "FR": "France", "JP": "Japan", "SG": "Singapore", "CA": "Canada", "AU": "Australia",
         "IN": "India", "KR": "Korea", "BR": "Brazil", "AE": "United Arab Emirates",
         "SA": "Saudi Arabia", "SE": "Sweden", "CH": "Switzerland", "IT": "Italy", "ES": "Spain"}
cc = sys.argv[2] or "US"
print(json.dumps({"cc": cc, "country": names.get(cc, cc), "config": open(sys.argv[1]).read()}))
PY
