import hashlib
import hmac
import os
import secrets
import time
import jwt

def hash_password(password):
    salt = secrets.token_hex(16)
    digest = hashlib.scrypt(password.encode(), salt=salt.encode(), n=16384, r=8, p=1).hex()
    return f'scrypt${salt}${digest}'

def verify_password(password, encoded):
    try:
        _, salt, expected = encoded.split('$')
        actual = hashlib.scrypt(password.encode(), salt=salt.encode(), n=16384, r=8, p=1).hex()
        return hmac.compare_digest(actual, expected)
    except ValueError:
        return False

def secret():
    value = os.getenv('JWT_SECRET', '')
    if len(value) < 32:
        raise RuntimeError('Configure JWT_SECRET com pelo menos 32 caracteres.')
    return value

def token(user_id):
    ttl = int(os.getenv('TOKEN_TTL_SECONDS', '3600'))
    issued = int(time.time())
    return jwt.encode({'sub': str(user_id), 'iat': issued, 'exp': issued + ttl,
                       'iss': 'raizes-do-nordeste', 'aud': 'raizes-api'}, secret(), algorithm='HS256'), ttl

def decode(value):
    return jwt.decode(value, secret(), algorithms=['HS256'], audience='raizes-api',
                      issuer='raizes-do-nordeste', options={'require': ['sub', 'iat', 'exp']})
