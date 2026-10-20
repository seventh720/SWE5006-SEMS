#!/usr/bin/env python3
"""Run on the SWE5006 host: restore one existing dump into a disposable database."""
import argparse
import datetime
import difflib
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('backup', type=Path)
parser.add_argument('report', type=Path)
args = parser.parse_args()
backup = args.backup.resolve(strict=True)
if backup.parent != Path('/home/ubuntu/swe5006/backups') or backup.suffix != '.dump':
    parser.error('Select a .dump from the SWE5006 backups directory')
container = 'swe5006-postgres-1'
prefix = ['sudo', '-n', 'docker', 'exec', '-i', container, 'sh', '-c']


def run(program, database=None, sql=None, stdin=None):
    commands = {
        'database': 'printf "%s" "$POSTGRES_DB"',
        'query': 'exec psql -XqAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1"',
        'create': 'exec createdb -U "$POSTGRES_USER" -T template0 "$1"',
        'drop': 'exec dropdb -U "$POSTGRES_USER" "$1"',
        'restore': 'exec pg_restore --exit-on-error --single-transaction --no-owner --no-acl -U "$POSTGRES_USER" -d "$1"',
        'schema': 'exec pg_dump -s --no-owner --no-acl --no-comments -U "$POSTGRES_USER" -d "$1"',
    }
    command = prefix + [commands[program], 'sh'] + ([] if database is None else [database])
    result = subprocess.run(command, input=None if sql is None else sql.encode(), stdin=stdin,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return result.stdout.decode()


def counts(database, tables):
    statements = [f'SELECT \'{name}\', count(*) FROM public."{name}"' for name in tables]
    rows = run('query', database, ' UNION ALL '.join(statements) + ';').splitlines()
    return {row.split('|')[0]: int(row.split('|')[1]) for row in rows}


def schema_text(database):
    schema = run('schema', database)
    # pg_dump randomises psql restriction keys; they do not describe schema.
    normalized = '\n'.join(line for line in schema.splitlines()
                           if not line.startswith(('\\restrict ', '\\unrestrict ')))
    return normalized


started = time.monotonic()
report = {'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'backup': backup.name, 'backupBytes': backup.stat().st_size,
          'backupSha256': hashlib.sha256(backup.read_bytes()).hexdigest()}
source = run('database').strip()
isolated = 'sems_restore_audit_' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d_%H%M%S')
created = False
schema_database = isolated + '_schema'
schema_created = False
try:
    tables = run('query', source, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;").splitlines()
    assert tables and all(re.fullmatch('[a-z_][a-z0-9_]*', name) for name in tables)
    live_counts = counts(source, tables)
    run('create', isolated)
    created = True
    with backup.open('rb') as archive:
        run('restore', isolated, stdin=archive)
    restored_counts = counts(isolated, tables)
    live_schema = schema_text(source)
    restored_schema = schema_text(isolated)
    # PostgreSQL may deparse casts differently after a dump/restore. Parse the
    # live schema in a second empty database before comparing its meaning.
    run('create', schema_database)
    schema_created = True
    run('query', schema_database, live_schema)
    canonical_live_schema = schema_text(schema_database)
    report.update({'isolatedDatabase': isolated, 'liveCounts': live_counts, 'restoredCounts': restored_counts,
                   'countsMatchLiveAtCheck': restored_counts == live_counts,
                   'rawLiveSchemaSha256': hashlib.sha256(live_schema.encode()).hexdigest(),
                   'canonicalLiveSchemaSha256': hashlib.sha256(canonical_live_schema.encode()).hexdigest(), 'restoredSchemaSha256': hashlib.sha256(restored_schema.encode()).hexdigest(),
                   'unvalidatedConstraints': int(run('query', isolated, "SELECT count(*) FROM pg_constraint WHERE connamespace='public'::regnamespace AND NOT convalidated;"))})
    report['restoredMigrationHistoryMatchesLive'] = (
        run('query', source, 'SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank;') ==
        run('query', isolated, 'SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank;'))
    if live_schema != restored_schema:
        report['schemaDiff'] = list(difflib.unified_diff(live_schema.splitlines(), restored_schema.splitlines(), fromfile='live', tofile='restored'))
    report['schemaMatchesAfterServerParse'] = canonical_live_schema == restored_schema
    assert report['schemaMatchesAfterServerParse'], 'Schema differs after server parsing'
    assert report['restoredMigrationHistoryMatchesLive'], 'Migration history differs'
    assert report['unvalidatedConstraints'] == 0, 'Restore left unvalidated constraints'
    report['status'] = 'PASS'
except Exception as error:
    report['status'] = 'FAIL'
    report['error'] = str(error)
    raise
finally:
    if schema_created:
        run('drop', schema_database)
        report['isolatedSchemaDatabaseDropped'] = True
    if created:
        run('drop', isolated)
        report['isolatedDatabaseDropped'] = True
    report['durationSeconds'] = round(time.monotonic() - started, 3)
    args.report.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    args.report.chmod(0o600)
    print(json.dumps(report, indent=2))
