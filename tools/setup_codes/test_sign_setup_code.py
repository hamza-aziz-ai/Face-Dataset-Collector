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

    def verify(self, code, base_url, client_id, client_name="Client A"):
        signature = self.folder / "sig"
        signature.write_bytes(base64.b64decode(code["sig"]))
        return subprocess.run(
            ["openssl", "dgst", "-sha256", "-verify", str(self.public), "-signature", str(signature)],
            input=tool.message(base_url, client_id, client_name), capture_output=True,
        ).returncode == 0

    def test_signature_covers_the_address_and_the_client(self):
        code = tool.sign(str(self.key), "k1", "https://client.example.com", "client-a", "Client A")
        self.assertEqual({"base_url", "client_id", "client_name", "kid", "sig"}, set(code))
        self.assertTrue(self.verify(code, "https://client.example.com", "client-a"))
        self.assertFalse(self.verify(code, "https://evil.example.com", "client-a"))
        self.assertFalse(self.verify(code, "https://client.example.com", "client-b"))
        self.assertFalse(self.verify(code, "https://client.example.com", "client-a", "Other Ltd"))

    def test_refuses_codes_the_app_would_refuse(self):
        for base_url, client_id, client_name in (
            ("http://client.example.com", "client-a", "Client A"),
            ("https://client.example.com/path", "client-a", "Client A"),
            ("https://user@client.example.com", "client-a", "Client A"),
            ("https://client.example.com:99999", "client-a", "Client A"),
            ("https://client.example.com:0", "client-a", "Client A"),
            ("https://client.example.com?", "client-a", "Client A"),
            ("https://client_a.example.com", "client-a", "Client A"),
            (f"https://{'a' * 64}.example.com", "client-a", "Client A"),
            ("https://client.example.com", "client a", "Client A"),
            ("https://client.example.com", "", "Client A"),
            ("https://client.example.com", "client-a", ""),
            ("https://client.example.com", "client-a", "x" * 81),
            ("https://client.example.com", "client-a", "Two\nlines"),
        ):
            with self.assertRaises(ValueError, msg=(base_url, client_id, client_name)):
                tool.sign(str(self.key), "k1", base_url, client_id, client_name)


if __name__ == "__main__":
    unittest.main()
