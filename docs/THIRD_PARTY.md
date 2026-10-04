# Calendar data

The application reads small JSON records using the original iFAFU Holiday model. School documents are processed only by the repository workflow; PDFBox and JExcelAPI are no longer included in the APK.

- Public school documents: [sources](../tools/calendars/fixtures/SOURCES.md).
- Statutory holiday facts, used only for festivals the school explicitly defers to the State Council: [NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn), MIT. Its notice URLs remain in the generator source. License retained in app assets/licenses.
- Build-time document readers: pdfplumber (MIT), openpyxl (MIT), python-docx (MIT), xlrd (BSD-3-Clause), Beautiful Soup (MIT). Installed from their published distributions by tools/calendars/requirements.txt; not bundled into the application.
