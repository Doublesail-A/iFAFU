import unittest
from pathlib import Path
from sync import document_text, parse, holiday_records

FIXTURES = Path(__file__).with_name('fixtures')


class SchoolDocumentTests(unittest.TestCase):
    def published(self, filename, year, term):
        return parse(document_text((FIXTURES / filename).read_bytes(), filename), year, term)

    def test_seven_published_document_formats_and_semesters(self):
        cases = [
            ('c2011a432573.pdf', '2026-2027', '1', '2026-08-30'),
            ('c2011a432574.pdf', '2026-2027', '2', '2027-02-21'),
            ('c2011a412601.pdf', '2025-2026', '1', '2025-08-31'),
            ('c2011a389470.pdf', '2024-2025', '1', '2024-09-01'),
            ('c2011a397398.docx', '2024-2025', '2', '2025-02-16'),
            ('c2011a364983.xlsx', '2023-2024', '2', '2024-02-25'),
            ('2021-2022-1.xls', '2021-2022', '1', '2021-08-29'),
        ]
        for filename, year, term, first_week in cases:
            with self.subTest(filename=filename):
                self.assertEqual(first_week, self.published(filename, year, term)['firstWeek'])

    def test_actual_closures_makeups_and_no_freshman_training_override(self):
        current = self.published('c2011a432573.pdf', '2026-2027', '1')
        for day in range(1, 8):
            self.assertEqual('国庆节', current['closed'][f'2026-10-{day:02}'])
        self.assertEqual({'2026-10-06': '2026-09-20', '2026-10-07': '2026-10-10'}, current['moves'])
        self.assertNotIn('2026-10-26', current['closed'])
        self.assertIn('元旦', current['deferred'])
        previous = self.published('c2011a412601.pdf', '2025-2026', '1')
        self.assertIn('2025-10-08', previous['closed'])
        self.assertEqual('2025-10-11', previous['moves']['2025-10-08'])
        spring = self.published('c2011a397398.docx', '2024-2025', '2')
        self.assertEqual('2025-04-27', spring['moves']['2025-05-05'])

    def test_future_year_needs_no_annual_code_change(self):
        future = parse('8月29日：学生上课\n国庆节:10月1日至7日放假\n12月31日至1月2日元旦放假', '2033-2034', '1')
        records = holiday_records(future['closed'], future['moves'])
        self.assertEqual('2033-08-28', future['firstWeek'])
        self.assertTrue(any(h['from'] == '2033-12-31' and h['days'] == 3 for h in records))

    def test_unknown_arrangements_fail_without_publishing_empty_data(self):
        for text in ['国庆节:十月一日至七日放假', '国庆节:10月1日至40日放假', '国庆节:10月1日至7日放假\n10月10日补上10月7日的课程']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                parse('9月1日：学生上课\n' + text + '\n寒假时间:1月21日至2月20日', '2026-2027', '1')


if __name__ == '__main__':
    unittest.main()
