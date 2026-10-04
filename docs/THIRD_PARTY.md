# Calendar dependencies

- PDFBox Android 2.0.27.0: https://github.com/TomRoush/PdfBox-Android — Apache-2.0. Unmodified library; license and notice are included in app assets/licenses.
- JExcelAPI 2.6.12: https://sourceforge.net/projects/jexcelapi/ — Copyright Andrew Khan, LGPL-2.1-or-later. Unmodified library used to read older XLS calendars. Original source: https://repo.maven.apache.org/maven2/net/sourceforge/jexcelapi/jxl/2.6.12/jxl-2.6.12-sources.jar . License included in app assets/licenses. The repository builds the application from source and the library can be replaced through its Gradle dependency.
- holiday-cn: https://github.com/NateScarlet/holiday-cn — MIT, copyright NateScarlet. Used for public statutory holiday facts when the school calendar explicitly defers to State Council arrangements. The source retains the State Council notice URLs; license is included in app assets/licenses.

Official school calendar fixtures are referenced in app/src/androidTest/assets/calendars/SOURCES.md. They are test-only documents, not runtime annual overrides.
