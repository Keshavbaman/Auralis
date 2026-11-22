#!/usr/bin/env python3
"""Make an Auralis transcript for an audiobook on this computer, with Whisper.

Free and private: the audio never leaves the machine. Output is WebVTT with a
timestamp before every word (karaoke timing), which the Auralis player uses for
word-by-word highlighting. Import the file on the phone with "Import captions".

Times count from the start of the first file, so pass a multi-file book's files
in the same order Auralis plays them.

Usage:
  tools/.whisper-venv/bin/python -I tools/transcribe_book.py BOOK.mp3 [MORE.mp3 ...] \
      --out book.vtt [--model small.en] [--threads 12] [--start 0 --duration 0]
"""

import argparse
import json
import os
import subprocess
import sys
import time

import numpy as np
from faster_whisper import WhisperModel

SAMPLE_RATE = 16000

SENTENCE_END = (".", "?", "!", "…", '."', '?"', '!"', ".”", "?”", "!”")


def duration_of(path):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "json", path],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return float(json.loads(out)["format"]["duration"])


def load_audio(path, start=0.0, duration=0.0):
    """Decode with ffmpeg straight to 16 kHz mono float samples (no PyAV needed)."""
    command = ["ffmpeg", "-nostdin", "-v", "error"]
    if start > 0:
        command += ["-ss", str(start)]
    if duration > 0:
        command += ["-t", str(duration)]
    command += ["-i", path, "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "f32le", "-"]
    raw = subprocess.run(command, check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32)


def stamp(seconds):
    ms = max(0, int(round(seconds * 1000)))
    return "%02d:%02d:%02d.%03d" % (ms // 3_600_000, ms // 60_000 % 60, ms // 1000 % 60, ms % 1000)


def escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def lines_from(words, max_words=14, max_span=8.0, max_gap=0.7):
    """Short readable lines: break after a sentence ends, on a pause, or when too long."""
    line = []
    for word in words:
        if line and (
            word["start"] - line[-1]["end"] > max_gap
            or len(line) >= max_words
            or word["end"] - line[0]["start"] > max_span
        ):
            yield line
            line = []
        line.append(word)
        if word["text"].endswith(SENTENCE_END) and len(line) >= 3:
            yield line
            line = []
    if line:
        yield line


def write_vtt(lines, path):
    with open(path, "w", encoding="utf-8") as out:
        out.write("WEBVTT\n\n")
        for line in lines:
            out.write("%s --> %s\n" % (stamp(line[0]["start"]), stamp(line[-1]["end"])))
            parts = []
            for i, word in enumerate(line):
                text = escape(word["text"])
                parts.append(text if i == 0 else "<%s>%s" % (stamp(word["start"]), text))
            out.write(" ".join(parts) + "\n\n")


CHUNK_SECONDS = 20 * 60
OVERLAP_SECONDS = 2.0


def transcribe(model, paths, out, start=0.0, duration=0.0, on_progress=None):
    """Transcribe audio file(s) in playback order into an Auralis WebVTT file.

    Long files are processed in 20-minute chunks, so memory stays small (a whole 20-hour book
    would need over 10 GB) and progress moves steadily. Each chunk hears a little past its end so
    a word on the boundary is whole; a word belongs to the chunk it starts in.

    on_progress(done_seconds, total_seconds), if given, is called as audio is processed.
    Returns (lines, words, seconds of audio, seconds taken).
    """
    words = []
    offset = 0.0
    total_audio = 0.0
    began = time.time()
    for index, path in enumerate(paths):
        length = duration_of(path)
        clip = index == 0 and duration > 0
        first = start if clip else 0.0
        last = min(length, start + duration) if clip else length
        span = last - first
        if on_progress:
            on_progress(0.0, span)
        cursor = first
        while cursor < last - 0.05:
            chunk_end = min(last, cursor + CHUNK_SECONDS)
            hear_until = min(last, chunk_end + OVERLAP_SECONDS)
            samples = load_audio(path, cursor, hear_until - cursor)
            segments, _ = model.transcribe(
                samples,
                language="en",
                beam_size=1,
                word_timestamps=True,
                vad_filter=True,
                condition_on_previous_text=False,
            )
            keep = chunk_end - cursor
            for segment in segments:
                for w in segment.words or []:
                    text = w.word.strip()
                    if text and w.start < keep:
                        words.append({"text": text, "start": offset + cursor + w.start, "end": offset + cursor + w.end})
                done = min(cursor + segment.end, chunk_end) - first
                if on_progress:
                    on_progress(done, span)
                print(
                    "\r%s: %3d%%  (%.0fs elapsed)"
                    % (os.path.basename(path), min(100, done / max(span, 1) * 100), time.time() - began),
                    end="",
                    file=sys.stderr,
                    flush=True,
                )
            cursor = chunk_end
            if on_progress:
                on_progress(cursor - first, span)
        print(file=sys.stderr)
        total_audio += span
        offset += length
        if clip:
            break
    words.sort(key=lambda w: w["start"])
    lines = list(lines_from(words))
    write_vtt(lines, out)
    return len(lines), len(words), total_audio, time.time() - began


def load_model(name, threads):
    return WhisperModel(name, device="cpu", compute_type="int8", cpu_threads=threads)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("audio", nargs="+", help="book audio file(s) in playback order")
    parser.add_argument("--out", required=True, help="WebVTT file to write")
    parser.add_argument("--model", default="small.en", help="Whisper model (small.en, medium.en, large-v3-turbo, ...)")
    parser.add_argument("--threads", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    parser.add_argument("--start", type=float, default=0.0, help="seconds into the first file (for a quick test)")
    parser.add_argument("--duration", type=float, default=0.0, help="seconds to transcribe; 0 = everything")
    args = parser.parse_args()

    model = load_model(args.model, args.threads)
    lines, words, audio, took = transcribe(model, args.audio, args.out, args.start, args.duration)
    print(
        "wrote %d lines, %d words to %s | %.0f s of audio in %.0f s = %.1fx real time (model %s, %d threads)"
        % (lines, words, args.out, audio, took, audio / max(took, 1), args.model, args.threads)
    )


if __name__ == "__main__":
    main()
