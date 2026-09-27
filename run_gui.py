import argparse
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent
REQUIRED_SOURCES = (
    "AdultPassenger.java", "Bus.java", "ConcessionPassenger.java",
    "HolidayFare.java", "NormalFare.java", "Train.java", "Tram.java",
    "TransitApp.java",
)


def java_sources(directory: Path) -> list[Path]:
    return sorted(directory.glob("*.java"))


def compile_sources(javac: str, build: Path, sources: list[Path]) \
        -> subprocess.CompletedProcess[str]:
    empty_source_path = build / "explicit-sources-only"
    empty_source_path.mkdir()
    return subprocess.run(
        [javac, "-classpath", str(build), "-sourcepath", str(empty_source_path),
         "-d", str(build), *map(str, sources)],
        cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )


def missing_sources() -> list[str]:
    missing = []
    for name in REQUIRED_SOURCES:
        path = ROOT / "src" / name
        if not path.is_file() or not path.read_bytes():
            missing.append(name)
    return missing


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Launch the Assignment 2 scaffold GUI")
    parser.add_argument(
        "--check", action="store_true",
        help="compile and print live or preview without opening a window",
    )
    args = parser.parse_args(argv)

    javac = shutil.which("javac")
    java = shutil.which("java")
    if javac is None or java is None:
        print(
            "Java JDK required: javac and java must be available on PATH.",
            file=sys.stderr,
        )
        return 2

    contracts = java_sources(ROOT / "contracts" / "given")
    student = java_sources(ROOT / "src")
    live_gui = java_sources(ROOT / "gui")
    preview_gui = java_sources(ROOT / "gui-preview")
    loader = java_sources(ROOT / "loader")
    with tempfile.TemporaryDirectory(prefix="a2-scaffold-gui-") as directory:
        temporary_root = Path(directory)
        live_build = temporary_root / "live"
        preview_build = temporary_root / "preview"
        live_build.mkdir()
        preview_build.mkdir()
        missing = missing_sources()
        live = None
        if any(path.read_bytes() for path in student):
            live = compile_sources(
                javac, live_build, contracts + student + loader + live_gui + preview_gui,
            )
        if live is not None and live.returncode == 0:
            mode = "live"
            build = live_build
            status = "Backend compiled; starting live mode."
        else:
            if live is not None:
                sys.stderr.write(live.stderr)
            preview = compile_sources(
                javac, preview_build,
                contracts + loader + [ROOT / "gui" / "ServiceMode.java"] + preview_gui,
            )
            if preview.returncode != 0:
                sys.stderr.write(preview.stderr)
                return 2
            mode = "preview"
            build = preview_build
            status = (
                "Missing implementation: " + ", ".join(missing)
                if missing
                else "Backend did not compile; see terminal diagnostics."
            )

        if args.check:
            print(mode)
            return 0

        status_file = build / "backend-status.txt"
        status_file.write_text(status, encoding="utf-8")
        launched = subprocess.run(
            [java, "-cp", str(build), "ScaffoldGuiLauncher", f"--{mode}",
             "--status-file", str(status_file)],
            cwd=ROOT,
        )
        return launched.returncode


if __name__ == "__main__":
    raise SystemExit(main())
