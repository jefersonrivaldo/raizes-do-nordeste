"""Cria configuração privada local sem sobrescrever arquivos existentes."""
import os
import secrets
from pathlib import Path
root = Path(__file__).resolve().parents[1]
try:
    fd = os.open(root / '.env', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    print('.env existente preservado.')
else:
    with os.fdopen(fd, 'w') as f:
        f.write('DATABASE_PATH=data/raizes.sqlite3\n')
        f.write('JWT_SECRET=' + secrets.token_hex(32) + '\n')
        f.write('TOKEN_TTL_SECONDS=3600\n')
        f.write('SEED_ADMIN_PASSWORD=Estudo' + secrets.token_hex(16) + '9\n')
    print('.env privado criado com segredos aleatórios. Nenhum valor foi exibido.')
