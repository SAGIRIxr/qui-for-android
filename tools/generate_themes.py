"""Extract qui's CSS theme tokens and emit a Kotlin source file with exact sRGB colors.

Usage:
    python tools/generate_themes.py <path-to-qui-checkout> [output.kt]

The qui checkout is https://github.com/autobrr/qui; this reads web/src/index.css and
web/src/themes/*.css from it. Re-run after pulling upstream theme changes.
"""
import math
import os
import re
import pathlib
import sys

DEFAULT_OUTPUT = (
    pathlib.Path(__file__).resolve().parent.parent
    / "app/src/main/java/dev/qui/android/ui/theme/QuiThemes.kt"
)

if len(sys.argv) < 2:
    sys.exit(__doc__)

SRC = pathlib.Path(sys.argv[1]).expanduser().resolve() / "web" / "src"
if not (SRC / "index.css").is_file():
    sys.exit(f"error: {SRC / 'index.css'} not found - is that a qui checkout?")

TOKENS = [
    "background", "foreground", "card", "card-foreground", "popover", "popover-foreground",
    "primary", "primary-foreground", "secondary", "secondary-foreground",
    "muted", "muted-foreground", "accent", "accent-foreground",
    "destructive", "destructive-foreground", "border", "input", "ring",
    "chart-1", "chart-2", "chart-3", "chart-4", "chart-5",
    "sidebar", "sidebar-foreground", "sidebar-primary", "sidebar-primary-foreground",
    "sidebar-accent", "sidebar-accent-foreground", "sidebar-border",
]


# ---------- color conversion ----------

def _lin_to_srgb(c):
    if c <= 0.0031308:
        v = 12.92 * c
    else:
        v = 1.055 * (c ** (1 / 2.4)) - 0.055
    return max(0.0, min(1.0, v))


def oklch_to_hex(l, c, h_deg):
    h = math.radians(h_deg)
    a = c * math.cos(h)
    b = c * math.sin(h)

    l_ = l + 0.3963377774 * a + 0.2158037573 * b
    m_ = l - 0.1055613458 * a - 0.0638541728 * b
    s_ = l - 0.0894841775 * a - 1.2914855480 * b

    l3, m3, s3 = l_ ** 3, m_ ** 3, s_ ** 3

    r = +4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    bb = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

    return "".join(f"{round(_lin_to_srgb(v) * 255):02X}" for v in (r, g, bb))


def hsl_to_hex(h, s, l):
    s /= 100.0
    l /= 100.0
    cc = (1 - abs(2 * l - 1)) * s
    x = cc * (1 - abs(((h / 60.0) % 2) - 1))
    m = l - cc / 2
    seg = int(h // 60) % 6
    r, g, b = [(cc, x, 0), (x, cc, 0), (0, cc, x), (0, x, cc), (x, 0, cc), (cc, 0, x)][seg]
    return "".join(f"{round((v + m) * 255):02X}" for v in (r, g, b))


NUM = r"([-+]?[0-9]*\.?[0-9]+)"


def parse_color(value):
    """Return an RRGGBB hex string, or None when the value is not a solid color."""
    value = value.strip().rstrip(";").strip()

    m = re.match(rf"^oklch\(\s*{NUM}%?\s+{NUM}\s+{NUM}", value, re.I)
    if m:
        l = float(m.group(1))
        if "%" in value.split()[0]:
            l /= 100.0
        if l > 1.5:  # written as a percentage without the sign
            l /= 100.0
        return oklch_to_hex(l, float(m.group(2)), float(m.group(3)))

    m = re.match(rf"^hsl\(\s*{NUM}\s*,?\s*{NUM}%\s*,?\s*{NUM}%", value, re.I)
    if m:
        return hsl_to_hex(float(m.group(1)), float(m.group(2)), float(m.group(3)))

    m = re.match(r"^#([0-9a-f]{6})$", value, re.I)
    if m:
        return m.group(1).upper()

    m = re.match(r"^#([0-9a-f]{3})$", value, re.I)
    if m:
        return "".join(ch * 2 for ch in m.group(1)).upper()

    if value.lower() in ("white", "#fff"):
        return "FFFFFF"
    if value.lower() in ("black",):
        return "000000"
    return None


# ---------- CSS block extraction ----------

def extract_block(css, selector):
    """Grab the declarations of the LAST block matching `selector` (later wins in CSS)."""
    out = {}
    pattern = re.compile(re.escape(selector) + r"\s*\{", re.I)
    for m in pattern.finditer(css):
        start = m.end()
        depth = 1
        i = start
        while i < len(css) and depth:
            if css[i] == "{":
                depth += 1
            elif css[i] == "}":
                depth -= 1
            i += 1
        body = css[start:i - 1]
        for decl in re.finditer(r"--([a-z0-9-]+)\s*:\s*([^;]+);", body, re.I):
            out[decl.group(1).lower()] = decl.group(2).strip()
    return out


VAR_RE = re.compile(r"var\(\s*--([a-z0-9-]+)\s*(?:,\s*([^)]*))?\)", re.I)


def deref(value, decls, depth=0):
    """Follow var(--x) chains inside a declaration set, honouring fallbacks."""
    if depth > 12 or not value:
        return value
    m = VAR_RE.match(value.strip())
    if not m:
        return value
    target = decls.get(m.group(1).lower())
    if target is None:
        target = m.group(2)
    if target is None:
        return value
    return deref(target.strip(), decls, depth + 1)


def resolve(decls, overrides=None):
    """Resolve TOKENS to hex. `overrides` re-points --variation-color for theme variations."""
    if overrides:
        decls = {**decls, **overrides}
    colors = {}
    for token in TOKENS:
        raw = decls.get(token)
        if raw is None:
            continue
        hexv = parse_color(deref(raw, decls))
        if hexv:
            colors[token] = hexv
    return colors


def kt_name(token):
    parts = token.split("-")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def theme_meta(css, fallback_id):
    name = re.search(r"@name:\s*(.+)", css)
    desc = re.search(r"@description:\s*(.+)", css)
    prem = re.search(r"@premium:\s*(\w+)", css)
    vary = re.search(r"@variations:\s*(.+)", css)
    variations = []
    if vary:
        variations = [v.strip() for v in vary.group(1).split(",") if v.strip()]
    return (
        (name.group(1).strip() if name else fallback_id),
        (desc.group(1).strip().rstrip("*/ ").strip() if desc else ""),
        (prem.group(1).strip().lower() == "true" if prem else False),
        variations,
    )


def main():
    themes = []

    base_css = (SRC / "index.css").read_text(encoding="utf-8")
    base_root = extract_block(base_css, ":root")
    base_dark_decls = extract_block(base_css, ".dark")
    themes.append({
        "id": "default",
        "name": "Default",
        "description": "The stock qui look",
        "premium": False,
        "variations": [],
        "light": resolve(base_root),
        "dark": resolve(base_dark_decls),
    })

    base_light = themes[0]["light"]
    base_dark = themes[0]["dark"]

    for path in sorted((SRC / "themes").glob("*.css")):
        css = path.read_text(encoding="utf-8")
        name, desc, premium, variation_ids = theme_meta(css, path.stem)
        root = extract_block(css, ":root")
        dark_decls = extract_block(css, ".dark")

        def build(decls, base, override=None):
            merged = dict(base)
            merged.update(resolve(decls, override))
            return merged

        variations = []
        for vid in variation_ids:
            key = f"variation-{vid}"
            if key not in root and key not in dark_decls:
                continue
            variations.append({
                "id": vid,
                "light": build(root, base_light, {"variation-color": f"var(--{key})"}),
                "dark": build(dark_decls, base_dark, {"variation-color": f"var(--{key})"}),
            })

        themes.append({
            "id": path.stem,
            "name": name,
            "description": desc,
            "premium": premium,
            "variations": variations,
            "light": build(root, base_light),
            "dark": build(dark_decls, base_dark),
        })

    lines = [
        "/*",
        " * Generated from qui's CSS theme tokens - do not edit by hand.",
        # Kotlin block comments nest, so a literal "themes/*.css" would open a nested
        # comment and leave the header unterminated.
        " * Source: autobrr/qui - web/src/index.css and the CSS files under web/src/themes.",
        " * OKLCH values were converted to sRGB so the Android palette matches the web UI exactly.",
        " */",
        "",
        "package dev.qui.android.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "",
        "data class QuiPalette(",
    ]
    for token in TOKENS:
        lines.append(f"    val {kt_name(token)}: Color,")
    lines.append(")")
    lines.append("")
    lines.append("data class QuiThemeVariation(")
    lines.append("    val id: String,")
    lines.append("    val light: QuiPalette,")
    lines.append("    val dark: QuiPalette,")
    lines.append(")")
    lines.append("")
    lines.append("data class QuiThemeSpec(")
    lines.append("    val id: String,")
    lines.append("    val name: String,")
    lines.append("    val description: String,")
    lines.append("    val premium: Boolean,")
    lines.append("    val light: QuiPalette,")
    lines.append("    val dark: QuiPalette,")
    lines.append("    val variations: List<QuiThemeVariation> = emptyList(),")
    lines.append(")")
    lines.append("")

    def palette_literal(colors, indent):
        pad = " " * indent
        out = ["QuiPalette("]
        for token in TOKENS:
            hexv = colors.get(token) or colors.get("foreground") or "000000"
            out.append(f"{pad}    {kt_name(token)} = Color(0xFF{hexv}),")
        out.append(pad + ")")
        return "\n".join(out)

    for theme in themes:
        ident = "".join(p.capitalize() for p in re.split(r"[^a-z0-9]+", theme["id"].lower()) if p)
        lines.append(f"internal val QuiTheme{ident} = QuiThemeSpec(")
        lines.append(f'    id = "{theme["id"]}",')
        lines.append(f'    name = "{theme["name"]}",')
        lines.append(f'    description = "{theme["description"]}",')
        lines.append(f"    premium = {str(theme['premium']).lower()},")
        lines.append("    light = " + palette_literal(theme["light"], 4) + ",")
        lines.append("    dark = " + palette_literal(theme["dark"], 4) + ",")
        if theme["variations"]:
            lines.append("    variations = listOf(")
            for v in theme["variations"]:
                lines.append("        QuiThemeVariation(")
                lines.append(f'            id = "{v["id"]}",')
                lines.append("            light = " + palette_literal(v["light"], 12) + ",")
                lines.append("            dark = " + palette_literal(v["dark"], 12) + ",")
                lines.append("        ),")
            lines.append("    ),")
        lines.append(")")
        lines.append("")

    lines.append("val QuiThemes: List<QuiThemeSpec> = listOf(")
    for theme in themes:
        ident = "".join(p.capitalize() for p in re.split(r"[^a-z0-9]+", theme["id"].lower()) if p)
        lines.append(f"    QuiTheme{ident},")
    lines.append(")")
    lines.append("")
    lines.append("fun quiThemeById(id: String): QuiThemeSpec =")
    lines.append("    QuiThemes.firstOrNull { it.id == id } ?: QuiThemes.first()")
    lines.append("")

    out_path = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_OUTPUT
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out_path} with {len(themes)} themes")
    for t in themes:
        print(f"  {t['id']:14} premium={t['premium']!s:5} light={len(t['light'])} dark={len(t['dark'])} primary={t['light'].get('primary')}/{t['dark'].get('primary')}")


main()
