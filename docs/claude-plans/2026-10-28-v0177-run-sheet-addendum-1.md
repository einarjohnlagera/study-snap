# v0.177.0 run sheet addendum 1 (owner-run commands, Amendment 1)

Status: **DRAFT FOR THE OWNER'S APPROVAL; not yet committed.** Companion to `docs/claude-plans/2026-10-28-v0177-run-sheet.md` (frozen, SHA-256 `20cdd28b…` in the pre-registration, **not edited**) and to Amendment 1 in `docs/claude-plans/2026-10-28-challenge-quiz-quality-preregistration-v0177.md` section 6. Every command below was produced by substitution from the frozen run sheet's lines (not retyped). **Nothing here authorizes more than the $4.00 ceiling; the owner runs every paid command with their own key, which I never see.**

## What the original record is (kept untouched, fingerprinted 2026-10-28 23:50)

| Folder | `results.jsonl` SHA-256 | Starts | Note |
|---|---|---|---|
| `quality-full-A1` | `887110ec8adf30bfa4674a8a716cbdb0f98e6c0c5704f89670319d2aec46743e` | see Amendment 1 | original, never edited |
| `quality-full-A2` | `b5e32f3e3359e9ba8f75933b4baa702734de0b7780b772e3cef8bc5e6eb73fbb` | see Amendment 1 | original, never edited |
| `quality-full-A3` | `bdcc655c4fa361e03c21d47dde7edfccd33663d3b2ee66467ccab5f5f35088bd` | see Amendment 1 | original, never edited |
| `quality-full-B2` | `d6d24f664f927051dc72352c51eb693977288d72af83a5c0fb5950b0d3e09e88` | see Amendment 1 | original, never edited |
| `quality-full-B3` | `c4ac7364cc2a6f18bf8a9812b6394f33764b99760c6083394367b9a8df327315` | see Amendment 1 | original, never edited |

## 1. Before anything: connectivity check (no key is sent)

The Sol failures were `UnknownHostException` (a DNS failure on the owner's machine). Before the first command and again before each Sol command:

```sh
curl -sS -o /dev/null -w '%{http_code}\n' --max-time 10 https://api.openai.com/v1/models
```

Expected: `401` (the request carries no key on purpose; any HTTP status proves DNS, TLS and routing work). A DNS or timeout error means **do not start the command**: fix the network first. Keep the machine awake and on one stable network for the Sol commands (each takes about 30 to 40 minutes).

## 2. Smoke pass for note 1 (never run; the frozen sheet's section 1, unchanged)

Run the five commands of section 1 of the frozen run sheet exactly as written, with its pre-checks (`test ! -e` for each folder, the tally after each command, and the prompt-hash loop at the end). Their folders `quality-smoke-A1`, `-A2`, `-A3`, `-B2`, `-B3` do not exist yet.

## 3. Re-runs of the void Sol starts (two commands, fresh folders)

`quality-rerun-A3` re-runs notes 5 to 20 only (notes 2 to 4 succeeded and stay). `quality-rerun-B3` re-runs notes 2 to 20. Run them back to back, A3 then B3, after the connectivity check, with the same `test ! -e` check and the tally rule (do not start if `TOTAL` plus the stop plus 0.07 exceeds 4.00).

```sh
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=5-20 -Dllm.benchmark.input=target/llm-benchmark-v0177/quality-e.jsonl -Dllm.benchmark.arm=A3 -Dllm.benchmark.outDir=target/llm-benchmark-v0177/quality-rerun-A3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=1.20
./mvnw test -Dtest=LlmModelBenchmark -Dnativequery.pg.skip=true -Dllm.benchmark=true -Dllm.benchmark.qualityEval=true -Dllm.benchmark.legs=E -Dllm.benchmark.cells=S15 -Dllm.benchmark.expectedNotes=20 -Dllm.benchmark.runs=2-20 -Dllm.benchmark.input=target/llm-benchmark-v0177/quality-e.jsonl -Dllm.benchmark.arm=B3 -Dllm.benchmark.challengePromptFile=src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.gplus.txt -Dllm.benchmark.outDir=target/llm-benchmark-v0177/quality-rerun-B3 -Dllm.benchmark.model=gpt-6.1-sol -Dllm.benchmark.priceInPerM=2.00 -Dllm.benchmark.priceOutPerM=10.00 -Dllm.benchmark.spendStopUsd=1.40
```

Spend stops: A3 re-run **1.20** (16 notes at about $0.069 each is about $1.10; the frozen sheet's stop for 19 notes was 1.40), B3 re-run **1.40** (unchanged, 19 notes).

## 4. Budget (the harness enforces nothing across commands; this is the manual plan)

| Item | Amount |
|---|---|
| Metered so far (all five full folders, 2026-10-28) | $0.5148 |
| Smoke pass stops (as frozen) | + $0.33 |
| A3 re-run stop | + $1.20 |
| B3 re-run stop | + $1.40 |
| **Sum of stops** | **$3.4448** |
| Overshoot allowance (one call per command: 0.07 x 4 Sol commands, 0.01 x 3 others) | + about $0.31 |
| **Estimated worst case** | **about $3.76, inside the $4.00 ceiling** |
| Expected actual (smoke about $0.16, A3 re-run about $1.10, B3 re-run about $1.30) | about $3.07 |

## 5. Offline steps I run (no key)

**5a. Build the effective folders** (data-only; reads the original and re-run `results.jsonl`, writes new folders, never edits an original; aborts if a note would appear twice or the target exists). The export and the descriptors both throw on a duplicate arm and note, and both read only `results.jsonl`, so they need a folder with the non-void original rows plus the re-run rows. Run from `backend/` once the re-runs finish:

```sh
python3 - <<'PY'
import json, os, sys
root = "target/llm-benchmark-v0177"
VOID = {"UnknownHostException", "SocketException"}

def rows(path):
    with open(path, "rb") as f:
        return [l for l in f.read().decode("utf-8").split("\n") if l]

def is_void(line):
    r = json.loads(line)
    return r.get("success") is False and r.get("failureClass") == "HTTP_ERROR" and r.get("errorMessage") in VOID

for arm in ("A3", "B3"):
    kept, voided = [], []
    for line in rows(f"{root}/quality-full-{arm}/results.jsonl"):
        (voided if is_void(line) else kept).append(line)
    rerun = rows(f"{root}/quality-rerun-{arm}/results.jsonl")
    ids = [json.loads(l)["inputId"] for l in kept + rerun]
    assert len(ids) == len(set(ids)), f"duplicate notes in {arm}: {ids}"
    out = f"{root}/quality-effective-{arm}"
    assert not os.path.exists(out), f"{out} already exists"
    os.makedirs(out)
    with open(f"{out}/results.jsonl", "w", encoding="utf-8", newline="") as f:
        f.write("\n".join(kept + rerun) + "\n")
    print(arm, "kept", len(kept), "original rows,", "voided", len(voided), "rows,", "re-run rows", len(rerun), "->", len(kept) + len(rerun), "starts")
PY
```

**5b. Replacement export and descriptor commands** (the frozen section 3 lines with `quality-full-A3` and `quality-full-B3` replaced by `quality-effective-A3` and `quality-effective-B3`; `quality-smoke-*` stay):

```sh
./mvnw test -Dtest=QualityBlindExport -Dnativequery.pg.skip=true -Dllm.benchmark.qualityBlind=true -Dllm.benchmark.blindDirs=target/llm-benchmark-v0177/quality-smoke-A1,target/llm-benchmark-v0177/quality-full-A1,target/llm-benchmark-v0177/quality-smoke-A2,target/llm-benchmark-v0177/quality-full-A2,target/llm-benchmark-v0177/quality-smoke-A3,target/llm-benchmark-v0177/quality-effective-A3,target/llm-benchmark-v0177/quality-smoke-B2,target/llm-benchmark-v0177/quality-full-B2,target/llm-benchmark-v0177/quality-smoke-B3,target/llm-benchmark-v0177/quality-effective-B3 -Dllm.benchmark.stock=target/llm-benchmark-v0177/quality-stock.jsonl -Dllm.benchmark.blindOutDir=target/llm-benchmark-v0177/quality-blind -Dllm.benchmark.blindSeed=177 -Dllm.benchmark.blindChunk=100
./mvnw test -Dtest=QualityDescriptors -Dnativequery.pg.skip=true -Dllm.benchmark.qualityDescriptors=true -Dllm.benchmark.input=target/llm-benchmark-v0177/quality-e.jsonl -Dllm.benchmark.blindDirs=target/llm-benchmark-v0177/quality-smoke-A1,target/llm-benchmark-v0177/quality-full-A1,target/llm-benchmark-v0177/quality-smoke-A2,target/llm-benchmark-v0177/quality-full-A2,target/llm-benchmark-v0177/quality-smoke-A3,target/llm-benchmark-v0177/quality-effective-A3,target/llm-benchmark-v0177/quality-smoke-B2,target/llm-benchmark-v0177/quality-full-B2,target/llm-benchmark-v0177/quality-smoke-B3,target/llm-benchmark-v0177/quality-effective-B3 -Dllm.benchmark.descriptorsOutDir=target/llm-benchmark-v0177/quality-descriptors
```

The review plan, batches and gate commands (frozen section 4) are unchanged.

## 6. Disclosure commitments

The evaluation report lists the 35 void starts (A3 notes 5 to 20, B3 notes 2 to 20) with their input ids, the fingerprints above, that they carried no tokens and no spend, and that the 4 mini parse failures in A1 (notes 2, 4, 15 and 20) are **not** void and stand. It states the changed run order, the time-of-day gap between the original and re-run starts, and that note 1 was generated after notes 2 to 20.
