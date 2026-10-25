"""Run five autocomplete strategies in an actual isolated Chrome browser."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor
import csv
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
import shutil
import socket
import statistics
import subprocess
import threading
import time

import psutil

spec = importlib.util.spec_from_file_location('common', Path(__file__).with_name('run-jmeter-cache.py'))
common = importlib.util.module_from_spec(spec); spec.loader.exec_module(common)
ROOT, http, write_json, percentile = common.ROOT, common.http, common.write_json, common.percentile
MODES = ['trie_only','trie_fallback','trie_cache_fallback','api_only','cache_api']
LABELS = ['Browser Trie only','Browser Trie + fallback','Browser Trie + Redis + fallback','API only','Redis + API']


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        target = self.path.split('?',1)[0]
        if not (target.startswith('/evaluation/autocomplete-five/') or target == '/frontend/src/services/trie.js'):
            self.send_error(404); return
        super().do_GET()
    def log_message(self, *args): pass


class Experiment:
    def __init__(self, args):
        self.args = args
        self.tag = common.dt.datetime.now().strftime('%Y%m%d-%H%M%S')
        self.output = ROOT / 'evaluation/results' / (self.tag + '-autocomplete-five')
        self.output.mkdir(parents=True)
        self.owned = {}; self.logs = []; self.server = None
        self.java = shutil.which('java')
        self.node = shutil.which('node')
        self.chrome = Path(args.chrome or Path(os.environ.get('ProgramFiles','C:/Program Files')) / 'Google/Chrome/Application/chrome.exe')
        self.redis = args.redis_exe or shutil.which('memurai') or shutil.which('redis-server') or 'C:/Program Files/Memurai/memurai.exe'
        self.jar = ROOT / 'backend/target/inventory-benchmark.jar'
        if not self.chrome.exists() or not self.jar.exists(): raise RuntimeError('Build the benchmark JAR and install Chrome (or pass --chrome)')
        self.url = f'http://127.0.0.1:{args.port}'
        self.api = self.url + '/internal/evaluation/autocomplete'
        self.database = 'inventory_autocomplete_' + self.tag.replace('-','_')
        sources = ['evaluation/autocomplete-five/browser.js','evaluation/autocomplete-five/drive-browser.mjs',
                   'frontend/src/services/trie.js','scripts/run-autocomplete-five.py',
                   'backend/src/main/java/com/example/inventory/evaluation/AutocompleteExperimentService.java']
        self.manifest = {'started_utc': common.dt.datetime.now(common.dt.timezone.utc).isoformat(),
            'revision': common.git('rev-parse','HEAD'), 'branch': common.git('branch','--show-current'),
            'working_tree_dirty': bool(common.git('status','--porcelain')), 'settings': vars(args),
            'database': self.database, 'jar_sha256': common.hashlib.sha256(self.jar.read_bytes()).hexdigest(),
            'source_sha256': {name: common.hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in sources},
            'logical_cpus': psutil.cpu_count(), 'ram_gib': psutil.virtual_memory().total/1024**3,
            'scope': 'One real Chrome tab, sequential completed-query events; no simulated network latency. Browser HTTP cache disabled for requests. Cache means Redis search-result cache, not legacy product-detail caching.',
            'endpoint': self.api, 'search_semantics': 'ASCII token-prefix over SKU/name/category plus contiguous SKU; top 8 valid matches, no ranking/order equivalence claimed',
            'backend_product_cache': False, 'limiter': False,
            'limitations': ['Same host for Chrome, API, Redis and MongoDB; limited warm-up.',
                'Dedicated opt-in token projection/search endpoint, not the normal substring-search API.',
                'A separate DOM harness uses the actual ProductTrie and mirrors the existing zero-hit fallback policy; this does not time the React component.',
                'Workload oracle is held by the harness; browser heap/RSS includes test and browser overhead.',
                'Catalogue snapshot is immutable during measurement; no production search-cache invalidation policy is claimed.']}
        write_json(self.output/'manifest.json', self.manifest)

    def launch(self, name, command, env=None):
        log=(self.output/(name+'.log')).open('wb'); self.logs.append(log)
        process=subprocess.Popen(command, env=env, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                 stdin=subprocess.DEVNULL, creationflags=common.HIDDEN)
        self.owned[name]=process; return process

    def prepare(self):
        for port in [self.args.port,self.args.redis_port,self.args.web_port,self.args.debug_port]: common.require_free(port)
        # Reuse the existing test replica set, or start it through its guarded script.
        try:
            with socket.create_connection(('127.0.0.1',self.args.mongo_port),timeout=.5): pass
        except OSError:
            subprocess.run(['powershell','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'scripts/start-consistency-mongo.ps1'),'-Port',str(self.args.mongo_port)],check=True)
        work=ROOT/'.verification'/('autocomplete-five-'+self.tag); work.mkdir()
        redis_config=work/'redis.conf'
        redis_config.write_text(f'bind 127.0.0.1\nport {self.args.redis_port}\nsave ""\nappendonly no\ndir "{work.as_posix()}"\n',encoding='utf-8')
        self.launch('redis',[self.redis,str(redis_config)])
        env=os.environ.copy()
        env.update({'MONGO_URI':f'mongodb://127.0.0.1:{self.args.mongo_port}/{self.database}?replicaSet=inventory-rs',
            'PORT':str(self.args.port),'SERVER_ADDRESS':'127.0.0.1','SHARDING_ENABLED':'false',
            'CACHE_ENABLED':'false','CACHE_NAMESPACE':'autocomplete-five:'+self.tag,'EVALUATION_ENABLED':'true',
            'RATELIMIT_ENABLED':'false','SNOWFLAKE_NODE_ID':'7','REDIS_HOST':'127.0.0.1','REDIS_PORT':str(self.args.redis_port),
            'LOGGING_LEVEL_ROOT':'ERROR','LOGGING_LEVEL_COM_EXAMPLE_INVENTORY':'ERROR',
            'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_MONGODB_CORE_MONGOTEMPLATE':'ERROR'})
        self.launch('backend',[self.java,'-Xms256m','-Xmx512m','-jar',str(self.jar)],env)
        deadline=time.monotonic()+100
        while time.monotonic()<deadline:
            if self.owned['backend'].poll() is not None: raise RuntimeError('Backend exited; inspect backend.log')
            try:
                http('GET',self.api+'/metrics',timeout=1); break
            except common.requests.RequestException: time.sleep(.3)
        else: raise RuntimeError('Backend startup timeout')
        print(f'Seeding {self.args.products} products in {self.database}',flush=True)
        def create(i):
            return http('POST',self.url+'/api/products',json={'sku':f'AS-{i:06d}','name':f'Sensor {i} Unit',
                        'category':f'Category-{i%10}','price':10,'reorderLevel':0,'active':True})
        with ThreadPoolExecutor(max_workers=8) as pool: list(pool.map(create,range(self.args.products)))
        rebuilt=http('POST',self.api+'/rebuild'); assert rebuilt['products']==self.args.products
        catalogue=http('GET',self.api+f'/catalogue?limit={self.args.products}')
        assert len(catalogue)==self.args.products
        # Prewarm the server and Redis connection, but leave every measured run-token cache empty.
        for _ in range(20): http('GET',self.api+'/search',params={'prefix':'sensor','run':'setup','cache':'false'})
        for _ in range(5): http('GET',self.api+'/search',params={'prefix':'zzsetup','run':'setup','cache':'true'})
        initial=catalogue[:self.args.initial]
        # Deep-catalogue query class is relative to the application's original 100-product index.
        deeper=catalogue[100:]
        queries=[]
        for kind, items in [('initial', [initial[i*max(1,len(initial)//8)%len(initial)] for i in range(8)]),
                            ('deeper',[deeper[i*max(1,len(deeper)//8)%len(deeper)] for i in range(8)])]:
            for item in items:
                queries.append({'kind':kind,'prefix':item['sku'].lower().replace('-',''),'expectedIds':[item['productId']]})
        queries.extend({'kind':'missing','prefix':f'zzmissing{i}','expectedIds':[]} for i in range(8))
        queries.extend({'kind':'broad','prefix':prefix,'expectedIds':[p['productId'] for p in catalogue]} for prefix in ['sensor','category'])
        self.config={'output':str(self.output),'api':self.api,'initial':self.args.initial,'queries':queries,
                     'warmLoops':self.args.warm_loops,'debounce':self.args.debounce,'repeats':self.args.repeats,
                     'debugPort':self.args.debug_port,'pageUrl':f'http://127.0.0.1:{self.args.web_port}/evaluation/autocomplete-five/index.html'}
        write_json(self.output/'workload.json',self.config)
        handler=functools.partial(Handler,directory=str(ROOT))
        self.server=ThreadingHTTPServer(('127.0.0.1',self.args.web_port),handler)
        threading.Thread(target=self.server.serve_forever,daemon=True).start()
        profile=work/'chrome-profile'
        self.launch('chrome',[str(self.chrome),'--headless=new',f'--remote-debugging-port={self.args.debug_port}',
            '--remote-debugging-address=127.0.0.1',f'--user-data-dir={profile}', '--no-first-run','--no-default-browser-check',
            '--disable-background-networking','--disable-background-timer-throttling','--disable-renderer-backgrounding',
            '--window-size=1400,1400','about:blank'])
        for _ in range(100):
            try: http('GET',f'http://127.0.0.1:{self.args.debug_port}/json/version',timeout=1); break
            except common.requests.RequestException: time.sleep(.1)
        else: raise RuntimeError('Chrome remote debugging did not start')

    def run(self):
        print('Results: '+str(self.output),flush=True); self.prepare()
        driver=self.launch('driver',[self.node,str(ROOT/'evaluation/autocomplete-five/drive-browser.mjs'),str(self.output/'workload.json')])
        roles={name:psutil.Process(process.pid) for name,process in self.owned.items()}
        for connection in psutil.net_connections(kind='tcp'):
            if connection.status==psutil.CONN_LISTEN and connection.laddr.port==self.args.mongo_port and connection.pid:
                roles['mongodb']=psutil.Process(connection.pid); break
        cpu_before={}; samples=[]; start=time.monotonic(); last_progress=None
        while driver.poll() is None:
            if time.monotonic()-start>self.args.repeats*300+120: raise RuntimeError('Browser workload timed out')
            progress=self.output/'browser-progress.json'
            if progress.exists():
                message=progress.read_text()
                if message!=last_progress: print(message,flush=True); last_progress=message
            for role,parent in roles.items():
                try:
                    processes=[parent]+(parent.children(recursive=True) if role=='chrome' else [])
                    rss=0; cpu=0
                    for process in processes:
                        try:
                            memory=process.memory_info().rss; times=process.cpu_times(); current=times.user+times.system
                            previous=cpu_before.get(process.pid,(current,time.monotonic()))
                            elapsed=time.monotonic()-previous[1]
                            cpu+=(current-previous[0])*100/max(elapsed,.001)/psutil.cpu_count()
                            cpu_before[process.pid]=(current,time.monotonic()); rss+=memory
                        except psutil.NoSuchProcess: pass
                    samples.append({'timestamp':int(time.time()*1000),'role':role,'cpu_host_pct':cpu,'rss_mib':rss/1024**2})
                except psutil.NoSuchProcess: pass
            time.sleep(.5)
        if driver.returncode: raise RuntimeError('Browser experiment failed; inspect driver.log')
        with (self.output/'resources.csv').open('w',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=['timestamp','role','cpu_host_pct','rss_mib']); writer.writeheader(); writer.writerows(samples)
        write_json(self.output/'resource-summary.json',{role:{'mean_cpu_host_pct':statistics.mean(r['cpu_host_pct'] for r in samples if r['role']==role),
            'peak_rss_mib':max(r['rss_mib'] for r in samples if r['role']==role)} for role in {r['role'] for r in samples}})
        self.report()
        self.manifest['finished_utc']=common.dt.datetime.now(common.dt.timezone.utc).isoformat()
        write_json(self.output/'manifest.json',self.manifest)
        print('Completed all five modes. Open README.md.',flush=True)

    def report(self):
        points=[json.loads(p.read_text()) for mode in MODES for p in sorted(self.output.glob(mode+'-r*.json'))]
        assert len(points)==5*self.args.repeats
        all_rows=[row for p in points for row in p['rows']]
        with (self.output/'queries.csv').open('w',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=[k for k in all_rows[0] if k!='resultIds']); writer.writeheader()
            writer.writerows({k:v for k,v in r.items() if k!='resultIds'} for r in all_rows)
        summaries=[]
        for mode in MODES:
            for phase in ['cold','warm']:
                rows=[r for r in all_rows if r['mode']==mode and r['phase']==phase]
                phases=[s for p in points if p['mode']==mode for s in p['phases'] if s['phase']==phase]
                counters={k:sum(p['counters'][k] for p in phases) for k in phases[0]['counters']}
                api_calls=sum(r['source'] in ['mongo','redis'] for r in rows)
                assert counters['requests']==api_calls and counters['redisFailures']==0
                row={'mode':mode,'phase':phase,'queries':len(rows),
                     'work_mean_ms':statistics.mean(r['work_ms'] for r in rows),'work_p95_ms':percentile([r['work_ms'] for r in rows],.95),
                     'display_mean_ms':statistics.mean(r['display_ms'] for r in rows),'display_p95_ms':percentile([r['display_ms'] for r in rows],.95),
                     'correct_pct':sum(r['correct'] for r in rows)*100/len(rows),
                     'valid_result_fill_pct':100*sum(r['valid'] for r in rows)/sum(r['expected'] for r in rows),
                     'api_calls':api_calls,'database_reads':counters['databaseReads'],'redis_hits':counters['redisHits'],
                     'redis_misses':counters['redisMisses'],'sources':dict(Counter(r['source'] for r in rows))}
                summaries.append(row)
        write_json(self.output/'summary.json',summaries)
        with (self.output/'summary.csv').open('w',newline='') as stream:
            writer=csv.DictWriter(stream,fieldnames=[k for k in summaries[0] if k!='sources']); writer.writeheader()
            writer.writerows({k:v for k,v in r.items() if k!='sources'} for r in summaries)
        lines=['# Five autocomplete paths in Chrome','',f"Catalogue: {self.args.products}; initial browser index: {self.args.initial}; {self.args.repeats} independent repetitions; 26 queries per cold pass, {self.args.warm_loops} repeated warm passes; debounce: {self.args.debounce} ms.",'',
            'Cache means a dedicated Redis search-result cache. All modes use the same token-prefix rules and top-eight limit. HTTP browser caching is disabled. The normal product search endpoint and operational UI are unchanged. This opt-in API queries a MongoDB token projection with a multikey token index.','',
            '| Mode | Phase | Queries | Work mean ms | Work p95 ms | Display mean ms | Correct queries % | Valid result fill % | API calls | Mongo reads | Redis hits |',
            '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
        for r in summaries:
            lines.append(f"| {LABELS[MODES.index(r['mode'])]} | {r['phase']} | {r['queries']} | {r['work_mean_ms']:.3f} | {r['work_p95_ms']:.3f} | {r['display_mean_ms']:.3f} | {r['correct_pct']:.1f} | {r['valid_result_fill_pct']:.1f} | {r['api_calls']} | {r['database_reads']} | {r['redis_hits']} |")
        lines+=['','## How to read this','',
            'Work time includes Trie lookup, actual fetch/JSON parsing if used, incremental indexing and DOM/layout update; it excludes deliberate debounce waiting. Display time includes that waiting, starting from the completed-query input event. Neither metric claims to measure pixels reaching a physical screen.',
            'Initial catalogue download and Trie construction are recorded separately in each point. Browser-only means no API search per query; the initial index still needs catalogue data.',
            'Cold starts with a new browser index and a unique empty Redis query namespace. Warm repeats the same queries in the same client. Positive API results are added to the Trie, so repeated positive fallbacks can become local hits. Negative results remain empty locally; Redis can cache those empty lists.',
            'Correct query means every returned product is a valid token-prefix match and the number returned equals min(8, full-catalogue matches). Fill compares valid returned counts with that expected top-eight count. No ranking/order equivalence is claimed. Empty Trie results for unloaded matching products count as incomplete, not successful fast answers.',
            'Query classes: 8 initially indexed SKU prefixes, 8 deeper-catalogue SKU prefixes, 8 absent prefixes, and 2 broad prefixes. See queries.csv for each class and phase. Expected sets are computed outside timed search work.',
            'The server has a warm-up before measured requests; cold refers to data caches, not cold process/OS/disk. Mode order rotates across repetitions. One actual Chrome tab sends sequential completed-query events. This is not a JMeter concurrency test, a keystroke cancellation test, or the React component itself.',
            'The catalogue is immutable during measurement. Snapshot revision and run tokens isolate Redis keys; all keys expire after five minutes. This does not implement normal product-edit search-cache invalidation.',
            'resources.csv samples backend, MongoDB, Redis, driver and Chrome. Chrome RSS sums its process tree and can count shared pages repeatedly; browser heap includes the oracle and harness. Source hashes and settings are in manifest.json; browser version is saved separately.','',
            '![Browser result page](browser-results.png)','',
            'Reproduce: `powershell -ExecutionPolicy Bypass -File scripts/test-autocomplete-five.ps1`. Change `-Initial 1000` to compare a fully loaded browser index.','']
        (self.output/'README.md').write_text('\n'.join(lines),encoding='utf-8')
        write_json(self.output/'verification.json',{'passed':True,'browser_points':len(points),'query_observations':len(all_rows),
            'cache_failures':0,'browser_server_counter_agreement':True,'fallback_and_api_results_complete':True,
            'browser_only_incomplete_results_counted':True})

    def close(self):
        for name,process in reversed(list(self.owned.items())):
            if process.poll() is None:
                children=[]
                if name=='chrome':
                    try: children=psutil.Process(process.pid).children(recursive=True)
                    except psutil.NoSuchProcess: pass
                process.terminate()
                try: process.wait(timeout=10)
                except subprocess.TimeoutExpired: process.kill(); process.wait()
                for child in children:
                    try: child.terminate()
                    except psutil.NoSuchProcess: pass
        if self.server: self.server.shutdown(); self.server.server_close()
        for log in self.logs: log.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--products',type=int,default=1000); parser.add_argument('--initial',type=int,default=100)
    parser.add_argument('--repeats',type=int,default=3); parser.add_argument('--warm-loops',type=int,default=3)
    parser.add_argument('--debounce',type=int,default=250)
    parser.add_argument('--port',type=int,default=8082); parser.add_argument('--redis-port',type=int,default=6381)
    parser.add_argument('--mongo-port',type=int,default=27019); parser.add_argument('--web-port',type=int,default=9003)
    parser.add_argument('--debug-port',type=int,default=9225); parser.add_argument('--chrome'); parser.add_argument('--redis-exe')
    args=parser.parse_args()
    if not 200<=args.products<=10000 or not 8<=args.initial<=args.products or args.repeats<1 or args.warm_loops<1 or args.debounce<0:
        parser.error('Use 200..10000 products, initial index >=8 and <=catalogue, positive repeats/warm loops, nonnegative debounce')
    experiment=Experiment(args)
    try: experiment.run()
    except Exception as error:
        write_json(experiment.output/'failure.json',{'type':type(error).__name__,'message':str(error)}); raise
    finally: experiment.close()


if __name__=='__main__': main()
