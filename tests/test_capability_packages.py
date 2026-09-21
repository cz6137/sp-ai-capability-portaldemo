import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class PublicCapabilityPackagesTest(unittest.TestCase):
    def test_only_platform_adapter_skill_is_published_as_source(self):
        skill_dirs = sorted(path.name for path in (ROOT / 'capabilities').iterdir() if (path / 'SKILL.md').is_file())
        self.assertEqual(skill_dirs, ['platform-skill-adapter'])

    def test_all_public_manifests_are_drafts(self):
        manifests = []
        for path in sorted((ROOT / 'capabilities').glob('*/capability.json')):
            manifests.append(json.loads(path.read_text(encoding='utf-8')))
        self.assertEqual(len(manifests), 4)
        self.assertTrue(all(item['governance']['status'] == 'DRAFT' for item in manifests))

    def test_adapter_cli_is_available_without_extra_dependencies(self):
        script = ROOT / 'capabilities/platform-skill-adapter/scripts/adapt.py'
        result = subprocess.run(
            [sys.executable, '-B', '-X', 'utf8', str(script), '--help'],
            cwd=ROOT, capture_output=True, text=True, encoding='utf-8', check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('inspect', result.stdout)


if __name__ == '__main__':
    unittest.main()
