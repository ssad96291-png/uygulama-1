#!/usr/bin/env python3
"""Loopback-only DNS fixture for the Android emulator, never a public resolver.

Start before the emulator, and launch the emulator with -dns-server 127.0.0.1.
Only *.akis.test.invalid resolves, to the emulator's host gateway 10.0.2.2.
Unique names avoid Android's DNS cache when testing VPN start and rule changes.
"""
import socketserver
import struct
import threading


def answer(query):
    if len(query) < 12:
        return None
    ident, flags, questions, _, _, _ = struct.unpack('!6H', query[:12])
    if flags & 0x8000 or questions != 1:
        return None
    labels, pos = [], 12
    while pos < len(query):
        length = query[pos]
        pos += 1
        if length == 0:
            break
        if length > 63 or pos + length > len(query):
            return None
        labels.append(query[pos:pos + length].decode('ascii', errors='replace'))
        pos += length
    if pos + 4 > len(query):
        return None
    qtype, qclass = struct.unpack('!HH', query[pos:pos + 4])
    question = query[12:pos + 4]
    hostname = '.'.join(labels).lower()
    accepted = hostname.endswith('.akis.test.invalid') and qclass == 1
    record = b''
    if accepted and qtype == 1:
        record = b'\xc0\x0c' + struct.pack('!HHIH', 1, 1, 0, 4) + bytes([10, 0, 2, 2])
    response_flags = 0x8080 | (flags & 0x0100) | (0 if accepted else 5)
    return struct.pack('!6H', ident, response_flags, 1, int(bool(record)), 0, 0) + question + record


class UDPHandler(socketserver.BaseRequestHandler):
    def handle(self):
        query, sock = self.request
        response = answer(query)
        if response is not None:
            sock.sendto(response, self.client_address)


class TCPHandler(socketserver.BaseRequestHandler):
    def read_exact(self, size):
        result = b''
        while len(result) < size:
            chunk = self.request.recv(size - len(result))
            if not chunk:
                return None
            result += chunk
        return result

    def handle(self):
        self.request.settimeout(10)
        while True:
            prefix = self.read_exact(2)
            if prefix is None:
                return
            query = self.read_exact(struct.unpack('!H', prefix)[0])
            if query is None:
                return
            response = answer(query)
            if response is not None:
                self.request.sendall(struct.pack('!H', len(response)) + response)


class TCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == '__main__':
    with TCPServer(('127.0.0.1', 53), TCPHandler) as tcp:
        threading.Thread(target=tcp.serve_forever, daemon=True).start()
        with socketserver.ThreadingUDPServer(('127.0.0.1', 53), UDPHandler) as udp:
            print('Local test DNS ready on 127.0.0.1:53', flush=True)
            udp.serve_forever()
