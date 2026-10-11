import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
def now():
    return datetime.now(timezone.utc).isoformat()

def connect():
    path = Path(os.getenv('DATABASE_PATH', str(ROOT / 'data/raizes.sqlite3')))
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute('PRAGMA foreign_keys=ON')
    db.execute('PRAGMA journal_mode=WAL')
    return db

@contextmanager
def transaction():
    db = connect()
    try:
        db.execute('BEGIN IMMEDIATE')
        yield db
        db.commit()
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

def migrate():
    db = connect()
    try:
        db.execute('CREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY, applied_at TEXT NOT NULL)')
        db.commit()
        for file in sorted((ROOT / 'migrations').glob('*.sql')):
            if not db.execute('SELECT 1 FROM schema_migrations WHERE version=?', (file.name,)).fetchone():
                escaped = file.name.replace("'", "''")
                db.executescript('BEGIN IMMEDIATE;\n' + file.read_text() +
                    f"\nINSERT INTO schema_migrations VALUES ('{escaped}', '{now()}');\nCOMMIT;")
    finally:
        db.close()

if __name__ == '__main__':
    migrate()
    print('Migrations aplicadas.')
