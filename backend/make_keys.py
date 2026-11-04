#!/usr/bin/env python3
"""Generates the server-list signing key pair (run once).

Prints the public key in the form the app expects for SERVER_LIST_PUBKEY.
Keep keys/private.pem secret: store it as the GitHub secret SERVER_LIST_KEY.
"""
import base64
import os

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

os.makedirs("keys", exist_ok=True)
key = ec.generate_private_key(ec.SECP256R1())
with open("keys/private.pem", "wb") as fh:
    fh.write(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                               serialization.NoEncryption()))
spki = key.public_key().public_bytes(serialization.Encoding.DER,
                                     serialization.PublicFormat.SubjectPublicKeyInfo)
print("SERVER_LIST_PUBKEY =", base64.b64encode(spki).decode())
