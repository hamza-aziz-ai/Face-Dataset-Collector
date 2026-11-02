"""Checks the signing tool. A throwaway key is made per run and deleted. Run: python -m unittest discover -s tools/setup_codes"""
import base64
import pathlib
import subprocess
import tempfile
import unittest

import sign_setup_code as tool


class SignSetupCodeTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.folder = pathlib.Path(folder.name)
        self.key = self.folder / "throwaway.pem"
        self.public = self.folder / "throwaway.pub"
        subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(self.key)], check=True, capture_output=True)
        subprocess.run(["openssl", "ec", "-in", str(self.key), "-pubout", "-out", str(self.public)], check=True, capture_output=True)

    def verify(self, code, base_url, client_id):
        signature = self.folder / "sig"
        signature.write_bytes(base64.b64decode(code["sig"]))
        return subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(self.public), "-signature", str(signature)],
            input=tool.message(base_url, client_id), capture_output=True,
        ).returncode == 0

    def test_signature_covers_the_address_and_the_client(self):
        code = tool.sign(str(self.key), "k1", "https://client.example.com", "client-a")
        self.assertEqual({"base_url", "client_id", "kid", "sig"}, set(code))
        self.assertTrue(self.verify(code, "https://client.example.com", "client-a"))
        self.assertFalse(self.verify(code, "https://evil.example.com", "client-a"))
        self.assertFalse(self.verify(code, "https://client.example.com", "client-b"))

    def test_refuses_what_the_app_would_refuse(self):
        for base_url, client_id in (
            ("http://client.example.com", "client-a"),
            ("https://client.example.com/path", "client-a"),
            ("https://user@client.example.com", "client-a"),
            ("https://client.example.com", "client a"),
            ("https://client.example.com", ""),
        ):
            with self.assertRaises(ValueError, msg=base_url):
                tool.sign(str(self.key), "k1", base_url, client_id)


if __name__ == "__main__":
    unittest.main()
