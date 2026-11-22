#!/usr/bin/env python3
"""Transcribe every audiobook on the phone that has no transcript yet, on this computer.

For each audio file under the phone's audiobook folder without a `<same name>.vtt` beside it:
copy it here over USB, transcribe it with Whisper (free, offline), put the `.vtt` next to the
audio on the phone, keep a copy in tools/out/transcripts/, and delete the copied audio.
Auralis picks up a transcript beside a book's audio by itself the next time the book opens.

Safe to stop and run again: books that already have a transcript are skipped.

Usage:
  tools/.whisper-venv/bin/python -I tools/transcribe_phone_library.py \
      [--root /storage/6235-6532/Audiobooks] [--only Goosebumps] [--model small.en] [--dry-run]
"""

import argparse
import json
import os
import shlex
import subprocess
import sys
import time

TOOLS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS)  # our own transcribe_book module (isolated mode skips the script dir)
import transcribe_book  # noqa: E402

AUDIO = (".mp3", ".m4b", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav")
WORK = os.path.join(TOOLS, "out", "work")
KEEP = os.path.join(TOOLS, "out", "transcripts")
STATUS = os.path.join(TOOLS, "out", "status.json")


class Status:
    """Live progress for the status page (tools/transcribe-status), written atomically."""

    def __init__(self, todo):
        self.data = {
            "state": "starting",
            "started": time.time(),
            "updated": time.time(),
            "queue": [os.path.basename(f) for f in todo],
            "current": None,
            "done": [],
            "skipped": [],
        }

    def write(self):
        self.data["updated"] = time.time()
        temporary = STATUS + ".tmp"
        with open(temporary, "w", encoding="utf-8") as out:
            json.dump(self.data, out)
        os.replace(temporary, STATUS)

    def begin(self, name, step):
        if name in self.data["queue"]:
            self.data["queue"].remove(name)
        self.data["state"] = step
        self.data["current"] = {"name": name, "step": step, "began": time.time(), "done": 0, "total": 0}
        self.write()

    def progress(self, done, total):
        current = self.data["current"]
        current.setdefault("since", time.time())  # when transcribing (not copying) began
        current.update(step="transcribing", done=done, total=total)
        self.data["state"] = "transcribing"
        # Writing every segment is cheap but pointless; a couple of times a second is plenty.
        if time.time() - self.data["updated"] > 0.5 or done >= total:
            self.write()

    def finish(self, name, lines, audio, took):
        self.data["done"].append(
            {"name": name, "lines": lines, "audio": audio, "took": took, "at": time.time()}
        )
        self.data["current"] = None
        self.write()

    def skip(self, name, reason):
        self.data["skipped"].append({"name": name, "reason": reason})
        self.data["current"] = None
        self.write()

    def end(self):
        self.data["state"] = "finished"
        self.data["current"] = None
        self.write()


def adb(serial, *args, capture=True, attempts=3):
    """Run adb; if the phone dropped off USB, wait for it to come back and try again."""
    command = ["adb"] + (["-s", serial] if serial else []) + list(args)
    for attempt in range(attempts):
        try:
            result = subprocess.run(command, check=True, capture_output=capture, text=True)
            return result.stdout if capture else ""
        except subprocess.CalledProcessError:
            if attempt == attempts - 1:
                raise
            print("  phone not reachable; waiting for it to reconnect...", flush=True)
            # Blocks until the phone is back (unplugged cable, phone asleep, USB reset).
            subprocess.run(["adb"] + (["-s", serial] if serial else []) + ["wait-for-device"], check=False)
            time.sleep(3)
    return ""


def phone_files(serial, root):
    # One quoted shell command on the phone; names come back one per line.
    listing = adb(serial, "shell", "find %s -type f" % shlex.quote(root))
    return [line.strip() for line in listing.splitlines() if line.strip()]


def remote_size(serial, path):
    try:
        return int(adb(serial, "shell", "stat -c %%s %s" % shlex.quote(path)).strip())
    except (subprocess.CalledProcessError, ValueError):
        return -1


def stem(path):
    return os.path.splitext(path)[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default="/storage/6235-6532/Audiobooks", help="audiobook folder on the phone")
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="adb device serial")
    parser.add_argument(
        "--only",
        action="append",
        default=[],
        help="only paths containing this text (case-insensitive); repeat to queue several, in order",
    )
    parser.add_argument("--model", default="small.en")
    parser.add_argument("--threads", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    parser.add_argument("--dry-run", action="store_true", help="list what would be transcribed and stop")
    args = parser.parse_args()

    files = phone_files(args.serial, args.root)
    have = {stem(f) for f in files if f.lower().endswith(".vtt")}
    pending = [f for f in files if f.lower().endswith(AUDIO) and stem(f) not in have]
    if args.only:
        # Each --only filter in turn, so the first one named is done first.
        todo = []
        for wanted in args.only:
            todo += [f for f in pending if wanted.lower() in f.lower() and f not in todo]
    else:
        todo = pending
    print("%d audio files on the phone, %d already transcribed, %d to do"
          % (sum(f.lower().endswith(AUDIO) for f in files), len(have), len(todo)), flush=True)
    if args.dry_run or not todo:
        for f in todo:
            print("  " + f)
        return

    os.makedirs(WORK, exist_ok=True)
    # Drop copies left by an interrupted run, except one that is still the right file.
    keepable = {"current" + os.path.splitext(f)[1].lower() for f in todo[:1]}
    for leftover in os.listdir(WORK):
        if leftover not in keepable:
            os.remove(os.path.join(WORK, leftover))
    status = Status(todo)
    status.write()
    model = transcribe_book.load_model(args.model, args.threads)
    began = time.time()
    done = 0
    for number, remote in enumerate(todo, 1):
        name = os.path.basename(remote)
        relative = os.path.relpath(remote, args.root)
        local_audio = os.path.join(WORK, "current" + os.path.splitext(name)[1].lower())
        local_vtt = os.path.join(KEEP, stem(relative) + ".vtt")
        os.makedirs(os.path.dirname(local_vtt), exist_ok=True)
        print("\n[%d/%d] %s" % (number, len(todo), relative), flush=True)
        try:
            status.begin(name, "copying from phone")
            # An interrupted run may already have copied this exact file.
            if not (os.path.exists(local_audio) and os.path.getsize(local_audio) == remote_size(args.serial, remote)):
                adb(args.serial, "pull", remote, local_audio)
            lines, words, audio, took = transcribe_book.transcribe(
                model, [local_audio], local_vtt, on_progress=status.progress
            )
            status.data["current"]["step"] = "copying to phone"
            status.write()
            adb(args.serial, "push", local_vtt, stem(remote) + ".vtt")
            status.finish(name, lines, audio, took)
            done += 1
            print("  %d lines in %.0f s (%.1fx real time) -> %s.vtt"
                  % (lines, took, audio / max(took, 1), stem(name)), flush=True)
        except (subprocess.CalledProcessError, OSError, RuntimeError) as error:
            # One bad file must not stop the rest of the library.
            print("  skipped: %s" % error, flush=True)
            status.skip(name, str(error)[:200])
        finally:
            if os.path.exists(local_audio):
                os.remove(local_audio)
    status.end()
    print("\nFinished: %d of %d transcribed in %.0f min." % (done, len(todo), (time.time() - began) / 60))


if __name__ == "__main__":
    main()
