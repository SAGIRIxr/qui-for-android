import subprocess
import tempfile
import unittest
from pathlib import Path
from check_release import validate


class ReleaseValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Release test')
        self.git('config', 'user.email', 'test@example.invalid')
        (self.root / 'app').mkdir()
        self.write_version('0.5.0', 11)
        self.commit()
        self.git('tag', 'v0.5.0')
        self.write_version('0.5.1', 12)
        self.commit()
        self.git('tag', 'v0.5.1')

    def git(self, *args):
        subprocess.run(['git', '-C', str(self.root), *args], check=True, capture_output=True)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')

    def write_version(self, name, code):
        (self.root / 'app/build.gradle.kts').write_text(
            f'versionName = "{name}"\nversionCode = {code}\n', encoding='utf-8')
        for filename in ('CHANGELOG.md', 'CHANGELOG.zh-CN.md'):
            (self.root / filename).write_text(f'## {name}\n\n### Fixed\n- Update\n', encoding='utf-8')

    def test_valid_release(self):
        self.assertEqual(('0.5.1', 12), validate(self.root, 'v0.5.1'))

    def test_tag_version_mismatch(self):
        with self.assertRaisesRegex(ValueError, 'versionName'):
            validate(self.root, 'v0.5.0')

    def test_manual_run_wrong_checkout(self):
        (self.root / 'extra').write_text('new commit')
        self.commit()
        with self.assertRaisesRegex(ValueError, 'Checkout'):
            validate(self.root, 'v0.5.1')

    def test_missing_chinese_notes(self):
        (self.root / 'CHANGELOG.zh-CN.md').write_text('## 0.5.0\n- Old')
        with self.assertRaisesRegex(ValueError, 'CHANGELOG.zh-CN'):
            validate(self.root, 'v0.5.1')

    def test_reused_version_code(self):
        self.write_version('0.5.1', 11)
        with self.assertRaisesRegex(ValueError, 'versionCode'):
            validate(self.root, 'v0.5.1')

    def test_invalid_tag(self):
        with self.assertRaisesRegex(ValueError, 'tag must'):
            validate(self.root, 'main')


if __name__ == '__main__':
    unittest.main()
