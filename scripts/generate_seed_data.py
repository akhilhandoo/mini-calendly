#!/usr/bin/env python3
"""
Generates src/main/resources/seed/seed-data.json, the demo data loaded on startup by SeedDataLoader.

Times are relative, so the data never goes stale:
  * `day` is an offset in days from the Monday of the current week, in the user's own time zone
    (0 = this Monday, -7 = last Monday, 9 = next Wednesday);
  * `start` is a local wall-clock time in the user's time zone.
All slots lie between 06:00 and 24:00 local time, so DST switches (00:00-03:00) never shift them.

The output is deterministic (fixed random seed). Re-run after changing this script:
    python3 scripts/generate_seed_data.py
"""

import json
import random
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/seed/seed-data.json"
WEEKS_BACK, WEEKS_AHEAD = 4, 8  # slots span roughly [-4 weeks, +8 weeks] around this week

rng = random.Random(20261008)

users = []          # dicts as written to JSON
slots_by_user = {}  # email -> list of slot dicts
busy_minutes = {}   # (email, day) -> list of (start, end) local minutes, to keep slots non-overlapping


# ---------------------------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------------------------

def add_user(name, email, tz, created_days_ago=None):
    user = {"name": name, "email": email, "timeZone": tz,
            "createdDaysAgo": created_days_ago if created_days_ago is not None else rng.randint(40, 700)}
    users.append(user)
    slots_by_user[email] = []
    return user


def hhmm(minutes):
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def free(email, day, start, end):
    return all(end <= s or start >= e for s, e in busy_minutes.get((email, day), []))


def add_slot(email, day, start, minutes, status="FREE", meeting=None):
    """Adds a slot (local minutes since midnight); returns it, or None if it would overlap."""
    end = start + minutes
    assert 5 <= minutes <= 480, minutes
    assert 6 * 60 <= start and end <= 24 * 60, (start, end)
    if not free(email, day, start, end):
        return None
    busy_minutes.setdefault((email, day), []).append((start, end))
    slot = {"day": day, "start": hhmm(start), "minutes": minutes, "status": status}
    if meeting:
        slot["status"] = "BUSY"
        slot["meeting"] = meeting
    slots_by_user[email].append(slot)
    return slot


def days(weekdays_only=True):
    for day in range(-7 * WEEKS_BACK, 7 * WEEKS_AHEAD + 7):
        if not weekdays_only or day % 7 < 5:
            yield day


def person(user):
    return {"email": user["email"], "name": user["name"]}


# ---------------------------------------------------------------------------------------------
# meeting content
# ---------------------------------------------------------------------------------------------

TITLES = [
    "Weekly sync", "1:1", "Sprint planning", "Sprint review", "Retrospective", "Design review",
    "Architecture deep dive", "Customer call", "Interview: backend engineer", "Interview: product designer",
    "Quarterly business review", "Onboarding session", "Pairing session", "Incident post-mortem",
    "Roadmap discussion", "Budget review", "Demo prep", "Coffee chat", "Hiring debrief", "Security review",
    "Release go/no-go", "Vendor evaluation", "Board prep", "Mentoring", "Lunch & learn: PostgreSQL indexes",
    "Kick-off", "Status update", "Brainstorming", "Contract negotiation", "Support escalation",
]
DESCRIPTIONS = [
    "Agenda in the shared doc.", "Please come prepared with your updates.",
    "Dial-in details are in the invite.", "Recording will be shared afterwards.",
    "Follow-up from last week's discussion.", "Bring questions!", "Room 4.12 / video link in chat.",
    "We'll walk through the open action items.", "Timebox: hard stop at the end of the slot.",
]
EXTERNAL_DOMAINS = ["partner.example.org", "client.example.net", "gmail.example.com", "vendor.example.io"]
EXTERNAL_FIRST = ["Sam", "Robin", "Alex", "Jordan", "Taylor", "Morgan", "Casey", "Jamie", "Riley", "Quinn"]
EXTERNAL_LAST = ["Smith", "Garcia", "Nguyen", "Kowalski", "Okafor", "Silva", "Andersen", "Rossi", "Cohen"]


def external_participant(with_name=True):
    first, last = rng.choice(EXTERNAL_FIRST), rng.choice(EXTERNAL_LAST)
    email = f"{first}.{last}@{rng.choice(EXTERNAL_DOMAINS)}".lower()
    # Participants without a name are allowed: only the e-mail is required.
    return {"email": email, "name": f"{first} {last}"} if with_name else {"email": email}


def random_meeting(organizer, colleagues):
    title = rng.choice(TITLES)
    meeting = {"title": title}
    if rng.random() < 0.75:  # description is optional
        meeting["description"] = rng.choice(DESCRIPTIONS)
    participants = []
    roll = rng.random()
    if roll < 0.08:
        pass  # a meeting without participants, e.g. a reserved block with a title
    elif roll < 0.25:
        participants = [external_participant(rng.random() < 0.8) for _ in range(rng.randint(1, 3))]
    else:
        others = [c for c in colleagues if c is not organizer]
        participants = [person(c) for c in rng.sample(others, min(len(others), rng.randint(1, 5)))]
        if rng.random() < 0.3:  # mixed: colleagues and external guests
            participants.append(external_participant())
    if participants:
        meeting["participants"] = participants
    if rng.random() < 0.15:
        meeting["updated"] = True  # details edited after scheduling
    return meeting


# ---------------------------------------------------------------------------------------------
# 1. hand-crafted scenario users (mirroring the test suite); documented in README "Demo data"
# ---------------------------------------------------------------------------------------------

ada = add_user("Ada Lovelace", "ada.lovelace@acme.example.com", "Europe/Berlin", 720)
grace = add_user("Grace Hopper", "grace.hopper@acme.example.com", "America/New_York", 700)
alan = add_user("Alan Turing", "alan.turing@acme.example.com", None, 650)   # no time zone -> UTC, no slots
edge = add_user("Edsger Dijkstra", "edsger.dijkstra@acme.example.com", "Europe/Amsterdam", 600)
focus = add_user("Barbara Liskov", "barbara.liskov@acme.example.com", "America/Los_Angeles", 580)
guest = add_user("Linus Torvalds", "linus.torvalds@acme.example.com", "Europe/Helsinki", 560)  # participant only
host = add_user("Margaret Hamilton", "margaret.hamilton@acme.example.com", "America/Chicago", 540)
odd1 = add_user("Ramanujan Srinivasa", "srinivasa.ramanujan@acme.example.com", "Asia/Kolkata", 500)    # +05:30
odd2 = add_user("Pemba Sherpa", "pemba.sherpa@acme.example.com", "Asia/Kathmandu", 480)               # +05:45
common = [add_user("Klaus Becker", "klaus.becker@acme.example.com", "Europe/Berlin", 400),
          add_user("Zoë Müller", "zoe.mueller@acme.example.com", "Europe/Berlin", 390),
          add_user("Léa Dubois", "lea.dubois@acme.example.com", "Europe/Paris", 380)]
night = add_user("Kenji Watanabe", "kenji.watanabe@acme.example.com", "Asia/Tokyo", 300)
newbie = add_user("Nora Newcomer", "nora.newcomer@acme.example.com", "UTC", 0)  # registered today, nothing yet

scenario_users = [ada, grace, alan, edge, focus, guest, host, odd1, odd2, *common, night, newbie]

# Ada: fully booked. Back-to-back 30-minute slots 09:00-17:00 every workday, almost all of them meetings,
# so consecutive BUSY slots merge into long busy intervals.
for day in days():
    for start in range(9 * 60, 17 * 60, 30):
        if rng.random() < 0.85:
            add_slot(ada["email"], day, start, 30, meeting=random_meeting(ada, scenario_users))
        else:
            add_slot(ada["email"], day, start, 30, "BUSY")

# Grace: wide-open calendar. Only FREE slots (adjacent ones merge into one free interval), no meetings.
for day in days():
    for start in range(10 * 60, 16 * 60, 60):
        add_slot(grace["email"], day, start, 60)

# Edsger: duration and adjacency edge cases.
for day in days():
    wd = day % 7
    if wd == 0:    # minimum duration: twelve 5-minute slots back to back, a couple of them booked
        for i in range(12):
            meeting = random_meeting(edge, scenario_users) if i in (3, 7) else None
            add_slot(edge["email"], day, 9 * 60 + 5 * i, 5, meeting=meeting)
    elif wd == 1:  # maximum duration: a single 8-hour slot
        add_slot(edge["email"], day, 9 * 60, 480, "BUSY" if day < 0 else "FREE")
    elif wd == 2:  # FREE -> BUSY -> FREE touching each other, then a gap, then FREE again
        add_slot(edge["email"], day, 9 * 60, 60)
        add_slot(edge["email"], day, 10 * 60, 60, meeting=random_meeting(edge, scenario_users))
        add_slot(edge["email"], day, 11 * 60, 60)
        add_slot(edge["email"], day, 14 * 60, 45)
    elif wd == 3:  # odd durations
        for start, minutes in ((8 * 60 + 15, 25), (9 * 60, 50), (10 * 60 + 5, 95), (13 * 60, 7)):
            add_slot(edge["email"], day, start, minutes)
    elif wd == 4:  # late evening up to midnight (end exactly at 24:00 local)
        add_slot(edge["email"], day, 22 * 60, 120)

# Barbara: owner-blocked focus time (BUSY without a meeting) around a few bookable slots.
for day in days():
    add_slot(focus["email"], day, 8 * 60, 240, "BUSY")                     # deep-work block
    add_slot(focus["email"], day, 12 * 60, 60, "BUSY")                     # lunch
    for start in (13 * 60, 14 * 60, 15 * 60 + 30):
        meeting = random_meeting(focus, scenario_users) if rng.random() < 0.3 else None
        add_slot(focus["email"], day, start, 60, meeting=meeting)

# Margaret: large meetings: many registered participants, external guests, participants without a name,
# and the same e-mail twice in different case (de-duplicated, as the API does).
for day in days():
    if day % 7 in (1, 3):
        participants = [person(u) for u in scenario_users if u is not host]
        participants += [external_participant(i % 3 != 0) for i in range(5)]
        participants.append({"email": guest["email"].upper(), "name": "LINUS (duplicate, different case)"})
        add_slot(host["email"], day, 16 * 60, 90, meeting={
            "title": "All-hands" if day % 7 == 1 else "Cross-team architecture forum",
            "description": "Company-wide update. Questions can be submitted in advance.",
            "participants": participants})
    add_slot(host["email"], day, 9 * 60, 60)

# Ramanujan (+05:30) and Pemba (+05:45): non-whole-hour offsets.
for user in (odd1, odd2):
    for day in days():
        for start in (9 * 60 + 30, 11 * 60, 14 * 60 + 15):
            meeting = random_meeting(user, scenario_users) if rng.random() < 0.4 else None
            add_slot(user["email"], day, start, 45, meeting=meeting)

# Linus: no slots of his own, but invited to meetings of others (visible in his meeting list).
for organizer in (ada, focus, odd1):
    for slot in rng.sample([s for s in slots_by_user[organizer["email"]] if "meeting" in s], 15):
        participants = slot["meeting"].setdefault("participants", [])
        if all(p["email"] != guest["email"] for p in participants):
            participants.append(person(guest))

# Klaus, Zoë, Léa: common availability. Every Tuesday 14:00-16:00 Berlin/Paris time all three are free,
# with staggered boundaries (common window 14:30-15:30). Every Thursday their free slots only touch
# (10-11, 11-12, 12-13), so there is no common free time. Wednesdays one of them is booked.
for day in days():
    wd = day % 7
    k, z, l = (u["email"] for u in common)
    if wd == 1:
        add_slot(k, day, 14 * 60, 120)
        add_slot(z, day, 14 * 60 + 30, 90)
        add_slot(l, day, 13 * 60, 150)
    elif wd == 3:
        add_slot(k, day, 10 * 60, 60)
        add_slot(z, day, 11 * 60, 60)
        add_slot(l, day, 12 * 60, 60)
    elif wd == 2:
        add_slot(k, day, 9 * 60, 120)
        add_slot(z, day, 9 * 60, 120)
        add_slot(l, day, 9 * 60, 120, meeting=random_meeting(common[2], common))
    for u in common:
        add_slot(u["email"], day, 16 * 60 + 30, 30, meeting=random_meeting(u, common) if rng.random() < 0.5 else None)

# Kenji: evening slots and weekend availability.
for day in days(weekdays_only=False):
    if day % 7 >= 5:
        add_slot(night["email"], day, 10 * 60, 180)
    else:
        add_slot(night["email"], day, 20 * 60, 60, meeting=random_meeting(night, scenario_users) if day % 2 else None)
        add_slot(night["email"], day, 21 * 60, 150)


# ---------------------------------------------------------------------------------------------
# 2. generated users: 86 more people in teams, with a mix of working styles
# ---------------------------------------------------------------------------------------------

FIRST = ["Olivia", "Liam", "Emma", "Noah", "Amelia", "Oliver", "Sophia", "Elijah", "Mia", "Lucas", "Aarav",
         "Priya", "Wei", "Mei", "Hiroshi", "Yuki", "Fatima", "Omar", "Chiara", "Mateo", "Sofía", "José",
         "Ingrid", "Lars", "Anna", "Jakub", "Ayşe", "Mehmet", "Chloé", "Björn", "Nadia", "Kwame", "Amara",
         "Thabo", "Isabela", "João", "Siobhan", "Declan", "Ana", "Dmitri", "Elena", "Raj", "Ananya", "Min-jun",
         "Ji-woo", "Lucía", "Hugo", "Freya", "Malik", "Zara"]
LAST = ["Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Martínez", "Hernández", "Lopez", "Wilson",
        "Patel", "Sharma", "Chen", "Wang", "Tanaka", "Sato", "Khan", "Haddad", "Bianchi", "Fernández", "Schmidt",
        "Schneider", "Fischer", "Nielsen", "Novák", "Yılmaz", "Lefèvre", "Lindqvist", "Ivanova", "Mensah",
        "Dlamini", "Costa", "O'Brien", "Kim", "Park", "Moreau", "Kowalczyk", "Hansen", "Rossi", "Mwangi"]

TEAMS = [  # name, time zones, size
    ("platform", ["Europe/Berlin", "Europe/Berlin", "Europe/Warsaw"], 8),
    ("payments", ["Europe/London", "Europe/Dublin"], 7),
    ("mobile", ["America/New_York", "America/Toronto"], 8),
    ("growth", ["America/Los_Angeles", "America/Denver"], 7),
    ("data", ["Asia/Kolkata", "Asia/Dubai"], 7),
    ("apac-sales", ["Asia/Singapore", "Australia/Sydney", "Asia/Tokyo"], 7),
    ("latam-support", ["America/Sao_Paulo", "America/Mexico_City", "America/Bogota"], 7),
    ("design", ["Europe/Paris", "Europe/Madrid", "Europe/Lisbon"], 7),
    ("security", ["Europe/Stockholm", "Africa/Johannesburg", "Africa/Lagos"], 7),
    ("anz", ["Pacific/Auckland", "Australia/Melbourne"], 7),
    ("infra", ["UTC", None, "Europe/Zurich"], 7),
    ("research", ["Asia/Seoul", "Asia/Shanghai", "Asia/Jakarta"], 7),
]
assert sum(size for _, _, size in TEAMS) + len(scenario_users) == 100

STYLES = {
    # name: (probability a workday is active, generator)
    "packed": 0.35, "consultant": 0.6, "flexible": 0.55, "light": 0.3,
}


def style_packed(user, day, team):
    for start in range(9 * 60, 17 * 60, 30):
        r = rng.random()
        if r < 0.55:
            add_slot(user["email"], day, start, 30, meeting=random_meeting(user, team))
        elif r < 0.65:
            add_slot(user["email"], day, start, 30, "BUSY")
        elif r < 0.9:
            add_slot(user["email"], day, start, 30)


def style_consultant(user, day, team):
    for start in (9 * 60, 10 * 60 + 30, 13 * 60, 14 * 60 + 30, 16 * 60):
        if rng.random() < 0.8:
            meeting = random_meeting(user, team) if rng.random() < 0.45 else None
            add_slot(user["email"], day, start, 60, meeting=meeting)


def style_flexible(user, day, team):
    for _ in range(rng.randint(2, 5)):
        minutes = rng.choice([15, 20, 30, 45, 60, 90, 120])
        start = rng.randrange(8 * 60, 19 * 60 - minutes + 1, 15)
        r = rng.random()
        if r < 0.4:
            add_slot(user["email"], day, start, minutes, meeting=random_meeting(user, team))
        elif r < 0.5:
            add_slot(user["email"], day, start, minutes, "BUSY")
        else:
            add_slot(user["email"], day, start, minutes)


def style_light(user, day, team):
    for start in rng.sample([9 * 60, 11 * 60, 14 * 60, 16 * 60], rng.randint(1, 2)):
        meeting = random_meeting(user, team) if rng.random() < 0.3 else None
        add_slot(user["email"], day, start, rng.choice([30, 60]), meeting=meeting)


STYLE_FUNCS = {"packed": style_packed, "consultant": style_consultant,
               "flexible": style_flexible, "light": style_light}

used_emails = {u["email"] for u in users}
teams = []
for team_name, zones, size in TEAMS:
    members = []
    for i in range(size):
        while True:
            first, last = rng.choice(FIRST), rng.choice(LAST)
            local = f"{first}.{last}".lower()
            for a, b in (("é", "e"), ("è", "e"), ("ë", "e"), ("á", "a"), ("í", "i"), ("ó", "o"), ("ú", "u"),
                         ("ñ", "n"), ("ş", "s"), ("ı", "i"), ("ö", "oe"), ("ü", "ue"), ("ä", "ae"),
                         ("å", "a"), ("ø", "o"), ("ã", "a"), ("ç", "c"), ("'", ""), ("-", "")):
                local = local.replace(a, b)
            email = f"{local}@acme.example.com"
            if email not in used_emails:
                used_emails.add(email)
                break
        members.append(add_user(f"{first} {last}", email, zones[i % len(zones)]))
    teams.append((team_name, members))

# A few generated users are left without any slots (just registered / inactive), one per team on average.
for team_name, members in teams:
    for index, user in enumerate(members):
        if index == len(members) - 1 and rng.random() < 0.5:
            continue  # inactive member
        style = rng.choice(list(STYLES))
        # Colleagues from the team plus a handful of people elsewhere in the company.
        circle = members + rng.sample(users, 4)
        for day in days():
            if rng.random() < STYLES[style]:
                STYLE_FUNCS[style](user, day, circle)

# ---------------------------------------------------------------------------------------------
# output
# ---------------------------------------------------------------------------------------------

for user in users:
    user["slots"] = sorted(slots_by_user[user["email"]], key=lambda s: (s["day"], s["start"]))
    if user["timeZone"] is None:
        del user["timeZone"]

slot_count = sum(len(u["slots"]) for u in users)
meetings = [s["meeting"] for u in users for s in u["slots"] if "meeting" in s]
stats = {
    "users": len(users),
    "usersWithoutSlots": sum(1 for u in users if not u["slots"]),
    "slots": slot_count,
    "freeSlots": sum(1 for u in users for s in u["slots"] if s["status"] == "FREE"),
    "busySlotsWithoutMeeting": sum(1 for u in users for s in u["slots"] if s["status"] == "BUSY" and "meeting" not in s),
    "meetings": len(meetings),
    "participants": sum(len(m.get("participants", [])) for m in meetings),
}

# One user per line block and one slot per line keeps the file diffable without being huge.
lines = ["{", '  "description": "Demo data, see scripts/generate_seed_data.py. day = offset from Monday of the '
              'current week, start = local time in the user\'s time zone.",',
         '  "users": [']
for ui, user in enumerate(users):
    header = {k: v for k, v in user.items() if k != "slots"}
    lines.append("    " + json.dumps(header, ensure_ascii=False)[:-1] + ', "slots": [')
    for si, slot in enumerate(user["slots"]):
        lines.append("      " + json.dumps(slot, ensure_ascii=False) + ("," if si < len(user["slots"]) - 1 else ""))
    lines.append("    ]}" + ("," if ui < len(users) - 1 else ""))
lines += ["  ]", "}", ""]
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text("\n".join(lines), encoding="utf-8")
json.loads(OUT.read_text(encoding="utf-8"))  # sanity check
print(f"Wrote {OUT} ({OUT.stat().st_size // 1024} KiB): {stats}")
