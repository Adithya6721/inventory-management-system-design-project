"""Run real supplier checks against isolated databases; preserve the seeded demo."""
from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]


def main():
    run_id = datetime.now().strftime('%Y%m%d-%H%M%S')
    output = ROOT / 'evaluation' / 'results' / (run_id + '-supplier-integration')
    jar = ROOT / 'backend' / 'target' / 'inventory-benchmark.jar'
    if not jar.exists():
        raise RuntimeError('Build first: mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests -q')
    for port in (27019, 6379):
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) != 0:
                raise RuntimeError(f'Service port {port} unavailable. Start MongoDB and Redis first.')
    for port in (8082, 8086):
        with socket.socket() as probe:
            if probe.connect_ex(('127.0.0.1', port)) == 0:
                raise RuntimeError(f'Port {port} is occupied; stop its test process first.')
    output.mkdir(parents=True)
    database = 'inventory_supplier_test_' + run_id.replace('-', '_')
    environment = dict(os.environ, MONGO_URI=f'mongodb://127.0.0.1:27019/{database}?replicaSet=inventory-rs',
        SHARDING_ENABLED='true', CACHE_ENABLED='true', CACHE_NAMESPACE=database,
        REDIS_HOST='127.0.0.1', REDIS_PORT='6379', EVALUATION_ENABLED='false', RATELIMIT_ENABLED='false',
        SNOWFLAKE_NODE_ID='8', PORT='8082', SERVER_PORT='8082', SERVER_ADDRESS='127.0.0.1',
        LOGGING_LEVEL_COM_EXAMPLE_INVENTORY='WARN',
        LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_MONGODB_CORE_MONGOTEMPLATE='WARN')
    for index in range(1, 5):
        environment[f'SHARD_DB_{index}'] = f'{database}_shard_{index}'
    processes = []
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    backend_log = (output / 'backend.log').open('w', encoding='utf-8')
    supplier_log = (output / 'supplier.log').open('w', encoding='utf-8')
    try:
        processes.append(subprocess.Popen([shutil.which('java'), '-jar', str(jar)], cwd=ROOT,
            env=environment, stdout=backend_log, stderr=subprocess.STDOUT, creationflags=flags))
        supplier_env = dict(environment, PORT='8086')
        processes.append(subprocess.Popen([shutil.which('node'), 'crawler/supplier_server.js'], cwd=ROOT,
            env=supplier_env, stdout=supplier_log, stderr=subprocess.STDOUT, creationflags=flags))
        for url in ('http://127.0.0.1:8082/actuator/health', 'http://127.0.0.1:8086/health'):
            for attempt in range(120):
                if any(process.poll() is not None for process in processes):
                    raise RuntimeError(f'Test service exited. Inspect {output}')
                try:
                    with urlopen(url, timeout=2) as response:
                        if json.load(response)['status'] == 'UP':
                            break
                except Exception:
                    time.sleep(0.5)
            else:
                raise RuntimeError('Service did not start: ' + url)
        test_env = dict(environment, BACKEND_API_URL='http://127.0.0.1:8082',
            SUPPLIER_BASE_URL='http://127.0.0.1:8086', SUPPLIER_REPORT=str(output / 'summary.json'))
        for script in ('crawler/test_crawler.js', 'scripts/e2e_integration_demo.js'):
            result = subprocess.run([shutil.which('node'), script], cwd=ROOT, env=test_env,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, creationflags=flags, timeout=120)
            (output / (Path(script).stem + '.txt')).write_text(result.stdout, encoding='utf-8')
            print(result.stdout, flush=True)
            if result.returncode:
                raise RuntimeError(f'{script} failed; see {output}')
        print('PASS. Evidence:', output)
        print('Isolated test databases retained:', database)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        backend_log.close()
        supplier_log.close()


if __name__ == '__main__':
    main()
