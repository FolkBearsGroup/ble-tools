import argparse
import socket
import sys
from datetime import datetime


def parse_args() -> argparse.Namespace:
	parser = argparse.ArgumentParser(description="Simple UDP log receiver")
	parser.add_argument("--host", default="0.0.0.0", help="Bind address (default: 0.0.0.0)")
	parser.add_argument("--port", type=int, default=5000, help="UDP port to listen on (default: 5000)")
	parser.add_argument("--bufsize", type=int, default=65535, help="Receive buffer size")
	return parser.parse_args()


def main() -> int:
	args = parse_args()
	addr = (args.host, args.port)
	sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
	try:
		sock.bind(addr)
	except OSError as exc:
		print(f"Failed to bind {addr}: {exc}", file=sys.stderr)
		return 1

	print(f"Listening on {addr[0]}:{addr[1]} (UDP)")
	try:
		while True:
			data, src = sock.recvfrom(args.bufsize)
			ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
			text = data.decode(errors="replace")
			print(f"[{ts}] {src[0]}:{src[1]} len={len(data)} -> {text}")
	except KeyboardInterrupt:
		print("\nStopped.")
	finally:
		sock.close()
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
