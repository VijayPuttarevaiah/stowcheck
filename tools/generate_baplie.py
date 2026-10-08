#!/usr/bin/env python3
"""Generate synthetic BAPLIE 2.2.1 stowage plans, optionally with injected errors.

Examples:
  python3 tools/generate_baplie.py --units 120 > plan.edi
  python3 tools/generate_baplie.py --units 120 --error-rate 0.05 --seed 7 > plan.edi
  python3 tools/generate_baplie.py --post http://localhost:8080/api/plans --count 50 --interval 2

All data is synthetic. Container numbers carry valid ISO 6346 check digits unless an
error is injected on purpose.
"""
import argparse
import random
import sys
import time
import urllib.request
from datetime import datetime, timedelta

CARRIERS = [  # (SCAC-style code used in TDT, operator code used in NAD, owner prefix)
    ("MSCU", "MSC", "MSCU"), ("MAEU", "MAE", "MAEU"), ("CMDU", "CMA", "CMAU"),
    ("HLCU", "HLC", "HLXU"), ("ONEY", "ONE", "ONEU"), ("OOLU", "OOL", "OOLU"),
]
VESSELS = [("9839131", "MSC AURORA"), ("9811000", "MAERSK HALIFAX"), ("9454448", "CMA CGM ATLAS"),
           ("9501332", "HAMBURG BRIDGE"), ("9776418", "ONE PACIFIC"), ("9795610", "OOCL CANADA")]
PORTS_OF_LOADING = ["BEANR", "DEHAM", "NLRTM", "GBFXT", "FRLEH"]
PORTS_OF_DISCHARGE = ["CAHAL", "CAHAL", "CAHAL", "USNYC", "USSAV", "CAMTR"]
DRY_TYPES = ["22G1", "45G1", "42G1"]
REEFER_TYPES = ["22R1", "45R1"]
DG = [("3", "1993"), ("8", "1760"), ("2.1", "1950"), ("9", "3077"), ("6.1", "2811")]
ERROR_KINDS = ["check_digit", "missing_weight", "overweight", "odd_bay_40", "no_pod",
               "temp_on_dry", "reefer_no_temp", "bad_dg_class", "not_vgm", "duplicate_cell"]


def letter_values():
    values, v = {}, 10
    for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
        if v % 11 == 0:
            v += 1
        values[ch] = v
        v += 1
    return values


LETTERS = letter_values()


def check_digit(first10):
    total = sum((int(c) if c.isdigit() else LETTERS[c]) * (2 ** i) for i, c in enumerate(first10))
    return total % 11 % 10


def container_number(prefix, rng):
    body = prefix + f"{rng.randint(0, 999999):06d}"
    return body + str(check_digit(body))


HOLD_TIERS = [2, 4, 6, 8]
DECK_TIERS = [82, 84, 86, 88]
ROWS = [0, 1, 2, 3, 4, 5, 6]


def cells(units, rng):
    """Build physically plausible stacks, bottom tier first, so nothing floats.

    Each 40ft bay (02, 06, 10 ...) spans the two 20ft bays either side of it. A stack in
    one row of one section (hold or deck) is either all 40ft in the even bay, or 20ft in
    both odd bays, so a 40ft box always has full support underneath.
    """
    stacks = []
    for even_bay in range(2, 40, 4):
        for row in ROWS:
            for tiers in (HOLD_TIERS, DECK_TIERS):
                stacks.append((even_bay, row, tiers))
    rng.shuffle(stacks)

    chosen = []
    for even_bay, row, tiers in stacks:
        height = rng.randint(1, len(tiers))
        forty = rng.random() < 0.6
        for tier in tiers[:height]:
            bays = [even_bay] if forty else [even_bay - 1, even_bay + 1]
            for bay in bays:
                chosen.append((bay, row, tier))
        if len(chosen) >= units:
            break
    return sorted(chosen[:units], key=lambda c: (c[0], c[1], c[2]))


def build(units, error_rate, rng):
    line = rng.randrange(len(CARRIERS))
    carrier = CARRIERS[line][0]
    imo, vessel = VESSELS[line]
    voyage = f"{rng.randint(1, 99):03d}{rng.choice('EWNS')}"
    pol_default = rng.choice(PORTS_OF_LOADING)
    now = datetime(2026, 10, 1) + timedelta(hours=rng.randint(0, 24 * 30))
    stamp = now.strftime("%y%m%d%H%M")
    ref = f"{rng.randint(1, 999999):06d}"

    body = [
        f"BGM++{ref}+9",
        f"DTM+137:{stamp}:201",
        f"TDT+20+{voyage}+++{carrier}:172:20+++{imo}:146:11:{vessel}",
        f"LOC+5+{pol_default}:139:6",
        "LOC+61+CAHAL:139:6",
        f"DTM+132:{(now + timedelta(days=8)).strftime('%y%m%d%H%M')}:201",
    ]
    previous_cell = None
    for bay, row, tier in cells(units, rng):
        size = rng.choice(["22G1"] if bay % 2 else ["45G1", "42G1", "45R1"])
        if bay % 2 and rng.random() < 0.12:
            size = "22R1"
        reefer = size[2] == "R"
        full = rng.random() > 0.15
        weight = rng.randint(12000, 30000) if full else rng.randint(2200, 4200)
        _, op, prefix = rng.choice(CARRIERS)
        number = container_number(prefix, rng)
        cell = f"{bay:03d}{row:02d}{tier:02d}"
        pod = rng.choice(PORTS_OF_DISCHARGE)
        qualifier = "VGM" if full else "WT"
        temperature = f"{rng.choice([-18.0, -20.0, 2.0, 5.0, 13.0]):.1f}" if reefer and full else None
        dg = rng.choice(DG) if (not reefer and full and rng.random() < 0.06) else None
        dg_class = dg[0] if dg else None
        include_pod, include_weight = True, True

        if rng.random() < error_rate:
            kind = rng.choice(ERROR_KINDS)
            if kind == "check_digit":
                number = number[:10] + str((int(number[10]) + 1) % 10)
            elif kind == "missing_weight":
                include_weight = False
            elif kind == "overweight":
                weight = rng.randint(37000, 45000)
            elif kind == "odd_bay_40" and not bay % 2:
                cell = f"{bay + 1:03d}{row:02d}{tier:02d}"
            elif kind == "no_pod":
                include_pod = False
            elif kind == "temp_on_dry" and not reefer:
                temperature = "-18.0"
            elif kind == "reefer_no_temp" and reefer:
                temperature = None
            elif kind == "bad_dg_class":
                dg, dg_class = ("X", "1993"), "X"
            elif kind == "not_vgm" and full:
                qualifier = "WT"
            elif kind == "duplicate_cell" and previous_cell:
                cell = previous_cell

        body.append(f"LOC+147+{cell}::5")
        if include_weight:
            body.append(f"MEA+{qualifier}++KGM:{weight}")
        if temperature is not None:
            body.append(f"TMP+2+{temperature}:CEL")
        body.append(f"LOC+9+{pol_default}")
        if include_pod:
            body.append(f"LOC+11+{pod}")
        body.append("RFF+BM:1")
        body.append(f"EQD+CN+{number}+{size}+++{5 if full else 4}")
        body.append(f"NAD+CA+{op}:172:20")
        if dg:
            body.append(f"DGS+IMD+{dg_class}+{dg[1]}")
        previous_cell = cell

    interchange = f"IC{ref}"
    message = [f"UNH+{ref}+BAPLIE:D:95B:UN:SMDG22"] + body
    message.append(f"UNT+{len(message) + 1}+{ref}")
    segments = [f"UNB+UNOA:2+{carrier}+CAHAL+{stamp[:6]}:{stamp[6:]}+{interchange}"] + message
    segments.append(f"UNZ+1+{interchange}")
    return "\n".join(s + "'" for s in segments) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--units", type=int, default=80, help="occupied cells per plan")
    p.add_argument("--error-rate", type=float, default=0.0, help="chance each unit gets one injected error")
    p.add_argument("--seed", type=int, help="random seed for repeatable output")
    p.add_argument("--post", help="POST each plan to this URL instead of printing it")
    p.add_argument("--count", type=int, default=1, help="number of plans to generate")
    p.add_argument("--interval", type=float, default=0.0, help="seconds between posts")
    args = p.parse_args()

    rng = random.Random(args.seed)
    for i in range(args.count):
        plan = build(args.units, args.error_rate, rng)
        if not args.post:
            sys.stdout.write(plan)
            continue
        request = urllib.request.Request(args.post, data=plan.encode("latin-1"),
                                         headers={"Content-Type": "text/plain"}, method="POST")
        with urllib.request.urlopen(request) as response:
            print(f"plan {i + 1}/{args.count}: HTTP {response.status} {response.headers.get('Location')}")
        if args.interval:
            time.sleep(args.interval)


if __name__ == "__main__":
    main()
