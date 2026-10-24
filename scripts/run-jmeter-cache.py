"""Run reproducible, local JMeter product cache comparisons and save raw evidence."""
import argparse
import collections
from concurrent.futures import ThreadPoolExecutor
import csv
import datetime as dt
import gzip
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import shutil
import socket
import statistics
import subprocess
import sys
import threading
import time

import psutil
import requests

ROOT = Path(__file__).resolve().parents[1]
HIDDEN = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
LOCAL = threading.local()


def http(method, url, **kwargs):
    if not hasattr(LOCAL, "session"):
        LOCAL.session = requests.Session()
        LOCAL.session.trust_env = False
    response = LOCAL.session.request(method, url, timeout=kwargs.pop("timeout", 15), **kwargs)
    response.raise_for_status()
    return response.json()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def percentile(values, fraction):
    values = sorted(values)
    position = (len(values) - 1) * fraction
    low, high = math.floor(position), math.ceil(position)
    return values[low] + (values[high] - values[low]) * (position - low)


def git(*arguments):
    return subprocess.run(["git", *arguments], cwd=ROOT, capture_output=True,
                          text=True, check=True).stdout.strip()


def require_free(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=.5):
            raise RuntimeError(f"Port {port} is occupied; refusing to replace an existing service")
    except (ConnectionRefusedError, TimeoutError):
        pass
    except OSError as error:
        if isinstance(error, RuntimeError):
            raise
        if getattr(error, "winerror", None) not in (None, 10061):
            raise


class Experiment:
    def __init__(self, args):
        self.args = args
        self.tag = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
        self.output = ROOT / "evaluation/load-results" / f"{self.tag}-cache"
        self.output.mkdir(parents=True, exist_ok=False)
        self.processes = {}
        self.logs = []
        self.points = []
        self.database = "inventory_load_" + self.tag.replace("-", "_")
        self.namespace = "load:" + self.tag
        self.csv = self.output / "products.csv"
        self.java = shutil.which("java")
        jmeter = shutil.which("jmeter.bat") or shutil.which("jmeter")
        self.jmeter = Path(args.jmeter_home) / "bin/ApacheJMeter.jar" if args.jmeter_home else Path(jmeter).resolve().parent / "ApacheJMeter.jar" if jmeter else None
        self.redis_exe = args.redis_exe or shutil.which("memurai") or shutil.which("redis-server")
        if not self.java or not self.jmeter or not self.jmeter.exists() or not self.redis_exe:
            raise RuntimeError("Java, Apache JMeter and Memurai/Redis must be installed (or supply their paths)")
        self.jar = ROOT / "backend/target/inventory-benchmark.jar"
        if not self.jar.exists():
            raise RuntimeError("Build the current backend with mvn -Pbenchmark package first")
        mongo_pids = {c.pid for c in psutil.net_connections(kind="tcp")
                      if c.status == psutil.CONN_LISTEN and c.laddr.port == args.mongo_port and c.pid}
        if len(mongo_pids) != 1:
            raise RuntimeError(f"Expected one MongoDB listener on {args.mongo_port}; start the test replica set first")
        self.mongo_pid = mongo_pids.pop()
        if "mongod" not in psutil.Process(self.mongo_pid).name().lower():
            raise RuntimeError("The selected database listener is not mongod")
        self.manifest = {"started_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                         "git_commit": git("rev-parse", "HEAD"), "branch": git("branch", "--show-current"),
                         "working_tree_dirty": bool(git("status", "--porcelain")),
                         "backend_jar_sha256": hashlib.sha256(self.jar.read_bytes()).hexdigest(),
                         "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                         "plan_sha256": hashlib.sha256((ROOT / "evaluation/jmeter/product-cache.jmx").read_bytes()).hexdigest(),
                         "jmeter_jar": str(self.jmeter), "java": self.java,
                         "os": platform.platform(), "logical_cpus": psutil.cpu_count(),
                         "physical_cpus": psutil.cpu_count(logical=False),
                         "ram_gib": psutil.virtual_memory().total / 1024**3,
                         "settings": vars(args), "mongo_pid": self.mongo_pid,
                         "database": self.database, "cache_namespace": self.namespace,
                         "request": "GET /api/products/{productId}", "limiter_enabled": False,
                         "stock_availability_cache": False,
                         "topology": "one host; one shared MongoDB replica-set member; isolated Redis process; two benchmark backend JVMs; one active JMeter load at a time",
                         "load_model": "closed loop, no think time; ramp excluded from steady-state comparison",
                         "limitations": ["Existing applications/background processes remain running on the shared host",
                                         "This measures product detail reads, not authoritative stock availability or full order throughput",
                                         "Hot MongoDB working set; a cold Redis population probe is separate from warm-cache comparisons"]}
        write_json(self.output / "manifest.json", self.manifest)

    def progress(self, message):
        print(message, flush=True)
        write_json(self.output / "progress.json", {"time": dt.datetime.now().isoformat(), "message": message,
                                                  "completed_runs": len(self.points)})

    def launch(self, name, command, env=None):
        log = (self.output / f"{name}.log").open("wb")
        self.logs.append(log)
        process = subprocess.Popen(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                   stdout=log, stderr=subprocess.STDOUT, creationflags=HIDDEN)
        self.processes[name] = process
        return process

    def prepare(self):
        for port in (self.args.base_port, self.args.base_port + 1, self.args.redis_port):
            require_free(port)
        redis_dir = ROOT / ".verification" / f"jmeter-redis-{self.tag}"
        redis_dir.mkdir(parents=True)
        config = redis_dir / "redis.conf"
        config.write_text(f'bind 127.0.0.1\nport {self.args.redis_port}\nsave ""\nappendonly no\ndir "{redis_dir.as_posix()}"\nlogfile "{(redis_dir / "redis.log").as_posix()}"\n', encoding="utf-8")
        self.redis_command = [self.redis_exe, str(config)]
        self.launch("redis", self.redis_command)
        for _ in range(100):
            if self.processes["redis"].poll() is not None:
                raise RuntimeError("Dedicated Redis failed; inspect redis.log")
            try:
                with socket.create_connection(("127.0.0.1", self.args.redis_port), timeout=.2):
                    break
            except OSError:
                time.sleep(.2)
        else:
            raise RuntimeError("Dedicated Redis did not start")

        self.urls = {}
        self.envs = {}
        for offset, mode in enumerate(("off", "on")):
            port = self.args.base_port + offset
            env = os.environ.copy()
            env.update({"MONGO_URI": f"mongodb://127.0.0.1:{self.args.mongo_port}/{self.database}?replicaSet=inventory-rs",
                        "PORT": str(port), "SERVER_ADDRESS": "127.0.0.1", "SHARDING_ENABLED": "true",
                        "CACHE_ENABLED": str(mode == "on").lower(), "CACHE_NAMESPACE": self.namespace,
                        "CACHE_PRODUCT_TTL_MINUTES": "60", "RATELIMIT_ENABLED": "false",
                        "EVALUATION_ENABLED": "true", "SNOWFLAKE_NODE_ID": str(4 + offset),
                        "REDIS_HOST": "127.0.0.1", "REDIS_PORT": str(self.args.redis_port),
                        "LOGGING_LEVEL_ROOT": "WARN", "LOGGING_LEVEL_COM_EXAMPLE_INVENTORY": "WARN",
                        "LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_MONGODB_CORE_MONGOTEMPLATE": "WARN"})
            for shard in range(1, 5):
                env[f"SHARD_DB_{shard}"] = f"{self.database}_shard_{shard}"
            self.envs[mode] = env
            self.urls[mode] = f"http://127.0.0.1:{port}"
            self.launch(f"backend-{mode}", [self.java, "-Xms256m", "-Xmx512m", "-jar", str(self.jar)], env)
            self.wait_backend(mode)

        self.progress(f"Seeding {self.args.products} products in {self.database}")
        def create(index):
            return http("POST", self.urls["off"] + "/api/products", json={
                "sku": f"JM-{self.tag}-{index:05d}", "name": f"Benchmark product {index:05d}",
                "description": "Isolated, deterministic JMeter product-read workload", "category": f"Category-{index % 10}",
                "price": (index % 1000) + 1, "reorderLevel": 10, "active": True})
        with ThreadPoolExecutor(max_workers=8) as pool:
            self.products = list(pool.map(create, range(self.args.products)))
        with self.csv.open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=["productId", "sku"])
            writer.writeheader()
            writer.writerows({"productId": p["productId"], "sku": p["sku"]} for p in self.products)
        self.manifest["backend_settings"] = {mode: {key: value for key, value in env.items() if key in
                                                  ["CACHE_ENABLED", "CACHE_NAMESPACE", "CACHE_PRODUCT_TTL_MINUTES", "RATELIMIT_ENABLED", "PORT", "REDIS_PORT", "MONGO_URI"]}
                                              for mode, env in self.envs.items()}
        self.manifest["pids"] = {name: process.pid for name, process in self.processes.items()}
        write_json(self.output / "manifest.json", self.manifest)

    def wait_backend(self, mode):
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if self.processes[f"backend-{mode}"].poll() is not None:
                raise RuntimeError(f"backend-{mode} exited; inspect its log")
            try:
                health = http("GET", self.urls[mode] + "/actuator/health", timeout=2)
                if health["status"] == "UP":
                    metrics = self.metrics(mode)
                    if metrics["cacheEnabled"] != (mode == "on"):
                        raise RuntimeError("Backend cache selection differs from requested mode")
                    return
            except requests.RequestException:
                pass
            time.sleep(.5)
        raise RuntimeError(f"backend-{mode} startup timeout")

    def metrics(self, mode):
        return http("GET", self.urls[mode] + "/internal/evaluation/metrics")

    def jmeter_command(self, extra):
        return [self.java, "-Xms256m", "-Xmx512m", "-Djava.awt.headless=true", "-jar", str(self.jmeter), *extra]

    def run_point(self, mode, users, repeat, phase="steady", duration=None, loops=-1, ramp=None):
        folder = self.output / f"{phase}-{mode}-u{users}-r{repeat}"
        folder.mkdir()
        raw = folder / "raw.jtl"
        ramp = self.args.ramp if ramp is None else ramp
        duration = self.args.duration if duration is None else duration
        before = self.metrics(mode)
        write_json(folder / "before.json", before)
        command = self.jmeter_command([
            "-n", "-t", str(ROOT / "evaluation/jmeter/product-cache.jmx"), "-l", str(raw), "-j", str(folder / "jmeter.log"),
            f"-JdataFile={self.csv.as_posix()}", f"-Jport={self.args.base_port + (mode == 'on')}",
            f"-Jusers={users}", f"-Jduration={duration + ramp}", f"-Jramp={ramp}", f"-Jloops={loops}",
            "-Jjmeter.save.saveservice.output_format=csv", "-Jjmeter.save.saveservice.print_field_names=true",
            "-Jjmeter.save.saveservice.timestamp_format=ms", "-Jsampleresult.timestamp.start=true",
            "-Jjmeter.save.saveservice.assertion_results_failure_message=true",
            "-Jjmeter.save.saveservice.thread_counts=true", "-Jjmeter.save.saveservice.response_data=false"])
        write_json(folder / "command.json", command)
        self.progress(f"{phase}: cache={mode}, users={users}, repeat={repeat}, measurement={duration}s, ramp={ramp}s")
        console = (folder / "console.log").open("wb")
        process = subprocess.Popen(command, cwd=folder, stdout=console, stderr=subprocess.STDOUT,
                                   stdin=subprocess.DEVNULL, creationflags=HIDDEN)
        roles = {"backend": psutil.Process(self.processes[f"backend-{mode}"].pid), "mongodb": psutil.Process(self.mongo_pid),
                 "jmeter": psutil.Process(process.pid)}
        if self.processes["redis"].poll() is None:
            roles["redis"] = psutil.Process(self.processes["redis"].pid)
        for monitored in roles.values():
            monitored.cpu_percent()
        resources = []
        deadline = time.monotonic() + duration + ramp + 90
        while process.poll() is None:
            if time.monotonic() > deadline:
                process.kill()
                raise RuntimeError("JMeter exceeded its time budget")
            for role, monitored in roles.items():
                try:
                    cpu = monitored.cpu_percent()
                    resources.append({"timestamp": int(time.time() * 1000), "role": role, "pid": monitored.pid,
                                      "cpu_one_core_pct": cpu, "cpu_host_pct": cpu / psutil.cpu_count(),
                                      "rss_mib": monitored.memory_info().rss / 1024**2})
                except psutil.NoSuchProcess:
                    pass
            time.sleep(1)
        console.close()
        if process.returncode:
            raise RuntimeError(f"JMeter failed with exit {process.returncode}; inspect {folder}")
        after = self.metrics(mode)
        write_json(folder / "after.json", after)
        with (folder / "resources.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=["timestamp", "role", "pid", "cpu_one_core_pct", "cpu_host_pct", "rss_mib"])
            writer.writeheader()
            writer.writerows(resources)
        point = analyse_samples(raw, folder / "measured.jtl", ramp if phase == "steady" else 0,
                                duration if phase == "steady" else None)
        requests_delta = after["productReads"]["detailRequests"] - before["productReads"]["detailRequests"]
        database_delta = after["productReads"]["detailDatabaseLoads"] - before["productReads"]["detailDatabaseLoads"]
        hits = after["cache"]["hits"] - before["cache"]["hits"]
        misses = after["cache"]["misses"] - before["cache"]["misses"]
        diag_after, diag_before = after.get("diagnostics") or {}, before.get("diagnostics") or {}
        failures = {key: diag_after.get(key, 0) - diag_before.get(key, 0)
                    for key in ("lookupFailures", "writeFailures", "invalidationFailures")}
        lookup_ns = diag_after.get("totalLookupTimeNs", 0) - diag_before.get("totalLookupTimeNs", 0)
        point.update({"mode": mode, "users": users, "repeat": repeat, "phase": phase,
                      "folder": folder.name, "requests_including_ramp": requests_delta,
                      "db_loads_including_ramp": database_delta, "db_loads_per_request": database_delta / requests_delta if requests_delta else None,
                      "cache_hits_including_ramp": hits, "cache_misses_including_ramp": misses,
                      "cache_hit_pct": 100 * hits / (hits + misses) if hits + misses else None,
                      "cache_lookup_mean_ms": lookup_ns / 1e6 / (hits + misses) if hits + misses else None,
                      "cache_failures": failures})
        point["resources"] = {}
        for role in roles:
            samples = [r for r in resources if r["role"] == role and point["window_start_ms"] <= r["timestamp"] <= point["window_end_ms"]]
            point["resources"][role] = {"samples": len(samples),
                                       "mean_cpu_host_pct": statistics.mean(s["cpu_host_pct"] for s in samples) if samples else None,
                                       "mean_cpu_one_core_pct": statistics.mean(s["cpu_one_core_pct"] for s in samples) if samples else None,
                                       "peak_rss_mib": max((s["rss_mib"] for s in samples), default=None)}
        point["counter_sample_count_agrees"] = requests_delta == point["raw_samples"]
        if not point["counter_sample_count_agrees"] or point["raw_errors"]:
            write_json(folder / "summary.json", point)
            raise RuntimeError(f"Invalid load point: errors or mismatched counters; inspect {folder}")
        if phase == "steady" and any(failures.values()):
            raise RuntimeError("Redis failures invalidate a normal cache baseline comparison")
        if mode == "off" and database_delta != requests_delta:
            raise RuntimeError("No-cache baseline did not load every requested product from MongoDB")
        write_json(folder / "summary.json", point)
        if phase != "warmup":
            self.points.append(point)
            write_json(self.output / "points.json", self.points)
        self.progress(f"Completed {folder.name}: {point['samples']} requests, mean {point['mean_ms']:.2f}ms, p95 {point['p95_ms']:.2f}ms, {point['successful_rps']:.1f} successful requests/s")
        return point

    def freshness_check(self):
        product = self.products[0]
        cached = http("GET", self.urls["on"] + f"/api/products/{product['productId']}")
        changed = {k: cached[k] for k in ("sku", "name", "description", "category", "price", "reorderLevel", "active")}
        changed["name"] += " updated"
        updated = http("PUT", self.urls["on"] + f"/api/products/{product['productId']}", json=changed)
        reread = http("GET", self.urls["on"] + f"/api/products/{product['productId']}")
        result = {"scope": "sequential product metadata invalidation check, outside load measurement",
                  "updated": updated["name"], "reread": reread["name"], "passed": updated["name"] == reread["name"]}
        write_json(self.output / "freshness-check.json", result)
        if not result["passed"]:
            raise RuntimeError("Cache invalidation check failed")

    def redis_failure_check(self):
        self.progress("Failure scenario: stopping only this experiment's dedicated Redis")
        self.processes["redis"].terminate()
        self.processes["redis"].wait(timeout=15)
        outage = self.run_point("on", 5, 0, "redis-outage", duration=10, ramp=0)
        if outage["cache_failures"]["lookupFailures"] == 0 or outage["db_loads_per_request"] != 1:
            raise RuntimeError("Redis outage did not exercise the expected database fallback")
        self.launch("redis", self.redis_command)
        self.wait_backend("on")
        self.run_point("on", 10, 0, "recovery-warmup", duration=5, ramp=0)
        recovered = self.run_point("on", 5, 0, "redis-recovery", duration=10, ramp=0)
        if any(recovered["cache_failures"].values()) or recovered["cache_hit_pct"] != 100:
            raise RuntimeError("Redis did not recover cleanly after restart")
        write_json(self.output / "redis-failure-check.json", {
            "passed": True, "scope": "product read fallback and recovery; dedicated Redis process unavailable",
            "outage": outage, "recovery": recovered,
            "note": "Existing Redis on port 6379 and the walkthrough application were untouched"})

    def run(self):
        self.prepare()
        # A finite traversal measures empty-Redis population separately; it is not
        # mixed into steady-state warm-cache points or called a cold disk test.
        self.run_point("on", 10, 0, "cold-cache", duration=60, loops=math.ceil(self.args.products / 10), ramp=0)
        for mode in ("off", "on"):
            self.run_point(mode, 10, 0, "warmup", duration=self.args.warmup, ramp=0)
        for repeat in range(1, self.args.repeats + 1):
            users_order = self.args.users if repeat % 2 else list(reversed(self.args.users))
            for users in users_order:
                modes = ("off", "on") if (repeat + self.args.users.index(users)) % 2 else ("on", "off")
                for mode in modes:
                    self.run_point(mode, users, repeat)
        write_summary(self.output, self.points, self.manifest)
        if self.args.redis_failure:
            self.redis_failure_check()
        self.freshness_check()
        write_json(self.output / "points.json", self.points)
        write_summary(self.output, self.points, self.manifest)
        self.html_reports()
        for jtl in self.output.rglob("*.jtl"):
            with jtl.open("rb") as source, gzip.open(str(jtl) + ".gz", "wb") as destination:
                shutil.copyfileobj(source, destination)
            jtl.unlink()  # Generated file retained losslessly in gzip next to it.
        self.manifest["finished_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        self.manifest["keep_services"] = self.args.keep_services
        self.manifest["final_pids"] = {name: process.pid for name, process in self.processes.items()}
        write_json(self.output / "manifest.json", self.manifest)
        self.progress("All cache comparisons completed; reports saved")

    def html_reports(self):
        if self.args.html == "none":
            return
        selected = [p for p in self.points if p["phase"] == "steady" and p["users"] == max(self.args.users) and p["repeat"] == 1]
        if self.args.html == "all":
            selected = [p for p in self.points if p["phase"] == "steady"]
        for point in selected:
            folder = self.output / point["folder"]
            command = self.jmeter_command(["-g", str(folder / "measured.jtl"), "-o", str(folder / "html"), "-j", str(folder / "html-generation.log")])
            with (folder / "html-generation-console.log").open("wb") as log:
                subprocess.run(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT,
                               creationflags=HIDDEN, check=True, timeout=120)

    def close(self):
        if not self.args.keep_services:
            for process in reversed(list(self.processes.values())):
                if process.poll() is None:
                    process.terminate()
                    try: process.wait(timeout=15)
                    except subprocess.TimeoutExpired: process.kill()
        for log in self.logs:
            log.close()


def analyse_samples(raw, measured, ramp_seconds, duration):
    with raw.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        fields = reader.fieldnames
        first = next(reader, None)
        if first is None:
            raise RuntimeError("JMeter recorded no samples")
        minimum = int(first["timeStamp"])
        maximum = minimum
        for row in reader:
            minimum = min(minimum, int(row["timeStamp"]))
            maximum = max(maximum, int(row["timeStamp"]) + int(row["elapsed"]))
        maximum = max(maximum, int(first["timeStamp"]) + int(first["elapsed"]))
    start = minimum + ramp_seconds * 1000
    end = start + duration * 1000 if duration is not None else maximum
    if duration is not None and maximum < end - 300:
        raise RuntimeError("JMeter did not cover the requested steady-state duration")
    latencies, success_latencies, statuses = [], [], collections.Counter()
    errors, raw_count, raw_errors = 0, 0, 0
    with raw.open(newline="", encoding="utf-8-sig") as source, measured.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=fields)
        writer.writeheader()
        for row in csv.DictReader(source):
            raw_count += 1
            if row["success"].lower() != "true":
                raw_errors += 1
            stamp, elapsed = int(row["timeStamp"]), int(row["elapsed"])
            if stamp < start or stamp + elapsed > end:
                continue
            writer.writerow(row)
            latencies.append(elapsed)
            statuses[row["responseCode"]] += 1
            if row["success"].lower() == "true": success_latencies.append(elapsed)
            else: errors += 1
    if not latencies:
        raise RuntimeError("Empty measurement window")
    seconds = max((end - start) / 1000, .001)
    return {"raw_samples": raw_count, "raw_errors": raw_errors, "samples": len(latencies), "errors": errors,
            "error_pct": errors / len(latencies) * 100, "status_counts": dict(statuses),
            "measurement_seconds": seconds, "window_start_ms": start, "window_end_ms": end,
            "mean_ms": statistics.mean(latencies), "p50_ms": percentile(latencies, .5),
            "p95_ms": percentile(latencies, .95), "p99_ms": percentile(latencies, .99),
            "successful_mean_ms": statistics.mean(success_latencies) if success_latencies else None,
            "attempted_rps": len(latencies) / seconds, "successful_rps": len(success_latencies) / seconds}


def write_summary(output, points, manifest):
    measured = [point for point in points if point["phase"] == "steady"]
    groups = []
    for users in manifest["settings"]["users"]:
        for mode in ("off", "on"):
            group = [p for p in measured if p["users"] == users and p["mode"] == mode]
            row = {"users": users, "cache": mode, "repetitions": len(group),
                   "total_requests": sum(p["samples"] for p in group)}
            for key in ("mean_ms", "p95_ms", "p99_ms", "successful_rps", "error_pct", "db_loads_per_request"):
                row[key] = statistics.mean(p[key] for p in group)
                row[key + "_stddev"] = statistics.stdev(p[key] for p in group) if len(group) > 1 else 0
            for key in ("cache_hit_pct", "cache_lookup_mean_ms"):
                values = [p[key] for p in group if p[key] is not None]
                row[key] = statistics.mean(values) if values else None
            for role in ("backend", "mongodb", "redis", "jmeter"):
                for key in ("mean_cpu_host_pct", "peak_rss_mib"):
                    values = [p["resources"][role][key] for p in group if p["resources"][role][key] is not None]
                    row[f"{role}_{key}"] = statistics.mean(values) if values else None
            groups.append(row)
    write_json(output / "summary.json", groups)
    with (output / "summary.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(groups[0]))
        writer.writeheader()
        writer.writerows(groups)
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(1, 3, figsize=(12, 3.7), constrained_layout=True)
    loads = manifest["settings"]["users"]
    for mode, color, shift in (("off", "#9099a8", -.18), ("on", "#3659c9", .18)):
        rows = [r for r in groups if r["cache"] == mode]
        for axis, field, title, ylabel in zip(axes, ("mean_ms", "p95_ms", "successful_rps"),
                                              ("Mean response time", "95th percentile response time", "Successful throughput"),
                                              ("Milliseconds", "Milliseconds", "Requests / second")):
            axis.bar([i + shift for i in range(len(loads))], [r[field] for r in rows], width=.34,
                     yerr=[r[field + "_stddev"] for r in rows], capsize=3, color=color, label="Cache " + mode)
            axis.set_title(title, fontsize=10)
            axis.set_ylabel(ylabel, fontsize=9)
            axis.set_xticks(range(len(loads)), [str(n) for n in loads])
            axis.set_xlabel("Concurrent JMeter users", fontsize=9)
            axis.spines[["top", "right"]].set_visible(False)
            axis.grid(axis="y", alpha=.15)
            axis.set_axisbelow(True)
    axes[0].legend(frameon=False, fontsize=8)
    fig.suptitle("AetherStock product reads · cache baseline comparison\nBars: repetition means; error bars: between-run standard deviation", fontsize=11)
    fig.savefig(output / "cache-comparison.png", dpi=180)
    plt.close(fig)
    lines = ["# JMeter product cache comparison", "", f"Run: `{output.name}`. Base revision: `{manifest['git_commit']}` (see recorded working-tree state and jar SHA).", "",
             f"Dataset: {manifest['settings']['products']} products. {manifest['settings']['duration']} steady seconds per run, "
             f"{manifest['settings']['ramp']} second ramp excluded, {manifest['settings']['repeats']} repetitions. Limiter off; stock reads remain authoritative.", "",
             "| Users | Cache | Repeats | Requests | Mean ms | p95 ms | Successful req/s | Error % | Cache hit % | DB reads/request |",
             "| ---: | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for row in groups:
        hit = f"{row['cache_hit_pct']:.2f}" if row["cache_hit_pct"] is not None else "N/A"
        lines.append(f"| {row['users']} | {row['cache']} | {row['repetitions']} | {row['total_requests']} | {row['mean_ms']:.3f} | {row['p95_ms']:.3f} | {row['successful_rps']:.1f} | {row['error_pct']:.2f} | {hit} | {row['db_loads_per_request']:.4f} |")
    lines += ["", "![Cache comparison](cache-comparison.png)", "", "## Interpretation and scope", "",
              "Cache hits and database loads are counted around each complete JMeter run, including ramp; latency, throughput and resources use only the steady measurement window. Raw samples are retained losslessly as .jtl.gz files.",
              "Cold-Redis population and JVM warm-up are recorded separately. Cold Redis is not cold MongoDB: the product working set is local and warmed by seeding/reads.",
              "All modes use the same HTTP endpoint, product CSV, response-code assertion and product ID/SKU assertions. A cache failure invalidates an ordinary baseline run.",
              "CPU percentages in summary.csv are shares of total host logical-CPU capacity. Per-core CPU and RSS observations are in each resources.csv. Backend, MongoDB, Redis and JMeter are measured separately.",
              "Two backend JVMs remain running, with one receiving benchmark traffic at a time. The shared MongoDB instance and background applications limit conclusions about isolated server capacity.",
              "This experiment does not measure stock-read cache freshness: stock reads bypass Redis. freshness-check.json checks sequential product metadata invalidation only, not all concurrent cache races.",
              "A local cache can reduce database reads without improving HTTP latency; both Redis and MongoDB use this same machine. Report observed results without assuming a speedup.",
              "The 3-workload cache experiment is one part of the lab evaluation. Rate limiting, stock-write contention, shard addition/removal, ID generation and autocomplete require their own workload/baseline comparisons.", ""]
    failure_path = output / "redis-failure-check.json"
    if failure_path.exists():
        failure = json.loads(failure_path.read_text(encoding="utf-8"))
        lines += ["## Redis outage and recovery", "",
                  "Separate 5-user failure scenario; these points are excluded from the cache comparison above.", "",
                  "| State | Requests | Mean ms | p95 ms | Successful req/s | Error % | DB reads/request |",
                  "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
        for state in ("outage", "recovery"):
            p = failure[state]
            lines.append(f"| {state} | {p['samples']} | {p['mean_ms']:.2f} | {p['p95_ms']:.2f} | {p['successful_rps']:.1f} | {p['error_pct']:.2f} | {p['db_loads_per_request']:.4f} |")
        lines += ["", "Product responses were checked throughout. During the outage Redis lookup/write failures are expected and counted; product reads fall back to MongoDB. Recovery includes a separate warm-up. This is a cache failure, not a database failover or shard failure test.", ""]
    (output / "README.md").write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--users", type=int, nargs="+", default=[5, 25, 100])
    parser.add_argument("--duration", type=int, default=30, help="Steady seconds after ramp")
    parser.add_argument("--ramp", type=int, default=5)
    parser.add_argument("--warmup", type=int, default=15)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--products", type=int, default=1000)
    parser.add_argument("--mongo-port", type=int, default=27019)
    parser.add_argument("--base-port", type=int, default=8082)
    parser.add_argument("--redis-port", type=int, default=6381)
    parser.add_argument("--jmeter-home")
    parser.add_argument("--redis-exe")
    parser.add_argument("--html", choices=["representative", "all", "none"], default="representative")
    parser.add_argument("--keep-services", action="store_true")
    parser.add_argument("--redis-failure", action="store_true", help="Also test dedicated Redis outage and recovery")
    args = parser.parse_args()
    if min(args.users) < 1 or args.duration < 2 or args.warmup < 1 or args.products < 10 or args.repeats < 1 or args.ramp < 0 or len(set(args.users)) != len(args.users):
        parser.error("Use positive workloads, at least 10 products and at least 2 measurement seconds")
    experiment = Experiment(args)
    try:
        experiment.run()
    except Exception as error:
        write_json(experiment.output / "failure.json", {"error": str(error), "type": type(error).__name__})
        experiment.args.keep_services = False
        raise
    finally:
        experiment.close()


if __name__ == "__main__":
    main()
