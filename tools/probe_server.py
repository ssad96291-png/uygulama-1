#!/usr/bin/env python3
"""Loopback-only HTTP+UDP server for Android emulator integration tests."""
import http.server, socket, threading
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        try: size = min(1048576, max(1, int(self.path.rsplit('/',1)[-1])))
        except ValueError: self.send_error(400); return
        self.send_response(200); self.send_header('Content-Length',str(size)); self.end_headers()
        try: self.wfile.write(b'A'*size)
        except (BrokenPipeError, ConnectionResetError): pass
    def log_message(self,*args): pass
def udp():
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        sock.bind(('127.0.0.1',18082))
        while True:
            data,addr=sock.recvfrom(65535);sock.sendto(data,addr)
threading.Thread(target=udp,daemon=True).start()
http.server.ThreadingHTTPServer(('127.0.0.1',18081),Handler).serve_forever()
