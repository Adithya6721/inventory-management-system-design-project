"""Small reproducible component experiments. HTTP traffic comes from JMeter."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
import csv
import datetime as dt
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import time
import zipfile

import psutil

spec = importlib.util.spec_from_file_location('cache_runner', Path(__file__).with_name('run-jmeter-cache.py'))
common = importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)
ROOT, http, write_json, percentile = common.ROOT, common.http, common.write_json, common.percentile
FIELDS = ['kind', 'path', 'productId', 'warehouseId', 'sku', 'body']


def dataset(path, rows):
    with path.open('w', newline='', encoding='utf-8') as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows({field: row.get(field, '') for field in FIELDS} for row in rows)
    return path


def describe(rows, seconds):
    elapsed = [float(row['elapsed']) for row in rows]
    accepted = [r for r in rows if r['outcome'] in ('accepted', 'reserved') and r['success'] == 'true']
    unexpected = sum(r['success'] != 'true' for r in rows)
    lags = [float(r['scheduledLagMs']) for r in rows if r.get('scheduledLagMs')]
    return {'requests': len(rows), 'seconds': seconds,
            'mean_ms': statistics.mean(elapsed) if elapsed else None,
            'p95_ms': percentile(elapsed, .95) if elapsed else None,
            'max_ms': max(elapsed, default=None), 'attempted_rps': len(rows) / seconds,
            'accepted_rps': len(accepted) / seconds, 'unexpected_errors': unexpected,
            'unexpected_error_pct': 100 * unexpected / len(rows) if rows else None,
            'status_counts': dict(Counter(r['responseCode'] for r in rows)),
            'outcomes': dict(Counter(r['outcome'] for r in rows)),
            'accepted_mean_ms': statistics.mean(float(r['elapsed']) for r in accepted) if accepted else None,
            'accepted_p95_ms': percentile([float(r['elapsed']) for r in accepted], .95) if accepted else None,
            'rejection_pct': 100 * sum(r['outcome'] == 'rate_limited' for r in rows) / len(rows) if rows else None,
            'p95_scheduling_lag_ms': percentile(lags, .95) if lags else None,
            'max_scheduling_lag_ms': max(lags, default=None)}


class Suite:
    def __init__(self, args):
        self.args = args
        self.tag = dt.datetime.now().strftime('%Y%m%d-%H%M%S')
        self.output = ROOT / 'evaluation/results' / (self.tag + '-components')
        self.output.mkdir(parents=True)
        self.backend = None
        self.logs = []
        self.points = []
        self.java = shutil.which('java')
        self.jar = ROOT / 'backend/target/inventory-benchmark.jar'
        home = args.jmeter_home or os.environ.get('JMETER_HOME', '')
        installed = Path(os.environ.get('ProgramFiles', 'C:/Program Files')) / 'apache-jmeter-5.6.3'
        self.jmeter = Path(home) / 'bin/ApacheJMeter.jar' if home else installed / 'bin/ApacheJMeter.jar'
        self.database_prefix = 'inventory_quick_' + self.tag.replace('-', '_')
        self.url = f'http://127.0.0.1:{args.port}'
        self.manifest = {'started_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
                         'revision': common.git('rev-parse', 'HEAD'), 'settings': vars(args),
                         'logical_cpus': psutil.cpu_count(), 'ram_gib': psutil.virtual_memory().total / 1024**3,
                         'os': common.platform.platform(), 'java': self.java, 'jmeter_jar': str(self.jmeter),
                         'jar_sha256': hashlib.sha256(self.jar.read_bytes()).hexdigest() if self.jar.exists() else None,
                         'runner_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                         'algorithm_source_sha256': {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in
                            ['evaluation/benchmarks/ComponentBenchmarks.java','evaluation/benchmarks/autocomplete.mjs','frontend/src/services/trie.js']},
                         'jmx_sha256': hashlib.sha256((ROOT / 'evaluation/jmeter/components.jmx').read_bytes()).hexdigest(),
                         'scope': 'Quick comparative runs, not production capacity or physical database scale-out',
                         'backend_cache_enabled': False, 'stock_transactions_enabled': True,
                         'topology': 'one benchmark backend at a time; existing shared one-member MongoDB replica set; background apps remain running'}
        write_json(self.output / 'manifest.json', self.manifest)
        self.progress('Results: ' + str(self.output))

    def progress(self, message):
        print(message, flush=True)
        write_json(self.output / 'progress.json', {'message': message, 'completed_http_runs': len(self.points)})

    def start(self, mode):
        self.stop()
        common.require_free(self.args.port)
        self.database = self.database_prefix + ('_single' if mode == 'limited' else '_' + mode)
        env = os.environ.copy()
        env.update({'MONGO_URI': f'mongodb://127.0.0.1:{self.args.mongo_port}/{self.database}?replicaSet=inventory-rs',
                    'PORT': str(self.args.port), 'SERVER_ADDRESS': '127.0.0.1',
                    'CACHE_ENABLED': 'false', 'RATELIMIT_ENABLED': str(mode == 'limited').lower(),
                    'SHARDING_ENABLED': str(mode == 'sharded').lower(), 'EVALUATION_ENABLED': 'true',
                    'SNOWFLAKE_NODE_ID': '6', 'RATELIMIT_CAPACITY': '10', 'RATELIMIT_REFILL_TOKENS': '5',
                    'RATELIMIT_REFILL_PERIOD_SECONDS': '1', 'LOGGING_LEVEL_ROOT': 'ERROR',
                    'LOGGING_LEVEL_COM_EXAMPLE_INVENTORY': 'ERROR',
                    'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_MONGODB_CORE_MONGOTEMPLATE': 'ERROR'})
        for number in range(1, 5):
            env[f'SHARD_DB_{number}'] = self.database + f'_shard_{number}'
        log = (self.output / f'backend-{mode}.log').open('wb')
        self.logs.append(log)
        self.backend = subprocess.Popen([self.java, '-Xms256m', '-Xmx512m', '-jar', str(self.jar)], env=env,
                                        stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                        creationflags=common.HIDDEN, cwd=ROOT)
        self.mode = mode
        self.progress('Starting isolated backend: ' + mode)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if self.backend.poll() is not None:
                raise RuntimeError(f'{mode} backend exited; inspect its log')
            try:
                value = http('GET', self.url + '/internal/evaluation/metrics', timeout=1)
                assert value['cacheEnabled'] is False
                break
            except common.requests.RequestException:
                time.sleep(.3)
        else:
            raise RuntimeError('Backend startup timed out')
        self.manifest.setdefault('deployments', {})[mode] = {'pid': self.backend.pid, 'database': self.database,
            'sharding': mode == 'sharded', 'limiter': mode == 'limited', 'cache': False, 'port': self.args.port}
        write_json(self.output / 'manifest.json', self.manifest)

    def stop(self):
        if self.backend and self.backend.poll() is None:
            self.backend.terminate()
            try: self.backend.wait(timeout=10)
            except subprocess.TimeoutExpired: self.backend.kill(); self.backend.wait()
        for log in self.logs:
            log.close()
        self.logs.clear()

    def api(self, method, path, body=None):
        return http(method, self.url + '/api' + path, **({'json': body} if body is not None else {}), timeout=120)

    def product(self, suffix):
        return self.api('POST', '/products', {'sku': f'Q-{self.tag}-{self.mode}-{suffix}',
                        'name': 'Component fixture ' + str(suffix), 'price': 10, 'reorderLevel': 0, 'active': True})

    def seed(self, inventory):
        self.progress(f'Seeding {self.args.products} products' + (' and 5 warehouses' if inventory else ''))
        with ThreadPoolExecutor(max_workers=8) as pool:
            self.products = list(pool.map(self.product, range(self.args.products)))
        if self.mode == 'single': self.single_product_id = self.products[0]['productId']
        self.product_csv = dataset(self.output / f'{self.mode}-products.csv', [
            dict(kind='product', path='/api/products/' + p['productId'], productId=p['productId'], sku=p['sku']) for p in self.products])
        for p in self.products[:50]:
            self.api('GET', '/products/' + p['productId'])
        if not inventory:
            return
        self.warehouses = [self.api('POST', '/warehouses', {'name': f'Fixture {i}', 'location': 'Test', 'status': 'ACTIVE'}) for i in range(5)]
        records = [(p['productId'], w['warehouseId']) for p in self.products for w in self.warehouses]
        with ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(lambda pair: self.adjust(*pair, 1000), records))
        rows = []
        for p, w in records:
            rows.extend([dict(kind='point', path=f'/api/inventory/{p}/{w}', productId=p, warehouseId=w),
                         dict(kind='scatter', path=f'/api/inventory/{p}', productId=p, warehouseId=w)])
        self.read_csv = dataset(self.output / f'{self.mode}-reads.csv', rows)
        # Sales fixtures must not add warehouses/reservations to the read catalogue.
        if self.args.only in ('all','stock'):
            with ThreadPoolExecutor(max_workers=8) as pool:
                self.work_products = list(pool.map(lambda i: self.product(f'work-{i}'), range(100)))
        self.snapshot('seed-' + self.mode)

    def adjust(self, product, warehouse, quantity):
        return self.api('POST', '/inventory/adjustments', {'productId': product, 'warehouseId': warehouse,
                        'physicalDelta': quantity, 'reservedDelta': 0, 'reason': 'Isolated component fixture'})

    def load(self, name, users, data, repeat=1, loops=-1, method='GET', paced=False, callback=None):
        folder = self.output / f'{name}-{self.mode}-u{users}-r{repeat}'
        folder.mkdir()
        finite = loops > 0
        ramp = 0 if finite else (5 if paced else 2)
        seconds = max(20, self.args.seconds * 2) if callback else self.args.seconds
        # Pacing uses a five-second in-process warm-up, then a fixed measurement
        # window. Extra tail time covers script/HTTP-client startup before sample 1.
        props = {'users': users, 'port': self.args.port, 'ramp': 0 if paced else ramp, 'duration': 180 if finite else seconds + ramp + 5,
                 'loops': loops, 'dataFile': data.as_posix(), 'method': method, 'runId': folder.name,
                 'pace': str(paced).lower(), 'rps': users * 2, 'limiterOn': str(self.mode == 'limited').lower(),
                 'clientKey': folder.name, 'sample_variables': 'outcome,orderId,scheduledLagMs',
                 'jmeter.save.saveservice.output_format': 'csv', 'jmeter.save.saveservice.print_field_names': 'true',
                 'jmeter.save.saveservice.timestamp_format': 'ms', 'sampleresult.timestamp.start': 'true',
                 'jmeter.save.saveservice.assertion_results_failure_message': 'true'}
        command = [self.java, '-Xms128m', '-Xmx384m', '-Djava.awt.headless=true', '-jar', str(self.jmeter), '-n',
                   '-t', str(ROOT / 'evaluation/jmeter/components.jmx'), '-l', str(folder / 'raw.jtl'), '-j', str(folder / 'jmeter.log')]
        command.extend(f'-J{k}={v}' for k, v in props.items())
        write_json(folder / 'command.json', command)
        before = self.api('GET', '/shards') if self.mode == 'sharded' else None
        self.progress(f'{name}: {self.mode}, {users} users, repeat {repeat}')
        resource_rows = []
        callback_future = None
        with (folder / 'console.log').open('wb') as console, ThreadPoolExecutor(max_workers=1) as pool:
            process = subprocess.Popen(command, stdout=console, stderr=subprocess.STDOUT, cwd=folder,
                                       stdin=subprocess.DEVNULL, creationflags=common.HIDDEN)
            roles = {'backend': psutil.Process(self.backend.pid), 'jmeter': psutil.Process(process.pid)}
            for conn in psutil.net_connections(kind='tcp'):
                if conn.status == psutil.CONN_LISTEN and conn.laddr.port == self.args.mongo_port and conn.pid:
                    roles['mongodb'] = psutil.Process(conn.pid); break
            for monitored in roles.values(): monitored.cpu_percent()
            started = time.monotonic()
            try:
                while process.poll() is None:
                    if time.monotonic() - started > (240 if finite else seconds + ramp + 90):
                        raise RuntimeError('JMeter exceeded the run deadline')
                    raw = folder / 'raw.jtl'
                    if callback and callback_future is None and raw.exists() and raw.stat().st_size > 1000:
                        # Wait for actual samples, not merely JMeter JVM startup.
                        time.sleep(2)
                        callback_future = pool.submit(callback)
                    for role, monitored in roles.items():
                        try:
                            resource_rows.append({'timestamp': int(time.time() * 1000), 'role': role,
                                'cpu_host_pct': monitored.cpu_percent() / psutil.cpu_count(),
                                'rss_mib': monitored.memory_info().rss / 1024**2})
                        except psutil.NoSuchProcess: pass
                    time.sleep(.25)
                if process.returncode: raise RuntimeError('JMeter failed; inspect ' + str(folder))
                if callback and callback_future is None: raise RuntimeError('No live traffic for shard scenario')
                if callback_future: callback_future.result()
            finally:
                if process.poll() is None: process.kill(); process.wait()
        with (folder / 'raw.jtl').open(newline='', encoding='utf-8-sig') as stream:
            raw_rows = list(csv.DictReader(stream))
        if not raw_rows: raise RuntimeError('JMeter produced no samples')
        first = min(int(r['timeStamp']) for r in raw_rows)
        start = first + ramp * 1000
        end = max(int(r['timeStamp']) + int(r['elapsed']) for r in raw_rows) if finite else start + seconds * 1000
        rows = [r for r in raw_rows if int(r['timeStamp']) >= start and int(r['timeStamp']) + int(r['elapsed']) <= end]
        if not rows: raise RuntimeError('No measured samples')
        if not finite and max(int(r['timeStamp']) for r in raw_rows) < end - (600 if paced else 300):
            raise RuntimeError('Incomplete measurement window')
        point = describe(rows, max(.001, (end - start) / 1000))
        point.update({'experiment': name, 'mode': self.mode, 'users': users, 'repeat': repeat, 'folder': folder.name,
                      'raw_requests': len(raw_rows), 'raw_unexpected_errors': sum(r['success'] != 'true' for r in raw_rows),
                      'offered_rps': users * 2 if paced else None, 'window_start_ms': start, 'window_end_ms': end,
                      'by_request': {label: describe([r for r in rows if r['label'] == label], point['seconds']) for label in sorted({r['label'] for r in rows})}})
        point['resources'] = {}
        for role in roles:
            samples = [r for r in resource_rows if r['role'] == role and start <= r['timestamp'] <= end]
            point['resources'][role] = {'samples': len(samples), 'mean_cpu_host_pct': statistics.mean(r['cpu_host_pct'] for r in samples) if samples else None,
                                        'peak_rss_mib': max((r['rss_mib'] for r in samples), default=None)}
        with (folder / 'resources.csv').open('w', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=['timestamp','role','cpu_host_pct','rss_mib']); writer.writeheader(); writer.writerows(resource_rows)
        if before:
            after = self.api('GET', '/shards')
            write_json(folder / 'shards-before.json', before); write_json(folder / 'shards-after.json', after)
            old = {s['shardId']: s['requests'] for s in before['shards']}
            point['shard_operation_delta_including_ramp'] = {s['shardId']: s['requests'] - old[s['shardId']] for s in after['shards']}
        self.points.append(point)
        write_json(folder / 'summary.json', point)
        write_json(self.output / 'http-results.json', self.points)
        if point['raw_unexpected_errors']: raise RuntimeError('Unexpected HTTP/assertion errors in ' + str(folder))
        if paced and not .9 <= point['attempted_rps'] / (users * 2) <= 1.1:
            raise RuntimeError('Load generator missed its fixed offered rate; inspect scheduling lag')
        if paced and point['p95_scheduling_lag_ms'] > 250:
            raise RuntimeError('Load generator scheduling lag exceeds half the request interval')
        with (folder / 'raw.jtl').open('rb') as source, gzip.open(folder / 'raw.jtl.gz', 'wb') as destination:
            shutil.copyfileobj(source, destination)
        (folder / 'raw.jtl').unlink()
        self.progress(f'Passed: {point["requests"]} requests, outcomes={point["outcomes"]}')
        return point, raw_rows

    def stock(self, users, repeat, hot):
        kind = 'hot-stock' if hot else 'distributed-sales'
        wh = self.api('POST', '/warehouses', {'name': kind + str(users), 'location': 'Test'})['warehouseId']
        products = [self.product(f'hot-{users}-{repeat}')] if hot else self.work_products
        initial = 10 if hot else 1000
        with ThreadPoolExecutor(max_workers=8) as pool:
            list(pool.map(lambda p: self.adjust(p['productId'], wh, initial), products))
        data = dataset(self.output / f'{kind}-{self.mode}-{users}-{repeat}.csv', [dict(
            kind='sales', path='/api/sales-orders', productId=p['productId'], warehouseId=wh,
            body=json.dumps({'items':[{'productId':p['productId'],'warehouseId':wh,'quantity':1}], 'idempotencyKey':'RUN_KEY'})) for p in products])
        point, raw = self.load(kind, users, data, repeat, loops=100 // users, method='POST')
        accepted = [r for r in raw if r['outcome'] == 'reserved']
        counts = Counter()
        order_ids = []
        for row in accepted:
            order = self.api('GET', '/sales-orders/' + row['orderId'])
            assert order['status'] == 'RESERVED' and len(order['items']) == 1
            item = order['items'][0]; counts[item['productId']] += 1; order_ids.append(order['orderId'])
        assert len(order_ids) == len(set(order_ids))
        observed_reserved, audits = 0, []
        for p in products:
            pid = p['productId']; stock = self.api('GET', f'/inventory/{pid}/{wh}')
            assert stock['quantity'] == initial and stock['reservedQuantity'] == counts[pid]
            assert 0 <= stock['reservedQuantity'] <= stock['quantity']
            observed_reserved += stock['reservedQuantity']
            audits.extend(m for m in self.api('GET', f'/inventory/{pid}/movements') if m['warehouseId'] == wh and m['movementType'] == 'SALE_RESERVE')
        assert observed_reserved == len(accepted) and {m['referenceId'] for m in audits} == set(order_ids) and len(audits) == len(accepted)
        assert len(raw) == 100
        if hot: assert len(accepted) <= 10
        else: assert len(accepted) == 100
        check = {'passed': True, 'initial_quantity_per_record': initial, 'attempts': len(raw), 'reserved_orders': len(accepted),
                 'persisted_reserved_units': observed_reserved, 'reservation_audits': len(audits),
                 'oversold_units': 0, 'order_ids': order_ids, 'note': '409 insufficient stock and retryable write conflicts are counted separately; no automatic request retries'}
        write_json(self.output / point['folder'] / 'stock-check.json', check)

    def compile_java(self):
        self.classes = ROOT / '.verification/component-benchmarks'
        self.classes.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(self.jar) as archive:
            for name in archive.namelist():
                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                    target = self.classes / 'lib' / Path(name).name
                elif name.startswith('BOOT-INF/classes/') and name.endswith('.class'):
                    target = self.classes / 'app' / name.removeprefix('BOOT-INF/classes/')
                else: continue
                target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(archive.read(name))
        self.classpath = os.pathsep.join([str(self.classes), str(self.classes / 'app'), str(self.classes / 'lib/*')])
        subprocess.run([shutil.which('javac'), '-cp', self.classpath, '-d', str(self.classes),
                        str(ROOT / 'evaluation/benchmarks/ComponentBenchmarks.java')], check=True, creationflags=common.HIDDEN)

    def java_task(self, task, output, *extra):
        command = [self.java, '-Xmx768m', '-cp', self.classpath, 'ComponentBenchmarks', task, str(output), *map(str, extra)]
        process = subprocess.Popen(command, creationflags=common.HIDDEN, stdout=subprocess.DEVNULL)
        monitor = psutil.Process(process.pid); monitor.cpu_percent(); samples = []; started = time.monotonic()
        try:
            while process.poll() is None:
                if time.monotonic() - started > 120: raise RuntimeError('Java benchmark timed out')
                try: samples.append({'cpu_host_pct': monitor.cpu_percent() / psutil.cpu_count(), 'rss_mib': monitor.memory_info().rss / 1024**2})
                except psutil.NoSuchProcess: pass
                time.sleep(.05)
            if process.returncode: raise RuntimeError('Java benchmark or database reconciliation failed')
        finally:
            if process.poll() is None: process.kill(); process.wait()
        write_json(output.with_name(output.stem + '-resources.json'), {'peak_process_rss_mib': max((r['rss_mib'] for r in samples), default=None),
                   'mean_cpu_host_pct': statistics.mean(r['cpu_host_pct'] for r in samples) if samples else None,
                   'scope': 'Whole helper process including startup and validation', 'samples': samples})
        return json.loads(output.read_text())

    def snapshot(self, name):
        return self.java_task('snapshot', self.output / (name + '.json'), self.args.mongo_port, self.database)

    def migration(self):
        before = self.snapshot('migration-before')
        self.events = []
        def change():
            for method in ('POST', 'DELETE'):
                start = int(time.time() * 1000)
                result = self.api(method, '/shards/shard-4')
                self.events.append({'action': 'add' if method == 'POST' else 'remove', 'start_ms': start,
                                    'end_ms': int(time.time() * 1000), 'result': result, 'status': self.api('GET', '/shards')})
                time.sleep(1)
        point, rows = self.load('shard-changes', 25, self.read_csv, callback=change)
        after = self.snapshot('migration-after')
        original = {r['document']['_id']: r for r in before['inventory']}
        final = {r['document']['_id']: r for r in after['inventory']}
        assert len(original) == len(before['inventory']) and len(final) == len(after['inventory'])
        assert original == final and before['orders'] == after['orders'] and before['movements'] == after['movements']
        overlaps = {}
        for event in self.events:
            overlaps[event['action']] = sum(int(r['timeStamp']) < event['end_ms'] and int(r['timeStamp']) + int(r['elapsed']) > event['start_ms'] for r in rows)
            assert overlaps[event['action']] > 0, 'Membership change did not overlap HTTP traffic'
        result = {'passed': True, 'records_preserved': len(original), 'duplicates': 0, 'events': self.events,
                  'requests_overlapping_changes': overlaps, 'max_http_ms': point['max_ms'],
                  'note': 'Graceful logical membership change with source data reachable; not physical shard failure or database failover'}
        write_json(self.output / 'migration-check.json', result)

    def run(self):
        selected = self.args.only
        http_needed = selected in ('all','limiter','shards','stock')
        if selected != 'autocomplete': self.compile_java()
        if http_needed:
            if not self.jmeter.exists(): raise RuntimeError('Set JMETER_HOME or pass --jmeter-home')
            inventory = selected in ('all','shards','stock')
            self.start('single'); self.seed(inventory)
            baseline_products = self.product_csv
            for repeat in range(1, self.args.repeats + 1):
                for users in self.args.users:
                    if selected in ('all','limiter'): self.load('limiter', users, self.product_csv, repeat, paced=True)
                    if selected in ('all','shards'): self.load('inventory-reads', users, self.read_csv, repeat)
                    if selected in ('all','stock'):
                        self.stock(users, repeat, False); self.stock(users, repeat, True)
            if inventory:
                self.snapshot('single-final')
                self.start('sharded'); self.seed(True)
                for repeat in range(1, self.args.repeats + 1):
                    for users in self.args.users:
                        if selected in ('all','shards'): self.load('inventory-reads', users, self.read_csv, repeat)
                        if selected in ('all','stock'):
                            self.stock(users, repeat, False); self.stock(users, repeat, True)
                self.snapshot('sharded-final')
                if selected in ('all','shards'): self.migration()
            if selected in ('all','limiter'):
                self.start('limited')
                for repeat in range(1, self.args.repeats + 1):
                    for users in self.args.users: self.load('limiter', users, baseline_products, repeat, paced=True)
                # A fresh request after a pause must be admitted again.
                time.sleep(1)
                recovered = http('GET', self.url + '/api/products/' + self.single_product_id,
                                 headers={'X-Forwarded-For': self.points[-1]['folder']})
                assert recovered['productId'] == self.single_product_id
                write_json(self.output / 'limiter-recovery.json', {'passed': True, 'same_client_bucket': self.points[-1]['folder'], 'pause_seconds': 1})
            self.stop()
        if selected in ('all','ids'):
            self.progress('Snowflake versus full UUID: 1, 8, 32 threads')
            self.java_task('ids', self.output / 'ids.json', self.args.repeats)
        if selected in ('all','hash'):
            self.progress('Consistent hashing versus modulo placement')
            self.java_task('hash', self.output / 'hashing.json')
        if selected in ('all','autocomplete'):
            self.progress('Actual frontend Trie versus linear prefix scan')
            subprocess.run([shutil.which('node'), '--expose-gc', str(ROOT / 'evaluation/benchmarks/autocomplete.mjs'),
                            str(self.output), str(self.args.repeats)], check=True, creationflags=common.HIDDEN, timeout=120)
            measured = json.loads((self.output / 'autocomplete.json').read_text())
            self.manifest['algorithm_source_sha256']['evaluation/benchmarks/autocomplete.mjs'] = measured['source_sha256']
        self.report()
        self.manifest['finished_utc'] = dt.datetime.now(dt.timezone.utc).isoformat()
        write_json(self.output / 'manifest.json', self.manifest)
        self.progress('All selected experiments passed. Open README.md in the results folder.')

    def report(self):
        rows = [{k:v for k,v in p.items() if not isinstance(v,(dict,list))} for p in self.points]
        if rows:
            with (self.output / 'http-summary.csv').open('w', newline='') as stream:
                writer = csv.DictWriter(stream, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
        report_spec = importlib.util.spec_from_file_location('component_report', Path(__file__).with_name('render-component-results.py'))
        report = importlib.util.module_from_spec(report_spec)
        report_spec.loader.exec_module(report)
        report.render(self.output)



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=['all','limiter','shards','stock','ids','autocomplete','hash'], default='all')
    parser.add_argument('--seconds', type=int, default=10)
    parser.add_argument('--repeats', type=int, default=1)
    parser.add_argument('--users', type=int, nargs='+', default=[5,25,100])
    parser.add_argument('--products', type=int, default=100)
    parser.add_argument('--port', type=int, default=8082)
    parser.add_argument('--mongo-port', type=int, default=27019)
    parser.add_argument('--jmeter-home')
    args = parser.parse_args()
    if args.seconds < 3 or args.repeats < 1 or args.products < 10 or any(u < 1 or 100 % u for u in args.users):
        parser.error('Use >=3 seconds, >=10 products, positive repeats and user counts dividing 100')
    if args.only in ('all','stock') and args.products < 100: parser.error('Distributed sales use 100 distinct products; keep --products >=100')
    suite = Suite(args)
    try: suite.run()
    except Exception as error:
        write_json(suite.output / 'failure.json', {'type': type(error).__name__, 'message': str(error)})
        raise
    finally: suite.stop()


if __name__ == '__main__': main()
