#!/usr/bin/env python3
"""Run existing M03 Java25 synthetic integration entrypoints, each batch ID separately.

Source and test classes are already compiled in CLASSES by caller. This does
not simulate a result or claim PostgreSQL/clinical acceptance.
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TEST_ROOT=ROOT/"services/clinical-core/src/test/java/org/synqoria/triage"
CLASSES=Path("/tmp/synqoria-m03-classes")
BATCH_ID=re.compile(r'\bcase\s+"(P\d{2}-B\d{4}|codec-[a-z0-9-]+|crypto-[a-z0-9-]+|jdbc-[a-z0-9-]+)"\s*(?:->|:)')


def cases(source):
    if not re.search(r'\bpublic\s+static\s+void\s+main\s*\(',source):
        return None
    matched=sorted(set(BATCH_ID.findall(source)))
    if matched and "args" not in source:
        raise ValueError("batch entrypoint missing args")
    return matched


def main():
    if not CLASSES.exists():
        raise SystemExit("Missing compiled Java M03 output")
    entrypoints=0
    executed=0
    batch_count=0
    for file in sorted(TEST_ROOT.glob("*Test.java")):
        source=file.read_text(encoding="utf-8")
        ids=cases(source)
        if ids is None:
            continue
        entrypoints+=1
        argv_sets=[[id] for id in ids] if ids else [[]]
        for argv in argv_sets:
            name="org.synqoria.triage."+file.stem
            print(f"M03_RUN {name} {','.join(argv) if argv else 'standalone'}",flush=True)
            subprocess.run(["java","-ea","-cp",str(CLASSES),name,*argv],
                           timeout=50,check=True)
            executed+=1
            batch_count+=bool(argv)
    if entrypoints<8 or batch_count<30:
        raise SystemExit(f"Insufficient M03 test discovery: entrypoints={entrypoints} batches={batch_count}")
    print(f"M03_JAVA25_SYNTHETIC_PASS entrypoints={entrypoints} distinct_invocations={executed} batch_cases={batch_count}")


if __name__=="__main__":
    main()
