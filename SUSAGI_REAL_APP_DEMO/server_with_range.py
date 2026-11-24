import os
import sys
import re
from http.server import HTTPServer, SimpleHTTPRequestHandler
from socketserver import ThreadingMixIn

DIRECTORY = r"D:\SuSagi_Guardian_V2\SUSAGI_REAL_APP_DEMO"
PORT = 8080

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class RangeRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def guess_type(self, path):
        if path.endswith(".apk"):
            return "application/vnd.android.package-archive"
        return super().guess_type(path)

    def end_headers(self):
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def do_HEAD(self):
        f = self.send_head()
        if f:
            f.close()

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return super().send_head()

        try:
            f = open(path, 'rb')
        except OSError:
            self.send_error(404, "File not found")
            return None

        fs = os.fstat(f.fileno())
        total_length = fs.st_size

        # Check for Range header
        range_header = self.headers.get('Range')
        if range_header:
            match = re.match(r"bytes=(\d+)-(\d*)", range_header)
            if match:
                start = int(match.group(1))
                end = int(match.group(2)) if match.group(2) else total_length - 1
                if start >= total_length:
                    self.send_error(416, "Requested Range Not Satisfiable")
                    self.send_header("Content-Range", f"bytes */{total_length}")
                    self.end_headers()
                    f.close()
                    return None

                length = end - start + 1
                self.send_response(206)
                self.send_header("Content-Type", self.guess_type(path))
                self.send_header("Content-Range", f"bytes {start}-{end}/{total_length}")
                self.send_header("Content-Length", str(length))
                if path.endswith(".apk"):
                    self.send_header("Content-Disposition", 'attachment; filename="susagi-guardian-v2.apk"')
                self.end_headers()
                f.seek(start)
                self.range_length = length
                return f

        self.send_response(200)
        self.send_header("Content-Type", self.guess_type(path))
        self.send_header("Content-Length", str(total_length))
        if path.endswith(".apk"):
            self.send_header("Content-Disposition", 'attachment; filename="susagi-guardian-v2.apk"')
        self.end_headers()
        self.range_length = total_length
        return f

    def copyfile(self, source, outputfile):
        bytes_to_send = getattr(self, 'range_length', None)
        buf_size = 64 * 1024
        try:
            if bytes_to_send is not None:
                remaining = bytes_to_send
                while remaining > 0:
                    chunk = source.read(min(remaining, buf_size))
                    if not chunk:
                        break
                    outputfile.write(chunk)
                    remaining -= len(chunk)
            else:
                while True:
                    chunk = source.read(buf_size)
                    if not chunk:
                        break
                    outputfile.write(chunk)
        except (ConnectionResetError, ConnectionAbortedError, BrokenPipeError):
            pass  # Normal when client pauses or finishes range chunk

if __name__ == "__main__":
    server = ThreadedHTTPServer(("0.0.0.0", PORT), RangeRequestHandler)
    print(f"Serving {DIRECTORY} on port {PORT} with HTTP Range & APK MIME support...")
    server.serve_forever()
