# INFO1113 Assignment 2 - Fare Time

The specification is on Ed:
https://edstem.org/au/courses/36387/lessons/119854/slides/821521

It describes everything in this folder and everything you need to do. This page repeats only the
commands, as a quick reference. Run every command from this top folder (the one that holds `src/`
and `references.txt`). On Ed, open a terminal: it is Linux, so use the macOS/Linux commands.

## Demo

macOS / Linux / Ed:

```bash
javac -d out contracts/given/*.java src/*.java loader/*.java demo/Demo.java
java -cp out Demo
java -cp out Demo scenarios/3-western-sydney.scenario
```

Windows (Command Prompt or PowerShell):

```bat
javac -d out contracts\given\*.java src\*.java loader\*.java demo\Demo.java
java -cp out Demo
java -cp out Demo scenarios\3-western-sydney.scenario
```

With no argument, `Demo` output must match `demo/expected-output.txt` exactly. With a scenario
file, it runs that scenario without the GUI.

## GUI on your finished backend

Once all eight classes in `src/` compile, this runs the GUI in live mode on your backend.

With Python (it compiles for you):

| System | Command |
| --- | --- |
| macOS / Linux / Ed | `python3 run_gui.py` |
| Windows | `py run_gui.py` |

Without Python, macOS / Linux / Ed:

```bash
javac -d out contracts/given/*.java src/*.java loader/*.java gui/*.java
java -cp out TransitGui
```

Without Python, Windows:

```bat
javac -d out contracts\given\*.java src\*.java loader\*.java gui\*.java
java -cp out TransitGui
```

If `javac` reports errors, fix them first: `out/` would still hold your last working build.

## GUI before your backend compiles (preview mode)

Preview mode shows the maps only. `python3 run_gui.py` (or `py run_gui.py`) switches to it by
itself when your backend is empty or does not compile; add `--check` to print `live` or
`preview` without opening a window.

Without Python, macOS / Linux / Ed:

```bash
javac -d out-preview contracts/given/*.java loader/*.java gui/ServiceMode.java gui-preview/*.java
java -cp out-preview ScaffoldGuiLauncher --preview
```

Without Python, Windows:

```bat
javac -d out-preview contracts\given\*.java loader\*.java gui\ServiceMode.java gui-preview\*.java
java -cp out-preview ScaffoldGuiLauncher --preview
```
