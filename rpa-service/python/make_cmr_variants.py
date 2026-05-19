"""Generate EN and RU variants of the CMR template from the bilingual original.

EN: German labels → English equivalents.
RU: German labels → "" (removed), only Russian remains.

Replaces across the main story AND textframe stories — the preamble
("Diese Beförderung unterliegt trotz …") and the title block
("Internationaler Frachtbrief") live in a textbox, which `doc.Content.Find`
alone does not reach.

Outputs:
    templates/CMR_EN.doc   — Russian + English
    templates/CMR_RU.doc   — Russian only
"""
from __future__ import annotations

import shutil
import time
from pathlib import Path

import pythoncom
import win32com.client as win32


TEMPLATES = Path(__file__).resolve().parent.parent / "templates"
SOURCE = TEMPLATES / "CMR Международная товарно-транспортная накладная.doc"
EN_OUT = TEMPLATES / "CMR_EN.doc"
RU_OUT = TEMPLATES / "CMR_RU.doc"


# (German source, English replacement). For RU mode the replacement is "".
# Sorted by source length (longest first) so substrings don't pre-match
# ("Empfänger (Name, Anschrift, Land)" must fire before the bare "Empfänger").
_RULES_RAW: list[tuple[str, str]] = [
    ("Unterschrift und Stempel des Absenders",      "Signature and stamp of the sender"),
    ("Unterschrift und Stempel des Frachtführers",  "Signature and stamp of the carrier"),
    ("Unterschrift und Stempel des Empfängers",     "Signature and stamp of the consignee"),
    ("Nachfolgende Frachtführer (Name, Anschrift, Land)",
                                                    "Successive carriers (name, address, country)"),
    ("Anweisungen des Absenders (Zoll- Und sonstige amtliche Bearbeitung)",
                                                    "Sender's instructions (customs and other formalities)"),
    ("Vorbehalte und Bemerkungen der Frachtführer", "Carrier's reservations and observations"),
    ("Frachtführer (Name, Anschrift, Land)",        "Carrier (name, address, country)"),
    ("Empfänger (Name, Anschrift, Land)",           "Consignee (name, address, country)"),
    ("Absender (Name, Anschrift, Land)",            "Sender (name, address, country)"),
    ("Ort und Tag Übernahme des Gutes",             "Place and date of taking over of the goods"),
    ("Auslieferungsort des Gutes",                  "Place of delivery of the goods"),
    ("Frachtzahlungsanweisungen",                   "Conditions of payment"),
    ("Besondere Vereinbarungen",                    "Special agreements"),
    ("Zu zahlende Ges.-Summe",                      "Total to be paid"),
    ("Bezeichnung des Gutes",                       "Nature of the goods"),
    ("Anzahl der Packstücke",                       "Number of packages"),
    ("Kennzeichen und Nummern",                     "Marks and numbers"),
    ("Amtliches Kennzeichen",                       "Registration number"),
    ("Beigefügte Dokumente",                        "Documents attached"),
    ("Art der Verpackung",                          "Method of packing"),
    ("Ankunft für Beladung",                        "Arrival for loading"),
    ("Rückerstattung",                              "Cash on delivery"),
    ("Bruttogew., kg",                              "Gross weight, kg"),
    ("Statistik-Nr.",                               "Statistical number"),
    ("Zu zahlen vom:",                              "To be paid by:"),
    ("Nebengebühren",                               "Miscellaneous"),
    ("Ausgefertigt in",                             "Established in"),
    ("Zwischensumme",                               "Balance"),
    ("Gut empfangen",                               "Goods received"),
    ("Ermäßigungen",                                "Deductions"),
    ("Zuschläge",                                   "Supplementary charges"),
    ("Sonstiges",                                   "Other charges"),
    ("Buchstabe",                                   "Letter"),
    ("Umfang m³",                                   "Volume m³"),
    ("Anhänger",                                    "Trailer"),
    ("Währung",                                     "Currency"),
    ("Abfahrt",                                     "Departure"),
    ("Datum",                                       "Date"),
    ("Unfrei",                                      "Carriage forward"),
    ("Klasse",                                      "Class"),
    ("Ziffer",                                      "Number"),
    # Textbox title — two words split by a paragraph mark, so they must be matched
    # individually. MUST precede the bare "Fracht" rule, otherwise it consumes
    # "Fracht" out of "Frachtbrief" and leaves a "brief" fragment behind.
    ("Internationaler",                             "International"),
    ("Frachtbrief",                                 "consignment note"),
    ("Fracht",                                      "Carriage charges"),
    ("Land",                                        "Country"),
    ("Frei",                                        "Carriage paid"),
    ("Kfz",                                         "Truck"),
    ("Typ",                                         "Type"),
    ("Ort",                                         "Place"),
    ("Uhr",                                         "h"),

    # --- Textbox content (story 5) — never reached before the textframe fix below ---
    # The title "Internationaler / Frachtbrief" is handled earlier (split across
    # two paragraphs in the textbox, and "Frachtbrief" must run before bare "Fracht").
    # 5-line German preamble; Russian preamble already exists alongside it.
    # Split per line so each paragraph mark in the textbox is preserved cleanly.
    ("Diese Beförderung unterliegt trotz",  "This carriage is subject"),
    ("einer gegenteiligen Abmachung den",   "notwithstanding any clause to the contrary"),
    ("Bestimmungen des Übereinkommens",     "to the Convention on the Contract"),
    ("über den Beförderungsvertrag im",     "for the International Carriage of"),
    ("intern. Straßengüterverkehr (CMR)",   "Goods by Road (CMR)"),

    # --- Standalone short labels that the (Name, Anschrift, Land) rules above
    # did not catch because they appear bare in adjacent cells / sub-labels.
    # MUST come after the longer "X (Name, Anschrift, Land)" rules to avoid
    # double-replacing the trailing "Sender" / "Consignee".
    ("Empfänger",   "Consignee"),
    ("Absender",    "Sender"),

    # --- Bilingual sub-labels: the German half sits redundantly next to the
    # Russian one (мин. / Min., ДОПОГ / ADR, Am „…“ for the date placeholder).
    # In EN we leave the international abbreviations as-is; in RU they vanish.
    ("Min.",  "Min."),
    ("ADR",   "ADR"),
    ("Am ",   "On "),   # only the German preposition, leaves the date underscores intact
    # Lowercase "am" appears in § 21 "Составлен в … Дата am". Whole-word + case-
    # sensitive — otherwise it eats letters out of words like "Datum"/"Amtliches".
    ("am", "on", True, True),
]


WD_REPLACE_ALL = 2
WD_FIND_STOP = 0
# Story IDs that can contain user text:
# 1 = MainTextStory, 5 = TextFrameStory, 6 = EvenPagesHeaderStory,
# 7 = PrimaryHeaderStory, 8 = EvenPagesFooterStory, 9 = PrimaryFooterStory,
# 10 = FirstPageHeaderStory, 11 = FirstPageFooterStory. We probe all of them
# — non-existent stories silently raise and are skipped.
_STORY_IDS = (1, 2, 3, 5, 6, 7, 8, 9, 10, 11)


def _replace_everywhere(doc, find_text: str, replacement: str,
                        match_case: bool = False, whole_word: bool = False) -> int:
    """Apply Find/Replace across every story in the doc; return total hit count."""
    total = 0
    for sid in _STORY_IDS:
        try:
            rng = doc.StoryRanges(sid)
        except Exception:
            continue
        while rng is not None:
            find = rng.Find
            find.ClearFormatting()
            find.Replacement.ClearFormatting()
            try:
                ok = find.Execute(
                    find_text, match_case, whole_word, False, False, False,
                    True, WD_FIND_STOP, False, replacement, WD_REPLACE_ALL,
                )
                if ok:
                    total += 1
            except Exception:
                pass
            try:
                rng = rng.NextStoryRange
            except Exception:
                rng = None
    return total


def make_variant(target: Path, mode: str) -> None:
    print(f"\n=== Generating {target.name} (mode={mode}) ===")
    t0 = time.time()
    shutil.copy(SOURCE, target)

    pythoncom.CoInitialize()
    app = win32.DispatchEx("Word.Application")
    app.Visible = False
    app.DisplayAlerts = 0
    try:
        doc = app.Documents.Open(str(target), ConfirmConversions=False, ReadOnly=False)
        try:
            for rule in _RULES_RAW:
                de, en, *flags = rule
                whole_word = bool(flags[0]) if len(flags) > 0 else False
                match_case = bool(flags[1]) if len(flags) > 1 else False
                replacement = en if mode == "en" else ""
                hits = _replace_everywhere(doc, de, replacement,
                                           match_case=match_case, whole_word=whole_word)
                marker = "✓" if hits else "·"
                print(f"  {marker} {de!r}  ({hits} story-hit(s))")
            doc.Save()
        finally:
            doc.Close(False)
    finally:
        app.Quit()
        pythoncom.CoUninitialize()
    print(f"  took {time.time() - t0:.1f}s")


if __name__ == "__main__":
    if not SOURCE.exists():
        raise SystemExit(f"Source CMR not found: {SOURCE}")
    make_variant(EN_OUT, "en")
    make_variant(RU_OUT, "ru")
    print("\nDone.")
