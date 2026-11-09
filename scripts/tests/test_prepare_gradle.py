"""Exercise download failure handling without network access or the real cache."""

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class PrepareGradleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        shutil.copy(Path(__file__).resolve().parents[1] / "prepare-gradle.py", self.root / "scripts")
        wrapper = self.root / "gradle/wrapper"
        wrapper.mkdir(parents=True)
        digest = hashlib.sha256(b"verified archive").hexdigest()
        (wrapper / "gradle-wrapper.properties").write_text(
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.5.1-bin.zip\n"
            f"distributionSha256Sum={digest}\n"
            "distributionBase=GRADLE_USER_HOME\nzipStoreBase=GRADLE_USER_HOME\n"
            "distributionPath=wrapper/dists\nzipStorePath=wrapper/dists\n"
        )
        binary = self.root / "bin"
        binary.mkdir()
        curl = binary / "curl"
        curl.write_text(f"#!{sys.executable}\n" + '''
import os
from pathlib import Path
import sys
url = sys.argv[-1]
with open(os.environ['CALLS'], 'a') as log:
    log.write(url + '\\n')
mode = os.environ['DOWNLOAD_MODE']
if mode == 'fail' or (mode == 'fallback' and 'services.gradle.org' in url):
    sys.exit(22)
output = Path(sys.argv[sys.argv.index('--output') + 1])
output.write_bytes(b'corrupt' if mode == 'corrupt' else b'verified archive')
''')
        curl.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
                                GRADLE_USER_HOME=str(self.root / "cache"),
                                CALLS=str(self.root / "calls"), DOWNLOAD_MODE="success")

    def run_prepare(self, mode="success"):
        return subprocess.run([sys.executable, str(self.root / "scripts/prepare-gradle.py")],
                              env=dict(self.environment, DOWNLOAD_MODE=mode),
                              capture_output=True, text=True, check=False)

    def test_official_release_fallback(self):
        result = self.run_prepare("fallback")
        self.assertEqual(0, result.returncode, result.stderr)
        calls = (self.root / "calls").read_text().splitlines()
        self.assertEqual(2, len(calls))
        self.assertIn("github.com/gradle/gradle-distributions/releases/download/v9.5.1/", calls[1])
        archives = list((self.root / "cache").rglob("*.zip"))
        self.assertEqual(1, len(archives))
        self.assertEqual(b"verified archive", archives[0].read_bytes())
        self.assertFalse(list((self.root / "cache").rglob("*.ok")))

    def test_checksum_mismatch_fails_closed(self):
        result = self.run_prepare("corrupt")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("SHA-256 mismatch", result.stderr)
        self.assertFalse(list((self.root / "cache").rglob("*.zip")))
        self.assertEqual(1, len((self.root / "calls").read_text().splitlines()))

    def test_both_routes_fail_without_installing(self):
        result = self.run_prepare("fail")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("both routes", result.stderr)
        self.assertFalse(list((self.root / "cache").rglob("*.zip")))

    def test_verified_cache_avoids_network(self):
        self.assertEqual(0, self.run_prepare().returncode)
        self.assertEqual(0, self.run_prepare("fail").returncode)
        self.assertEqual(1, len((self.root / "calls").read_text().splitlines()))

    def test_bad_cache_is_replaced_with_verified_archive(self):
        self.assertEqual(0, self.run_prepare().returncode)
        archive = next((self.root / "cache").rglob("*.zip"))
        archive.write_bytes(b"broken previous download")
        self.assertEqual(0, self.run_prepare().returncode)
        self.assertEqual(b"verified archive", archive.read_bytes())


if __name__ == "__main__":
    unittest.main()
