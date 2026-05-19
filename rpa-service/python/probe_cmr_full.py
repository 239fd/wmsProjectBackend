"""Walk every cell of CMR table. Identify label-cells vs data-cells (empty + paragraphs)."""
from pathlib import Path
import pythoncom
import win32com.client as win32

TEMPLATE = (Path(__file__).resolve().parent.parent / "templates"
            / "CMR Международная товарно-транспортная накладная.doc")

pythoncom.CoInitialize()
app = win32.DispatchEx("Word.Application")
app.Visible = False
app.DisplayAlerts = 0
try:
    doc = app.Documents.Open(str(TEMPLATE), ConfirmConversions=False, ReadOnly=True)
    try:
        tbl = doc.Tables(1)
        print(f"Table: {tbl.Rows.Count}x{tbl.Columns.Count}\n")
        for r in range(1, tbl.Rows.Count + 1):
            for c in range(1, tbl.Columns.Count + 1):
                try:
                    cell = tbl.Cell(r, c)
                except Exception:
                    continue
                try:
                    txt = (cell.Range.Text or "").replace("\r", " | ").replace("\a", "").strip()
                except Exception:
                    continue
                # Show empty cells too so we can spot data slots
                if len(txt) > 80:
                    txt = txt[:80] + "…"
                marker = "DATA" if not txt or txt == "|" else "LBL "
                print(f"  [{r:>2},{c:>2}] {marker}: {txt}")
    finally:
        doc.Close(False)
finally:
    app.Quit()
    pythoncom.CoUninitialize()
