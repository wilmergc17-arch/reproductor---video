"""Compila con ICU (el motor de expresiones regulares de Android) cada Regex(...) del código Kotlin.

Java en la computadora acepta patrones que Android rechaza (por ejemplo una "}" suelta), y eso
solo se nota en el teléfono. Uso: python3 scripts/check_regex_icu.py app/src/main/java
"""
import ctypes
import glob
import pathlib
import re
import sys

libs = sorted(glob.glob("/usr/lib/x86_64-linux-gnu/libicui18n.so.*[0-9]"))
if not libs:
    sys.exit("No encontré libicui18n en el sistema")
lib = ctypes.CDLL(libs[0])
version = libs[0].split(".so.")[1].split(".")[0]
uregex_open = getattr(lib, f"uregex_open_{version}")
uregex_open.restype = ctypes.c_void_p
uregex_open.argtypes = [ctypes.c_char_p, ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p, ctypes.POINTER(ctypes.c_int)]
uregex_close = getattr(lib, f"uregex_close_{version}")
uregex_close.argtypes = [ctypes.c_void_p]

RAW = re.compile(r'Regex\(\s*"""(.*?)"""', re.S)
PLAIN = re.compile(r'Regex\(\s*"(?!"")((?:[^"\\]|\\.)*)"')

failures = 0
total = 0
for root in sys.argv[1:]:
    for path in pathlib.Path(root).rglob("*.kt"):
        src = path.read_text(encoding="utf-8")
        patterns = RAW.findall(src) + [p.encode().decode("unicode_escape") for p in PLAIN.findall(src)]
        for pattern in patterns:
            total += 1
            data = pattern.encode("utf-16-le")
            error = ctypes.c_int(0)
            handle = uregex_open(data, len(data) // 2, 0, None, ctypes.byref(error))
            if handle:
                uregex_close(handle)
            if error.value > 0:
                failures += 1
                print(f"{path}: ICU rechaza el patrón (código {error.value}): {pattern}")
print(f"{total} patrones revisados, {failures} con error")
sys.exit(1 if failures else 0)
